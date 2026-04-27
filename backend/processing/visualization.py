import cv2

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

# Cache de resolución de pantalla (se detecta solo la primera vez)
_SCREEN_SIZE = None

def _get_screen_size():
    global _SCREEN_SIZE
    if _SCREEN_SIZE is None:
        try:
            import tkinter as tk
            root = tk.Tk()
            root.withdraw()
            _SCREEN_SIZE = (root.winfo_screenwidth(), root.winfo_screenheight())
            root.destroy()
        except Exception:
            _SCREEN_SIZE = (1920, 1080)
    return _SCREEN_SIZE


def mostrar_frame(frame, results):
    """
    Dibuja los landmarks de pose detectados sobre el frame y lo muestra por pantalla.
    Uso: comentar/descomentar la llamada en main.py para activar/desactivar el debug visual.

    Args:
        frame:   Frame BGR original de OpenCV.
        results: Objeto de resultados devuelto por PoseProcessor.process_frame().
    """
    # --- 1. Redimensionar primero al tamaño de display (40% de pantalla) ---
    screen_w, screen_h = _get_screen_size()
    max_w = int(screen_w * 0.60)
    max_h = int(screen_h * 0.60)

    h, w = frame.shape[:2]
    scale = min(max_w / w, max_h / h, 1.0)
    disp_w = int(w * scale)
    disp_h = int(h * scale)
    display = cv2.resize(frame, (disp_w, disp_h), interpolation=cv2.INTER_AREA)

    # --- 2. Dibujar sobre el frame ya reducido ---
    if results.pose_landmarks:
        for pose_landmarks in results.pose_landmarks:
            puntos = []
            for lm in pose_landmarks:
                px = int(lm.x * disp_w)
                py = int(lm.y * disp_h)
                puntos.append((px, py))

            # Grosor y radio proporcionales al tamaño del frame reducido
            grosor_linea = max(1, int(disp_w / 300))
            radio_punto  = max(2, int(disp_w / 200))

            # Conexiones con color por zona
            for (a, b) in POSE_CONNECTIONS:
                if a < len(puntos) and b < len(puntos):
                    zona = _CONEXION_ZONA.get((a, b), "torso")
                    color = _COLORES_CONEXION[zona]
                    cv2.line(display, puntos[a], puntos[b], color, grosor_linea, cv2.LINE_AA)

            # Landmarks: círculo blanco con borde negro para que resalten
            for (px, py) in puntos:
                cv2.circle(display, (px, py), radio_punto + 1, (0, 0, 0),   -1, cv2.LINE_AA)
                cv2.circle(display, (px, py), radio_punto,     (255, 255, 255), -1, cv2.LINE_AA)

    # --- 3. Mostrar centrada y redimensionable ---
    cv2.namedWindow("Debug - Pose", cv2.WINDOW_NORMAL)
    cv2.resizeWindow("Debug - Pose", disp_w, disp_h)
    pos_x = (screen_w - disp_w) // 2
    pos_y = (screen_h - disp_h) // 2
    cv2.moveWindow("Debug - Pose", pos_x, pos_y)
    cv2.imshow("Debug - Pose", display)
    cv2.waitKey(1)


def draw_skeleton_on_frame(frame, results):
    """
    Dibuja el esqueleto de pose sobre el frame BGR (sin mostrar por pantalla).
    Devuelve el frame con el esqueleto pintado, listo para escribir en vídeo.

    Args:
        frame:   Frame BGR original de OpenCV.
        results: Objeto de resultados devuelto por PoseProcessor.process_frame().
    Returns:
        frame modificado (BGR) con esqueleto dibujado.
    """
    out = frame.copy()
    h, w = out.shape[:2]

    if results.pose_landmarks:
        for pose_landmarks in results.pose_landmarks:
            puntos = []
            for lm in pose_landmarks:
                px = int(lm.x * w)
                py = int(lm.y * h)
                puntos.append((px, py))

            grosor_linea = max(2, int(w / 320))
            radio_punto  = max(3, int(w / 220))

            # Conexiones coloreadas por zona
            for (a, b) in POSE_CONNECTIONS:
                if a < len(puntos) and b < len(puntos):
                    zona  = _CONEXION_ZONA.get((a, b), "torso")
                    color = _COLORES_CONEXION[zona]
                    cv2.line(out, puntos[a], puntos[b], color, grosor_linea, cv2.LINE_AA)

            # Landmarks
            for (px, py) in puntos:
                cv2.circle(out, (px, py), radio_punto + 1, (0, 0, 0),       -1, cv2.LINE_AA)
                cv2.circle(out, (px, py), radio_punto,     (255, 255, 255),  -1, cv2.LINE_AA)

    return out


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


