import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
import numpy as np
import cv2
import time


class PoseProcessor:
    def __init__(self, fps, model_path='modelos/pose_landmarker_heavy.task'):
        base_options = python.BaseOptions(model_asset_path=model_path)
        options = vision.PoseLandmarkerOptions(
            base_options=base_options,
            running_mode=vision.RunningMode.VIDEO,
            min_pose_detection_confidence=0.5,
            min_pose_presence_confidence=0.5,
            min_tracking_confidence=0.5
        )
        self.detector = vision.PoseLandmarker.create_from_options(options)
        self.fps = fps
        self.frame_idx = 0  # contador de frames

    def process_frame(self, frame):
        """
        Recibe frame BGR de OpenCV
        Devuelve:
            - results (objeto de la nueva API)
            - pose_vector (numpy array plano) o None si no hay pose
        """
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)

        self.frame_idx += 1
        # timestamp en ms usando fps real (monótono)
        frame_timestamp_ms = int(self.frame_idx * (1000.0 / self.fps))

        results = self.detector.detect_for_video(mp_image, frame_timestamp_ms)

        pose_vector = None
        if results.pose_landmarks and len(results.pose_landmarks) > 0:
            first_person = results.pose_landmarks[0]
            pose_vector = np.array(
                [[lm.x, lm.y, lm.z, getattr(lm, "visibility", 1.0)] for lm in first_person],
                dtype=np.float32
            ).flatten()

        return results, pose_vector

    def close(self):
        self.detector.close()