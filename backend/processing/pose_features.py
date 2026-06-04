import numpy as np
import pandas as pd
from scipy.signal import savgol_filter

# --- CONFIGURACIÓN GLOBAL ---
UMBRAL_CONFIANZA = 0.65   # Filtro de fiabilidad (0.0 a 1.0)
VENTANA_SUAVIZADO = 11    # Impar (para Savitzky-Golay)

# Orden EXACTO de las 18 columnas que produce process_historial(). El pipeline
# lee siempre por nombre (el orden es irrelevante en inferencia), PERO este es
# además el orden con el que video.py serializa el .npy de entrenamiento, así que
# debe coincidir con el ALL_COLUMNS del repo de entrenamiento. video.py reutiliza
# esta misma lista para el export → una única fuente de verdad del orden de columnas.
FEATURE_COLS = [
    'L_knee', 'R_knee', 'L_hip', 'R_hip',       # 0-3
    'L_ankle', 'R_ankle', 'torso',               # 4-6
    'sust_dx', 'sust_dy', 'sust_dz',             # 7-9
    'vel_y_hip', 'acc_y_hip', 'vel_knee',        # 10-12
    'L_valgus', 'R_valgus',                      # 13-14  desviación medial-lateral signada
    'L_knee_2d', 'R_knee_2d',                    # 15-16  ángulo rodilla en plano XY (inmune al ruido Z)
    'hip_zx_ratio',                              # 17     orientación cámara (0=frontal, 1=lateral)
]

def calculate_angle_3d(a, b, c):
    """Calcula el ángulo real en el espacio 3D usando vectores (x, y, z)."""
    # Convertimos a vectores 3D ignorando la cuarta columna (visibilidad)
    a, b, c = np.array(a[:3]), np.array(b[:3]), np.array(c[:3])
    ba = a - b
    bc = c - b
    
    denominator = np.linalg.norm(ba) * np.linalg.norm(bc)
    if denominator < 1e-6:
        return np.nan
        
    cosine_angle = np.dot(ba, bc) / denominator
    angle = np.arccos(np.clip(cosine_angle, -1.0, 1.0))
    return np.degrees(angle)

def calculate_torso_3d(shoulder_mid, hip_mid):
    """Calcula la inclinación del torso respecto a la vertical en el espacio 3D."""
    v_torso = shoulder_mid - hip_mid
    # Vertical de referencia (MediaPipe Y crece hacia abajo)
    v_vertical = np.array([0, -1, 0]) 
    
    norm = np.linalg.norm(v_torso) * np.linalg.norm(v_vertical)
    if norm < 1e-6:
        return np.nan
        
    return np.degrees(np.arccos(np.clip(np.dot(v_torso, v_vertical) / norm, -1.0, 1.0)))

def calcular_articulacion_segura(kp, indices, umbral=UMBRAL_CONFIANZA):
    """
    Solo calcula el ángulo si los TRES puntos involucrados son fiables.
    Esto elimina los ángulos 'fantasma' cuando falta un extremo (ej. el tobillo).
    """
    # Verificamos si los 3 puntos (A, B, C) superan el umbral de visibilidad
    for idx in indices:
        if kp[idx][3] < umbral:
            return np.nan

    return calculate_angle_3d(kp[indices[0]], kp[indices[1]], kp[indices[2]])


def calculate_angle_2d(a, b, c):
    """Ángulo en el plano XY de la imagen (ignorando Z)."""
    a = np.array(a[:2]); b = np.array(b[:2]); c = np.array(c[:2])
    ba = a - b
    bc = c - b
    denom = np.linalg.norm(ba) * np.linalg.norm(bc)
    if denom < 1e-6:
        return np.nan
    cos_a = np.dot(ba, bc) / denom
    return float(np.degrees(np.arccos(np.clip(cos_a, -1.0, 1.0))))


def calcular_articulacion_segura_2d(kp, indices, umbral=UMBRAL_CONFIANZA):
    """Versión 2D (plano XY) del cálculo seguro de ángulo articular.
    Más robusta al ruido de la coordenada Z estimada por MediaPipe,
    especialmente útil para sentadillas grabadas de perfil."""
    for idx in indices:
        if kp[idx][3] < umbral:
            return np.nan
    return calculate_angle_2d(kp[indices[0]], kp[indices[1]], kp[indices[2]])