def generate_skeleton_video(video_path: str, output_path: str, pose_processor) -> bool:
    """
    Procesa el vídeo original frame a frame, dibuja el esqueleto de MediaPipe
    sobre cada frame y escribe el resultado en *output_path*.

    Aplica dos técnicas para eliminar el parpadeo de landmarks:
      · Persistencia por landmark: si un punto concreto desaparece (visibilidad
        baja), se reutiliza su última posición conocida en lugar de borrarlo.
      · Suavizado EMA: las coordenadas se interpolan entre el frame anterior y
        el actual para evitar saltos bruscos.

    Args:
        video_path:     Ruta al vídeo original.
        output_path:    Ruta donde guardar el vídeo con esqueleto (mp4).
        pose_processor: Instancia de PoseProcessor ya configurada.
    Returns:
        True si se generó correctamente, False en caso de error.
    """
    import os

    # ── Parámetros de suavizado ───────────────────────────────────────────
    # ALPHA: peso del frame actual en el EMA (0=todo pasado, 1=sin suavizado)
    # 0.45 da prioridad ligera al historial → movimiento fluido sin lag excesivo
    ALPHA               = 0.45
    VISIBILITY_THRESHOLD = 0.45   # por debajo → usar última posición conocida

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        return False

    fps    = cap.get(cv2.CAP_PROP_FPS) or 30.0
    width  = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    writer = cv2.VideoWriter(output_path, fourcc, fps, (width, height))

    # Buffer de coordenadas suavizadas: lista de 33 tuplas (x_norm, y_norm) o None
    smoothed = None   # None hasta detectar la primera pose

    try:
        while True:
            ret, frame = cap.read()
            if not ret:
                break

            results, _ = pose_processor.process_frame(frame)

            # ── Extraer landmarks del frame actual ────────────────────────
            raw = None
            if results.pose_landmarks:
                for pose_lms in results.pose_landmarks:
                    raw = [(lm.x, lm.y, lm.visibility) for lm in pose_lms]
                    break  # una sola persona

            if raw is not None:
                if smoothed is None:
                    # Primera detección: inicializar sin suavizado
                    smoothed = [(x, y) for (x, y, _) in raw]
                else:
                    # EMA landmark a landmark, con persistencia por visibilidad
                    new_smoothed = []
                    for i, (rx, ry, vis) in enumerate(raw):
                        sx, sy = smoothed[i]
                        if vis >= VISIBILITY_THRESHOLD:
                            # Landmark visible: suavizar con EMA
                            new_smoothed.append((
                                ALPHA * rx + (1 - ALPHA) * sx,
                                ALPHA * ry + (1 - ALPHA) * sy,
                            ))
                        else:
                            # Landmark no fiable: mantener última posición conocida
                            new_smoothed.append((sx, sy))
                    smoothed = new_smoothed
            # Si raw es None (pose completa no detectada) → smoothed sin cambios (persistencia total)

            # ── Dibujar ───────────────────────────────────────────────────
            if smoothed is not None:
                annotated = _draw_skeleton_from_coords(frame, smoothed)
            else:
                annotated = frame.copy()   # Aún no hay ninguna detección inicial

            writer.write(annotated)

    except Exception:
        return False
    finally:
        cap.release()
        writer.release()

    return os.path.exists(output_path) and os.path.getsize(output_path) > 0