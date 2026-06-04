"""
training_phases.py  —  Entrena la BiLSTM de clasificacion de fases.

Arquitectura:
    Masking -> BiLSTM(64) -> Dropout(0.3) -> BiLSTM(32) -> TimeDistributed(Dense(4, softmax))

Entrada : secuencias de longitud variable  (T, 8)  normalizadas con z-score
Salida  : etiqueta por frame  {0=stand, 1=descent, 2=bottom, 3=ascent}

Pipeline:
  1. Carga secuencias RAW desde el .npz (sin normalizar)
  2. Split train/val por video (no por frame)
  3. Augmentacion por espejo SOLO en train -> duplica el dataset
  4. Recalcula mu/std en el train aumentado
  5. Normaliza train y val con esos mu/std
  6. Padding y entrenamiento

Uso:
    python training_phases.py
    python training_phases.py --no_mirror      # sin augmentacion (debug)
    python training_phases.py --epochs 60 --batch 8
"""

import argparse
import json
import numpy as np
from pathlib import Path

from mirror_utils import mirror_phase_seq

# ── Configuracion ─────────────────────────────────────────────────────────────
DATASET_PATH  = Path("entrenamiento/phases_dataset.npz")
MODEL_OUT     = Path("bilstm_phases.keras")
STATS_OUT     = Path("bilstm_phases.normstats.npz")
HISTORY_OUT   = Path("entrenamiento/phases_training_history.json")

N_FEATURES  = 8
N_CLASSES   = 4
LSTM_UNITS  = [64, 32]
DROPOUT     = 0.3
MASK_VALUE  = 0.0

DEFAULT_EPOCHS = 80
DEFAULT_BATCH  = 8
VAL_SPLIT      = 0.15
PATIENCE       = 15      # early stopping
DEFAULT_FPS    = 30.0

# Pesos de clase: Bottom es minoritario (~7%), le damos mas peso
CLASS_WEIGHTS = {0: 1.2, 1: 1.0, 2: 2.0, 3: 1.0}


def load_dataset(path: Path):
    """Carga secuencias RAW (sin normalizar) y etiquetas."""
    data = np.load(path, allow_pickle=True)
    sequences = list(data["sequences"])   # lista de (T, 8) float32 RAW
    labels    = list(data["labels"])      # lista de (T,) int8
    mu        = data["mu"]                # referencia (no se aplica aqui)
    std       = data["std"]
    names     = list(data["names"])
    return sequences, labels, mu, std, names


def compute_zscore_stats(sequences):
    """Media y std globales por columna sobre todos los frames."""
    all_frames = np.concatenate(sequences, axis=0)
    mu  = all_frames.mean(axis=0).astype(np.float32)
    std = all_frames.std(axis=0).astype(np.float32)
    std[std < 1e-6] = 1.0
    return mu, std


def normalize(sequences, mu, std):
    return [((s - mu) / std).astype(np.float32) for s in sequences]


def augment_with_mirror(sequences_train_raw, labels_train, names_train, fps):
    """
    Por cada secuencia de entrenamiento crea su version reflejada.
    Las etiquetas de fase son invariantes al espejo (stand sigue siendo stand,
    descent sigue siendo descent, etc.).

    Devuelve sequences, labels, names duplicados con sufijo "_mir" en mirrors.
    """
    augmented_seqs   = list(sequences_train_raw)
    augmented_labels = list(labels_train)
    augmented_names  = list(names_train)

    for seq, lab, name in zip(sequences_train_raw, labels_train, names_train):
        seq_mir = mirror_phase_seq(seq, fps=fps)
        augmented_seqs.append(seq_mir)
        augmented_labels.append(lab.copy())
        augmented_names.append(f"{name}_mir")

    return augmented_seqs, augmented_labels, augmented_names


