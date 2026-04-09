import os
import argparse
import glob
import numpy as np
import json
from tqdm import tqdm

import tensorflow as tf
from tensorflow.keras.callbacks import ModelCheckpoint, EarlyStopping, ReduceLROnPlateau

from red_BILSTM import build_bilstm_model

# ---------- Config ----------
DEFAULT_DATA_DIR = "/archivos_entrenamiento"
DEFAULT_LABELS = "etiquetas_videos.json"
BATCH_SIZE = 4  # Reducido un poco porque ahora cargamos vídeos completos
EPOCHS = 150
NUM_PHASES = 5

# ---------- Utilidades de carga ----------
def load_labels(labels_path: str):
    with open(labels_path, "r", encoding="utf-8") as f:
        return json.load(f)

def build_frame_targets(video_len: int, reps: list):
    phase = np.zeros((video_len,), dtype=np.int32)
    kpis = np.zeros((video_len, 5), dtype=np.float32)

    for rep in reps:
        f_pts = rep["frames"]
        inicio, fin = f_pts[0], f_pts[-1]
        centro = f_pts[2] # El punto de máxima profundidad según tu JSON
        
        # 1. Asignación de Fases Reales (Usando los 5 puntos del JSON)
        phase[f_pts[0]:f_pts[1]] = 1 # Preparación
        phase[f_pts[1]:f_pts[2]] = 2 # Descenso
        phase[f_pts[2]:f_pts[3]] = 3 # Fondo (Apex)
        phase[f_pts[3]:f_pts[4]] = 4 # Ascenso

        # 2. Bucle para KPIs (Rampa de profundidad)
        for f in range(inicio, fin):
            distancia_al_centro = abs(f - centro)
            # Normalizamos la rampa según la duración total
            duracion_total = fin - inicio
            factor_profundidad = max(0, 1 - (distancia_al_centro / (duracion_total / 2)))
            
            # NOTA: No dividas por 10 si tu JSON ya tiene 0.9 o 1.0
            kpis[f, 0] = rep["kpis"]["depth"] * factor_profundidad 
            kpis[f, 1] = rep["kpis"]["torso"]
            kpis[f, 2] = rep["kpis"]["stability"]
            kpis[f, 3] = rep["kpis"]["knees"]
            kpis[f, 4] = rep["kpis"]["ritmo"]

    return phase, kpis
# ---------- Preparación de datos (Sin ventanas fijas) ----------
def load_variable_sequences(data_dir: str, labels: dict):
    """
    Carga los vídeos como una lista de arrays de longitud variable.
    """
    all_files = sorted(glob.glob(os.path.join(data_dir, "*.npy")))
    file_list = [fp for fp in all_files if os.path.basename(fp) in labels]
    
    Xs, phases, kpis = [], [], []
    
    print(f"Cargando {len(file_list)} vídeos de longitud variable...")
    for fp in tqdm(file_list):
        fname = os.path.basename(fp)
        arr = np.load(fp).astype(np.float32)
        T = arr.shape[0]
        
        p_target, k_target = build_frame_targets(T, labels[fname]["reps"])
        
        Xs.append(arr)
        phases.append(p_target)
        kpis.append(k_target)
        
    return Xs, phases, kpis

def compute_normalization_variable(Xs):
    """Calcula media y std recorriendo la lista de arrays."""
    all_data = np.concatenate(Xs, axis=0)
    mean = np.mean(all_data, axis=0)
    std = np.std(all_data, axis=0)
    std[std == 0] = 1.0
    return mean, std

