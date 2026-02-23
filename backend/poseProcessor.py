import mediapipe as mp
import numpy as np

class PoseProcessor:
    def __init__(self):
        self.mp_pose = mp.solutions.pose
        self.pose = self.mp_pose.Pose(
            static_image_mode=False,
            model_complexity=2,
            smooth_landmarks=True,
            enable_segmentation=False,
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5
        )
        self.mp_drawing = mp.solutions.drawing_utils

    def process_frame(self, frame):
        """
        Recibe frame BGR
        Devuelve:
            - results
            - pose_vector
        """
        results = self.pose.process(frame)

        pose_vector = None
        if results.pose_landmarks:
            pose_vector = np.array(
                [[lm.x, lm.y, lm.z, lm.visibility] for lm in results.pose_landmarks.landmark]
            ).flatten()

        return results, pose_vector

    def draw_landmarks(self, frame, results):
        """Dibuja el esqueleto sobre el frame original"""
        if results.pose_landmarks:
            self.mp_drawing.draw_landmarks(
                frame,
                results.pose_landmarks,
                self.mp_pose.POSE_CONNECTIONS,
                self.mp_drawing.DrawingSpec(color=(245,117,66), thickness=2, circle_radius=2),
                self.mp_drawing.DrawingSpec(color=(245,66,230), thickness=2, circle_radius=2)
            )
        return frame

    def close(self):
        self.pose.close()
