import numpy as np
import tensorflow as tf
from tensorflow.keras.preprocessing.sequence import pad_sequences # <--- IMPORTANTE
import matplotlib.pyplot as plt
import rep_report  

# CONFIGURACIÓN (debe ser igual a la del entrenamiento)
MAX_FRAMES = 1500 

# 1. CARGAR DATOS Y MODELO
try:
    data = np.load('./entrenamiento/archivos_entrenamiento.npy') # Carga (639, 15)
    model = tf.keras.models.load_model('./modelos/juez_sentadillas_nuevo.h5')
    print("Modelo y datos cargados correctamente.")
except Exception as e:
    print(f"Error al cargar: {e}")
    exit()

# 2. PROCESAMIENTO (PADOING)
# El modelo espera (1, 1200, 15). 
# Primero metemos el vídeo en una lista y aplicamos padding.
data_padded = pad_sequences([data], maxlen=MAX_FRAMES, dtype='float32', padding='post', value=0.0)

# 3. PREDICCIÓN (Inferencia)
# data_padded ya tiene la dimensión de batch incluida por pad_sequences: (1, 1200, 15)
predicciones = model.predict(data_padded)

# --- El resto de tu código igual ---
informe = rep_report.obtener_informe_reps(predicciones)
rep_report.mostrar_informe(informe) 

# ... (Visualización)

# 3. EXTRAER SEÑALES
# predicciones[0] -> Fases, [1] -> Profundidad, [2] -> Torso, [6] -> Ritmo
fases_predichas = np.argmax(predicciones[0][0], axis=-1)
nota_profundidad = predicciones[1][0]
nota_ritmo = predicciones[5][0]

# 4. VISUALIZACIÓN
plt.figure(figsize=(12, 8))

plt.subplot(3, 1, 1)
plt.plot(fases_predichas, color='blue', label='Fases (0:Stand, 1:Down, 2:Bottom, 3:Up)')
plt.title("Segmentación de Fases")
plt.legend()

plt.subplot(3, 1, 2)
plt.plot(nota_profundidad, color='green', label='Calidad Profundidad (0-1)')
plt.axhline(y=0.5, color='r', linestyle='--')
plt.ylim(-0.1, 1.1)
plt.legend()

plt.subplot(3, 1, 3)
plt.plot(nota_ritmo, color='orange', label='Calidad Ritmo/Control (0-1)')
plt.axhline(y=0.5, color='r', linestyle='--')
plt.ylim(-0.1, 1.1)
plt.legend()

plt.tight_layout()
plt.savefig("verificacion_modelo.png")
print("Imagen 'verificacion_modelo.png' generada. Revisa si las gráficas tienen sentido.")