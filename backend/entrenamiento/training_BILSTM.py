import numpy as np
import os
import ast
import re
import tensorflow as tf
from tensorflow.keras.preprocessing.sequence import pad_sequences
from red_BILSTM import crear_bilstm_analizador

# =============================================================================
# --- 1. CARGADOR DE ANOTACIONES DESDE FICHERO .TXT ---
# =============================================================================

def cargar_anotaciones(ruta_txt: str) -> dict:
    """
    Parsea el fichero de anotaciones .txt y devuelve un diccionario Python.

    Formato esperado en el .txt (uno o más bloques):
        "104.npy": {
          "reps": [
            { "frames": (0, 199, 263, 274, 335),
              "kpis": {"depth": 0, "torso": 0, ...} },
            ...
          ]
        }

    El fichero NO necesita llaves externas {}; esta función las añade.
    """
    with open(ruta_txt, "r", encoding="utf-8") as f:
        contenido = f.read()

    # 1. Eliminamos comas finales antes de } o ] (trailing commas no son válidas en literal_eval)
    contenido = re.sub(r",\s*([}\]])", r"\1", contenido)

    # 2. Añadimos coma entre entradas del diccionario raíz donde falta.
    #    El patrón es: } seguido de espacios/saltos y luego una nueva clave "xxx.npy":
    #    Ejemplo:  }\n\n"105.npy":  →  },\n\n"105.npy":
    contenido = re.sub(r"}\s*\n(\s*\"[^\"]+\.npy\")", r"},\n\1", contenido)

    # Envolvemos el contenido en un dict literal válido
    contenido_dict = "{" + contenido + "}"

    try:
        datos = ast.literal_eval(contenido_dict)
    except Exception as e:
        raise ValueError(
            f"Error al parsear el fichero de anotaciones: {e}\n"
            "Comprueba que el formato del .txt es correcto."
        )

    print(f"✅ Anotaciones cargadas: {len(datos)} vídeos encontrados.")
    return datos


# =============================================================================
# --- 2. GENERADOR DE ETIQUETAS ---
# Las tuplas de frames tienen 5 valores:
#   (rep_ini, desc_ini, fondo_ini, fondo_fin, rep_fin)
#
# Mapeo a 4 fases del modelo (sparse_categorical, clases 0-3):
#   0 → Entre repeticiones (idle)
#   1 → Bajando   : rep_ini  → fondo_ini
#   2 → Fondo     : fondo_ini → fondo_fin
#   3 → Subiendo  : fondo_fin → rep_fin
#
# Nota: desc_ini (2º valor) se conserva en los datos pero se integra en
# la fase "Bajando". Puedes usarlo en el futuro para dividirla en 2 fases
# si amplías el modelo a 5 clases.
# =============================================================================

def generar_labels_calidad(num_frames: int, reps_info: list):
    """
    Genera los arrays de etiquetas frame a frame para un vídeo.

    Args:
        num_frames : número real de frames del .npy
        reps_info  : lista de dicts con claves 'frames' (5-tupla) y 'kpis'

    Returns:
        fases, kpi_d, kpi_t, kpi_s, kpi_k, kpi_r  — arrays shape (num_frames,)
    """
    fases = np.zeros(num_frames, dtype=np.int32)   # clase 0 = idle por defecto
    kpi_d = np.zeros(num_frames, dtype=np.float32)
    kpi_t = np.zeros(num_frames, dtype=np.float32)
    kpi_s = np.zeros(num_frames, dtype=np.float32)
    kpi_k = np.zeros(num_frames, dtype=np.float32)
    kpi_r = np.zeros(num_frames, dtype=np.float32)

    for rep in reps_info:
        frames = rep["frames"]

        # --- Validación de la tupla ---
        if len(frames) != 5:
            raise ValueError(
                f"Cada 'frames' debe tener 5 valores "
                f"(rep_ini, desc_ini, fondo_ini, fondo_fin, rep_fin), "
                f"pero se encontró: {frames}"
            )

        rep_ini, _desc_ini, fondo_ini, fondo_fin, rep_fin = frames

        # Clampear índices para no salir del array
        rep_ini   = max(0, min(rep_ini,   num_frames))
        fondo_ini = max(0, min(fondo_ini, num_frames))
        fondo_fin = max(0, min(fondo_fin, num_frames))
        rep_fin   = max(0, min(rep_fin,   num_frames))

        k = rep["kpis"]

        # --- Fases ---
        fases[rep_ini:fondo_ini]  = 1   # Bajando
        fases[fondo_ini:fondo_fin] = 2  # Fondo
        fases[fondo_fin:rep_fin]   = 3  # Subiendo

        # --- KPIs aplicados a toda la duración de la repetición ---
        kpi_d[rep_ini:rep_fin] = k["depth"]
        kpi_t[rep_ini:rep_fin] = k["torso"]
        kpi_s[rep_ini:rep_fin] = k["stability"]
        kpi_k[rep_ini:rep_fin] = k["knees"]
        kpi_r[rep_ini:rep_fin] = k["ritmo"]

    return fases, kpi_d, kpi_t, kpi_s, kpi_k, kpi_r


# =============================================================================
# --- 3. PREPARACIÓN DEL DATASET ---
# =============================================================================

