import cv2
import argparse
from poseProcessor import PoseProcessor
from calculosKeypoints import process_historial
from redBILSTM import segmentar_repeticiones, crear_bilstm_analizador

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
    print("No se detectaron landmarks en ningún frame. No hay nada que segmentar.")
else:
    # process_historial debe devolver un pandas.DataFrame con columnas (p. ej. 'R_knee', etc.)
    df_angles = process_historial(historial_landmarks)

    # debug rápido (opcional): ver columnas para confirmar que segmentar_repeticiones recibirá lo que espera
    try:
        print("Columns produced by process_historial:", list(df_angles.columns))
    except Exception:
        pass

    # Intentamos pasar el DataFrame a segmentar_repeticiones; si la función espera la lista,
    # lo manejamos con try/except y lo llamamos con historial_landmarks como fallback.
    try:
        repeticiones = segmentar_repeticiones(df_angles)
    except Exception as e:
        print("segmentar_repeticiones falló con DataFrame (intentando lista). Error:", e)
        print("Llamando segmentar_repeticiones con la lista raw 'historial_landmarks' por compatibilidad...")
    
    reps = segmentar_repeticiones(df_angles, smooth_window=11, min_distance=int(0.4*fps))
    print(f"Repeticiones segmentadas: {len(reps)}")

    # guardar CSV
    df_angles.to_csv("angles_per_frame.csv", index=False)
    print(
        f"CSV generado:\n"
        f"{frame_count or 'N/A'} frames procesados.\n"
        f"{len(df_angles)} filas en el CSV.\n"
        f"FPS: {fps}"
    )