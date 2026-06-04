import cv2
import numpy as np
import os
import json
import pickle

from app.config import settings
from app.database import SessionLocal
from app.models import Session, SessionResult
from processing.pose_processor import PoseProcessor
from processing.pose_features import process_historial, detect_view, FEATURE_COLS
from processing.phase_predictor import PhasePredictor
from processing.rep_features import extract_rep_features, KPI_NAMES, CLASS_TO_SCORE
from processing.rep_report import save_visual_report
from processing.visualization import generate_skeleton_video

# ──────────────────────────────────────────────────────────────────────────────
# Flag de DESARROLLADOR — export del JSON de etiquetado tras cada análisis.
#
# Cuando está en True, además de guardar los resultados en BD se escribe:
#   · el .npy de features en entrenamiento/archivos_entrenamiento/
#   · un snippet JSON (propuesta_<video>.json) con las predicciones del modelo
#     en formato listo para revisar a mano y añadir al dataset de entrenamiento.
#
# Es una herramienta interna para seguir mejorando la red: NO afecta a la app
# ni a la API. Poner a False para desactivarlo por completo.
# ──────────────────────────────────────────────────────────────────────────────
EXPORT_TRAINING_JSON = True
TRAINING_NPY_DIR  = settings.BASE_DIR / "entrenamiento" / "archivos_entrenamiento"
TRAINING_JSON_DIR = settings.BASE_DIR / "entrenamiento" / "propuestas_analisis"


# ── Modelos cargados UNA sola vez al importar el módulo ────────────────────────
#    Evita recargar desde disco en cada análisis.
#      · BiLSTM  → conteo de repeticiones (segmentación de fases)
#      · XGBoost → predicción de KPIs por repetición (frontal / lateral)
_phase_predictor = PhasePredictor(
    model_path=settings.MODEL_DIR / "bilstm_phases.keras",
    stats_path=settings.MODEL_DIR / "bilstm_phases.normstats.npz",
)


def _load_xgb(path) -> dict:
    with open(path, "rb") as f:
        return pickle.load(f)


_xgb_frontal = _load_xgb(settings.MODEL_DIR / "xgb_squat_models_frontal.pkl")
_xgb_lateral = _load_xgb(settings.MODEL_DIR / "xgb_squat_models_lateral.pkl")


def _predict_rep_kpis(payload: dict, feats: np.ndarray) -> dict:
    """
    Devuelve {kpi: clase 1/2/3 | None} aplicando los modelos XGBoost del perfil.
    Los KPIs no presentes en el payload (p.ej. valgo/simetría en lateral) → None.
    """
    modelos     = payload["models"]
    binary_kpis = set(payload.get("binary_kpis", []))
    out = {}
    for kpi in KPI_NAMES:
        if kpi in modelos:
            raw = int(modelos[kpi].predict(feats.reshape(1, -1))[0])
            if kpi in binary_kpis:
                out[kpi] = 1 if raw == 0 else 3   # binario: problema→1, ok→3
            else:
                out[kpi] = raw + 1                # 0/1/2 → 1/2/3
        else:
            out[kpi] = None
    return out