def calcular_centro_sustentacion(kp, umbral=UMBRAL_CONFIANZA):
    """Calcula el promedio de los puntos de apoyo que sean fiables."""
    # Puntos de la base: Tobillos, Talones y Punteras
    foot_indices = [27, 28, 29, 30, 31, 32]
    puntos_validos = [kp[i][:3] for i in foot_indices if kp[i][3] >= umbral]
    
    if len(puntos_validos) < 2: # Necesitamos al menos 2 puntos para una base mínima
        return np.nan, np.nan, np.nan
        
    return np.mean(puntos_validos, axis=0)

def extract_features(kp):
    """Mapea los puntos de MediaPipe y extrae las métricas del frame."""
    data = {}

    # Índices MediaPipe BlazePose
    L_SH, R_SH = 11, 12
    L_EL, R_EL = 13, 14
    L_WR, R_WR = 15, 16
    L_HP, R_HP = 23, 24
    L_KN, R_KN = 25, 26
    L_AN, R_AN = 27, 28
    L_FT, R_FT = 31, 32

    # --- CÁLCULOS ARTICULARES ---
    # data['L_elbow']    = calcular_articulacion_segura(kp, (L_SH, L_EL, L_WR))
    # data['R_elbow']    = calcular_articulacion_segura(kp, (R_SH, R_EL, R_WR))
    # data['L_shoulder'] = calcular_articulacion_segura(kp, (L_EL, L_SH, L_HP))
    # data['R_shoulder'] = calcular_articulacion_segura(kp, (R_EL, R_SH, R_HP))
    data['L_knee']     = calcular_articulacion_segura(kp, (L_HP, L_KN, L_AN))
    data['R_knee']     = calcular_articulacion_segura(kp, (R_HP, R_KN, R_AN))
    data['L_hip']      = calcular_articulacion_segura(kp, (L_SH, L_HP, L_KN))
    data['R_hip']      = calcular_articulacion_segura(kp, (R_SH, R_HP, R_KN))
    data['L_ankle']    = calcular_articulacion_segura(kp, (L_KN, L_AN, L_FT))
    data['R_ankle']    = calcular_articulacion_segura(kp, (R_KN, R_AN, R_FT))

    # Versión 2D de las rodillas (proyectadas en el plano XY de la imagen).
    # Para vídeos laterales es MUCHO más fiable que la 3D porque no depende
    # de la coordenada Z estimada (que MediaPipe predice con gran ruido).
    data['L_knee_2d']  = calcular_articulacion_segura_2d(kp, (L_HP, L_KN, L_AN))
    data['R_knee_2d']  = calcular_articulacion_segura_2d(kp, (R_HP, R_KN, R_AN))

    # --- TORSO ---
    hombros_visibles = all(kp[i][3] >= UMBRAL_CONFIANZA for i in [L_SH, R_SH, L_HP, R_HP])
    if hombros_visibles:
        mid_shoulder = (kp[L_SH][:3] + kp[R_SH][:3]) / 2
        mid_hip      = (kp[L_HP][:3] + kp[R_HP][:3]) / 2
        data['torso'] = calculate_torso_3d(mid_shoulder, mid_hip)
    else:
        data['torso'] = np.nan
        mid_shoulder = mid_hip = None

    # --- CENTRO DE SUSTENTACIÓN (relativo a la cadera) ---
    # Se guarda el desplazamiento CdS - cadera_media en lugar de coordenadas
    # absolutas, que dependen del encuadre y la distancia a la cámara.
    caderas_visibles = all(kp[i][3] >= UMBRAL_CONFIANZA for i in [L_HP, R_HP])
    sust = calcular_centro_sustentacion(kp)
    sust_valido = not np.any(np.isnan(sust))

    if caderas_visibles and sust_valido:
        if mid_hip is None:
            mid_hip = (kp[L_HP][:3] + kp[R_HP][:3]) / 2
        delta = sust - mid_hip

        # Normalización por longitud del torso: elimina diferencias entre
        # personas de distinta estatura y variaciones de distancia a la cámara.
        if mid_shoulder is not None:
            longitud_torso = np.linalg.norm(mid_shoulder - mid_hip)
            if longitud_torso > 1e-6:
                delta = delta / longitud_torso

        data['sust_dx'] = delta[0]  # desplazamiento lateral
        data['sust_dy'] = delta[1]  # desplazamiento vertical
        data['sust_dz'] = delta[2]  # desplazamiento en profundidad
    else:
        data['sust_dx'] = data['sust_dy'] = data['sust_dz'] = np.nan

    # --- VALGO LATERAL (plano frontal) ---
    # Desviación normalizada de la rodilla respecto a la línea cadera-tobillo
    data['L_valgus'] = calcular_valgus_lateral(kp, L_HP, L_KN, L_AN)
    data['R_valgus'] = calcular_valgus_lateral(kp, R_HP, R_KN, R_AN)

    # --- ORIENTACIÓN DE CÁMARA ---
    # Ratio |Z|/(|X|+|Z|) del eje cadera-cadera:
    #   ≈ 0  → cadera alineada con el eje X de la imagen (cámara frontal)
    #   ≈ 1  → cadera alineada con el eje Z (cámara lateral)
    if caderas_visibles:
        hip_vec = kp[R_HP][:3] - kp[L_HP][:3]
        abs_x, abs_z = abs(hip_vec[0]), abs(hip_vec[2])
        data['hip_zx_ratio'] = abs_z / (abs_x + abs_z + 1e-6)
    else:
        data['hip_zx_ratio'] = np.nan

    return data

