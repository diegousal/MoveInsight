import cv2
import argparse
from poseProcessor import PoseProcessor
from calculosKeypoints import process_historial
from redBILSTM import segmentar_repeticiones, crear_bilstm_analizador
import numpy as np

historial_landmarks = []

parser = argparse.ArgumentParser()
parser.add_argument('--fuente', type=str, default='sentadilla.mp4')
args = parser.parse_args()

try:
    fuente = int(args.fuente)
except ValueError:
    fuente = args.fuente

cap = cv2.VideoCapture(fuente)
if not cap.isOpened():
    raise RuntimeError(f"No se pudo abrir la fuente: {fuente}")

# Obtener FPS del vídeo; si no está disponible, usar valor por defecto 30.0
fps = cap.get(cv2.CAP_PROP_FPS)
if not fps or fps != fps:  # check for 0 or NaN
    fps = 30.0

pose_processor = PoseProcessor(fps=fps)

frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT)) if cap.get(cv2.CAP_PROP_FRAME_COUNT) else None

while True:
    ret, frame = cap.read()
    if not ret:
        break

    # process_frame espera frame BGR
    results, pose_vector = pose_processor.process_frame(frame)

    if pose_vector is not None:
        historial_landmarks.append(pose_vector)

# liberamos recursos de vídeo y detector
cap.release()
pose_processor.close()

# --- Aquí estaba el problema: primero convertir lista -> DataFrame usando process_historial ---
if len(historial_landmarks) == 0:
    print("No se detectaron landmarks.")
else:
    # 1. Obtenemos el DataFrame con todos los ángulos (L y R)
    df_angles = process_historial(historial_landmarks)

    # 2. Añadimos la columna de tiempo real (delta_time)
    # Esto permite que la red entienda la velocidad real independientemente de los FPS
    df_angles['delta_time'] = 1.0 / fps

    # 3. Tratamiento de datos faltantes (Muy importante para .npy)
    # Los archivos .npy no gestionan bien los NaNs en el entrenamiento.
    # Llenamos con 0.0 los puntos no visibles. La red aprenderá que 0 = "punto oculto".
    df_raw = df_angles.fillna(0.0)

    # 4. Convertimos TODO el set de datos a un array de NumPy
    # Esto incluye: L_elbow, R_elbow, L_shoulder, R_shoulder, L_knee, R_knee, 
    # L_hip, R_hip, L_ankle, R_ankle, torso, sust_x, sust_y, sust_z Y delta_time.
    datos = df_raw.values 

# 5. Guardar en formato .npy
if 'Sentadilla' in args.fuente:
    nombre_base = args.fuente.rsplit('.', 1)[0]
    nombre_archivo = nombre_base.replace("Sentadilla", "Entrenamiento") + ".npy"
else:
    nombre_archivo = args.fuente.rsplit('.', 1)[0] + ".npy"
    
np.save(nombre_archivo, datos)

# Añades la dimensión batch al principio
# Ahora es (1, 955, 15)
datosRed= np.expand_dims(datos, axis=0)

# Ahora la red ya puede procesarlo
#prediccion = modelo.predict(datosRed)
    
print(
    f"\n--- EXPORTACIÓN COMPLETADA ---"
    f"\nArchivo: {nombre_archivo}"
    f"\nColumnas incluidas: {list(df_angles.columns)}"
    f"\nDimensiones (Frames, Características): {datos_para_red.shape}"
    f"\nFPS del video: {fps}"
    )