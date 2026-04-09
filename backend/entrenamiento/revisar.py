import json
import numpy as np
import os

# Configuración (Asegúrate de que coincidan con tus rutas)
DATA_DIR = "./archivos_entrenamiento"
LABELS_PATH = "etiquetas_videos.json"

def audit():
    with open(LABELS_PATH, "r", encoding="utf-8") as f:
        labels = json.load(f)

    print("--- Iniciando Auditoría de Datos ---")
    
    archivos_en_carpeta = os.listdir(DATA_DIR)
    errores_encontrados = 0

    for fname, data in labels.items():
        path = os.path.join(DATA_DIR, fname)
        
        # 1. Comprobar si el archivo existe
        if not os.path.exists(path):
            print(f"[ERROR: ARCHIVO FALTANTE] {fname} está en el JSON pero no en la carpeta.")
            continue

        # 2. Cargar el archivo para ver su tamaño real
        try:
            arr = np.load(path)
            max_frames = arr.shape[0] # El tamaño del eje 0 (frames)
        except Exception as e:
            print(f"[ERROR: CORRUPTO] No se pudo cargar {fname}: {e}")
            continue

        # 3. Revisar cada repetición
        for i, rep in enumerate(data["reps"]):
            frames = rep["frames"]
            
            # Verificar si algún frame excede el límite
            # El límite superior válido es max_frames - 1
            for val in frames:
                if val >= max_frames:
                    print(f"[CULPABLE ENCONTRADO] Archivo: {fname} | Repetición: {i}")
                    print(f"   -> El JSON dice frame {val}, pero el vídeo solo tiene {max_frames} frames.")
                    print(f"   -> (Debes bajar ese {val} a {max_frames - 1} o menos en el JSON)")
                    errores_encontrados += 1

    if errores_encontrados == 0:
        print("\n--- No se encontraron errores de índices. El problema podría ser otro. ---")
    else:
        print(f"\n--- Auditoría terminada. Se encontraron {errores_encontrados} inconsistencias. ---")

if __name__ == "__main__":
    audit()