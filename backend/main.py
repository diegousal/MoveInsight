import cv2
import argparse
import numpy as np
import tensorflow as tf
# 1. CAMBIO: Importar la nueva función visual
from rep_report import save_visual_report 
import visualization
from pose_processor import PoseProcessor
from pose_features import process_historial

MODEL_PATH = './modelos/best_model.h5'
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
    
    # --- FASE DE INFERENCIA Y RECONSTRUCCIÓN ---
    try:
        model = tf.keras.models.load_model(MODEL_PATH)
        mean = np.load("./modelos/norm_mean.npy")
        std = np.load("./modelos/norm_std.npy")
        
        datos_norm = (datos - mean) / std

        window_size = 1300 
        stride = 150      
        num_frames = datos_norm.shape[0]
        num_features = datos_norm.shape[1]

        accum_phases = np.zeros((num_frames, 5)) 
        accum_kpis = np.zeros((num_frames, 5))
        counts = np.zeros((num_frames, 1)) 

        for start in range(0, max(1, num_frames - window_size + 1), stride):
            end = start + window_size
            ventana = datos_norm[start:end]
            actual_len = ventana.shape[0]
            
            if actual_len < window_size:
                pad = np.zeros((window_size - actual_len, num_features))
                ventana = np.vstack([ventana, pad])
            
            input_tensor = np.expand_dims(ventana, axis=0)
            pred_phases, pred_kpis = model.predict(input_tensor, verbose=0)

            accum_phases[start:start+actual_len] += pred_phases[0][:actual_len]
            accum_kpis[start:start+actual_len] += pred_kpis[0][:actual_len]
            counts[start:start+actual_len] += 1

        counts[counts == 0] = 1
        final_phases = accum_phases / counts
        final_kpis = accum_kpis / counts

        # 2. CAMBIO: Llamada a la nueva función de reporte visual
        # Esto generará el archivo PNG con las dos gráficas solicitadas
        ruta_reporte = save_visual_report(
            predicciones=[final_phases, final_kpis], 
            video_source=args.fuente, 
            seq_len=num_frames, 
            output_dir="./reportes", 
            min_run_length=3
        )
        
        print(f"Proceso finalizado. Gráfico guardado en: {ruta_reporte}")

    except Exception as e:
        print(f"Error en la fase de análisis: {e}")

else:
    print("Error: No se detectó movimiento.")