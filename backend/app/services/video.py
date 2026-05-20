import cv2
import numpy as np
import os
import tensorflow as tf

from app.config import settings
from app.database import SessionLocal
from app.models import Session, SessionResult
from processing.pose_processor import PoseProcessor
from processing.pose_features import process_historial
from processing.rep_report import save_visual_report
from processing.visualization import generate_skeleton_video

# ── Modelo cargado una sola vez al importar el módulo ────────────────────────
#    Evita recargar el Bi-LSTM desde disco en cada análisis (~5-10 s extra).
_model: tf.keras.Model = tf.keras.models.load_model(
    settings.MODEL_DIR / "best_model.h5"
)
_norm_mean: np.ndarray = np.load(settings.MODEL_DIR / "norm_mean.npy")
_norm_std:  np.ndarray = np.load(settings.MODEL_DIR / "norm_std.npy")


def process_video(session_id: int, video_path: str):
    """
    Pipeline completo: vídeo → pose → features → modelo → BD.
    Se ejecuta como BackgroundTask (hilo aparte), por eso crea su propia sesión de BD.
    """
    db = SessionLocal()
    try:
        session = db.query(Session).get(session_id)
        session.status = "processing"
        session.message = "Analizando vídeo..."
        db.commit()

        # 1. Captura de vídeo
        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            raise RuntimeError(f"No se pudo abrir: {video_path}")

        fps = cap.get(cv2.CAP_PROP_FPS) or 30.0

        # 2. Detección de pose frame a frame
        mediapipe_model = str(settings.BASE_DIR / "modelos" / "pose_landmarker_heavy.task")
        pose_processor = PoseProcessor(fps=fps, model_path=mediapipe_model)
        historial = []

        while True:
            ret, frame = cap.read()
            if not ret:
                break
            _, pose_vector = pose_processor.process_frame(frame)
            if pose_vector is not None:
                historial.append(pose_vector)

        cap.release()
        pose_processor.close()

        if not historial:
            session.status = "failed"
            session.message = "No se detectó movimiento en el vídeo"
            db.commit()
            return

        # 3. Extracción de features
        df_angles = process_historial(historial, fps)
        datos = df_angles.fillna(0.0).values

        # 4. Inferencia con el modelo Bi-LSTM (modelo ya cargado en memoria)
        mask = (datos != 0).astype(np.float32)
        datos_norm = (datos - _norm_mean) / _norm_std * mask
        input_tensor = np.expand_dims(datos_norm, axis=0)

        pred_phases, pred_kpis = _model.predict(input_tensor, verbose=0)

        # 5. Reporte visual + datos estructurados
        user_report_dir = str(settings.REPORT_DIR / str(session.user_id))
        result = save_visual_report(
            predicciones=[pred_phases[0], pred_kpis[0]],
            video_source=video_path,
            seq_len=datos_norm.shape[0],
            output_dir=user_report_dir,
            min_run_length=3,
        )

        # 6. Guardar resultados en BD y marcar como completado
        #    ── Commit AQUÍ para que la app reciba los resultados de inmediato,
        #       sin esperar a que termine la generación del vídeo de esqueleto.
        for i, rep in enumerate(result["reps"], start=1):
            kpis = rep["kpis"]
            db.add(SessionResult(
                session_id=session_id,
                rep_number=i,
                depth_score=round(kpis[0], 2),
                torso_score=round(kpis[1], 2),
                stability_score=round(kpis[2], 2),
                knees_score=round(kpis[3], 2),
                rhythm_score=round(kpis[4], 2),
                overall_score=round(sum(kpis) / len(kpis), 2),
                report_image_path=result["report_path"],
            ))

        session.status  = "completed"
        session.message = f"Análisis completado: {len(result['reps'])} repeticiones detectadas"
        db.commit()   # ← a partir de aquí el polling de la app detecta "completed"

        # 7. Generar vídeo con esqueleto superpuesto
        #    Este paso puede tardar pero ya no bloquea la respuesta al cliente.
        try:
            skel_dir = str(settings.REPORT_DIR / str(session.user_id))
            os.makedirs(skel_dir, exist_ok=True)
            base_name     = os.path.splitext(os.path.basename(video_path))[0]
            skel_out_path = os.path.join(skel_dir, f"{base_name}_skeleton.mp4")

            skel_processor = PoseProcessor(fps=fps, model_path=mediapipe_model)
            ok = generate_skeleton_video(video_path, skel_out_path, skel_processor)
            skel_processor.close()

            if ok:
                session.skeleton_video_path = skel_out_path
                db.commit()   # segundo commit solo para el path del esqueleto
        except Exception as e_skel:
            print(f"[skeleton] Error generando vídeo de esqueleto: {e_skel}")

    except Exception as e:
        db.rollback()
        session = db.query(Session).get(session_id)
        if session:
            session.status = "failed"
            session.message = str(e)[:500]
            db.commit()
    finally:
        db.close()
