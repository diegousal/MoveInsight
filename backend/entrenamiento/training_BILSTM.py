# train_bilstm.py
"""
Script para preparar datos y entrenar el modelo BiLSTM definido en model_bilstm.py

Uso:
    python train_bilstm.py --data_dir /ruta/a/npy --labels /ruta/etiquetas_videos.txt
"""

import os
import argparse
import glob
import numpy as np
import json
import ast
from tqdm import tqdm

import tensorflow as tf
from tensorflow.keras.callbacks import ModelCheckpoint, EarlyStopping, ReduceLROnPlateau

from red_BILSTM import build_bilstm_model

# ---------- Config ----------
DEFAULT_DATA_DIR = "/archivos_entrenamiento"  # el directorio donde guardas los .npy generados por main.py
DEFAULT_LABELS = "etiquetas_videos.json"  # el fichero que subiste (se utiliza en el repo). :contentReference[oaicite:2]{index=2}
MAX_SEQ_LEN = 300   # ventana máxima (frames). Ajusta según memoria.
STRIDE = 150        # solapamiento entre ventanas
BATCH_SIZE = 8
EPOCHS = 60

# fases: 0 = none/outside, 1=down, 2=bottom, 3=up, 4=stand
NUM_PHASES = 5

# ---------- utilidades de parseo ----------
def load_labels(labels_path: str):
    """
    Carga el fichero de etiquetas. Asume que el archivo es una representación
    literal de dict (como el que subiste). Usamos ast.literal_eval para seguridad.
    """
    with open(labels_path, "r", encoding="utf-8") as f:
        labels = json.load(f)
    return labels
def build_frame_targets(video_len: int, reps: list):
    """
    Dado el número de frames y la lista de reps (cada una con 'frames' y 'kpis'),
    construye arrays por frame:
      - phase_targets: entero [0..NUM_PHASES-1]
      - kpi_targets: 5 floats (0..1), 0 fuera de rep (más tarde se puede enmascarar)
    Strategy:
      cada rep tiene frames tuple (start, down, bottom, up, end)
      - frames [start, down) -> phase 1 (down)
      - frames [down, bottom) -> phase 2 (bottom approach)
      - frames [bottom, up) -> phase 3 (up)
      - frames [up, end] -> phase 4 (stand)
    """
    phase = np.zeros((video_len,), dtype=np.int32)
    kpis = np.zeros((video_len, 5), dtype=np.float32)

    for rep in reps:
        frs = tuple(rep["frames"])
        kp = rep["kpis"]
        # normalizar claves del dict de etiquetas a orden: depth, torso, stability, knees, ritmo
        kpi_vec = np.array([kp["depth"], kp["torso"], kp["stability"], kp["knees"], kp["ritmo"]], dtype=np.float32)
        s, down, bottom, up, e = frs
        s = max(0, int(s))
        down = max(0, int(down))
        bottom = max(0, int(bottom))
        up = max(0, int(up))
        e = max(0, int(e))
        # segment ranges (clamp by video_len)
        down = min(down, video_len-1)
        bottom = min(bottom, video_len-1)
        up = min(up, video_len-1)
        e = min(e, video_len-1)

        # assign phases
        phase[s:down] = 1
        phase[down:bottom] = 2
        phase[bottom:up] = 3
        phase[up:e+1] = 4

        # assign kpi for frames in rep range
        kpis[s:e+1, :] = kpi_vec  # same KPIs repeated per frame inside rep

    return phase, kpis

# ---------- Ventanas y padding ----------
def sliding_windows_and_pad(X, phase, kpis, max_len=MAX_SEQ_LEN, stride=STRIDE):
    """
    Corta la secuencia en ventanas de max_len con stride y las rellena (pad) al final.
    Devuelve listas de arrays (padded_X, padded_phase, padded_kpis, lengths)
    """
    seq_len = X.shape[0]
    idx = 0
    windows = []
    while idx < seq_len:
        end = min(idx + max_len, seq_len)
        xw = X[idx:end]
        pw = phase[idx:end]
        kw = kpis[idx:end]
        L = xw.shape[0]
        if L < max_len:
            pad_len = max_len - L
            xw = np.vstack([xw, np.zeros((pad_len, X.shape[1]), dtype=X.dtype)])
            pw = np.concatenate([pw, np.zeros((pad_len,), dtype=pw.dtype)])
            kw = np.vstack([kw, np.zeros((pad_len, kw.shape[1]), dtype=kw.dtype)])
        windows.append((xw, pw, kw, L))
        if end == seq_len:
            break
        idx += stride
    return windows