def pad_sequences(sequences, labels, mask_value=MASK_VALUE, T_max=None):
    """
    Rellena secuencias a la longitud T_max con mask_value.
    Si T_max es None, usa el maximo de las secuencias dadas.

    Devuelve:
        X  (N, T_max, 8)
        Y  (N, T_max, 4)  one-hot
        SW (N, T_max)     sample_weight (frames padding -> 0)
    """
    import tensorflow as tf

    if T_max is None:
        T_max = max(len(s) for s in sequences)

    X  = np.full((len(sequences), T_max, N_FEATURES), mask_value, dtype=np.float32)
    Y  = np.zeros((len(sequences), T_max, N_CLASSES), dtype=np.float32)
    SW = np.zeros((len(sequences), T_max), dtype=np.float32)

    for i, (seq, lab) in enumerate(zip(sequences, labels)):
        T = min(len(seq), T_max)
        X[i, :T, :]  = seq[:T]
        Y[i, :T, :]  = tf.keras.utils.to_categorical(lab[:T], num_classes=N_CLASSES)
        SW[i, :T]    = np.array([CLASS_WEIGHTS[int(c)] for c in lab[:T]], dtype=np.float32)

    return X, Y, SW, T_max


def build_model(T_max: int):
    import tensorflow as tf
    from tensorflow.keras import layers, Model

    inp = layers.Input(shape=(T_max, N_FEATURES), name="input")
    x = layers.Masking(mask_value=MASK_VALUE)(inp)

    x = layers.Bidirectional(
        layers.LSTM(LSTM_UNITS[0], return_sequences=True), name="bilstm_1"
    )(x)
    x = layers.Dropout(DROPOUT, name="drop_1")(x)

    x = layers.Bidirectional(
        layers.LSTM(LSTM_UNITS[1], return_sequences=True), name="bilstm_2"
    )(x)
    x = layers.Dropout(DROPOUT, name="drop_2")(x)

    out = layers.TimeDistributed(
        layers.Dense(N_CLASSES, activation="softmax"), name="output"
    )(x)

    return Model(inp, out, name="BiLSTM_phases")


def train(dataset_path: Path, model_out: Path, stats_out: Path,
          epochs: int, batch_size: int, fps: float, use_mirror: bool):

    print("Cargando dataset (RAW, sin normalizar)...")
    sequences, labels, mu_ref, std_ref, names = load_dataset(dataset_path)
    print(f"  {len(sequences)} videos.")

    import tensorflow as tf
    from tensorflow.keras import callbacks

    tf.random.set_seed(42)
    np.random.seed(42)

    # ── Split por video ────────────────────────────────────────────────────────
    n_val = max(1, int(len(sequences) * VAL_SPLIT))
    idx = np.random.permutation(len(sequences))
    val_idx   = idx[:n_val]
    train_idx = idx[n_val:]

    seqs_train_raw = [sequences[i] for i in train_idx]
    labs_train     = [labels[i]    for i in train_idx]
    names_train    = [names[i]     for i in train_idx]

    seqs_val_raw   = [sequences[i] for i in val_idx]
    labs_val       = [labels[i]    for i in val_idx]
    names_val      = [names[i]     for i in val_idx]

    print(f"  Train: {len(seqs_train_raw)} videos   Val: {len(seqs_val_raw)} videos")

    # ── Augmentacion por espejo (solo train) ──────────────────────────────────
    if use_mirror:
        seqs_train_raw, labs_train, names_train = augment_with_mirror(
            seqs_train_raw, labs_train, names_train, fps=fps
        )
        print(f"  Train tras espejo: {len(seqs_train_raw)} secuencias (originales + mirror)")
    else:
        print("  [INFO] Espejo desactivado (--no_mirror)")

    # ── Normalizacion: mu/std en el train aumentado ───────────────────────────
    mu, std = compute_zscore_stats(seqs_train_raw)
    print(f"  mu (train aumentado) : {np.round(mu, 3)}")
    print(f"  std (train aumentado): {np.round(std, 3)}")

    seqs_train = normalize(seqs_train_raw, mu, std)
    seqs_val   = normalize(seqs_val_raw,   mu, std)

    # Guardar mu/std para inferencia
    np.savez(stats_out, mu=mu, std=std,
             col_names=np.array(["L_knee","R_knee","L_hip","R_hip",
                                 "sust_dy","vel_knee","vel_y_hip","acc_y_hip"]))
    print(f"  Stats de normalizacion guardadas en: {stats_out}")

    # ── Padding ────────────────────────────────────────────────────────────────
    print("Aplicando padding...")
    X_train, Y_train, SW_train, T_max = pad_sequences(seqs_train, labs_train)
    X_val,   Y_val,   SW_val,   _     = pad_sequences(seqs_val,   labs_val, T_max=T_max)
    print(f"  X_train: {X_train.shape}   X_val: {X_val.shape}   T_max: {T_max}")

    # ── Modelo ─────────────────────────────────────────────────────────────────
    model = build_model(T_max)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )
    model.summary()

    cbs = [
        callbacks.EarlyStopping(monitor="val_loss", patience=PATIENCE,
                                restore_best_weights=True, verbose=1),
        callbacks.ReduceLROnPlateau(monitor="val_loss", factor=0.5,
                                    patience=7, min_lr=1e-5, verbose=1),
        callbacks.ModelCheckpoint(str(model_out), monitor="val_loss",
                                  save_best_only=True, verbose=0),
    ]

    print(f"\nEntrenando ({epochs} epochs, batch={batch_size})...")
    history = model.fit(
        X_train, Y_train,
        sample_weight=SW_train,
        validation_data=(X_val, Y_val, SW_val),
        epochs=epochs,
        batch_size=batch_size,
        callbacks=cbs,
        verbose=1,
    )

    # ── Guardado del historial ────────────────────────────────────────────────
    HISTORY_OUT.parent.mkdir(parents=True, exist_ok=True)
    hist_dict = {k: [float(v) for v in vals] for k, vals in history.history.items()}
    with open(HISTORY_OUT, "w") as f:
        json.dump(hist_dict, f, indent=2)

    # ── Evaluacion final ──────────────────────────────────────────────────────
    print("\nEvaluacion en conjunto de validacion (solo originales, sin mirror):")
    loss, acc = model.evaluate(X_val, Y_val, sample_weight=SW_val, verbose=0)
    print(f"  Val loss: {loss:.4f}   Val acc: {acc:.4f}")

    _confusion_matrix(model, X_val, Y_val, seqs_val_raw)

    print(f"\nModelo guardado en   : {model_out}")
    print(f"Stats guardadas en   : {stats_out}")
    print(f"Historial guardado en: {HISTORY_OUT}")

    return model


