import tensorflow as tf
from tensorflow.keras.models import Model
from tensorflow.keras.layers import (
    Input, LSTM, Bidirectional, Dense,
    Dropout, TimeDistributed, Masking
)

def crear_bilstm_analizador(max_frames=None, n_features=15):
    """
    Red BiLSTM para análisis de sentadillas frame a frame.

    Entradas:
        max_frames  : longitud de la secuencia (None = dinámica, int = fija con padding)
        n_features  : número de características por frame (landmarks, ángulos, etc.)

    Salidas por frame:
        fase_frame       : clase 0-3 (idle / bajando / fondo / subiendo)
        kpi_profundidad  : 0.0 (error) → 1.0 (perfecto)
        kpi_torso        : 0.0 → 1.0
        kpi_estabilidad  : 0.0 → 1.0
        kpi_rodillas     : 0.0 → 1.0
        kpi_ritmo        : 0.0 → 1.0
    """

    # --- Entrada ---
    input_layer = Input(shape=(max_frames, n_features), name="input_secuencia")

    # Masking: los frames de padding (valor 0.0) no influyen en la pérdida
    x = Masking(mask_value=0.0)(input_layer)

    # --- Tronco BiLSTM ---
    # Primera capa: captura contexto a largo plazo
    x = Bidirectional(LSTM(128, return_sequences=True), name="bilstm_1")(x)
    x = Dropout(0.3)(x)

    # Segunda capa: refina las representaciones frame a frame
    # return_sequences=True es OBLIGATORIO para salidas TimeDistributed
    x = Bidirectional(LSTM(64, return_sequences=True), name="bilstm_2")(x)
    x = Dropout(0.2)(x)

    # --- Cabezas de salida ---

    # Fase: clasificación multiclase (0=idle, 1=bajando, 2=fondo, 3=subiendo)
    output_fase = TimeDistributed(
        Dense(4, activation='softmax'), name="fase_frame"
    )(x)

    # KPIs de calidad técnica (regresión 0→1 por frame)
    output_depth    = TimeDistributed(Dense(1, activation='sigmoid'), name="kpi_profundidad")(x)
    output_torso    = TimeDistributed(Dense(1, activation='sigmoid'), name="kpi_torso")(x)
    output_stability = TimeDistributed(Dense(1, activation='sigmoid'), name="kpi_estabilidad")(x)
    output_knees    = TimeDistributed(Dense(1, activation='sigmoid'), name="kpi_rodillas")(x)
    output_ritmo    = TimeDistributed(Dense(1, activation='sigmoid'), name="kpi_ritmo")(x)

    model = Model(
        inputs=input_layer,
        outputs=[output_fase, output_depth, output_torso, output_stability, output_knees, output_ritmo]
    )

    # --- Compilación ---
    # loss_weights: la fase es más importante que cada KPI individual
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss={
            "fase_frame":      "sparse_categorical_crossentropy",
            "kpi_profundidad": "binary_crossentropy",
            "kpi_torso":       "binary_crossentropy",
            "kpi_estabilidad": "binary_crossentropy",
            "kpi_rodillas":    "binary_crossentropy",
            "kpi_ritmo":       "binary_crossentropy",
        },
        loss_weights={
            "fase_frame":      2.0,   # La fase tiene el doble de peso
            "kpi_profundidad": 1.0,
            "kpi_torso":       1.0,
            "kpi_estabilidad": 1.0,
            "kpi_rodillas":    1.0,
            "kpi_ritmo":       1.0,
        },
        metrics={
            "fase_frame":      "accuracy",
            "kpi_profundidad": tf.keras.metrics.MeanAbsoluteError(name="mae"),
            "kpi_torso":       tf.keras.metrics.MeanAbsoluteError(name="mae"),
            "kpi_estabilidad": tf.keras.metrics.MeanAbsoluteError(name="mae"),
            "kpi_rodillas":    tf.keras.metrics.MeanAbsoluteError(name="mae"),
            "kpi_ritmo":       tf.keras.metrics.MeanAbsoluteError(name="mae"),
        }
    )

    return model