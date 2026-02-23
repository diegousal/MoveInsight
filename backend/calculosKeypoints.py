import numpy as np
import pandas as pd
from scipy.signal import savgol_filter

def calculate_angle_3d(a, b, c):
    """Calcula el ángulo real en el espacio 3D usando vectores (x, y, z)."""
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
    # En MediaPipe, Y crece hacia abajo, por lo que la vertical hacia arriba es (0, -1, 0)
    v_vertical = np.array([0, -1, 0]) 
    
    norm = np.linalg.norm(v_torso) * np.linalg.norm(v_vertical)
    if norm < 1e-6:
        return np.nan
        
    return np.degrees(np.arccos(np.clip(np.dot(v_torso, v_vertical) / norm, -1.0, 1.0)))

def calcular_articulacion_segura(kp, indices, umbral=0.6):
    """
    Devuelve el ángulo solo si la visibilidad del vértice es alta.
    Si no es fiable, devuelve NaN para permitir interpolación posterior.
    """
    vertice_idx = indices[1]
    if kp[vertice_idx][3] < umbral: # kp[idx][3] es la visibilidad/confianza
        return np.nan
    return calculate_angle_3d(kp[indices[0]], kp[indices[1]], kp[indices[2]])

def calcular_centro_sustentacion(kp):
    """Calcula la proyección del centro de gravedad sobre la base de apoyo."""
    # Puntos clave de los pies: Talones, Tobillos y Punteras
    foot_indices = [27, 28, 29, 30, 31, 32]
    puntos_validos = [kp[i][:3] for i in foot_indices if kp[i][3] > 0.5]
    
    if not puntos_validos:
        return np.nan, np.nan, np.nan
        
    return np.mean(puntos_validos, axis=0)

def extract_features(kp):
    """Extrae todos los ángulos y métricas de un único frame."""
    data = {}
    
    # Mapeo de índices MediaPipe BlazePose
    L_SH, R_SH = 11, 12
    L_EL, R_EL = 13, 14
    L_WR, R_WR = 15, 16
    L_HP, R_HP = 23, 24
    L_KN, R_KN = 25, 26
    L_AN, R_AN = 27, 28
    L_FT, R_FT = 31, 32

    # Cálculos independientes (Sin Mirroring para detectar asimetrías reales)
    data['L_elbow'] = calcular_articulacion_segura(kp, (L_SH, L_EL, L_WR))
    data['R_elbow'] = calcular_articulacion_segura(kp, (R_SH, R_EL, R_WR))
    
    data['L_shoulder'] = calcular_articulacion_segura(kp, (L_EL, L_SH, L_HP))
    data['R_shoulder'] = calcular_articulacion_segura(kp, (R_EL, R_SH, R_HP))
    
    data['L_knee'] = calcular_articulacion_segura(kp, (L_HP, L_KN, L_AN))
    data['R_knee'] = calcular_articulacion_segura(kp, (R_HP, R_KN, R_AN))
    
    data['L_hip'] = calcular_articulacion_segura(kp, (L_SH, L_HP, L_KN))
    data['R_hip'] = calcular_articulacion_segura(kp, (R_SH, R_HP, R_KN))
    
    data['L_ankle'] = calcular_articulacion_segura(kp, (L_KN, L_AN, L_FT))
    data['R_ankle'] = calcular_articulacion_segura(kp, (R_KN, R_AN, R_FT))

    # Torso 3D usando puntos medios
    mid_shoulder = (kp[L_SH][:3] + kp[R_SH][:3]) / 2
    mid_hip = (kp[L_HP][:3] + kp[R_HP][:3]) / 2
    data['torso'] = calculate_torso_3d(mid_shoulder, mid_hip)
    
    # Centro de sustentación
    data['sust_x'], data['sust_y'], data['sust_z'] = calcular_centro_sustentacion(kp)
    
    return data

def process_historial(historial_landmarks):
    """
    Procesa toda la serie temporal, aplica limpieza y filtros científicos.
    """
    # 1. Extracción inicial
    raw_data = []
    for frame_kp_flat in historial_landmarks:
        kp = frame_kp_flat.reshape(-1, 4)
        raw_data.append(extract_features(kp))
    
    df = pd.DataFrame(raw_data)
    
    # 2. LIMPIEZA: Interpolación Lineal
    # En lugar de copiar el otro lado (espejo), unimos los puntos válidos.
    # Esto mantiene la asimetría real del cuerpo.
    df = df.interpolate(method='linear', limit_direction='both')
    
    # 3. FILTRADO: Suavizado Savitzky-Golay (Elimina el jitter)
    # window_length debe ser impar. Ajustar según los FPS (p.ej. 11 para 30fps)
    window = 11
    if len(df) > window:
        for col in df.columns:
            try:
                # El filtro suaviza los picos de ruido sin perder la forma del movimiento
                df[col] = savgol_filter(df[col], window_length=window, polyorder=2)
            except:
                pass # Por si la columna tiene demasiados NaNs
                
    return df

# Ejemplo de uso:
# historial_cargado = np.load('tus_keypoints.npy')
# df_final = process_full_workout(historial_cargado)
# df_final.to_csv('analisis_biomecanico_perfecto.csv', index=False)