"""
reprocesar_videos.py — Regenera los .npy de todos los vídeos de una carpeta.

Actualizado: ya no invoca analizar.py (eliminado). Ahora es un atajo de
procesar_video.py en modo lote --solo_npy, ejecutando el pipeline en proceso
(sin subprocesos), lo que además reutiliza la carga de modelos.

Uso:
    python entrenamiento/reprocesar_videos.py
    python entrenamiento/reprocesar_videos.py --videos_dir "ruta\\a\\carpeta"
    python entrenamiento/reprocesar_videos.py --skip_existing
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import procesar_video

DEFAULT_VIDEOS_DIR = str(Path(__file__).resolve().parent / "dataset")

if __name__ == "__main__":
    argv = sys.argv[1:]
    # Traducir la interfaz antigua a la nueva
    if "--videos_dir" in argv:
        i = argv.index("--videos_dir")
        carpeta = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    else:
        carpeta = DEFAULT_VIDEOS_DIR

    sys.argv = [sys.argv[0], "--carpeta", carpeta, "--solo_npy"] + argv
    procesar_video.main()
