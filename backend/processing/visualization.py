import cv2
import numpy as np

# Conexiones estándar del modelo de pose de MediaPipe (33 landmarks)
POSE_CONNECTIONS = [
    (0, 1), (1, 2), (2, 3), (3, 7),
    (0, 4), (4, 5), (5, 6), (6, 8),
    (9, 10),
    (11, 12), (11, 13), (13, 15), (15, 17), (15, 19), (15, 21), (17, 19),
    (12, 14), (14, 16), (16, 18), (16, 20), (16, 22), (18, 20),
    (11, 23), (12, 24), (23, 24),
    (23, 25), (25, 27), (27, 29), (29, 31), (27, 31),
    (24, 26), (26, 28), (28, 30), (30, 32), (28, 32),
]

# Colores por zona corporal (BGR)
_COLORES_CONEXION = {
    "cara":    (180, 180, 180),  # gris claro
    "torso":   (0, 200, 255),    # amarillo-naranja
    "brazo_i": (255, 100, 0),    # azul
    "brazo_d": (0, 100, 255),    # rojo
    "pierna_i":(100, 255, 100),  # verde claro
    "pierna_d":(50, 180, 50),    # verde oscuro
}

_CONEXION_ZONA = {
    (0,1):"cara",  (1,2):"cara",  (2,3):"cara",  (3,7):"cara",
    (0,4):"cara",  (4,5):"cara",  (5,6):"cara",  (6,8):"cara",
    (9,10):"cara",
    (11,12):"torso", (11,23):"torso", (12,24):"torso", (23,24):"torso",
    (11,13):"brazo_i", (13,15):"brazo_i", (15,17):"brazo_i",
    (15,19):"brazo_i", (15,21):"brazo_i", (17,19):"brazo_i",
    (12,14):"brazo_d", (14,16):"brazo_d", (16,18):"brazo_d",
    (16,20):"brazo_d", (16,22):"brazo_d", (18,20):"brazo_d",
    (23,25):"pierna_i", (25,27):"pierna_i", (27,29):"pierna_i",
    (29,31):"pierna_i", (27,31):"pierna_i",
    (24,26):"pierna_d", (26,28):"pierna_d", (28,30):"pierna_d",
    (30,32):"pierna_d", (28,32):"pierna_d",
}

def _draw_skeleton_from_coords(frame, landmarks):
    """
    Dibuja el esqueleto a partir de coordenadas normalizadas (0-1) ya calculadas.

    Args:
        frame:     Frame BGR original.
        landmarks: Lista de (x_norm, y_norm) con exactamente 33 puntos.
    Returns:
        Copia del frame con el esqueleto pintado.
    """
    out = frame.copy()
    h, w = out.shape[:2]

    puntos = [(int(x * w), int(y * h)) for (x, y) in landmarks]

    grosor_linea = max(2, int(w / 320))
    radio_punto  = max(3, int(w / 220))

    for (a, b) in POSE_CONNECTIONS:
        if a < len(puntos) and b < len(puntos):
            zona  = _CONEXION_ZONA.get((a, b), "torso")
            color = _COLORES_CONEXION[zona]
            cv2.line(out, puntos[a], puntos[b], color, grosor_linea, cv2.LINE_AA)

    for (px, py) in puntos:
        cv2.circle(out, (px, py), radio_punto + 1, (0, 0, 0),       -1, cv2.LINE_AA)
        cv2.circle(out, (px, py), radio_punto,     (255, 255, 255),  -1, cv2.LINE_AA)

    return out


def generate_skeleton_video(video_path: str, output_path: str, frame_landmarks: list) -> bool:
    """
    Dibuja el esqueleto sobre cada frame del vídeo REUTILIZANDO los landmarks
    ya calculados durante el análisis (no se vuelve a ejecutar MediaPipe).

    Antes este vídeo se generaba con una segunda pasada completa de pose, lo que
    duplicaba el coste de cómputo. Ahora recibe los landmarks precalculados.

    Como disponemos de TODA la secuencia de antemano, el suavizado se hace con
    una media móvil CENTRADA (filtro de fase cero): elimina el parpadeo SIN el
    retardo visible ("lag") que introducía el antiguo EMA causal, que solo podía
    mirar al pasado y por eso arrastraba los puntos por detrás del movimiento.

    Args:
        video_path:      Ruta al vídeo original.
        output_path:     Ruta donde guardar el vídeo con esqueleto (mp4).
        frame_landmarks: Lista paralela a los frames del vídeo. Cada elemento es
                         un vector plano (33*4) [x, y, z, visibility] o None si
                         no hubo detección de pose en ese frame.
    Returns:
        True si se generó correctamente, False en caso de error.
    """
    import os

    T = len(frame_landmarks)
    if T == 0:
        return False

    SMOOTH_WINDOW = 5   # impar → media móvil centrada (fase cero, sin lag)

    # ── 1) Coordenadas normalizadas (T, 33, 2); NaN en frames sin pose ────────
    coords = np.full((T, 33, 2), np.nan, dtype=np.float32)
    for t, vec in enumerate(frame_landmarks):
        if vec is None:
            continue
        lm = np.asarray(vec, dtype=np.float32).reshape(-1, 4)
        if lm.shape[0] < 33:
            continue
        coords[t, :, 0] = lm[:33, 0]
        coords[t, :, 1] = lm[:33, 1]

    # ── 2) Interpolación temporal por landmark (rellena los frames sin pose) ──
    idx = np.arange(T)
    for j in range(33):
        for c in range(2):
            s = coords[:, j, c]
            m = np.isnan(s)
            if m.all():
                s[:] = 0.0                       # landmark nunca detectado
            elif m.any():
                s[m] = np.interp(idx[m], idx[~m], s[~m])
            coords[:, j, c] = s

    # ── 3) Suavizado de fase cero (media móvil centrada) ──────────────────────
    if T >= SMOOTH_WINDOW:
        pad    = SMOOTH_WINDOW // 2
        kernel = np.ones(SMOOTH_WINDOW, dtype=np.float32) / SMOOTH_WINDOW
        for j in range(33):
            for c in range(2):
                padded = np.pad(coords[:, j, c], pad, mode="edge")
                coords[:, j, c] = np.convolve(padded, kernel, mode="valid")

    # ── 4) Dibujar sobre los frames del vídeo original ────────────────────────
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        return False

    fps    = cap.get(cv2.CAP_PROP_FPS) or 30.0
    width  = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    writer = cv2.VideoWriter(output_path, fourcc, fps, (width, height))

    try:
        t = 0
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            if t < T:
                pts = [(float(coords[t, j, 0]), float(coords[t, j, 1])) for j in range(33)]
                annotated = _draw_skeleton_from_coords(frame, pts)
            else:
                annotated = frame   # más frames que landmarks (no debería ocurrir)
            writer.write(annotated)
            t += 1

    except Exception:
        return False
    finally:
        cap.release()
        writer.release()

    return os.path.exists(output_path) and os.path.getsize(output_path) > 0