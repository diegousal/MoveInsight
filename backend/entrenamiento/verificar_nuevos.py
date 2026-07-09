# -*- coding: utf-8 -*-
"""Verifica que los XGBoost recién entrenados reproducen las etiquetas
de los vídeos nuevos (reutiliza los .npy: no vuelve a pasar MediaPipe)."""
import json, pickle, sys
from pathlib import Path
import cv2
import numpy as np
import pandas as pd

BACKEND = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(BACKEND))
from processing.rep_features import extract_rep_features, KPI_NAMES

sys.path.insert(0, str(BACKEND / "entrenamiento"))
from mirror_utils import ALL_COLUMNS

VIDEOS = ["V1","V2","V3","V4","V5","V6","V7","V8","V9","V10","V11","V12","V13","Vp"]
NPY_DIR = BACKEND / "entrenamiento" / "archivos_entrenamiento"
DS_DIR  = BACKEND / "entrenamiento" / "dataset"
LABELS  = BACKEND / "entrenamiento" / "etiquetas_kpis.json"

# Modelos RECIÉN entrenados (backend/, salida de training_xgb.py)
payloads = {}
for perfil in ("frontal", "lateral"):
    with open(BACKEND / f"xgb_squat_models_{perfil}.pkl", "rb") as f:
        payloads[perfil] = pickle.load(f)

def predict(payload, feats):
    out = {}
    binary = set(payload.get("binary_kpis", []))
    for kpi in KPI_NAMES:
        if kpi in payload["models"]:
            raw = int(payload["models"][kpi].predict(feats.reshape(1, -1))[0])
            out[kpi] = (1 if raw == 0 else 3) if kpi in binary else raw + 1
        else:
            out[kpi] = None
    return out

labels = json.load(open(LABELS, encoding="utf-8"))
tot = ok = 0
fallos = []
for v in VIDEOS:
    entry = labels[f"{v}.npy"]
    arr = np.load(NPY_DIR / f"{v}.npy").astype(np.float32)
    df = pd.DataFrame(arr, columns=ALL_COLUMNS[:arr.shape[1]])
    # fps real del vídeo (como hará la app en inferencia)
    fps = 30.0
    for ext in (".mp4", ".mov", ".avi"):
        p = DS_DIR / f"{v}{ext}"
        if p.exists():
            cap = cv2.VideoCapture(str(p)); fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
            cap.release(); break
    perfil = entry[0]["perfil"]
    diffs = []
    for i, rep in enumerate(entry, 1):
        feats = extract_rep_features(df, int(rep["start"]), int(rep["end"]), fps)
        pred = predict(payloads[perfil], feats)
        for kpi in KPI_NAMES:
            esperado = rep[kpi]
            if esperado == "-":
                continue
            tot += 1
            obtenido = str(pred[kpi]) if pred[kpi] is not None else "-"
            if obtenido == esperado:
                ok += 1
            else:
                diffs.append(f"rep {i} {kpi}: etiqueta={esperado} pred={obtenido}")
    estado = "✓ perfecto" if not diffs else f"{len(diffs)} fallos"
    print(f"{v:4} ({perfil}, fps={fps:.0f}): {estado}")
    for d in diffs:
        print(f"      {d}")
    fallos += [(v, d) for d in diffs]

print()
print(f"TOTAL: {ok}/{tot} correctos ({100*ok/tot:.1f}%) · {len(fallos)} fallos")
