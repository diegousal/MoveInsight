import cv2
import argparse
from poseProcessor import PoseProcessor
from calculosKeypoints import process_historial

historial_landmarks = []

parser = argparse.ArgumentParser()
parser.add_argument('--fuente', type=str, default='sentadilla.mp4')
args = parser.parse_args()

try:
    fuente = int(args.fuente)
except ValueError:
    fuente = args.fuente

cap = cv2.VideoCapture(fuente)
num_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
print(f"Número total de frames en el vídeo: {num_frames}")

pose_processor = PoseProcessor()

frame_count = 0
SKIP_FRAMES = 1

while cap.isOpened():
    ret, frame = cap.read()

    if not ret:
        print("Fin del vídeo. Pulsa cualquier tecla.")
        cv2.waitKey(0)
        break

    frame_count += 1

    if frame_count % SKIP_FRAMES == 0:
  
        # frame_ia = cv2.resize(frame, (256, 256))
        frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results, pose_vector = pose_processor.process_frame(frame_rgb)

        if pose_vector is not None:
            historial_landmarks.append(pose_vector)

    # Dibujado
        if 'results' in locals() and results.pose_landmarks:
            mp_drawing = pose_processor.mp_drawing
            mp_utils = pose_processor.mp_pose
    
        pose_processor.draw_landmarks(frame, results)
        #cv2.imshow("Pipeline TFG Modular", frame)
 
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

print(f"Frames procesados: {len(historial_landmarks)}")

cap.release()
pose_processor.close()
cv2.destroyAllWindows()
print(historial_landmarks)
df_angles = process_historial(historial_landmarks)
df_angles = df_angles.ffill()

df_angles.to_csv("angles_per_frame.csv", index=False)
# df_reps.to_csv("features_per_repetition.csv", index=False)
print("CSV generados: angles_per_frame.csv")