"""
reprocesar_videos.py  —  Procesa todos los videos del dataset con analizar.py
                         --solo_npy para regenerar los .npy desde cero.

Lee todos los videos de VIDEOS_DIR y ejecuta `python analizar.py --fuente <v>
--solo_npy` para cada uno. Imprime progreso, tiempo total y errores.

Uso:
    python reprocesar_videos.py
    python reprocesar_videos.py --videos_dir "ruta\\a\\carpeta"
    python reprocesar_videos.py --skip_existing   # salta los .npy que ya existen
"""

import argparse
import subprocess
import sys
import time
from pathlib import Path

VIDEO_EXTS = {".mp4", ".mov", ".avi", ".mkv", ".m4v", ".webm"}

DEFAULT_VIDEOS_DIR = r"C:\Users\Usuario\Desktop\TFG\backend\entrenamiento\ejemplos"
NPY_DIR = Path("entrenamiento/archivos_entrenamiento")


def find_videos(videos_dir: Path) -> list:
    """Devuelve lista de Paths a videos en la carpeta (no recursivo)."""
    videos = []
    for ext in VIDEO_EXTS:
        videos.extend(videos_dir.glob(f"*{ext}"))
        videos.extend(videos_dir.glob(f"*{ext.upper()}"))
    return sorted(set(videos))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--videos_dir", default=DEFAULT_VIDEOS_DIR,
                        help="Carpeta con los videos originales")
    parser.add_argument("--skip_existing", action="store_true",
                        help="Salta los videos cuyo .npy ya existe")
    args = parser.parse_args()

    videos_dir = Path(args.videos_dir)
    if not videos_dir.exists():
        print(f"ERROR: no existe la carpeta {videos_dir}")
        sys.exit(1)

    videos = find_videos(videos_dir)
    if not videos:
        print(f"No se encontro ningun video en {videos_dir}")
        sys.exit(1)

    print(f"Carpeta de videos : {videos_dir}")
    print(f"Carpeta de .npy   : {NPY_DIR.resolve()}")
    print(f"Videos detectados : {len(videos)}")
    print()

    NPY_DIR.mkdir(parents=True, exist_ok=True)

    ok = []
    fail = []
    skip = []
    t0 = time.time()

    for i, v in enumerate(videos, 1):
        npy_path = NPY_DIR / (v.stem + ".npy")

        if args.skip_existing and npy_path.exists():
            skip.append(v.name)
            print(f"[{i:3d}/{len(videos)}] SALTADO  {v.name}  (npy ya existe)")
            continue

        print(f"[{i:3d}/{len(videos)}] {v.name} ...", flush=True)
        t_start = time.time()
        try:
            result = subprocess.run(
                [sys.executable, "analizar.py",
                 "--fuente", str(v),
                 "--solo_npy"],
                capture_output=True, text=True, timeout=600,
            )
            dt = time.time() - t_start
            if result.returncode == 0 and npy_path.exists():
                # Cargar para verificar shape
                try:
                    import numpy as np
                    from mirror_utils import ALL_COLUMNS as _ALL_COLS
                    arr = np.load(npy_path)
                    print(f"     OK ({dt:.1f}s)  shape={arr.shape}")
                    if arr.shape[1] != len(_ALL_COLS):
                        print(f"     [AVISO] columnas != {len(_ALL_COLS)} ({arr.shape[1]})")
                except Exception as e:
                    print(f"     OK ({dt:.1f}s)  pero no pude verificar: {e}")
                ok.append(v.name)
            else:
                print(f"     FAIL ({dt:.1f}s)")
                if result.stderr:
                    print("     stderr:", result.stderr.strip()[-300:])
                fail.append(v.name)
        except subprocess.TimeoutExpired:
            print(f"     TIMEOUT (>10min)")
            fail.append(v.name)
        except Exception as e:
            print(f"     EXCEPCION: {e}")
            fail.append(v.name)

    total = time.time() - t0
    print()
    print("=" * 60)
    print(f"Tiempo total: {total/60:.1f} min")
    print(f"OK     : {len(ok)}")
    print(f"FAIL   : {len(fail)}")
    print(f"SKIP   : {len(skip)}")
    if fail:
        print()
        print("Videos fallidos:")
        for f in fail:
            print(f"  - {f}")
    print("=" * 60)


if __name__ == "__main__":
    main()
