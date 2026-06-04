"""
phase_predictor.py  —  Inferencia BiLSTM para detectar repeticiones.

Carga el modelo entrenado, aplica la normalizacion z-score y convierte
la secuencia de fases frame a frame en una lista de repeticiones (start, end).

Una repeticion valida es: DESCENT -> BOTTOM -> ASCENT (rodeado de STAND).
Se ignoran fases demasiado cortas para filtrar ruido.

Uso como modulo:
    from processing.phase_predictor import PhasePredictor
    pp = PhasePredictor(model_path=..., stats_path=...)
    reps = pp.predict_reps(df, fps)   # df = DataFrame de process_historial
"""

import numpy as np
import pandas as pd
from pathlib import Path

# ── Configuracion ─────────────────────────────────────────────────────────────
MODEL_PATH   = Path("bilstm_phases.keras")
STATS_PATH   = Path("bilstm_phases.normstats.npz")        # mu/std del training
DATASET_PATH = Path("entrenamiento/phases_dataset.npz")  # fallback de mu/std

# Fases
STAND   = 0
DESCENT = 1
BOTTOM  = 2
ASCENT  = 3

# Columnas de entrada (mismo orden que fases_dataset.py)
PHASE_COLS = ["L_knee", "R_knee", "L_hip", "R_hip",
              "sust_dy", "vel_knee", "vel_y_hip", "acc_y_hip"]

# Filtros de calidad
MIN_BOTTOM_FRAMES  = 3    # minimo de frames en fase BOTTOM para considerar rep valida
MIN_REP_FRAMES     = 20   # duracion minima de una rep completa (frames)
SMOOTHING_WINDOW   = 7    # ventana de mayoria para suavizar etiquetas ruidosas


class PhasePredictor:
    """
    Wrapper de inferencia para el modelo BiLSTM de fases.

    Parametros
    ----------
    model_path   : ruta al .keras entrenado
    dataset_path : ruta al .npz del dataset (para leer mu y std)
    """

    def __init__(self,
                 model_path: Path = MODEL_PATH,
                 stats_path: Path = STATS_PATH,
                 dataset_path: Path = DATASET_PATH):

        self._mu  = None
        self._std = None
        self._model = None
        self._T_max = None

        self._model_path   = Path(model_path)
        self._stats_path   = Path(stats_path)
        self._dataset_path = Path(dataset_path)

        self._load()

    # ── Carga ──────────────────────────────────────────────────────────────────

    def _load(self):
        if not self._model_path.exists():
            raise FileNotFoundError(
                f"Modelo no encontrado: {self._model_path}\n"
                "Entrena primero con:  python training_phases.py"
            )

        import tensorflow as tf
        self._model = tf.keras.models.load_model(str(self._model_path))
        self._T_max = self._model.input_shape[1]   # longitud de padding del entrenamiento

        # Prefiere el sidecar generado por el training (mu/std post-augmentacion)
        # y, si no existe, cae al .npz del dataset.
        if self._stats_path.exists():
            data = np.load(self._stats_path)
            self._mu  = data["mu"].astype(np.float32)
            self._std = data["std"].astype(np.float32)
        elif self._dataset_path.exists():
            data = np.load(self._dataset_path, allow_pickle=True)
            self._mu  = data["mu"].astype(np.float32)
            self._std = data["std"].astype(np.float32)
        else:
            raise FileNotFoundError(
                f"No hay stats de normalizacion. Esperaba {self._stats_path} "
                f"o {self._dataset_path}.\n"
                "Entrena con:  python training_phases.py"
            )

    # ── Inferencia publica ─────────────────────────────────────────────────────

    def predict_phases(self, df: pd.DataFrame) -> np.ndarray:
        """
        Devuelve array de etiquetas de fase (int) con longitud == len(df).
        Valores: 0=stand, 1=descent, 2=bottom, 3=ascent
        """
        seq = self._extract_sequence(df)            # (T, 8)
        seq_norm = (seq - self._mu) / self._std     # z-score

        T = len(seq_norm)
        # Padding al T_max del entrenamiento; si T > T_max se trunca
        if T > self._T_max:
            # Procesamos en chunks solapados para no perder datos
            return self._predict_long(seq_norm)

        X = np.zeros((1, self._T_max, 8), dtype=np.float32)
        X[0, :T, :] = seq_norm

        probs = self._model.predict(X, verbose=0)   # (1, T_max, 4)
        phases = np.argmax(probs[0, :T, :], axis=-1).astype(np.int8)

        return _smooth_phases(phases, window=SMOOTHING_WINDOW)

    def predict_reps(self, df: pd.DataFrame, fps: float) -> list:
        """
        Devuelve lista de (start_frame, end_frame) para cada rep valida detectada.
        """
        phases = self.predict_phases(df)
        return _phases_to_reps(phases,
                               min_bottom=MIN_BOTTOM_FRAMES,
                               min_rep=MIN_REP_FRAMES)

    # ── Helpers internos ───────────────────────────────────────────────────────

    def _extract_sequence(self, df: pd.DataFrame) -> np.ndarray:
        """Extrae y rellena las 8 columnas necesarias del DataFrame."""
        T = len(df)
        seq = np.zeros((T, 8), dtype=np.float32)
        for j, col in enumerate(PHASE_COLS):
            if col in df.columns:
                seq[:, j] = df[col].fillna(0.0).values.astype(np.float32)
        return seq

    def _predict_long(self, seq_norm: np.ndarray) -> np.ndarray:
        """
        Para videos mas largos que T_max del entrenamiento:
        divide en ventanas de T_max con solapamiento del 50% y
        promedia las probabilidades en la zona de solape.
        """
        T = len(seq_norm)
        probs = np.zeros((T, 4), dtype=np.float32)
        counts = np.zeros(T, dtype=np.float32)
        step = self._T_max // 2

        start = 0
        while start < T:
            end = min(start + self._T_max, T)
            chunk = seq_norm[start:end]
            L = len(chunk)

            X = np.zeros((1, self._T_max, 8), dtype=np.float32)
            X[0, :L, :] = chunk

            p = self._model.predict(X, verbose=0)[0, :L, :]
            probs[start:end]  += p
            counts[start:end] += 1

            if end == T:
                break
            start += step

        counts[counts == 0] = 1
        avg_probs = probs / counts[:, None]
        phases = np.argmax(avg_probs, axis=-1).astype(np.int8)
        return _smooth_phases(phases, window=SMOOTHING_WINDOW)


