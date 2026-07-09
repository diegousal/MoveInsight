"""
mirror_utils.py  —  Augmentacion por espejo horizontal.

Funciones para reflejar las features procesadas (no los videos):
  - Intercambia columnas izquierda <-> derecha (rodillas, caderas, tobillos)
  - Niega el desplazamiento lateral (sust_dx)
  - Recalcula vel_knee desde la nueva L_knee
  - Intercambia y niega L_valgus <-> R_valgus (valgo es signado)
  - Intercambia L_knee_2d <-> R_knee_2d
  - hip_zx_ratio invariante (orientacion de camara, no L/R)

La imagen especular es invariante para nuestras etiquetas de fase
(stand/descent/bottom/ascent) y para todos los KPIs (depth, torso,
knees-valgo, symmetry, rhythm), por lo que sirve como augmentacion
gratuita: duplica el dataset sin sesgar las etiquetas.
"""

import numpy as np

# Solo estas 8 columnas se usan como entrada a la BiLSTM (fuente única aquí;
# fases_dataset las importa de este módulo para evitar el import circular)
PHASE_COLS = ["L_knee", "R_knee", "L_hip", "R_hip",
              "sust_dy", "vel_knee", "vel_y_hip", "acc_y_hip"]

# Orden de columnas tal y como las produce process_historial -> .npy (T, 18)
ALL_COLUMNS = [
    "L_knee", "R_knee", "L_hip", "R_hip",       # 0-3
    "L_ankle", "R_ankle", "torso",               # 4-6
    "sust_dx", "sust_dy", "sust_dz",             # 7-9
    "vel_y_hip", "acc_y_hip", "vel_knee",        # 10-12
    "L_valgus", "R_valgus",                      # 13-14  desviacion medial-lateral signada
    "L_knee_2d", "R_knee_2d",                    # 15-16  angulo rodilla en plano XY (inmune al ruido Z)
    "hip_zx_ratio",                              # 17     orientacion camara (0=frontal, 1=lateral)
]


# Indices del .npy completo (18 cols)
_IDX_FULL = {name: i for i, name in enumerate(ALL_COLUMNS)}

# Indices dentro del array de 8 columnas
_IDX_8 = {name: i for i, name in enumerate(PHASE_COLS)}


def mirror_npy_full(arr: np.ndarray, fps: float = 30.0) -> np.ndarray:
    """
    Refleja un array (T, 18) producido por process_historial.

    Transformaciones:
      - L_knee  <-> R_knee,  L_hip  <-> R_hip,  L_ankle <-> R_ankle
      - L_knee_2d <-> R_knee_2d
      - sust_dx negado (componente lateral del centro de sustentacion)
      - vel_knee recalculado desde la nueva L_knee
      - L_valgus <-> R_valgus CON negacion de signo:
          L_valgus_mirror = -R_valgus_original  (la pierna derecha pasa a ser
          R_valgus_mirror = -L_valgus_original   la izquierda y el eje ML se invierte)
      - hip_zx_ratio invariante (no depende de izq/dcha)

    Devuelve un nuevo array (T, 18) con float32.
    """
    if arr.ndim != 2 or arr.shape[1] != len(ALL_COLUMNS):
        raise ValueError(f"Forma inesperada: {arr.shape}, esperaba (T, {len(ALL_COLUMNS)})")

    m = arr.copy().astype(np.float32)
    i = _IDX_FULL

    # 1) Intercambios L <-> R en angulos articulares 3D
    m[:, [i["L_knee"],  i["R_knee"]]]  = m[:, [i["R_knee"],  i["L_knee"]]]
    m[:, [i["L_hip"],   i["R_hip"]]]   = m[:, [i["R_hip"],   i["L_hip"]]]
    m[:, [i["L_ankle"], i["R_ankle"]]] = m[:, [i["R_ankle"], i["L_ankle"]]]

    # 2) Intercambio L <-> R en angulos 2D de rodilla
    m[:, [i["L_knee_2d"], i["R_knee_2d"]]] = m[:, [i["R_knee_2d"], i["L_knee_2d"]]]

    # 3) Negar sust_dx (componente lateral)
    m[:, i["sust_dx"]] = -m[:, i["sust_dx"]]

    # 4) Recalcular vel_knee desde la nueva L_knee
    dt = 1.0 / fps
    new_L_knee = m[:, i["L_knee"]]
    vel_knee = np.zeros_like(new_L_knee, dtype=np.float32)
    vel_knee[1:] = np.diff(new_L_knee) / dt
    m[:, i["vel_knee"]] = vel_knee

    # 5) Valgo: swap + negacion de signo
    #    El eje medial-lateral (L_HP -> R_HP) se invierte en el espejo,
    #    por lo que la proyeccion cambia de signo ademas de intercambiarse.
    old_L_valgus = m[:, i["L_valgus"]].copy()
    old_R_valgus = m[:, i["R_valgus"]].copy()
    m[:, i["L_valgus"]] = -old_R_valgus
    m[:, i["R_valgus"]] = -old_L_valgus

    # 6) hip_zx_ratio: invariante — no se modifica

    return m


def mirror_phase_seq(seq: np.ndarray, fps: float = 30.0) -> np.ndarray:
    """
    Refleja una secuencia (T, 8) en el orden PHASE_COLS.

    Solo intercambia L<->R y recalcula vel_knee.  No hay sust_dx
    en estas 8 columnas, asi que no hay negaciones.
    """
    if seq.ndim != 2 or seq.shape[1] != len(PHASE_COLS):
        raise ValueError(f"Forma inesperada: {seq.shape}, esperaba (T, {len(PHASE_COLS)})")

    m = seq.copy().astype(np.float32)
    i = _IDX_8

    # 1) Swap L <-> R
    m[:, [i["L_knee"], i["R_knee"]]] = m[:, [i["R_knee"], i["L_knee"]]]
    m[:, [i["L_hip"],  i["R_hip"]]]  = m[:, [i["R_hip"],  i["L_hip"]]]

    # 2) Recalcular vel_knee desde la nueva L_knee
    dt = 1.0 / fps
    new_L_knee = m[:, i["L_knee"]]
    vel_knee = np.zeros_like(new_L_knee, dtype=np.float32)
    vel_knee[1:] = np.diff(new_L_knee) / dt
    m[:, i["vel_knee"]] = vel_knee

    return m
