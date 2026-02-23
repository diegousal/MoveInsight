import numpy as np
import pandas as pd

def calculate_angle_3d(a, b, c):
    """
    Calcula el ángulo real en el espacio 3D usando vectores (x, y, z).
    """
    a, b, c = np.array(a[:3]), np.array(b[:3]), np.array(c[:3])
    ba = a - b
    bc = c - b
    
    # Cálculo del coseno y arco-coseno
    denominator = np.linalg.norm(ba) * np.linalg.norm(bc)
    if denominator < 1e-6: # Evitar división por cero
        return 0.0
        
    cosine_angle = np.dot(ba, bc) / denominator
    angle = np.arccos(np.clip(cosine_angle, -1.0, 1.0))
    
    return np.degrees(angle)

def calculate_torso(shoulder, hip):
    """
    Mide la inclinación del torso respecto a la vertical (gravedad).
    """
    v_torso = np.array([shoulder[0] - hip[0], shoulder[1] - hip[1]])
    v_vertical = np.array([0, -1]) 
    
    norm_product = np.linalg.norm(v_torso) * np.linalg.norm(v_vertical)
    if norm_product < 1e-6:
        return 0.0
        
    return np.degrees(np.arccos(np.clip(np.dot(v_torso, v_vertical) / norm_product, -1.0, 1.0)))

def calcular_par_corregido(kp, indices_L, indices_R, umbral=0.6):
    """
    Función generalizada para calcular ángulos de un par de articulaciones (Izq/Der)
    aplicando corrección por visibilidad (Si una no se ve, copia la otra).
    
    Args:
        kp: Array de keypoints
        indices_L: Tupla de 3 índices para el lado IZQUIERDO (A, B, C)
        indices_R: Tupla de 3 índices para el lado DERECHO (A, B, C)
        umbral: Confianza mínima para considerar el ángulo válido
    """
    # 1. Extraer visibilidad del punto central (Vértice de la articulación)
    vis_L = kp[indices_L[1]][3] 
    vis_R = kp[indices_R[1]][3]
    
    # 2. Calcular ángulos 3D puros
    ang_L = calculate_angle_3d(kp[indices_L[0]], kp[indices_L[1]], kp[indices_L[2]])
    ang_R = calculate_angle_3d(kp[indices_R[0]], kp[indices_R[1]], kp[indices_R[2]])
    
    # 3. Lógica de Corrección (Espejo)
    if vis_L < umbral and vis_R > umbral:
        # Si la Izquierda no se ve -> Copiamos la Derecha
        return ang_R, ang_R
    elif vis_R < umbral and vis_L > umbral:
        # Si la Derecha no se ve -> Copiamos la Izquierda
        return ang_L, ang_L
    elif vis_L < umbral and vis_R < umbral:
        # Si ninguna se ve -> Devolvemos NaN o un valor neutro
        return None, None
    # Si la IA no ve ni el brazo izquierdo ni el derecho, cualquier dato que te dé será "alucinación" o ruido. Lo más honesto científicamente es marcar ese frame como NaN (Not a Number) o None para no ensuciar tus gráficas. Al llegar al main None, en el csv automaticamente rellenara esos valores con el último valor válido. Es la solución estándar en ciencia de datos para series temporales con pérdida de señal.
    
    # Si ambas se ven, devolvemos los valores originales
    return ang_L, ang_R
def calcular_centro_sustentacion(kp):
    """
    Calcula el centroide (X, Y, Z) de la base de sustentación 
    usando los talones y las punteras de los pies.
    """
    # Índices de MediaPipe para los extremos de los pies
    L_HEEL, R_HEEL = 29, 30
    L_FOOT_INDEX, R_FOOT_INDEX = 31, 32
    
    foot_indices = [L_HEEL, R_HEEL, L_FOOT_INDEX, R_FOOT_INDEX]
    
    puntos_apoyo = []
    
    # Recorremos los puntos y guardamos los que tienen buena visibilidad (> 0.5)
    for idx in foot_indices:
        if kp[idx][3] > 0.5:  # kp[idx][3] es la visibilidad
            puntos_apoyo.append(kp[idx][:3])  # Guardamos (X, Y, Z)
            
    # Si la cámara no ve las punteras/talones, intentamos usar los tobillos (27 y 28)
    if len(puntos_apoyo) == 0:
        for idx in [27, 28]:
            if kp[idx][3] > 0.5:
                puntos_apoyo.append(kp[idx][:3])
                
    # Si definitivamente no se ve ningún pie en la imagen
    if len(puntos_apoyo) == 0:
        return np.nan, np.nan, np.nan # Devolvemos NaN para no romper el DataFrame
        
    # El centro de sustentación es la media de todos los puntos de apoyo válidos
    centro_x, centro_y, centro_z = np.mean(puntos_apoyo, axis=0)
    
    return centro_x, centro_y, centro_z