def _confusion_matrix(model, X_val, Y_val, sequences_val_raw):
    """Classification report ignorando padding."""
    from sklearn.metrics import classification_report
    import warnings

    y_true_all, y_pred_all = [], []
    preds = model.predict(X_val, verbose=0)   # (N_val, T_max, 4)

    for i in range(len(sequences_val_raw)):
        T = len(sequences_val_raw[i])
        y_true = np.argmax(Y_val[i, :T, :], axis=-1)
        y_pred = np.argmax(preds[i, :T, :], axis=-1)
        y_true_all.append(y_true)
        y_pred_all.append(y_pred)

    y_true_all = np.concatenate(y_true_all)
    y_pred_all = np.concatenate(y_pred_all)

    target_names = ["Stand", "Descent", "Bottom", "Ascent"]
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        print("\nClassification report (validacion):")
        print(classification_report(y_true_all, y_pred_all,
                                    target_names=target_names, digits=3))


def main():
    parser = argparse.ArgumentParser(
        description="Entrena la BiLSTM de clasificacion de fases con augmentacion por espejo"
    )
    parser.add_argument("--dataset",   default=str(DATASET_PATH))
    parser.add_argument("--out",       default=str(MODEL_OUT))
    parser.add_argument("--stats_out", default=str(STATS_OUT))
    parser.add_argument("--epochs",    type=int,   default=DEFAULT_EPOCHS)
    parser.add_argument("--batch",     type=int,   default=DEFAULT_BATCH)
    parser.add_argument("--fps",       type=float, default=DEFAULT_FPS)
    parser.add_argument("--no_mirror", action="store_true",
                        help="Desactiva la augmentacion por espejo (debug)")
    args = parser.parse_args()

    train(
        dataset_path = Path(args.dataset),
        model_out    = Path(args.out),
        stats_out    = Path(args.stats_out),
        epochs       = args.epochs,
        batch_size   = args.batch,
        fps          = args.fps,
        use_mirror   = not args.no_mirror,
    )


if __name__ == "__main__":
    main()
