import cv2
import argparse
import numpy as np
import tensorflow as tf
# 1. CAMBIO: Importar la nueva función visual
from rep_report import save_visual_report 
import visualization
from pose_processor import PoseProcessor
from pose_features import process_historial

MODEL_PATH = './modelos/v2/best_model.h5'
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

fps = cap.get(cv2.CAP_PROP_FPS)
if not fps or fps != fps:  
    fps = 30.0

pose_processor = PoseProcessor(fps=fps)
frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT)) if cap.get(cv2.CAP_PROP_FRAME_COUNT) else None

while True:
    ret, frame = cap.read()
    if not ret:
        break

    results, pose_vector = pose_processor.process_frame(frame)
    if pose_vector is not None:
        historial_landmarks.append(pose_vector)
    
    visualization.mostrar_frame(frame, results)

cap.release()
cv2.destroyAllWindows() 
pose_processor.close()

if len(historial_landmarks) > 0:
    df_angles = process_historial(historial_landmarks)
    df_angles['delta_time'] = 1.0 / fps
    datos = df_angles.fillna(0.0).values

    # Guardar .npy
    if 'sentadilla' in args.fuente:
        nombre_base = args.fuente.rsplit('.', 1)[0]
        nombre_archivo = nombre_base.replace("ejemplos\\sentadilla", "entrenamiento\\archivos_entrenamiento") + ".npy"
    else:
        nombre_archivo = args.fuente.rsplit('.', 1)[0] + ".npy"

    np.save(nombre_archivo, datos)
    
# --- FASE DE INFERENCIA Y RECONSTRUCCIÓN OPTIMIZADA ---
    try:
        # 1. Cargar modelo y parámetros de normalización
        model = tf.keras.models.load_model(MODEL_PATH)
        mean = np.load("./modelos/v2/norm_mean.npy")
        std = np.load("./modelos/v2/norm_std.npy")
        
        # 2. Normalización de los datos completos
        datos_norm = (datos - mean) / std

        # 3. Preparar el tensor de entrada (Batch, Time, Features)
        # Ya no necesitamos ventanas ni loops. Pasamos el vídeo completo.
        input_tensor = np.expand_dims(datos_norm, axis=0) # (1, num_frames, 15)

        # 4. Predicción en un solo paso
        # El modelo usará la capa Masking internamente si fuera necesario, 
        # pero aquí T es la longitud real del vídeo.
        pred_phases, pred_kpis = model.predict(input_tensor, verbose=1)

        # 5. Extraer resultados (quitando la dimensión de batch)
        # Obtenemos directamente las secuencias para todo el vídeo
        final_phases = pred_phases[0]
        final_kpis = pred_kpis[0]

        # 6. Llamada a la función de reporte visual
        ruta_reporte = save_visual_report(
            predicciones=[final_phases, final_kpis], 
            video_source=args.fuente, 
            seq_len= datos_norm.shape[0], 
            output_dir="./reportes", 
            min_run_length=3
        )
        
        print(f"Análisis completado con éxito. Reporte en: {ruta_reporte}")

    except Exception as e:
        print(f"Error crítico en la fase de análisis: {e}")

else:
    print("Error: No se detectó movimiento.")