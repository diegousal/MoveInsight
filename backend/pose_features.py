import numpy as np
import pandas as pd
from scipy.signal import savgol_filter

# --- CONFIGURACIÓN GLOBAL ---
UMBRAL_CONFIANZA = 0.65  # Filtro de fiabilidad (0.0 a 1.0)
VENTANA_SUAVIZADO = 11   # Debe ser un número impar (para el filtro Savitzky-Golay)

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

    return data

def process_historial(historial_landmarks,fps):
    """
    Convierte el historial de puntos en un DataFrame limpio y filtrado.
    """
    # 1. Extracción de datos frame a frame
    records = []
    for f_kp in historial_landmarks:
        kp_matrix = f_kp.reshape(-1, 4)
        records.append(extract_features(kp_matrix))
    
    df = pd.DataFrame(records)
    dt = 1.0 / fps

    
    # 2. Interpolación controlada
    # Solo rellenamos huecos pequeños (máximo 10 frames). 
    # Si la pierna no se ve durante medio video, se queda como NaN.
    df = df.interpolate(method='linear', limit=10, limit_direction='both')
    
    # 3. Suavizado de señal (Savitzky-Golay)
    # Solo aplicamos si hay suficientes datos y la columna no es todo NaN
    if len(df) > VENTANA_SUAVIZADO:
        for col in df.columns:
            if df[col].notna().sum() > VENTANA_SUAVIZADO:
                try:
                    df[col] = savgol_filter(df[col], window_length=VENTANA_SUAVIZADO, polyorder=2)
                except ValueError:
                    pass
    # 1. Velocidad Vertical (Clave para RITMO)
    # 3. Derivadas (Velocidades y Aceleración) - USANDO NOMBRES CORRECTOS
    df['vel_y_hip'] = df['sust_dy'].diff().fillna(0) / dt
    df['acc_y_hip'] = df['vel_y_hip'].diff().fillna(0) / dt
    # Aquí cambiamos 'knee_angle_l' por 'L_knee' para evitar el KeyError
    df['vel_knee'] = df['L_knee'].diff().fillna(0) / dt
    return df