# ---------- Dataset generator ----------
def make_dataset_from_dir(data_dir: str, labels: dict, max_len=MAX_SEQ_LEN, stride=STRIDE):
    Xs, phases, kpis, lengths = [], [], [], []
    # file_list = sorted(glob.glob(os.path.join(data_dir, "*.npy")))
    all_files = sorted(glob.glob(os.path.join(data_dir, "*.npy")))
    # mantener solo los que aparecen en el JSON de labels
    file_list = [fp for fp in all_files if os.path.basename(fp) in labels]
    print(f"Encontrados {len(file_list)} .npy etiquetados en {data_dir}")
    for fp in tqdm(file_list, desc="Cargando vídeos"):
        fname = os.path.basename(fp)
        arr = np.load(fp)  # forma (T, features)
        # Si las columnas ya están (15), ok. Si hay fila/col extra, recortar:
        if arr.ndim != 2:
            raise ValueError(f"Formato .npy inesperado en {fp}: ndim={arr.ndim}")
        T = arr.shape[0]
        # Obtener etiquetas para este fichero si existen
        label_entry = labels.get(fname, None)
        if label_entry is None:
            # Si no hay etiqueta, marcamos todo como fuera (phase 0) y kpis ceros
            phase_target = np.zeros((T,), dtype=np.int32)
            kpi_target = np.zeros((T,5), dtype=np.float32)
        else:
            phase_target, kpi_target = build_frame_targets(T, label_entry["reps"])

        windows = sliding_windows_and_pad(arr, phase_target, kpi_target, max_len=max_len, stride=stride)
        for xw, pw, kw, L in windows:
            Xs.append(xw)
            phases.append(pw)
            kpis.append(kw)
            lengths.append(L)

    Xs = np.stack(Xs, axis=0)
    phases = np.stack(phases, axis=0)
    kpis = np.stack(kpis, axis=0)
    lengths = np.array(lengths, dtype=np.int32)
    return Xs, phases, kpis, lengths

# ---------- Normalización ----------
def compute_normalization(Xs):
    """
    Xs: array (N_windows, max_len, n_features)
    Calcula mean/std por feature (ignora filas de padding = 0)
    """
    mask = np.any(Xs != 0.0, axis=2)  # True donde hay datos reales
    sums = np.sum(Xs, axis=(0,1))
    counts = np.sum(mask, axis=(0,1))
    # counts es un escalar por feature, pero se hizo sum incorrectly; hacemos por-feature:
    # Recomputamos de forma segura:
    flat = Xs.reshape(-1, Xs.shape[-1])
    valid = flat[np.any(flat != 0.0, axis=1)]
    if valid.shape[0] == 0:
        mean = np.zeros((Xs.shape[-1],), dtype=np.float32)
        std = np.ones((Xs.shape[-1],), dtype=np.float32)
    else:
        mean = np.mean(valid, axis=0)
        std = np.std(valid, axis=0)
        std[std == 0] = 1.0
    return mean, std

# ---------- Utilities for tf dataset ----------
def create_tf_dataset(Xs, phases, kpis, lengths, batch_size=BATCH_SIZE, shuffle=True):
    dataset = tf.data.Dataset.from_tensor_slices((Xs, phases, kpis, lengths))
    if shuffle:
        dataset = dataset.shuffle(buffer_size=1024)
    # We will return (inputs, outputs, sample_weights) where sample_weights for kpi_out
    # son 1 para frames que pertenecen a una rep (phase != 0), 0 para padding/outside.
    def map_fn(x, p, k, L):
        # sample weights: para phase_out podemos dar peso 1 everywhere; para kpi_out 1 only where p != 0
        kpi_mask = tf.cast(tf.not_equal(p, 0), tf.float32)
        # expand to (timesteps,) -> (timesteps, 1) by TimeDistributed handling at compile training
        return x, {"phase_out": p, "kpi_out": k}, {"phase_out": tf.ones_like(p, dtype=tf.float32), "kpi_out": kpi_mask}
    dataset = dataset.map(map_fn, num_parallel_calls=tf.data.AUTOTUNE)
    dataset = dataset.batch(batch_size).prefetch(tf.data.AUTOTUNE)
    return dataset

# ---------- Postprocessing: detección de rep y agregación de KPIs ----------
def detect_reps_from_phase(pred_phase_seq, threshold=0.5):
    """
    pred_phase_seq: array (T,) con enteros fase por frame (0..4)
    Detecta rep iniciando cuando hay transición de 0/4 -> 1 (o 4->1),
    y finalizando cuando vuelve a 4 (o 1->4). Devuelve lista de (start_idx, end_idx).
    """
    reps = []
    T = len(pred_phase_seq)
    in_rep = False
    start = None
    for i in range(1, T):
        prev = pred_phase_seq[i-1]
        cur = pred_phase_seq[i]
        if not in_rep:
            if prev in (0,4) and cur == 1:  # inicio
                in_rep = True
                start = i-1
        else:
            # terminación: cuando cur becomes 4 (stand) after being up
            if prev in (3,4) and cur == 4:
                end = i
                reps.append((start, end))
                in_rep = False
                start = None
    # si quedó abierto y hay start:
    if in_rep and start is not None:
        reps.append((start, T-1))
    return reps