def detect_view(historial_landmarks, vis_threshold=0.5):
    """
    Detecta si la camara esta en vista frontal o lateral.

    Criterio en tres pasos (orden de prioridad):

    1. Geometrico claro:
       - ratio X/Z > 1.6 → FRONTAL (sin duda)
       - ratio X/Z < 0.35 → LATERAL (sin duda, un lado totalmente oculto)

    2. Visibilidad bilateral fuerte (override geometrico):
       Si las DOS rodillas y los DOS hombros son MUY visibles
       (mediana > 0.75), se considera FRONTAL aunque la geometria
       sugiera lateral. Esto cubre los casos limite donde la
       camara esta muy oblicua pero ambos lados del cuerpo
       siguen siendo perfectamente detectables (caso ejemplo2).

    3. Zona oblicua intermedia (0.35 <= ratio <= 1.6):
       - Bilateral con visibilidad clara (>= 0.65) → FRONTAL
       - En cualquier otro caso                   → LATERAL

    Devuelve: ('frontal'|'lateral', ratio_geometrico)
    """
    L_SH, R_SH = 11, 12
    L_KN, R_KN = 25, 26
    L_HP, R_HP = 23, 24

    x_diffs, z_diffs = [], []
    kn_vis_L, kn_vis_R = [], []
    sh_vis_L, sh_vis_R = [], []

    for vec in historial_landmarks:
        kp = vec.reshape(-1, 4)
        # Solo frames donde hombros y caderas son detectables
        if all(kp[i][3] >= vis_threshold for i in [L_SH, R_SH, L_HP, R_HP]):
            sh_x = abs(kp[L_SH][0] - kp[R_SH][0])
            sh_z = abs(kp[L_SH][2] - kp[R_SH][2])
            hp_x = abs(kp[L_HP][0] - kp[R_HP][0])
            hp_z = abs(kp[L_HP][2] - kp[R_HP][2])
            x_diffs.append((sh_x + hp_x) / 2)
            z_diffs.append((sh_z + hp_z) / 2)

            # Visibilidad de rodillas y hombros
            kn_vis_L.append(kp[L_KN][3])
            kn_vis_R.append(kp[R_KN][3])
            sh_vis_L.append(kp[L_SH][3])
            sh_vis_R.append(kp[R_SH][3])

    if not x_diffs:
        return "frontal", 0.0  # fallback: sin datos suficientes

    avg_x = float(np.median(x_diffs))
    avg_z = float(np.median(z_diffs))
    ratio = avg_x / (avg_z + 1e-6)

    # Medianas de visibilidad
    med_kn_L = float(np.median(kn_vis_L))
    med_kn_R = float(np.median(kn_vis_R))
    med_sh_L = float(np.median(sh_vis_L))
    med_sh_R = float(np.median(sh_vis_R))

    # ── Paso 1: caso geometrico claramente frontal ─────────────────────────
    if ratio > 1.6:
        return "frontal", ratio

    # ── Paso 2: visibilidad bilateral muy fuerte → override a frontal ──────
    # Si los dos lados del cuerpo son MUY claramente detectables, es frontal
    # aunque la geometria diga lo contrario (caso oblicuo extremo / ejemplo2).
    VIS_FUERTE = 0.75
    bilateral_fuerte = (med_kn_L >= VIS_FUERTE and med_kn_R >= VIS_FUERTE and
                        med_sh_L >= VIS_FUERTE and med_sh_R >= VIS_FUERTE)
    if bilateral_fuerte and ratio >= 0.35:
        return "frontal", ratio

    # ── Paso 3: lateral claro por geometria (sin visibilidad fuerte) ───────
    if ratio < 0.35:
        return "lateral", ratio

    # ── Paso 4: zona oblicua → decidir por visibilidad clara ───────────────
    VIS_CLARA = 0.65
    ambas_rodillas = (med_kn_L >= VIS_CLARA and med_kn_R >= VIS_CLARA)
    ambos_hombros  = (med_sh_L >= VIS_CLARA and med_sh_R >= VIS_CLARA)

    if ambas_rodillas and ambos_hombros:
        return "frontal", ratio   # oblicuo pero con suficiente visibilidad bilateral
    else:
        return "lateral", ratio   # oblicuo con un lado oculto


