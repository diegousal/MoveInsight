import numpy as np
import tensorflow as tf
from tensorflow.keras.preprocessing.sequence import pad_sequences
from redBILSTM import crear_bilstm_analizador #
import os

# --- 1. DICCIONARIO DE ETIQUETADO MANUAL ---
# RELLENA AQUÍ LOS FRAMES: (inicio_bajada, inicio_fondo, fin_fondo, fin_subida)
etiquetas_videos = {
    "1.npy": {
        "reps": [(10, 75, 105, 155), (165, 225, 275, 325), (330, 385, 435, 475), (495, 545, 585, 620)]
    },
    "2.npy": {
        "reps": [(5, 30, 65, 75), (125, 145, 185, 195), (240, 265, 295, 304)]
    },
    "3.npy": {
        "reps": [(10, 40, 60, 120), (135, 175, 200, 240), (250, 300, 335, 380), (410, 450, 470, 520), (530, 575, 595, 639)]
    },
    "4.npy": {
        "reps": [(5, 15, 40, 65), (85, 105, 125, 145), (160, 195, 245, 265), (280, 315, 345, 365), (385, 415, 445, 470), (490, 520, 555, 580), (595, 620, 645, 658)]
    },
    "5.npy": {
        "reps": [(10, 18, 38, 45), (78, 88, 118, 130), (155, 165, 175, 183)]
    },
    "7.npy": {
        "reps": [(76, 118, 143, 171), (210, 250, 273, 301), (337, 377, 405, 433), (466, 504, 533, 561), (596, 633, 660, 687), (720, 760, 782, 815), (847, 888, 910, 940)]
    },
 
    "9.npy": {
        "reps": [(55, 75, 95, 122), (180, 205, 235, 260), (305, 335, 355, 385), (415, 445, 480, 512), (545, 570, 600, 630), (680, 705, 735, 760)]
    },
}


# --- CONFIGURACIÓN TÉCNICA ---
MAX_FRAMES = 1200  # Longitud máxima para unificar todos los vídeos
N_FEATURES = 15    # Las 15 columnas de tus datos

def cargar_y_etiquetar():
    x_train_list = []
    y_fases_list = []
    
    for nombre, info in etiquetas_videos.items():
        if not os.path.exists("entrenamiento/" + nombre):
            print(f"Saltando {nombre}: Archivo no encontrado.")
            continue
            
        print(f"Procesando {nombre}...")
        datos = np.load("entrenamiento/" + nombre) # Carga (Frames, 15)
        num_f = datos.shape[0]
        x_train_list.append(datos)
        
        # Crear etiquetas de fases (0, 1, 2, 3)
        fase_array = np.zeros(num_f)
        for (ini, b_ini, b_fin, fin) in info["reps"]:
            fase_array[ini:b_ini] = 1   # Bajando
            fase_array[b_ini:b_fin] = 2    # Fondo
            fase_array[b_fin:fin] = 3      # Subiendo
        y_fases_list.append(fase_array)

    # 1. Padding: Forzamos que todos los vídeos midan lo mismo (MAX_FRAMES)
    X = pad_sequences(x_train_list, maxlen=MAX_FRAMES, dtype='float32', padding='post', value=0.0)
    Y_fases = pad_sequences(y_fases_list, maxlen=MAX_FRAMES, padding='post', value=0)
    
    # 2. Etiquetas de Calidad: Todas a 1.0 (Excelencia) para todo el vídeo
    # Forma: (Num_Videos, MAX_FRAMES, 1)
    Y_calidad = np.ones((len(x_train_list), MAX_FRAMES, 1))
    
    return X, Y_fases, Y_calidad

# --- EJECUCIÓN DEL ENTRENAMIENTO ---

X_train, Y_fases, Y_calidad = cargar_y_etiquetar()

# Crear el modelo usando tu arquitectura
model = crear_bilstm_analizador(max_frames=MAX_FRAMES, n_features=N_FEATURES)

# Preparamos las 7 salidas de la red
y_dict = {
    "fase_frame": Y_fases,
    "kpi_profundidad": Y_calidad,
    "kpi_torso": Y_calidad,
    "kpi_estabilidad": Y_calidad,
    "kpi_simetria": Y_calidad,
    "kpi_rodillas": Y_calidad,
    "kpi_ritmo": Y_calidad
}

print(f"\nIniciando entrenamiento con {len(X_train)} vídeos...")
model.fit(
    X_train, 
    y_dict, 
    epochs=100, 
    batch_size=2, 
    verbose=1
)

# Guardar el modelo
model.save("juez_sentadillas_v2_reps.h5")
print("\nModelo guardado correctamente como 'juez_sentadillas_v2_manual.h5'")