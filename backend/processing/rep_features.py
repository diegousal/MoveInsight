import warnings
import numpy as np
import pandas as pd

# 1=malo, 2=medio, 3=bueno — uniforme para todos los KPIs
LABEL_TO_FLOAT = {kpi: {1: 0.30, 2: 0.70, 3: 1.00} for kpi in
                  ["depth", "torso", "knees", "symmetry", "rhythm"]}

LABEL_TEXT = {
    "depth":    {1: "Insuficiente", 2: "Paralela",       3: "Completa"},
    "torso":    {1: "Excesiva",     2: "Leve inclin.",    3: "Normal"},
    "knees":    {1: "Valgo",        2: "Ligero valgo",    3: "Sin valgo"},
    "symmetry": {1: "Asimetría",   2: "Leve asimetría",  3: "Simétrico"},
    "rhythm":   {1: "Descontrol.", 2: "Aceptable",       3: "Buen ritmo"},
}

KPI_NAMES = ["depth", "torso", "knees", "symmetry", "rhythm"]

# ── Opción B del TFG: mapeo de clase categórica (1/2/3) → escala continua 0-10 ──
# Permite mantener intacta toda la lógica aguas abajo (readiness, recomendaciones,
# gráficos) que asume 0-10, mientras la UI muestra las etiquetas cualitativas.
CLASS_TO_SCORE = {1: 3.0, 2: 7.0, 3: 10.0}

# Nombre legible en español de cada KPI (para reportes y export).
KPI_ES = {
    "depth":    "Profundidad",
    "torso":    "Torso",
    "knees":    "Valgo de rodilla",
    "symmetry": "Simetría",
    "rhythm":   "Ritmo",
}

FEATURE_NAMES = [
    # Depth 3D (4)
    "min_L_knee", "min_R_knee", "min_hip_angle", "knee_depth_avg",
    # Depth 2D — angulo en plano XY, inmune al ruido Z (3) [NUEVO]
    "min_L_knee_2d", "min_R_knee_2d", "knee_depth_avg_2d",
    # Torso (4)
    "max_torso", "p90_torso", "mean_torso_bottom", "torso_range",
    # Knees — valgo proxy via asymmetry (4)
    "knee_sym_at_bottom", "knee_diff_max", "ankle_L_min", "ankle_R_min",
    # Knees — valgo real medial-lateral (4) [NUEVO]
    "valgus_L_peak", "valgus_R_peak", "valgus_bottom_mean", "valgus_bilateral",
    # Symmetry (5)
    "knee_diff_integral", "hip_diff_mean", "hip_diff_max",
    "L_knee_range", "R_knee_range",
    # Rhythm (8)
    "descent_speed_peak", "ascent_speed_peak", "descent_ratio",
    "jerk_mean", "jerk_max", "total_duration_s", "descent_frames", "ascent_frames",
    # Stability (4)
    "sust_dx_range", "sust_dx_std", "sust_dy_range", "sust_dz_range",
    # View hint (1) [NUEVO] — ayuda al modelo a distinguir frontal/lateral implicitamente
    "hip_zx_ratio_mean",
]

N_FEATURES = len(FEATURE_NAMES)  # 37


