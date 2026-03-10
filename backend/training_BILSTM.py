import numpy as np
import os
import tensorflow as tf
from tensorflow.keras.preprocessing.sequence import pad_sequences
from red_BILSTM import crear_bilstm_analizador

# --- 1. DICCIONARIO DE ETIQUETADO DETALLADO ---
# Aquí es donde ocurre la "magia". Para cada repetición, defines técnica real.
etiquetas_videos = {
    "1.npy": {
        "reps": [
            {
                "frames": (10, 75, 105, 155), 
                "kpis": {"depth": 1.0, "torso": 0.9, "stability": 0.8, "knees": 1.0, "ritmo": 1.0}
            },
            {
                "frames": (165, 225, 275, 325), 
                "kpis": {"depth": 0.5, "torso": 0.4, "stability": 0.5, "knees": 0.7, "ritmo": 0.6} # Rep mala
            },
        ]
    },
    "2.npy": {
        "reps": [
            {
                "frames": (5, 30, 65, 75), 
                "kpis": {"depth": 0.9, "torso": 1.0, "stability": 1.0, "knees": 0.9, "ritmo": 0.9}
            },
        ]
    },
    # Añade aquí el resto de tus archivos siguiendo este formato...
}

# --- CONFIGURACIÓN ---
MAX_FRAMES = 1200
N_FEATURES = 15
MODEL_PATH = "./modelos/juez_sentadillas_v2_profesional.h5"

def generar_labels_calidad(num_frames, reps_info):
    """
    Crea arrays de etiquetas para un vídeo específico basándose en sus repeticiones.
    """
    # Inicializamos a 0.0 o a un valor "neutro". 
    # Nota: Si el frame no es parte de una repetición, el modelo no debería ser penalizado 
    # fuertemente, pero aquí pondremos el valor de la rep para esos frames.
    fases = np.zeros(num_frames)
    kpi_d = np.zeros(num_frames)
    kpi_t = np.zeros(num_frames)
    kpi_s = np.zeros(num_frames)
    kpi_k = np.zeros(num_frames)
    kpi_r = np.zeros(num_frames)

    for rep in reps_info:
        ini, b_ini, b_fin, fin = rep["frames"]
        k = rep["kpis"]
        
        # Etiquetado de Fases
        fases[ini:b_ini] = 1   # Bajando
        fases[b_ini:b_fin] = 2 # Fondo
        fases[b_fin:fin] = 3   # Subiendo
        
        # Etiquetado de KPIs (aplicado a toda la duración de la repetición)
        kpi_d[ini:fin] = k["depth"]
        kpi_t[ini:fin] = k["torso"]
        kpi_s[ini:fin] = k["stability"]
        kpi_k[ini:fin] = k["knees"]
        kpi_r[ini:fin] = k["ritmo"]
        
    return fases, kpi_d, kpi_t, kpi_s, kpi_k, kpi_r

def preparar_dataset():
    x_train, y_fases = [], []
    y_kpis = { "depth": [], "torso": [], "stability": [], "knees": [], "ritmo": [] }
    
    for nombre, info in etiquetas_videos.items():
        ruta = os.path.join("entrenamiento", nombre)
        if not os.path.exists(ruta):
            print(f"⚠️ Archivo {nombre} no encontrado. Saltando...")
            continue
            
        datos = np.load(ruta)
        num_f = datos.shape[0]
        x_train.append(datos)
        
        # Generar etiquetas específicas para este vídeo
        f, d, t, s, k, r = generar_labels_calidad(num_f, info["reps"])
        
        y_fases.append(f)
        y_kpis["depth"].append(d)
        y_kpis["torso"].append(t)
        y_kpis["stability"].append(s)
        y_kpis["knees"].append(k)
        y_kpis["ritmo"].append(r)

    # Padding para unificar longitudes
    X = pad_sequences(x_train, maxlen=MAX_FRAMES, dtype='float32', padding='post')
    Y_fases = pad_sequences(y_fases, maxlen=MAX_FRAMES, padding='post')
    
    # Padding para cada KPI y expandir dimensiones para que coincida con la salida (Batch, Frames, 1)
    Y_outputs = {
        "fase_frame": Y_fases,
        "kpi_profundidad": pad_sequences(y_kpis["depth"], maxlen=MAX_FRAMES, padding='post', dtype='float32')[:, :, np.newaxis],
        "kpi_torso": pad_sequences(y_kpis["torso"], maxlen=MAX_FRAMES, padding='post', dtype='float32')[:, :, np.newaxis],
        "kpi_estabilidad": pad_sequences(y_kpis["stability"], maxlen=MAX_FRAMES, padding='post', dtype='float32')[:, :, np.newaxis],
        "kpi_rodillas": pad_sequences(y_kpis["knees"], maxlen=MAX_FRAMES, padding='post', dtype='float32')[:, :, np.newaxis],
        "kpi_ritmo": pad_sequences(y_kpis["ritmo"], maxlen=MAX_FRAMES, padding='post', dtype='float32')[:, :, np.newaxis]
    }
    
    return X, Y_outputs

# --- FLUJO PRINCIPAL ---

print("--- Cargando y Estructurando Datos ---")
X_train, Y_dict = preparar_dataset()

model = crear_bilstm_analizador(max_frames=MAX_FRAMES, n_features=N_FEATURES)

print(f"\nEntrenando con {len(X_train)} ejemplos...")
# Ajustamos pesos de pérdida si queremos que la fase sea más importante que los KPIs
history = model.fit(
    X_train, 
    Y_dict, 
    epochs=100, 
    batch_size=4, # Aumentado un poco para estabilidad
    validation_split=0.1, # Para ver si generaliza bien
    verbose=1
)

model.save(MODEL_PATH)
print(f"\n✅ Modelo profesional guardado en: {MODEL_PATH}")