def process_video(session_id: int, video_path: str):
    """
    Pipeline completo: vídeo → pose → features → reps (BiLSTM) → KPIs (XGBoost) → BD.
    Se ejecuta como BackgroundTask (hilo aparte), por eso crea su propia sesión de BD.
    """
    db = SessionLocal()
    try:
        session = db.get(Session, session_id)
        if session is None:
            return
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
        historial = []          # features por frame (NaN si no hubo detección)
        frame_landmarks = []    # landmarks crudos por frame (o None) → reutilizados en el esqueleto
        NAN_VECTOR = np.full(33 * 4, np.nan, dtype=np.float32)

        while True:
            ret, frame = cap.read()
            if not ret:
                break
            _, pose_vector = pose_processor.process_frame(frame)
            frame_landmarks.append(pose_vector)
            # Mantener un registro por CADA frame del vídeo: si no hay pose, NaN.
            # Así no se "comprime" el eje temporal y las velocidades, el ritmo y la
            # duración se calculan sobre la cadencia real (la interpolación de
            # process_historial rellena los huecos cortos).
            historial.append(pose_vector if pose_vector is not None else NAN_VECTOR)

        cap.release()
        pose_processor.close()

        if not any(v is not None for v in frame_landmarks):
            session.status = "failed"
            session.message = "No se detectó movimiento en el vídeo"
            db.commit()
            return

        # 3. Extracción de features (DataFrame con 18 columnas)
        df = process_historial(historial, fps)

        # 4. Detección automática del perfil de cámara → elige modelo XGBoost
        perfil, _ratio = detect_view(historial)
        payload = _xgb_frontal if perfil == "frontal" else _xgb_lateral

        # 5. Detección de repeticiones con la BiLSTM (segmentación de fases)
        reps = _phase_predictor.predict_reps(df, fps)
        if not reps:
            session.status = "failed"
            session.message = "No se detectaron repeticiones en el vídeo"
            db.commit()
            return

        # 6. Predicción de KPIs por repetición + mapeo 1/2/3 → 0-10 (Opción B)
        reps_results = []
        for start, end in reps:
            feats   = extract_rep_features(df, start, end, fps)
            classes = _predict_rep_kpis(payload, feats)              # {kpi: 1/2/3|None}
            scores  = {kpi: (CLASS_TO_SCORE[c] if c is not None else None)
                       for kpi, c in classes.items()}               # {kpi: 0-10|None}
            measurable = [v for v in scores.values() if v is not None]
            overall    = round(float(np.mean(measurable)), 2) if measurable else None
            reps_results.append({
                "start": int(start), "end": int(end),
                "classes": classes, "scores": scores, "overall": overall,
            })

        # 7. Reporte visual (PNG)
        user_report_dir = str(settings.REPORT_DIR / str(session.user_id))
        report = save_visual_report(reps_results, video_path, perfil, user_report_dir, fps)

        # 8. Guardar resultados en BD y marcar como completado.
        #    ── Commit AQUÍ para que la app reciba los resultados de inmediato,
        #       sin esperar a la generación del vídeo de esqueleto.
        def _sc(scores: dict, kpi: str):
            v = scores.get(kpi)
            return round(v, 2) if v is not None else None

        for i, r in enumerate(reps_results, start=1):
            s = r["scores"]
            db.add(SessionResult(
                session_id     = session_id,
                rep_number     = i,
                depth_score    = _sc(s, "depth"),
                torso_score    = _sc(s, "torso"),
                knees_score    = _sc(s, "knees"),      # valgo de rodilla
                symmetry_score = _sc(s, "symmetry"),
                rhythm_score   = _sc(s, "rhythm"),
                overall_score  = r["overall"],
                report_image_path = report["report_path"],
            ))

        session.status  = "completed"
        session.message = f"Análisis completado: {len(reps_results)} repeticiones detectadas"
        db.commit()   # ← a partir de aquí el polling de la app detecta "completed"

        # 8b. Export del JSON de etiquetado (solo desarrollador, flag arriba)
        if EXPORT_TRAINING_JSON:
            try:
                _export_training_json(df, reps_results, perfil, video_path)
            except Exception as e_json:
                print(f"[training-json] Error exportando snippet: {e_json}")

        # 9. Generar vídeo con esqueleto superpuesto (no bloquea la respuesta)
        try:
            skel_dir = str(settings.REPORT_DIR / str(session.user_id))
            os.makedirs(skel_dir, exist_ok=True)
            base_name     = os.path.splitext(os.path.basename(video_path))[0]
            skel_out_path = os.path.join(skel_dir, f"{base_name}_skeleton.mp4")

            # Reutiliza los landmarks ya calculados en el análisis: NO se vuelve a
            # ejecutar MediaPipe (antes el vídeo se procesaba dos veces).
            ok = generate_skeleton_video(video_path, skel_out_path, frame_landmarks)

            if ok:
                session.skeleton_video_path = skel_out_path
                db.commit()
        except Exception as e_skel:
            print(f"[skeleton] Error generando vídeo de esqueleto: {e_skel}")

    except Exception as e:
        db.rollback()
        session = db.get(Session, session_id)
        if session:
            session.status = "failed"
            session.message = str(e)[:500]
            db.commit()
    finally:
        db.close()


# ──────────────────────────────────────────────────────────────────────────────
# Export del JSON de etiquetado (herramienta de desarrollador)
# ──────────────────────────────────────────────────────────────────────────────

def _export_training_json(df, reps_results, perfil, video_path):
    """
    Guarda el .npy de features + un snippet JSON con las predicciones, en el
    formato esperado por el pipeline de entrenamiento. Pensado para revisar a
    mano las etiquetas y enriquecer el dataset de la red.

    Formato del snippet (idéntico al de analizar.py):
        { "<video>.npy": [ {start, end, depth, torso, knees, symmetry, rhythm, perfil}, ... ] }
    Cada KPI es la clase "1"/"2"/"3" o "-" si no es medible en este perfil.
    """
    TRAINING_NPY_DIR.mkdir(parents=True, exist_ok=True)
    TRAINING_JSON_DIR.mkdir(parents=True, exist_ok=True)

    stem     = os.path.splitext(os.path.basename(video_path))[0]
    npy_name = f"{stem}.npy"

    # .npy con las 18 columnas en el orden de entrenamiento (FEATURE_COLS).
    # df ya viene en ese orden; el reindex garantiza shape/orden y NaN→0.
    arr_npy = df.reindex(columns=FEATURE_COLS).fillna(0.0).values.astype(np.float32)
    np.save(TRAINING_NPY_DIR / npy_name, arr_npy)

    snippet = []
    for r in reps_results:
        rep = {"start": int(r["start"]), "end": int(r["end"])}
        for kpi in KPI_NAMES:
            c = r["classes"].get(kpi)
            rep[kpi] = "-" if c is None else str(c)
        rep["perfil"] = perfil
        snippet.append(rep)

    out_path = TRAINING_JSON_DIR / f"propuesta_{stem}.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump({npy_name: snippet}, f, indent=2, ensure_ascii=False)
