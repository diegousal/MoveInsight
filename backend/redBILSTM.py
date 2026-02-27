import tensorflow as tf
from tensorflow.keras.models import Model
from tensorflow.keras.layers import (
    Input, LSTM, Bidirectional, Dense, 
    Dropout, TimeDistributed, Masking, GlobalAveragePooling1D
)

def crear_bilstm_analizador(max_frames, n_features=15):
    """
    Crea una red BiLSTM multi-salida para análisis de sentadillas.
    
    Inputs:
        max_frames: Longitud máxima de la secuencia (ej. 1000)
        n_features: 15 (tus ángulos + posiciones + delta_time)
    """
    
    # --- ENTRADA ---
    input_layer = Input(shape=(max_frames, n_features), name="input_secuencia")

    # --- PRE-PROCESAMIENTO ---
    # El valor 0.0 se ignora (útil para articulaciones no visibles o padding)
    mask = Masking(mask_value=0.0)(input_layer)

    # --- TRONCO COMÚN (Extracción de patrones temporales) ---
    x = Bidirectional(LSTM(128, return_sequences=True))(mask)
    x = Dropout(0.3)(x)
    x = Bidirectional(LSTM(64, return_sequences=True))(x)
    
    # --- CABEZA 1: SEGMENTACIÓN (Frame a Frame) ---
    # Esta salida clasifica CADA frame en: Stand, Down, Bottom, Up
    # Sirve para contar repeticiones analizando la secuencia de estados.
    output_fase = TimeDistributed(
        Dense(4, activation='softmax'), name="fase_frame"
    )(x)

    # --- PROCESAMIENTO GLOBAL PARA KPIs ---
    # Reducimos la secuencia a un vector resumen para los valores finales
    x_global = GlobalAveragePooling1D()(x)
    x_global = Dense(64, activation='relu')(x_global)

    # --- CABEZA 2: PROFUNDIDAD (Regresión) ---
    # Predice el ángulo mínimo de rodilla alcanzado
    output_depth = Dense(1, activation='linear', name="kpi_profundidad")(x_global)

    # --- CABEZA 3: INCLINACIÓN TORSO (Regresión) ---
    # Predice el ángulo máximo de inclinación de la espalda
    output_torso = Dense(1, activation='linear', name="kpi_torso")(x_global)

    # --- CABEZA 4: ESTABILIDAD (Probabilidad 0-1) ---
    # 1 = Muy estable, 0 = Muy inestable
    output_stability = Dense(1, activation='sigmoid', name="kpi_estabilidad")(x_global)

    # --- CABEZA 5: CALIDAD GLOBAL (Clasificación) ---
    # 0 = Mala, 1 = Buena, 2 = Excelente
    output_quality = Dense(3, activation='softmax', name="calidad_global")(x_global)

    # --- CONSTRUCCIÓN DEL MODELO ---
    model = Model(
        inputs=input_layer, 
        outputs=[output_fase, output_depth, output_torso, output_stability, output_quality]
    )

    # --- COMPILACIÓN ---
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss={
            "fase_frame": "sparse_categorical_crossentropy",
            "kpi_profundidad": "mse",
            "kpi_torso": "mse",
            "kpi_estabilidad": "binary_crossentropy",
            "calidad_global": "sparse_categorical_crossentropy"
        },
        loss_weights={
            "fase_frame": 1.0,
            "kpi_profundidad": 0.5,
            "kpi_torso": 0.5,
            "kpi_estabilidad": 0.3,
            "calidad_global": 0.8
        },
        metrics={"calidad_global": "accuracy", "fase_frame": "accuracy"}
    )

    return model

# --- EJEMPLO DE USO ---
# max_frames debe ser el vídeo más largo de tu dataset
model = crear_bilstm_analizador(max_frames=1200, n_features=15)
model.summary()