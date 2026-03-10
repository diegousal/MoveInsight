import tensorflow as tf
from tensorflow.keras.models import Model
from tensorflow.keras.layers import (
    Input, LSTM, Bidirectional, Dense, 
    Dropout, TimeDistributed, Masking
)

def crear_bilstm_analizador(max_frames=None, n_features=15):
    input_layer = Input(shape=(max_frames, n_features), name="input_secuencia")
    x = Masking(mask_value=0.0)(input_layer)

    # TRONCO BILSTM (El motor que entiende el movimiento)
    x = Bidirectional(LSTM(128, return_sequences=True))(x)
    x = Dropout(0.3)(x)
    x = Bidirectional(LSTM(64, return_sequences=True))(x)

    # --- SALIDAS DE CALIDAD (Todas de 0 a 1) ---
    # 1 significa "Ejecución Perfecta", 0 significa "Error Técnico"

    # Fase: Sigue siendo clasificación (0,1,2,3) para segmentar
    output_fase = TimeDistributed(Dense(4, activation='softmax'), name="fase_frame")(x)

    # KPIs de Calidad (Sigmoide: 0 a 1)
    output_depth = TimeDistributed(Dense(1, activation='sigmoid'), name="kpi_profundidad")(x)
    output_torso = TimeDistributed(Dense(1, activation='sigmoid'), name="kpi_torso")(x)
    output_stability = TimeDistributed(Dense(1, activation='sigmoid'), name="kpi_estabilidad")(x)
    output_knees = TimeDistributed(Dense(1, activation='sigmoid'), name="kpi_rodillas")(x)
    output_ritmo = TimeDistributed(Dense(1, activation='sigmoid'), name="kpi_ritmo")(x)

    model = Model(
        inputs=input_layer, 
        outputs=[output_fase, output_depth, output_torso, output_stability, output_knees, output_ritmo]
    )

    # Compilamos con binary_crossentropy para todos los KPIs de 0 a 1
    model.compile(
        optimizer='adam',
        loss={
            "fase_frame": "sparse_categorical_crossentropy",
            "kpi_profundidad": "binary_crossentropy",
            "kpi_torso": "binary_crossentropy",
            "kpi_estabilidad": "binary_crossentropy",
            "kpi_rodillas": "binary_crossentropy",
            "kpi_ritmo": "binary_crossentropy"
        }
    )
    return model