def aggregate_kpis_for_reps(pred_kpis, rep_ranges):
    """
    pred_kpis: (T,5) float
    rep_ranges: list of (s,e) indices
    Devuelve lista de dicts con kpis promedio por rep.
    """
    out = []
    for s, e in rep_ranges:
        seg = pred_kpis[s:e+1]
        if seg.shape[0] == 0:
            avg = np.zeros((5,), dtype=np.float32)
        else:
            avg = seg.mean(axis=0)
        out.append({
            "start": int(s),
            "end": int(e),
            "kpis": {
                "kpi_profundidad": float(avg[0]),
                "kpi_torso": float(avg[1]),
                "kpi_estabilidad": float(avg[2]),
                "kpi_rodillas": float(avg[3]),
                "kpi_ritmo": float(avg[4]),
            }
        })
    return out

# ---------- Entrenamiento ----------
def main(args):
    labels = load_labels(args.labels)
    Xs, phases, kpis, lengths = make_dataset_from_dir(args.data_dir, labels,
                                                     max_len=args.max_len, stride=args.stride)

    print("X windows shape:", Xs.shape)
    mean, std = compute_normalization(Xs)
    print("mean/std computed. Normalizando...")

    # Normalizar inplace
    Xs = (Xs - mean) / std

    # split train/val (aleatorio)
    N = Xs.shape[0]
    perm = np.random.permutation(N)
    split = int(N * 0.85)
    train_idx = perm[:split]
    val_idx = perm[split:]

    X_train, p_train, k_train, len_train = Xs[train_idx], phases[train_idx], kpis[train_idx], lengths[train_idx]
    X_val, p_val, k_val, len_val = Xs[val_idx], phases[val_idx], kpis[val_idx], lengths[val_idx]

    train_ds = create_tf_dataset(X_train, p_train, k_train, len_train, batch_size=args.batch_size, shuffle=True)
    val_ds = create_tf_dataset(X_val, p_val, k_val, len_val, batch_size=args.batch_size, shuffle=False)

    model = build_bilstm_model(input_dim=Xs.shape[-1], lstm_units=args.lstm_units, num_layers=args.num_layers, num_phases=NUM_PHASES)
    model.summary()

    # Callbacks
    checkpoint = ModelCheckpoint(args.checkpoint, save_best_only=True, monitor="val_phase_out_sparse_categorical_accuracy", mode="max")
    early = EarlyStopping(monitor="val_phase_out_sparse_categorical_accuracy", patience=8, mode="max", restore_best_weights=True)
    reduce_lr = ReduceLROnPlateau(monitor="val_phase_out_sparse_categorical_accuracy", factor=0.5, patience=4, mode="max")

    model.fit(train_ds,
              validation_data=val_ds,
              epochs=args.epochs,
              callbacks=[checkpoint, early, reduce_lr])

    # Guardar normalización y el modelo final
    np.save(os.path.join(args.output_dir, "norm_mean.npy"), mean)
    np.save(os.path.join(args.output_dir, "norm_std.npy"), std)
    model.save(os.path.join(args.output_dir, "final_bilstm_model"))

    print("Entrenamiento completado. Modelo y normalización guardados en", args.output_dir)

    # Ejemplo de inferencia rápida usando el conjunto de validación (primer batch)
    print("Ejemplo de inferencia en primer vídeo de validación (postproceso):")
    sample_X = X_val[0]
    sample_L = len_val[0]
    sample_in = (sample_X - mean) / std
    sample_in = np.expand_dims(sample_in, axis=0)  # batch dim
    phase_pred, kpi_pred = model.predict(sample_in)
    # phase_pred: (1, max_len, NUM_PHASES) -> tomamos argmax y recortamos según sample_L
    phase_seq = np.argmax(phase_pred[0], axis=-1)[:sample_L]
    kpi_seq = kpi_pred[0][:sample_L]
    rep_ranges = detect_reps_from_phase(phase_seq)
    rep_kpis = aggregate_kpis_for_reps(kpi_seq, rep_ranges)
    print(f"Se detectaron {len(rep_kpis)} repeticiones en la ventana (ejemplo).")
    for i, r in enumerate(rep_kpis):
        print(f"Rep {i+1}: frames {r['start']} -> {r['end']}, kpis: {r['kpis']}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--data_dir", type=str, default=DEFAULT_DATA_DIR, help="Directorio con ficheros .npy")
    parser.add_argument("--labels", type=str, default=DEFAULT_LABELS, help="Fichero etiquetas (txt).")
    parser.add_argument("--output_dir", type=str, default="./model_output", help="Donde guardar checkpoints y modelo final")
    parser.add_argument("--max_len", type=int, default=MAX_SEQ_LEN)
    parser.add_argument("--stride", type=int, default=STRIDE)
    parser.add_argument("--batch_size", type=int, default=BATCH_SIZE)
    parser.add_argument("--epochs", type=int, default=EPOCHS)
    parser.add_argument("--lstm_units", type=int, default=128)
    parser.add_argument("--num_layers", type=int, default=2)
    parser.add_argument("--checkpoint", type=str, default="./best_model.h5")
    args = parser.parse_args()

    os.makedirs(args.output_dir, exist_ok=True)
    main(args)