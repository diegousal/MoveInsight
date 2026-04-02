"""
Definición del modelo BiLSTM para:
 - salida 1: clasificación por frame (phase) -> softmax (num_phases clases)
 - salida 2: regresión por frame de los 5 KPIs (sigmoid -> 0..1)

Entrada: secuencias de forma (batch, timesteps, n_features)
"""
import tensorflow as tf
from tensorflow.keras import layers, Model

MASK_VALUE = -99.0  # Valor de máscara para sustituir datos faltantes

def build_bilstm_model(input_dim: int,
                       lstm_units: int = 128,
                       num_layers: int = 2,
                       num_phases: int = 5,
                       dropout: float = 0.2) -> Model:
    """
    Construye y devuelve un modelo Keras.

    Args:
        input_dim: número de features por frame (p. ej. 15 según tu descripción).
        lstm_units: unidades LSTM por dirección.
        num_layers: número de capas LSTM (apiladas).
        num_phases: número de clases de fase por frame (incluye 'no-rep' como clase 0).
        dropout: drop entre capas.

    Returns:
        modelo compilado (sin entrenar).
    """
    seq_in = layers.Input(shape=(None, input_dim), name="sequence_input")

    # Enmascarado para secuencias padded (asume padding con ceros)
    x = layers.Masking(mask_value=MASK_VALUE)(seq_in)

    # Apilado de Bidirectional LSTM
    for i in range(num_layers):
        x = layers.Bidirectional(
                layers.LSTM(lstm_units, return_sequences=return_sequences,
                            dropout=dropout, recurrent_dropout=0.0),
                name=f"bilstm_{i}"
            )(x)

    # Cabeza para clasificación por frame (fases)
    phase_logits = layers.TimeDistributed(
                    layers.Dense(64, activation="relu"),
                    name="phase_dense1"
                  )(x)
    phase_out = layers.TimeDistributed(
                    layers.Dense(num_phases, activation="softmax"),
                    name="phase_out"
                )(phase_logits)

    # Cabeza para KPIs (regresión por frame: 5 KPIs entre 0 y 1)
    kpi_head = layers.TimeDistributed(
                    layers.Dense(64, activation="relu"),
                    name="kpi_dense1"
                )(x)
    kpi_out = layers.TimeDistributed(
                    layers.Dense(5, activation="sigmoid"),
                    name="kpi_out"
              )(kpi_head)

    model = Model(inputs=seq_in, outputs=[phase_out, kpi_out], name="bilstm_kpi_model")

    # Compilación: pérdidas combinadas. Para KPIs usamos mse; para fases categorical_crossentropy.
    losses = {
        "phase_out": "sparse_categorical_crossentropy",
        "kpi_out": "mse"
    }
    loss_weights = {"phase_out": 1.0, "kpi_out": 1.0}

    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss=losses,
        loss_weights=loss_weights,
        metrics={"phase_out": "sparse_categorical_accuracy", "kpi_out": "mse"},
        weighted_metrics={"phase_out": "sparse_categorical_accuracy", "kpi_out": "mse"}
    )

    return model