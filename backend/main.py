import cv2
import argparse
import numpy as np
import tensorflow as tf
import rep_report
import visualization
from pose_processor import PoseProcessor
from pose_features import process_historial


MODEL_PATH = './modelos/juez_sentadillas.h5'
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
    
    # --- 2. LLAMADA PARA DEBUG (Comentar/Descomentar aquí) ---
    #visualization.mostrar_frame(frame, results)
    # ---------------------------------------------------------    

# liberamos recursos de vídeo y detector
cap.release()
cv2.destroyAllWindows() # <--- Parte de visualiz
pose_processor.close()

if len(historial_landmarks) > 0:
    # 1. Transformación de datos
    df_angles = process_historial(historial_landmarks)
    df_angles['delta_time'] = 1.0 / fps
    datos = df_angles.fillna(0.0).values

    # 5. Guardar en formato .npy
    if 'sentadilla' in args.fuente:
        nombre_base = args.fuente.rsplit('.', 1)[0]
        nombre_archivo = nombre_base.replace("sentadilla", "entrenamiento") + ".npy"
    else:
        nombre_archivo = args.fuente.rsplit('.', 1)[0] + ".npy"
   

    np.save(nombre_archivo, datos)
    
#     # 2. Cargar Juez Virtual e Inferencia
#     try:
#         model = tf.keras.models.load_model(MODEL_PATH)
#         input_tensor = np.expand_dims(datos, axis=0)
#         predicciones = model.predict(input_tensor)
        
#         # 3. Generar y mostrar informe
#         informe = rep_report.obtener_informe_reps(predicciones)
#         rep_report.mostrar_informe(informe)    
#     except Exception as e:
#         print(f"Error en la fase de análisis: {e}")
# else:
#     print("Error: No se detectó movimiento.")