# ── Conversion fases -> reps ───────────────────────────────────────────────────

def _smooth_phases(phases: np.ndarray, window: int = 7) -> np.ndarray:
    """
    Suaviza la secuencia de fases por mayoria en una ventana deslizante.
    Elimina cambios de fase de duracion < window//2.
    """
    # Voto por mayoria manual (medfilt no aplica bien sobre etiquetas categoricas)
    half = window // 2
    out = phases.copy()
    for i in range(half, len(phases) - half):
        window_slice = phases[i - half: i + half + 1]
        counts = np.bincount(window_slice.astype(np.int32), minlength=4)
        out[i] = int(np.argmax(counts))
    return out


def _phases_to_reps(phases: np.ndarray,
                    min_bottom: int = 3,
                    min_rep: int = 20) -> list:
    """
    Maquina de estados: extrae reps validas de la secuencia de fases.

    Una rep valida debe contener, en orden:
        STAND (o inicio de video) → DESCENT → BOTTOM → ASCENT → STAND (o fin)

    Devuelve lista de (start, end) en indices de frame.
    """
    reps = []
    state   = "wait_descent"   # esperando que empiece el descenso
    rep_start = 0
    has_bottom = False

    i = 0
    while i < len(phases):
        p = int(phases[i])

        if state == "wait_descent":
            if p == DESCENT:
                rep_start = i
                has_bottom = False
                state = "in_descent"

        elif state == "in_descent":
            if p == BOTTOM:
                has_bottom = True
                state = "in_bottom"
            elif p == STAND:
                # Volvio a stand sin llegar al fondo -> rep abortada
                state = "wait_descent"
            # ASCENT directo (sin BOTTOM detectado) -> ignorar

        elif state == "in_bottom":
            if p == ASCENT:
                state = "in_ascent"
            elif p == DESCENT:
                pass  # sigue en fondo (etiqueta ruidosa)
            elif p == STAND:
                # Nunca llego a subir -> rep invalida
                state = "wait_descent"

        elif state == "in_ascent":
            if p == STAND or i == len(phases) - 1:
                rep_end = i
                rep_len = rep_end - rep_start
                if has_bottom and rep_len >= min_rep:
                    reps.append((rep_start, rep_end))
                state = "wait_descent"
            elif p == DESCENT:
                # Empezo a bajar de nuevo: cierra la rep actual
                rep_end = i - 1
                rep_len = rep_end - rep_start
                if has_bottom and rep_len >= min_rep:
                    reps.append((rep_start, rep_end))
                # Empieza nueva rep desde aqui
                rep_start = i
                has_bottom = False
                state = "in_descent"

        i += 1

    return reps
