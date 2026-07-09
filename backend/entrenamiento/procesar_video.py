"""
procesar_video.py — Pipeline de entrenamiento en un solo paso.

Le pasas un vídeo .mp4 y ejecuta el pipeline completo SIN levantar la API:
  pose (MediaPipe) → features (.npy) → reps (BiLSTM) → KPIs (XGBoost)
y deja el resultado listo para entrenar:
  · el .npy en          entrenamiento/archivos_entrenamiento/<nombre>.npy
  · las etiquetas DENTRO de entrenamiento/etiquetas_kpis.json  (con backup .bak)
  · el vídeo copiado a  entrenamiento/dataset/<nombre>.mp4

Las etiquetas insertadas son las PREDICCIONES del modelo actual: revísalas y
corrige las clases que no te convenzan directamente en etiquetas_kpis.json.
Después: python entrenamiento/training_xgb.py

USO (desde cualquier sitio; no requiere PYTHONPATH):
    python entrenamiento/procesar_video.py --video mi_video.mp4 --nombre 150
    python entrenamiento/procesar_video.py --video mi_video.mp4            # usa el nombre del fichero
    python entrenamiento/procesar_video.py --carpeta C:\\videos_nuevos     # lote completo
    python entrenamiento/procesar_video.py --carpeta ... --solo_npy        # regenerar solo .npy
    python entrenamiento/procesar_video.py --video x.mp4 --no_insertar     # deja propuesta suelta

Flags útiles: --sobrescribir (si la clave ya existe), --skip_existing (lote),
              --model_dir / --labels / --npy_dir / --dataset_dir (rutas alternativas).
"""

import argparse
import json
import shutil
import sys
import time
from pathlib import Path

import cv2
import numpy as np

# ── Rutas base (independientes del directorio de trabajo) ────────────────────
BACKEND = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(BACKEND))                    # → 'processing' importable

from processing.pose_processor import PoseProcessor
from processing.pose_features import process_historial, detect_view, FEATURE_COLS
from processing.phase_predictor import PhasePredictor
from processing.rep_features import extract_rep_features, KPI_NAMES

DEF_MODEL_DIR   = BACKEND / "modelos" / "v16"
DEF_MEDIAPIPE   = BACKEND / "modelos" / "pose_landmarker_heavy.task"
DEF_NPY_DIR     = BACKEND / "entrenamiento" / "archivos_entrenamiento"
DEF_LABELS      = BACKEND / "entrenamiento" / "etiquetas_kpis.json"
DEF_DATASET_DIR = BACKEND / "entrenamiento" / "dataset"
DEF_PROP_DIR    = BACKEND / "entrenamiento" / "propuestas_analisis"

VIDEO_EXTS = {".mp4", ".mov", ".avi", ".mkv", ".m4v", ".webm"}

# Intentar usar la MISMA carpeta de modelos que la API (única fuente de verdad)
def resolve_model_dir(cli_value: str | None) -> Path:
    if cli_value:
        return Path(cli_value)
    try:
        from app.config import settings                     # usa .env local
        return Path(settings.MODEL_DIR)
    except Exception:
        return DEF_MODEL_DIR


# ── Modelos (carga perezosa: solo si hay que predecir KPIs) ──────────────────
_models = {}

def load_models(model_dir: Path):
    if _models:
        return _models
    import pickle
    _models["phase"] = PhasePredictor(
        model_path=model_dir / "bilstm_phases.keras",
        stats_path=model_dir / "bilstm_phases.normstats.npz",
    )
    for perfil in ("frontal", "lateral"):
        with open(model_dir / f"xgb_squat_models_{perfil}.pkl", "rb") as f:
            _models[perfil] = pickle.load(f)
    return _models


def predict_rep_kpis(payload: dict, feats: np.ndarray) -> dict:
    """Idéntico al de app/services/video.py: {kpi: 1/2/3 | None}."""
    modelos     = payload["models"]
    binary_kpis = set(payload.get("binary_kpis", []))
    out = {}
    for kpi in KPI_NAMES:
        if kpi in modelos:
            raw = int(modelos[kpi].predict(feats.reshape(1, -1))[0])
            out[kpi] = (1 if raw == 0 else 3) if kpi in binary_kpis else raw + 1
        else:
            out[kpi] = None
    return out


