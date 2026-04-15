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