def extract_angles(kp):
    angles = {}
    
    # --- DICCIONARIO DE ÍNDICES BLAZEPOSE ---
    # Brazos
    L_SHOULDER, R_SHOULDER = 11, 12
    L_ELBOW, R_ELBOW = 13, 14
    L_WRIST, R_WRIST = 15, 16
    # Tronco y Piernas
    L_HIP, R_HIP = 23, 24
    L_KNEE, R_KNEE = 25, 26
    L_ANKLE, R_ANKLE = 27, 28
    L_FOOT_INDEX, R_FOOT_INDEX = 31, 32 # Puntera del pie

    # --- CÁLCULO DE ARTICULACIONES CON CORRECCIÓN ---
    
    # 1. CODOS (Hombro - Codo - Muñeca)
    angles['L_elbow'], angles['R_elbow'] = calcular_par_corregido(
        kp, (L_SHOULDER, L_ELBOW, L_WRIST), (R_SHOULDER, R_ELBOW, R_WRIST)
    )

    # 2. HOMBROS (Codo - Hombro - Cadera) -> Flexión relativa al tronco
    angles['L_shoulder'], angles['R_shoulder'] = calcular_par_corregido(
        kp, (L_ELBOW, L_SHOULDER, L_HIP), (R_ELBOW, R_SHOULDER, R_HIP)
    )

    # 3. RODILLAS (Cadera - Rodilla - Tobillo)
    angles['L_knee'], angles['R_knee'] = calcular_par_corregido(
        kp, (L_HIP, L_KNEE, L_ANKLE), (R_HIP, R_KNEE, R_ANKLE)
    )

    # 4. CADERAS (Hombro - Cadera - Rodilla) -> Flexión de tronco sobre pierna
    angles['L_hip'], angles['R_hip'] = calcular_par_corregido(
        kp, (L_SHOULDER, L_HIP, L_KNEE), (R_SHOULDER, R_HIP, R_KNEE)
    )

    # 5. TOBILLOS (Rodilla - Tobillo - Puntera) -> Dorsiflexión
    angles['L_ankle'], angles['R_ankle'] = calcular_par_corregido(
        kp, (L_KNEE, L_ANKLE, L_FOOT_INDEX), (R_KNEE, R_ANKLE, R_FOOT_INDEX)
    )

    # --- CÁLCULO DE TORSO (Sin par, es único) ---
    mid_shoulder = (kp[L_SHOULDER] + kp[R_SHOULDER]) / 2
    mid_hip = (kp[L_HIP] + kp[R_HIP]) / 2
    angles['torso'] = calculate_torso(mid_shoulder, mid_hip)
    
    # # --- CÁLCULO DEL CENTRO DE SUSTENTACIÓN ---
    # cx, cy, cz = calcular_centro_sustentacion(kp)
    
    # # Guardamos las coordenadas en el diccionario
    # angles['sustentacion_x'] = cx
    # angles['sustentacion_y'] = cy # Y es la altura (suele estar cerca del suelo)
    # angles['sustentacion_z'] = cz # Z es la profundidad

    return angles

def process_historial(historial_landmarks):
    features_list = []
    for keypoints_flat in historial_landmarks:
        # Convertimos vector plano a matriz (33, 4)
        kp = keypoints_flat.reshape(-1, 4)
        
        angles = extract_angles(kp)
        features_list.append(angles)

    return pd.DataFrame(features_list)