# ── Análisis de un vídeo ──────────────────────────────────────────────────────

def extraer_features(video_path: Path, mediapipe_model: Path):
    """Vídeo → (df 18 columnas, historial, fps). La parte cara (MediaPipe)."""
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise RuntimeError(f"No se pudo abrir el vídeo: {video_path}")
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0

    pose = PoseProcessor(fps=fps, model_path=str(mediapipe_model))
    historial = []
    NAN_VECTOR = np.full(33 * 4, np.nan, dtype=np.float32)
    detectado = False
    while True:
        ret, frame = cap.read()
        if not ret:
            break
        _, pose_vector = pose.process_frame(frame)
        if pose_vector is not None:
            detectado = True
        historial.append(pose_vector if pose_vector is not None else NAN_VECTOR)
    cap.release()
    pose.close()

    if not detectado:
        raise RuntimeError("No se detectó ninguna pose en el vídeo")

    df = process_historial(historial, fps)
    return df, historial, fps


def analizar(video_path: Path, model_dir: Path, mediapipe_model: Path,
             solo_npy: bool = False):
    """Pipeline completo. Devuelve (npy_array, snippet|None, perfil|None, fps)."""
    df, historial, fps = extraer_features(video_path, mediapipe_model)
    arr_npy = df.reindex(columns=FEATURE_COLS).fillna(0.0).values.astype(np.float32)

    if solo_npy:
        return arr_npy, None, None, fps

    m = load_models(model_dir)
    perfil, _ = detect_view(historial)
    payload = m[perfil]

    reps = m["phase"].predict_reps(df, fps)
    if not reps:
        raise RuntimeError("La BiLSTM no detectó repeticiones")

    snippet = []
    for start, end in reps:
        feats   = extract_rep_features(df, int(start), int(end), fps)
        classes = predict_rep_kpis(payload, feats)
        rep = {"start": int(start), "end": int(end)}
        for kpi in KPI_NAMES:
            c = classes.get(kpi)
            rep[kpi] = "-" if c is None else str(c)
        rep["perfil"] = perfil
        snippet.append(rep)

    return arr_npy, snippet, perfil, fps


# ── Inserción en etiquetas_kpis.json ─────────────────────────────────────────

def insertar_etiquetas(labels_path: Path, key: str, snippet: list,
                       sobrescribir: bool = False):
    labels = {}
    if labels_path.exists() and labels_path.stat().st_size > 0:
        with open(labels_path, "r", encoding="utf-8") as f:
            labels = json.load(f)

    if key in labels and not sobrescribir:
        raise SystemExit(f"❌ '{key}' ya existe en {labels_path.name}. "
                         "Usa --sobrescribir o cambia --nombre.")

    # Backup antes de tocar el fichero grande
    if labels_path.exists():
        shutil.copy2(labels_path, labels_path.with_suffix(".json.bak"))

    labels[key] = snippet
    with open(labels_path, "w", encoding="utf-8") as f:
        json.dump(labels, f, indent=2, ensure_ascii=False)


# ── Procesado de un vídeo (orquestación de salidas) ──────────────────────────