# ---------- Generador y Dataset ----------
def create_variable_tf_dataset(Xs, phases, kpis, batch_size=BATCH_SIZE, shuffle=True):
    def generator():
        for x, p, k in zip(Xs, phases, kpis):
            yield x, p, k

    # Definimos la firma (None indica longitud variable)
    output_signature = (
        tf.TensorSpec(shape=(None, Xs[0].shape[-1]), dtype=tf.float32),
        tf.TensorSpec(shape=(None,), dtype=tf.int32),
        tf.TensorSpec(shape=(None, 5), dtype=tf.float32)
    )

    dataset = tf.data.Dataset.from_generator(generator, output_signature=output_signature)
    
    if shuffle:
        dataset = dataset.shuffle(buffer_size=len(Xs))

    # El truco: padded_batch rellena cada lote hasta la longitud del video más largo del lote
    dataset = dataset.padded_batch(
        batch_size,
        padded_shapes=([None, Xs[0].shape[-1]], [None], [None, 5]),
        padding_values=(0.0, 0, 0.0)
    )

    def map_fn(x, p, k):
        # Máscara para KPIs: solo calculamos error donde hay una repetición (fase != 0)
        kpi_mask = tf.cast(tf.not_equal(p, 0), tf.float32)
        return x, {"phase_out": p, "kpi_out": k}, {"phase_out": tf.ones_like(p, dtype=tf.float32), "kpi_out": kpi_mask}

    return dataset.map(map_fn).prefetch(tf.data.AUTOTUNE)

# ---------- Main Training ----------
def main(args):
    labels = load_labels(args.labels)
    Xs_raw, phases_raw, kpis_raw = load_variable_sequences(args.data_dir, labels)

    # Normalización
    mean, std = compute_normalization_variable(Xs_raw)
    Xs_norm = [(x - mean) / std for x in Xs_raw]

    # Split manual (85/15)
    n = len(Xs_norm)
    indices = np.arange(n)
    np.random.seed(42)
    np.random.shuffle(indices)    
    split = int(n * 0.85)
    
    train_idx, val_idx = indices[:split], indices[split:]
    
    train_ds = create_variable_tf_dataset([Xs_norm[i] for i in train_idx], 
                                          [phases_raw[i] for i in train_idx], 
                                          [kpis_raw[i] for i in train_idx], batch_size=args.batch_size)
    
    val_ds = create_variable_tf_dataset([Xs_norm[i] for i in val_idx], 
                                        [phases_raw[i] for i in val_idx], 
                                        [kpis_raw[i] for i in val_idx], batch_size=args.batch_size, shuffle=False)

    # Construir modelo
    model = build_bilstm_model(input_dim=Xs_raw[0].shape[-1], 
                               lstm_units=args.lstm_units, 
                               num_layers=args.num_layers, 
                               num_phases=NUM_PHASES)
    
    # Callbacks
    callbacks = [
        ModelCheckpoint(args.checkpoint, save_best_only=True, monitor="val_phase_out_sparse_categorical_accuracy"),
        EarlyStopping(monitor="val_phase_out_sparse_categorical_accuracy", patience=10, restore_best_weights=True),
        ReduceLROnPlateau(patience=5)
    ]

    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs, callbacks=callbacks)

    # Guardar todo
    os.makedirs(args.output_dir, exist_ok=True)
    np.save(os.path.join(args.output_dir, "norm_mean.npy"), mean)
    np.save(os.path.join(args.output_dir, "norm_std.npy"), std)
    model.save(os.path.join(args.output_dir, "final_bilstm_model"))
    print(f"Éxito. Modelo guardado en {args.output_dir}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--data_dir", type=str, default=DEFAULT_DATA_DIR)
    parser.add_argument("--labels", type=str, default=DEFAULT_LABELS)
    parser.add_argument("--output_dir", type=str, default="./model_output")
    parser.add_argument("--batch_size", type=int, default=BATCH_SIZE)
    parser.add_argument("--epochs", type=int, default=EPOCHS)
    parser.add_argument("--lstm_units", type=int, default=128)
    parser.add_argument("--num_layers", type=int, default=2)
    parser.add_argument("--checkpoint", type=str, default="./best_model.h5")
    args = parser.parse_args()
    main(args)