def extract_rep_features(df: pd.DataFrame, start: int, end: int, fps: float) -> np.ndarray:
    """
    Extract view-agnostic features for a single rep [start, end].
    Returns 1D float32 array of length N_FEATURES.
    """
    end = min(end, len(df) - 1)
    seg = df.iloc[start : end + 1]
    n = len(seg)
    if n < 3:
        return np.zeros(N_FEATURES, dtype=np.float32)

    dt = 1.0 / fps

    def col(name):
        return seg[name].values.astype(float) if name in seg.columns else np.full(n, np.nan)

    L_knee     = col("L_knee")
    R_knee     = col("R_knee")
    L_hip      = col("L_hip")
    R_hip      = col("R_hip")
    L_ank      = col("L_ankle")
    R_ank      = col("R_ankle")
    torso      = col("torso")
    sust_dx    = col("sust_dx")
    sust_dy    = col("sust_dy")
    sust_dz    = col("sust_dz")
    L_valgus   = col("L_valgus")    # desviacion medial-lateral signada
    R_valgus   = col("R_valgus")
    L_knee_2d  = col("L_knee_2d")  # angulo rodilla en plano XY
    R_knee_2d  = col("R_knee_2d")
    hip_zx     = col("hip_zx_ratio")

    knee_min_sig = np.fmin(L_knee, R_knee)
    bottom_mask  = _bottom_mask(knee_min_sig, margin_deg=15.0)

    # ── Depth 3D ─────────────────────────────────────────────────────────────
    min_L_knee     = _nanmin(L_knee)
    min_R_knee     = _nanmin(R_knee)
    min_hip        = _nanmin(np.fmin(L_hip, R_hip))
    knee_depth_avg = np.nanmean([min_L_knee, min_R_knee])

    # ── Depth 2D — angulo en plano XY, robusto para vista lateral ────────────
    min_L_knee_2d     = _nanmin(L_knee_2d)
    min_R_knee_2d     = _nanmin(R_knee_2d)
    knee_depth_avg_2d = np.nanmean([v for v in [min_L_knee_2d, min_R_knee_2d]
                                    if not np.isnan(v)] or [0.0])

    # ── Torso ────────────────────────────────────────────────────────────────
    max_torso      = _nanmax(torso)
    p90_torso      = float(np.nanpercentile(torso, 90)) if _has_data(torso) else 0.0
    mean_torso_bot = _nanmean_raw(torso[bottom_mask]) if bottom_mask.any() else _nanmean(torso)
    torso_range    = max_torso - _nanmin(torso)

    # ── Knees / valgo proxy (asimetria angular) ───────────────────────────────
    knee_diff     = np.abs(L_knee - R_knee)
    knee_sym_bot  = _nanmean_raw(knee_diff[bottom_mask]) if bottom_mask.any() else _nanmean(knee_diff)
    knee_diff_max = _nanmax(knee_diff)
    ankle_L_min   = _nanmin(L_ank)
    ankle_R_min   = _nanmin(R_ank)

    # ── Valgo real medial-lateral ─────────────────────────────────────────────
    # L_valgus > 0 → rodilla izq se desvia medialmente (valgus)
    # R_valgus < 0 → rodilla der se desvia medialmente (valgus)
    # Invariante al espejo: L_mir=-R_orig, R_mir=-L_orig → features identicas
    abs_L_valgus = np.abs(L_valgus)
    abs_R_valgus = np.abs(R_valgus)
    valgus_L_peak = _nanmax(abs_L_valgus)
    valgus_R_peak = _nanmax(abs_R_valgus)

    # Media del modulo del valgo en los frames de fondo (momento mas critico)
    avg_abs_valgus = (abs_L_valgus + abs_R_valgus) / 2.0
    valgus_bottom_mean = (_nanmean_raw(avg_abs_valgus[bottom_mask])
                          if bottom_mask.any() else _nanmean(avg_abs_valgus))

    # Valgo bilateral signado: captura cuando AMBAS rodillas colapsan
    # L_valgus - R_valgus > 0 cuando L va al centro Y R va al centro
    bilateral_raw = L_valgus - R_valgus
    valgus_bilateral = (_nanmean_raw(bilateral_raw[bottom_mask])
                        if bottom_mask.any() else _nanmean(bilateral_raw))

    # ── Symmetry ─────────────────────────────────────────────────────────────
    knee_diff_integral = float(np.nansum(knee_diff)) / n
    hip_diff      = np.abs(L_hip - R_hip)
    hip_diff_mean = _nanmean(hip_diff)
    hip_diff_max  = _nanmax(hip_diff)
    L_knee_range  = _nanmax(L_knee) - _nanmin(L_knee)
    R_knee_range  = _nanmax(R_knee) - _nanmin(R_knee)

    # ── Rhythm ───────────────────────────────────────────────────────────────
    vert = sust_dy if _has_data(sust_dy) else knee_min_sig / 180.0
    vert = _interpolate_nan(vert)

    vel  = np.diff(vert) / dt
    acc  = np.diff(vel)  / dt if len(vel) > 1 else np.array([0.0])
    jerk = np.diff(acc)  / dt if len(acc) > 1 else np.array([0.0])

    valley_idx     = int(np.nanargmin(knee_min_sig)) if _has_data(knee_min_sig) else n // 2
    descent_frames = float(max(valley_idx, 1))
    ascent_frames  = float(max(n - valley_idx, 1))
    descent_ratio  = descent_frames / n

    descent_vel = vel[:valley_idx] if valley_idx > 0 else np.array([0.0])
    ascent_vel  = vel[valley_idx:] if valley_idx < len(vel) else np.array([0.0])

    descent_speed_peak = float(np.nanmax(np.abs(descent_vel))) if len(descent_vel) else 0.0
    ascent_speed_peak  = float(np.nanmax(np.abs(ascent_vel)))  if len(ascent_vel)  else 0.0
    jerk_mean = float(np.nanmean(np.abs(jerk))) if len(jerk) else 0.0
    jerk_max  = float(np.nanmax(np.abs(jerk)))  if len(jerk) else 0.0
    total_dur = n / fps

    # ── Stability ────────────────────────────────────────────────────────────
    sust_dx_range = _nanmax(sust_dx) - _nanmin(sust_dx)
    sust_dx_std   = float(np.nanstd(sust_dx)) if _has_data(sust_dx) else 0.0
    sust_dy_range = _nanmax(sust_dy) - _nanmin(sust_dy)
    sust_dz_range = _nanmax(sust_dz) - _nanmin(sust_dz)

    # ── View hint — orientacion de camara ────────────────────────────────────
    # 0 ≈ frontal, 1 ≈ lateral. Ayuda al modelo a separar implicitamente
    # los patrones de features segun el tipo de grabacion.
    hip_zx_ratio_mean = _nanmean(hip_zx)

    features = np.array([
        # Depth 3D (4)
        min_L_knee, min_R_knee, min_hip, knee_depth_avg,
        # Depth 2D (3)
        min_L_knee_2d, min_R_knee_2d, knee_depth_avg_2d,
        # Torso (4)
        max_torso, p90_torso, mean_torso_bot, torso_range,
        # Knees proxy (4)
        knee_sym_bot, knee_diff_max, ankle_L_min, ankle_R_min,
        # Valgo real (4)
        valgus_L_peak, valgus_R_peak, valgus_bottom_mean, valgus_bilateral,
        # Symmetry (5)
        knee_diff_integral, hip_diff_mean, hip_diff_max, L_knee_range, R_knee_range,
        # Rhythm (8)
        descent_speed_peak, ascent_speed_peak, descent_ratio,
        jerk_mean, jerk_max, total_dur, descent_frames, ascent_frames,
        # Stability (4)
        sust_dx_range, sust_dx_std, sust_dy_range, sust_dz_range,
        # View hint (1)
        hip_zx_ratio_mean,
    ], dtype=np.float32)

    return np.nan_to_num(features, nan=0.0, posinf=0.0, neginf=0.0)


