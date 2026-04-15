import cv2
import numpy as np
import tensorflow as tf

from app.config import settings
from app.database import SessionLocal
from app.models import Session, SessionResult
from processing.pose_processor import PoseProcessor
from processing.pose_features import process_historial
from processing.rep_report import save_visual_report


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

        # 4. Inferencia con el modelo Bi-LSTM
        model = tf.keras.models.load_model(settings.MODEL_DIR / "best_model.h5")
        mean = np.load(settings.MODEL_DIR / "norm_mean.npy")
        std = np.load(settings.MODEL_DIR / "norm_std.npy")

        mask = (datos != 0).astype(np.float32)
        datos_norm = (datos - mean) / std * mask
        input_tensor = np.expand_dims(datos_norm, axis=0)

        pred_phases, pred_kpis = model.predict(input_tensor, verbose=0)
        # --- DEBUG: borrar después ---
        import collections
        phase_seq = pred_phases[0].argmax(axis=-1)
        print(f"DEBUG: {len(historial)} frames, fases únicas: {collections.Counter(phase_seq.tolist())}")
        # --- FIN DEBUG ---

        # 5. Reporte visual + datos estructurados
        user_report_dir = str(settings.REPORT_DIR / str(session.user_id))
        result = save_visual_report(
            predicciones=[pred_phases[0], pred_kpis[0]],
            video_source=video_path,
            seq_len=datos_norm.shape[0],
            output_dir=user_report_dir,
            min_run_length=3,
        )

        # 6. Guardar resultados en BD
        kpi_names = ["depth", "torso", "stability", "knees", "rhythm"]
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

        session.status = "completed"
        session.message = f"Análisis completado: {len(result['reps'])} repeticiones detectadas"
        db.commit()

    except Exception as e:
        db.rollback()
        session = db.query(Session).get(session_id)
        if session:
            session.status = "failed"
            session.message = str(e)[:500]
            db.commit()
    finally:
        db.close()
