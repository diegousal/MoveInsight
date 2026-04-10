import numpy as np
import glob
import os
import json
from tqdm import tqdm

# --- CONFIGURACIÓN (Ajusta estas rutas si son distintas) ---
DATA_DIR = "./archivos_entrenamiento"
LABELS_PATH = "etiquetas_videos.json"
OUTPUT_DIR = "./modelos/v14" # Donde quieras guardar los .npy

# 1. Cargar etiquetas
with open(LABELS_PATH, 'r') as f:
    labels = json.load(f)

# 2. La misma lógica de carga que usamos para entrenar (Oversampling V14)
all_files = sorted(glob.glob(os.path.join(DATA_DIR, "*.npy")))
file_list = [fp for fp in all_files if os.path.basename(fp) in labels]

Xs = []
print("Calculando estadísticas de la V14...")

for fp in tqdm(file_list):
    fname = os.path.basename(fp)
    arr_orig = np.load(fp).astype(np.float32)
    
    # Extraemos la nota de profundidad para saber cuántas veces repetir
    depth_scores = [rep["kpis"]["depth"] for rep in labels[fname]["reps"]]
    min_depth = min(depth_scores) if depth_scores else 1.0
    
    # La misma lógica que en el entrenamiento:
    if min_depth <= 0.4:
        multiplier = 15
    elif min_depth <= 0.6:
        multiplier = 8
    else:
        multiplier = 1
        
    for _ in range(multiplier):
        Xs.append(arr_orig)

# 3. Concatenar todo para calcular media y std
todas_las_secuencias = np.concatenate(Xs, axis=0)
# Solo calculamos sobre los frames que no son cero (los que la red realmente ve)
mask = (todas_las_secuencias != 0).any(axis=1)
datos_validos = todas_las_secuencias[mask]

mean = np.mean(datos_validos, axis=0)
std = np.std(datos_validos, axis=0)
std[std < 1e-6] = 1.0 # Evitar división por cero

# 4. Guardar
os.makedirs(OUTPUT_DIR, exist_ok=True)
np.save(os.path.join(OUTPUT_DIR, "norm_mean.npy"), mean)
np.save(os.path.join(OUTPUT_DIR, "norm_std.npy"), std)

print(f"\n✅ ¡Rescate completado! Archivos guardados en {OUTPUT_DIR}")
print("Ahora ya puedes usar el main.py con tu nuevo modelo V14.")