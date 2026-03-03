import numpy as np
import tensorflow as tf
from redBILSTM import crear_bilstm_analizador

# 1. CARGAR DATOS
x_train = np.load('./entrenamiento/7.npy')
num_frames = x_train.shape[0]

# 2. ETIQUETADO DETALLADO (4 CAMPOS POR REP)
# (inicio_descenso, inicio_fondo, fin_fondo, fin_ascenso)
reps_detalladas = [
    (76, 118, 143, 171),  # Rep 1
    (210, 250, 273, 301), # Rep 2
    (337, 377, 405, 433), # Rep 3
    (466, 504, 533, 561), # Rep 4
    (596, 633, 660, 687), # Rep 5
    (720, 760, 782, 815), # Rep 6
    (847, 888, 910, 940)  # Rep 7
]

# Crear el array de fases frame a frame
fases = np.zeros(num_frames)
for inicio, b_ini, b_fin, fin in reps_detalladas:
    fases[inicio:b_ini] = 1   # Bajando
    fases[b_ini:b_fin] = 2    # Fondo (Máxima profundidad)
    fases[b_fin:fin] = 3      # Subiendo

# 3. ETIQUETADO DE KPIs DE CALIDAD (0 a 1)
# Como este video es el ejemplo de "Cómo se hace bien", ponemos todo a 1.0
# La red aprenderá que este patrón de movimiento merece un 10 (1.0)
target_excelencia = np.ones((1, num_frames, 1))

y_train = {
    "fase_frame": np.expand_dims(fases, axis=0),      # Para contar reps
    "kpi_profundidad": target_excelencia,             # Calidad de bajada
    "kpi_torso": target_excelencia,                   # Calidad de espalda
    "kpi_estabilidad": target_excelencia,             # Calidad de balance
    "kpi_simetria": target_excelencia,                # Lado L vs R
    "kpi_rodillas": target_excelencia,                # Alineación
    "kpi_ritmo": target_excelencia                    # Control de velocidad
}

# 4. PREPARAR MODELO Y ENTRENAR
model = crear_bilstm_analizador(max_frames=num_frames, n_features=15)
x_batch = np.expand_dims(x_train, axis=0)

print("Entrenando al Juez Virtual con 4 fases y KPIs de calidad...")
model.fit(
    x_batch, 
    y_train, 
    epochs=100, # Al ser un solo video, necesitamos épocas para que memorice el patrón
    verbose=1
)

# 5. GUARDAR EL CEREBRO
model.save("./modelos/juez_sentadillas.h5")
print("Modelo guardado. Listo para juzgar nuevos videos.")