def procesar(video_path: Path, args) -> bool:
    nombre = args.nombre if (args.nombre and not args.carpeta) else video_path.stem
    npy_dir  = Path(args.npy_dir);  npy_dir.mkdir(parents=True, exist_ok=True)
    npy_path = npy_dir / f"{nombre}.npy"

    if args.skip_existing and npy_path.exists():
        print(f"  SALTADO (npy ya existe): {video_path.name}")
        return True

    t0 = time.time()
    try:
        arr, snippet, perfil, fps = analizar(
            video_path, resolve_model_dir(args.model_dir),
            Path(args.mediapipe), solo_npy=args.solo_npy,
        )
    except Exception as e:
        print(f"  ❌ {video_path.name}: {e}")
        return False

    np.save(npy_path, arr)
    dt = time.time() - t0
    print(f"  ✅ {video_path.name} → {npy_path.name}  "
          f"(shape={arr.shape}, fps={fps:.0f}, {dt:.1f}s)")

    if args.solo_npy:
        return True

    # Resumen de la propuesta por consola
    print(f"     perfil={perfil} · {len(snippet)} reps:")
    for i, r in enumerate(snippet, 1):
        kpis = "  ".join(f"{k}={r[k]}" for k in KPI_NAMES)
        print(f"       rep {i}: frames {r['start']}-{r['end']}  |  {kpis}")

    key = f"{nombre}.npy"
    if args.no_insertar:
        DEF_PROP_DIR.mkdir(parents=True, exist_ok=True)
        out = DEF_PROP_DIR / f"propuesta_{nombre}.json"
        with open(out, "w", encoding="utf-8") as f:
            json.dump({key: snippet}, f, indent=2, ensure_ascii=False)
        print(f"     Propuesta suelta guardada en: {out}")
    else:
        insertar_etiquetas(Path(args.labels), key, snippet, args.sobrescribir)
        print(f"     Etiquetas insertadas en {Path(args.labels).name} "
              f"(backup en .json.bak)")

    # Copia del vídeo al archivo del dataset
    if not args.no_copiar:
        ds = Path(args.dataset_dir); ds.mkdir(parents=True, exist_ok=True)
        dest = ds / f"{nombre}{video_path.suffix.lower()}"
        if not dest.exists():
            shutil.copy2(video_path, dest)
            print(f"     Vídeo archivado en: dataset/{dest.name}")
    return True


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[1],
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    src = p.add_mutually_exclusive_group(required=True)
    src.add_argument("--video",   help="Vídeo individual a procesar")
    src.add_argument("--carpeta", help="Procesa todos los vídeos de una carpeta")
    p.add_argument("--nombre", help="Nombre destino sin extensión (p.ej. 150). "
                                    "Solo con --video; por defecto, el del fichero")
    p.add_argument("--solo_npy",      action="store_true",
                   help="Solo regenera el .npy (sin BiLSTM/XGBoost ni etiquetas)")
    p.add_argument("--no_insertar",   action="store_true",
                   help="No toca etiquetas_kpis.json; deja propuesta suelta")
    p.add_argument("--sobrescribir",  action="store_true",
                   help="Permite reemplazar una clave ya existente en las etiquetas")
    p.add_argument("--no_copiar",     action="store_true",
                   help="No copia el vídeo a entrenamiento/dataset/")
    p.add_argument("--skip_existing", action="store_true",
                   help="Salta vídeos cuyo .npy ya existe (útil en lote)")
    p.add_argument("--model_dir",   default=None, help="Carpeta de modelos (def.: la de la API)")
    p.add_argument("--mediapipe",   default=str(DEF_MEDIAPIPE))
    p.add_argument("--npy_dir",     default=str(DEF_NPY_DIR))
    p.add_argument("--labels",      default=str(DEF_LABELS))
    p.add_argument("--dataset_dir", default=str(DEF_DATASET_DIR))
    args = p.parse_args()

    if args.video:
        videos = [Path(args.video)]
    else:
        carpeta = Path(args.carpeta)
        if not carpeta.exists():
            raise SystemExit(f"No existe la carpeta {carpeta}")
        videos = sorted(v for v in carpeta.iterdir()
                        if v.suffix.lower() in VIDEO_EXTS)
        if not videos:
            raise SystemExit(f"Sin vídeos en {carpeta}")
        print(f"{len(videos)} vídeos en {carpeta}\n")

    ok = sum(procesar(v, args) for v in videos)
    print(f"\n{'='*58}\nProcesados {ok}/{len(videos)} vídeos.")
    if not args.solo_npy and not args.no_insertar and ok:
        print("⚠️  Recuerda REVISAR las clases insertadas en etiquetas_kpis.json")
        print("    y después reentrenar:  python entrenamiento/training_xgb.py")


if __name__ == "__main__":
    main()
