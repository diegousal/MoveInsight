"""
fases_dataset.py  —  Construccion del dataset para la BiLSTM de fases.

Lee etiquetas_reps.json y los .npy correspondientes, genera etiquetas
frame a frame (0=stand, 1=descent, 2=bottom, 3=ascent) y serializa
el dataset en un .npz listo para entrenar.

Uso:
    python fases_dataset.py
    python fases_dataset.py --out entrenamiento/phases_dataset.npz
"""

import argparse
import json
import numpy as np
from pathlib import Path

# ── Configuracion ─────────────────────────────────────────────────────────────
LABELS_JSON   = Path("entrenamiento/etiquetas_reps.json")
NPY_DIR       = Path("entrenamiento/archivos_entrenamiento")
DEFAULT_OUT   = Path("entrenamiento/phases_dataset.npz")

# Columnas del .npy — importadas desde mirror_utils para mantener una unica
# fuente de verdad. El .npy ahora tiene 18 columnas (antes 13).
from mirror_utils import ALL_COLUMNS, PHASE_COLS

# Indices dentro del array completo (posiciones no cambian: siguen en 0-12)
COL_IDX = [ALL_COLUMNS.index(c) for c in PHASE_COLS]

# Etiquetas de fase
STAND   = 0
DESCENT = 1
BOTTOM  = 2
ASCENT  = 3
N_CLASSES = 4


def _label_sequence(n_frames: int, reps: list) -> np.ndarray:
    """
    Genera un array de etiquetas frame a frame para un video completo.

    Cada rep tiene 5 frames clave:
        [stand_start, descent_start, bottom, ascent_start, stand_end]

    La logica de etiquetado entre hitos es:
        stand_start  ..< descent_start  -> STAND
        descent_start ..< bottom        -> DESCENT
        bottom        ..< ascent_start  -> BOTTOM
        ascent_start  ..< stand_end     -> ASCENT

    Los frames fuera de cualquier rep se marcan como STAND.
    """
    labels = np.full(n_frames, STAND, dtype=np.int8)

    for rep in reps:
        f = rep["frames"]           # [s0, d0, bot, a0, s1]
        if len(f) != 5:
            continue
        s0, d0, bot, a0, s1 = [int(x) for x in f]

        # Limitamos al rango real del array
        s0  = max(s0,  0);        s1  = min(s1,  n_frames - 1)
        d0  = max(d0,  0);        a0  = max(a0,  0)
        bot = max(bot, 0)

        labels[s0 : d0]      = STAND
        labels[d0 : bot]     = DESCENT
        labels[bot: a0]      = BOTTOM
        labels[a0 : s1 + 1]  = ASCENT

    return labels


def build_dataset(labels_path: Path, npy_dir: Path) -> tuple:
    """
    Devuelve (sequences, label_seqs, names):
        sequences  : lista de arrays float32 (T, 8)
        label_seqs : lista de arrays int8    (T,)
        names      : lista de nombres de archivo
    """
    with open(labels_path, "r", encoding="utf-8") as f:
        etiquetas = json.load(f)

    sequences, label_seqs, names = [], [], []
    skipped = []

    for npy_name, entry in etiquetas.items():
        npy_path = npy_dir / npy_name
        if not npy_path.exists():
            skipped.append(f"  FALTA: {npy_name}")
            continue

        reps = entry.get("reps", [])
        if not reps:
            skipped.append(f"  SIN REPS: {npy_name}")
            continue

        # Cargar .npy  (T, 13)
        arr = np.load(npy_path)                    # float32, (T, 13)
        if arr.ndim != 2 or arr.shape[1] != len(ALL_COLUMNS):
            skipped.append(f"  FORMA INESPERADA {arr.shape}: {npy_name}")
            continue

        # Seleccionar las 8 columnas de fase
        seq = arr[:, COL_IDX].astype(np.float32)  # (T, 8)

        # Generar etiquetas
        labels = _label_sequence(len(seq), reps)

        sequences.append(seq)
        label_seqs.append(labels)
        names.append(npy_name)

    return sequences, label_seqs, names, skipped


def z_score_stats(sequences: list) -> tuple:
    """
    Calcula media y desviacion tipica globales (columna a columna)
    sobre todos los frames de entrenamiento.
    """
    all_frames = np.concatenate(sequences, axis=0)   # (N_total, 8)
    mu  = all_frames.mean(axis=0)
    std = all_frames.std(axis=0)
    std[std < 1e-6] = 1.0   # evitar division por cero en columnas constantes
    return mu.astype(np.float32), std.astype(np.float32)


def normalize(sequences: list, mu: np.ndarray, std: np.ndarray) -> list:
    return [(s - mu) / std for s in sequences]


def main(out_path: Path):
    print("Construyendo dataset de fases BiLSTM...")
    print(f"  Etiquetas : {LABELS_JSON}")
    print(f"  .npy dir  : {NPY_DIR}")

    sequences, label_seqs, names, skipped = build_dataset(LABELS_JSON, NPY_DIR)

    if skipped:
        print(f"\nArchivos omitidos ({len(skipped)}):")
        for s in skipped:
            print(s)

    if not sequences:
        print("ERROR: no se encontro ningun archivo valido.")
        return

    # Estadisticas globales (de referencia - el entrenamiento las recalcula
    # despues del split y la augmentacion por espejo)
    mu, std = z_score_stats(sequences)

    # Distribucion de clases
    all_labels = np.concatenate(label_seqs)
    total = len(all_labels)
    print(f"\nVideos cargados : {len(sequences)}")
    print(f"Frames totales  : {total}")
    print("Distribucion de fases:")
    for clase, nombre in [(STAND, "Stand"), (DESCENT, "Descent"),
                           (BOTTOM, "Bottom"), (ASCENT, "Ascent")]:
        n = int((all_labels == clase).sum())
        print(f"  {nombre:<10} {n:6d}  ({100*n/total:.1f}%)")

    # Guardar SECUENCIAS RAW (sin normalizar). El training las normaliza
    # despues de aplicar el espejo en el conjunto de entrenamiento.
    out_path.parent.mkdir(parents=True, exist_ok=True)
    np.savez(
        out_path,
        sequences  = np.array(sequences, dtype=object),
        labels     = np.array(label_seqs, dtype=object),
        names      = np.array(names),
        mu         = mu,        # referencia global (no se aplica aqui)
        std        = std,
        col_names  = np.array(PHASE_COLS),
    )
    print(f"\nDataset guardado en: {out_path}")
    print("Columnas de entrada:", PHASE_COLS)
    print("mu (global, referencia) :", np.round(mu, 3))
    print("std (global, referencia):", np.round(std, 3))
    print("\nNota: las secuencias se guardan SIN normalizar.")
    print("training_phases.py aplica espejo sobre train y recalcula mu/std.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=str(DEFAULT_OUT),
                        help="Ruta de salida del .npz")
    args = parser.parse_args()
    main(Path(args.out))