def preparar_dataset(etiquetas_videos: dict, carpeta_npy: str, max_frames: int):
    x_train = []
    y_fases  = []
    y_kpis   = {"depth": [], "torso": [], "stability": [], "knees": [], "ritmo": []}

    for nombre, info in etiquetas_videos.items():
        ruta = os.path.join(carpeta_npy, nombre)
        if not os.path.exists(ruta):
            print(f"  ⚠️  Archivo '{nombre}' no encontrado en '{carpeta_npy}'. Saltando...")
            continue

        datos = np.load(ruta)           # shape: (num_frames, n_features)
        num_f = datos.shape[0]

        # Advertencia si el vídeo supera MAX_FRAMES
        if num_f > max_frames:
            print(f"  ⚠️  '{nombre}' tiene {num_f} frames > MAX_FRAMES={max_frames}. "
                  f"Se truncará al hacer padding.")

        x_train.append(datos)

        f, d, t, s, k, r = generar_labels_calidad(num_f, info["reps"])

        y_fases.append(f)
        y_kpis["depth"].append(d)
        y_kpis["torso"].append(t)
        y_kpis["stability"].append(s)
        y_kpis["knees"].append(k)
        y_kpis["ritmo"].append(r)

    if not x_train:
        raise RuntimeError("No se cargó ningún archivo .npy. Revisa la carpeta y las anotaciones.")

    # --- Padding ---
    # X: float32, relleno con 0.0 (coincide con mask_value de la red)
    X = pad_sequences(x_train, maxlen=max_frames, dtype='float32',
                      padding='post', truncating='post', value=0.0)

    # Y fases: enteros, relleno con 0 (clase idle, no penaliza erróneamente)
    Y_fases = pad_sequences(y_fases, maxlen=max_frames,
                            padding='post', truncating='post', value=0)

    def pad_kpi(lista):
        arr = pad_sequences(lista, maxlen=max_frames, dtype='float32',
                            padding='post', truncating='post', value=0.0)
        return arr[:, :, np.newaxis]   # (N, max_frames, 1)

    Y_outputs = {
        "fase_frame":    Y_fases,                       # (N, max_frames)
        "kpi_profundidad": pad_kpi(y_kpis["depth"]),   # (N, max_frames, 1)
        "kpi_torso":       pad_kpi(y_kpis["torso"]),
        "kpi_estabilidad": pad_kpi(y_kpis["stability"]),
        "kpi_rodillas":    pad_kpi(y_kpis["knees"]),
        "kpi_ritmo":       pad_kpi(y_kpis["ritmo"]),
    }

    print(f"\n📊 Dataset preparado:")
    print(f"   X shape       : {X.shape}")
    print(f"   Y fases shape : {Y_outputs['fase_frame'].shape}")
    print(f"   Y kpi shape   : {Y_outputs['kpi_profundidad'].shape}")

    return X, Y_outputs


# =============================================================================
# --- 4. CONFIGURACIÓN ---
# =============================================================================

# MAX_FRAMES se calcula automáticamente desde las anotaciones para no perder frames,
# pero puedes fijar un valor fijo si prefieres.
RUTA_ANOTACIONES = "annotations_tuple_format.txt"  # <-- cambia si está en otra ruta
CARPETA_NPY = r"C:\Users\Usuario\Desktop\TFG\backend\entrenamiento\archivos_entrenamiento"
N_FEATURES       = 15
MODEL_PATH = r"C:\Users\Usuario\Desktop\TFG\backend\modelos\juez_sentadillas_nuevo.h5"

# --- Calcular MAX_FRAMES dinámicamente ---
# Tomamos el frame máximo de todas las anotaciones + margen de seguridad
def calcular_max_frames(anotaciones: dict, margen: int = 50) -> int:
    max_f = 0
    for info in anotaciones.values():
        for rep in info["reps"]:
            max_f = max(max_f, rep["frames"][-1])
    return max_f + margen


# =============================================================================
# --- 5. FLUJO PRINCIPAL ---
# =============================================================================

print("=" * 60)
print("  ENTRENAMIENTO BILSTM — JUEZ DE SENTADILLAS")
print("=" * 60)

print(f"\n📂 Cargando anotaciones desde: {RUTA_ANOTACIONES}")
etiquetas_videos = cargar_anotaciones(RUTA_ANOTACIONES)

MAX_FRAMES = calcular_max_frames(etiquetas_videos)
print(f"📏 MAX_FRAMES calculado automáticamente: {MAX_FRAMES}")

print("\n🔧 Preparando dataset...")
X_train, Y_dict = preparar_dataset(etiquetas_videos, CARPETA_NPY, MAX_FRAMES)

print("\n🧠 Construyendo modelo...")
model = crear_bilstm_analizador(max_frames=MAX_FRAMES, n_features=N_FEATURES)
model.summary()

os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)

print(f"\n🚀 Entrenando con {len(X_train)} ejemplos...")
history = model.fit(
    X_train,
    Y_dict,
    epochs=100,
    batch_size=4,
    validation_split=0.15,
    verbose=1,
    callbacks=[
        # Para early stopping si quieres evitar overfitting
        tf.keras.callbacks.EarlyStopping(
            monitor='val_loss', patience=15, restore_best_weights=True, verbose=1
        ),
        tf.keras.callbacks.ModelCheckpoint(
            MODEL_PATH, monitor='val_loss', save_best_only=True, verbose=1
        ),
    ]
)

print(f"\n✅ Modelo guardado en: {MODEL_PATH}")