def process_historial(historial_landmarks, fps):
    """
    Convierte el historial de puntos en un DataFrame limpio, filtrado y con cinemática.
    """
    # 1. Extracción de datos frame a frame
    records = []
    for f_kp in historial_landmarks:
        kp_matrix = f_kp.reshape(-1, 4)
        records.append(extract_features(kp_matrix))
    
    df = pd.DataFrame(records)
    dt = 1.0 / fps
    
    # 2. Interpolación base (rellena huecos pequeños de hasta 10 frames)
    df = df.interpolate(method='linear', limit=10, limit_direction='both')
    
    # 3. Suavizado SEGURO (Protección contra propagación de NaNs)
    if len(df) > VENTANA_SUAVIZADO:
        for col in df.columns:
            if df[col].notna().sum() > VENTANA_SUAVIZADO:
                # Rellenamos temporalmente con 0 para que el filtro no falle si hay NaNs
                temp_col = df[col].interpolate(method='linear', limit_direction='both').fillna(0)
                try:
                    df[col] = savgol_filter(temp_col, window_length=VENTANA_SUAVIZADO, polyorder=2)
                except ValueError:
                    pass

    # 4. Cálculo de Velocidades y Aceleración (Después del suavizado)
    df['vel_y_hip'] = df['sust_dy'].diff().fillna(0) / dt
    df['acc_y_hip'] = df['vel_y_hip'].diff().fillna(0) / dt

    if 'L_knee' in df.columns:
        df['vel_knee'] = df['L_knee'].diff().fillna(0) / dt
    else:
        df['vel_knee'] = 0.0

    # 5. Reordenar columnas para que coincidan EXACTAMENTE con FEATURE_COLS,
    #    independientemente del orden de inserción en extract_features.
    #    Columnas ausentes se rellenan con NaN.
    df = df.reindex(columns=FEATURE_COLS, fill_value=np.nan)

    return df


def calcular_valgus_lateral(kp, hip_idx, knee_idx, ankle_idx, umbral=UMBRAL_CONFIANZA):
    """
    Desviación medial-lateral de la rodilla respecto a la línea cadera-tobillo,
    medida en el sistema de referencia del cuerpo (eje ML = línea L_HP→R_HP en 3D).

    Aisla la componente de valgo/varo de la desviación anterior-posterior
    normal en sentadilla. Funciona con la cámara en cualquier orientación.

    Returns: fracción de la longitud de la pierna (|valor| = severidad).
    """
    L_HP_IDX, R_HP_IDX = 23, 24
    required = (hip_idx, knee_idx, ankle_idx, L_HP_IDX, R_HP_IDX)
    if any(kp[i][3] < umbral for i in required):
        return np.nan

    hip   = kp[hip_idx][:3]
    knee  = kp[knee_idx][:3]
    ankle = kp[ankle_idx][:3]

    # Eje medial-lateral del cuerpo (normalizado)
    ml_axis = kp[R_HP_IDX][:3] - kp[L_HP_IDX][:3]
    ml_len  = np.linalg.norm(ml_axis)
    if ml_len < 1e-6:
        return np.nan
    ml_axis = ml_axis / ml_len

    # Línea cadera→tobillo
    line     = ankle - hip
    line_len = np.linalg.norm(line)
    if line_len < 1e-6:
        return np.nan
    line_dir = line / line_len

    # Componente perpendicular de (cadera→rodilla) respecto a (cadera→tobillo)
    knee_rel = knee - hip
    proj_len = np.dot(knee_rel, line_dir)
    perp_vec = knee_rel - proj_len * line_dir

    # Proyección sobre eje ML del cuerpo → aisla la desviación frontal pura
    ml_deviation = np.dot(perp_vec, ml_axis)

    return float(ml_deviation / line_len)
