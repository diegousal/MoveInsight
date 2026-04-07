import argparse
from pathlib import Path

import cv2
import numpy as np
import tensorflow as tf

from visualization import mostrar_frame
from pose_features import process_historial
from pose_processor import PoseProcessor
from rep_report import save_visual_report

# Rutas de modelos y entrenamiento
MODEL_PATH = Path("./modelos/v9")
TRAINING_DIR = Path("entrenamiento/archivos_entrenamiento")

# Función para determinar la ruta de salida del archivo .npy basado en la fuente de video
def get_output_path(source: str) -> Path:
    source_path = Path(source)
    if "sentadilla" in source:
        return TRAINING_DIR / source_path.stem
    return source_path.with_suffix("")


def main() -> None:
    # --- Configuración de argumentos ---
    parser = argparse.ArgumentParser()
    parser.add_argument("--fuente", type=str, default="sentadilla.mp4")
    args = parser.parse_args()
    try:
        fuente = int(args.fuente)
    except ValueError:
        fuente = args.fuente
        
    #-- Inicialización de captura de video ---
    cap = cv2.VideoCapture(fuente)
    if not cap.isOpened():
        raise RuntimeError(f"No se pudo abrir la fuente: {fuente}")
    
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0

    #-- Inicialización del procesador de pose --
    pose_processor = PoseProcessor(fps=fps)
    historial_landmarks = []

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        results, pose_vector = pose_processor.process_frame(frame)
        if pose_vector is not None:
            historial_landmarks.append(pose_vector)

        #Opcional: Mostrar el video con las detecciones de pose, comentar si no se desea visualizar
        mostrar_frame(frame, results)
    
    #-- Liberar recursos --
    cap.release()
    cv2.destroyAllWindows()
    pose_processor.close()

    #-- Procesamiento de datos y generación de reporte --
    if not historial_landmarks:
        print("Error: No se detectó movimiento.")
        return

    df_angles = process_historial(historial_landmarks)
    df_angles["delta_time"] = 1.0 / fps
    datos = df_angles.fillna(0.0).values

    # Guardar los datos procesados en un archivo .npy para entrenamiento o análisis posterior
    npy_path = get_output_path(args.fuente)
    npy_path.parent.mkdir(parents=True, exist_ok=True)
    np.save(npy_path, datos)

    #-- Cargar modelo y realizar predicciones --
    try:
        model = tf.keras.models.load_model(MODEL_PATH / "best_model.h5")
        mean = np.load(MODEL_PATH / "norm_mean.npy")
        std = np.load(MODEL_PATH / "norm_std.npy")

        datos_norm = (datos - mean) / std
        input_tensor = np.expand_dims(datos_norm, axis=0)  # (1, T, Features)

        pred_phases, pred_kpis = model.predict(input_tensor, verbose=1)

        ruta_reporte = save_visual_report(
            predicciones=[pred_phases[0], pred_kpis[0]],
            video_source=args.fuente,
            seq_len=datos_norm.shape[0],
            output_dir="./reportes",
            min_run_length=3,
        )

        print(f"Análisis completado con éxito. Reporte en: {ruta_reporte}")

    except Exception as e:
        print(f"Error crítico en la fase de análisis: {e}")


if __name__ == "__main__":
    main()