# ── Helpers ───────────────────────────────────────────────────────────────────

def _has_data(arr):
    return not np.all(np.isnan(arr))

def _nanmin(arr):
    return float(np.nanmin(arr)) if _has_data(arr) else 0.0

def _nanmax(arr):
    return float(np.nanmax(arr)) if _has_data(arr) else 0.0

def _nanmean(arr):
    return float(np.nanmean(arr)) if _has_data(arr) else 0.0

def _nanmean_raw(arr):
    """np.nanmean que devuelve NaN en slices vacíos/todo-NaN SIN emitir el
    RuntimeWarning 'Mean of empty slice'. El valor resultante es idéntico al de
    np.nanmean (NaN cuando no hay datos): se mantiene a propósito para no alterar
    las features con las que se entrenó el modelo (evita train/inference skew)."""
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", category=RuntimeWarning)
        return float(np.nanmean(arr))

def _bottom_mask(signal: np.ndarray, margin_deg: float = 15.0) -> np.ndarray:
    if not _has_data(signal):
        return np.zeros(len(signal), dtype=bool)
    return signal <= (np.nanmin(signal) + margin_deg)

def _interpolate_nan(arr: np.ndarray) -> np.ndarray:
    mask = np.isnan(arr)
    if not mask.any():
        return arr
    idx = np.arange(len(arr))
    valid = ~mask
    if valid.sum() < 2:
        return np.where(mask, 0.0, arr)
    out = arr.copy()
    out[mask] = np.interp(idx[mask], idx[valid], arr[valid])
    return out
