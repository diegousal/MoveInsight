import tensorflow as tf
import numpy as np
import pandas as pd
from scipy.signal import find_peaks
from tensorflow.keras.layers import Input, LSTM, Bidirectional, Dense, Dropout

import numpy as np
import pandas as pd

def segmentar_repeticiones(
    df,
    knee_col=None,
    smooth_window=11,
    min_distance=30,
    peak_prominence_frac=0.2,
    expand_frac=0.5,
    return_ranges=False
):
    """
    Segmenta repeticiones de sentadilla en un DataFrame de ángulos por frame.

    Parámetros
    ----------
    df : pandas.DataFrame
        DataFrame con columnas al menos 'R_knee' o 'L_knee' (ángulos por frame).
    knee_col : str|None
        Columna a usar como señal. Si None, intenta ['R_knee','L_knee'] o su media.
    smooth_window : int
        Ventana (frames) para suavizado (rolling median). Debe ser impar y >=1.
    min_distance : int
        Distancia mínima entre picos en frames (ej: ~30 para 1s a 30fps).
    peak_prominence_frac : float
        Fracción del rango (max-min) utilizada como prominencia mínima por defecto.
    expand_frac : float
        Fracción de la prominencia usada para expandir alrededor del pico y definir inicio/fin.
    return_ranges : bool
        Si True, devuelve (lista_de_DataFrames, lista_de_(start,peak,end)).
        Si False (por defecto) devuelve solo la lista de DataFrames.

    Retorna
    -------
    list[pd.DataFrame]  or  (list[pd.DataFrame], list[(start,peak,end)])
    """
    # --- elegir la señal de rodilla ---
    if knee_col is None:
        if 'R_knee' in df.columns and 'L_knee' in df.columns:
            señal = df[['R_knee', 'L_knee']].mean(axis=1)
        elif 'R_knee' in df.columns:
            señal = df['R_knee']
        elif 'L_knee' in df.columns:
            señal = df['L_knee']
        else:
            raise ValueError("No se encontró ninguna columna 'R_knee' ni 'L_knee' en df")
    else:
        if knee_col not in df.columns:
            raise ValueError(f"Columna {knee_col} no encontrada en df")
        señal = df[knee_col]

    # --- limpieza / relleno ---
    señal = señal.astype(float)
    señal = señal.interpolate(limit_direction='both').fillna(señal.mean())

    # --- suavizado (rolling median) ---
    if smooth_window is None or smooth_window <= 1:
        smooth = señal.values
    else:
        # garantizar impar
        if smooth_window % 2 == 0:
            smooth_window += 1
        smooth = señal.rolling(window=smooth_window, center=True, min_periods=1).median().values

    # --- invertimos porque buscamos valles (profundidad de sentadilla) ---
    sig = -np.array(smooth, dtype=float)

    # --- prominencia por defecto relativa al rango de la señal ---
    sig_range = np.nanmax(sig) - np.nanmin(sig)
    if sig_range <= 0:
        # señal plana -> no repeticiones
        return ([], []) if return_ranges else []

    prom_default = sig_range * peak_prominence_frac

    # --- intentar usar scipy.find_peaks, si no está usar heurística simple ---
    try:
        from scipy.signal import find_peaks
        peaks, props = find_peaks(sig, distance=min_distance, prominence=prom_default)
        prominences = props.get('prominences', None)
    except Exception:
        # fallback: detectar máximos locales simples + umbral basado en percentil
        peaks = []
        # umbral heurístico
        thr = np.percentile(sig, 75)
        N = len(sig)
        for i in range(min_distance, N - min_distance):
            window = sig[i - min_distance:i + min_distance + 1]
            if sig[i] == np.max(window) and sig[i] > thr:
                peaks.append(i)
        peaks = np.array(peaks, dtype=int)
        prominences = None

    if len(peaks) == 0:
        return ([], []) if return_ranges else []

    # --- determinar prominencias si no vinieron de scipy ---
    if prominences is None:
        # aproximamos la prominencia como distancia al mínimo local en un vecindario
        prominences = []
        k = max(1, min_distance // 4)
        for p in peaks:
            left_min = np.min(sig[max(0, p - k):p + 1])
            right_min = np.min(sig[p:min(len(sig), p + k + 1)])
            prominences.append(sig[p] - max(left_min, right_min))
        prominences = np.array(prominences)

    # --- para cada pico, expandir hasta que la señal caiga por debajo de (peak - expand_frac*prom) ---
    windows = []
    for p, prom in zip(peaks, prominences):
        thresh = sig[p] - expand_frac * max(prom, 1e-6)
        # buscar inicio
        start = p
        while start > 0 and sig[start] > thresh:
            start -= 1
        # buscar fin
        end = p
        while end < len(sig) - 1 and sig[end] > thresh:
            end += 1
        windows.append((max(0, start), p, min(len(sig) - 1, end)))

    # --- fusionar ventanas solapadas (si aparecen picos cercanos) ---
    merged = []
    for s, p, e in sorted(windows, key=lambda x: x[0]):
        if not merged:
            merged.append([s, p, e])
        else:
            prev = merged[-1]
            if s <= prev[2] + 1:  # solapan o contiguos
                prev[1] = prev[1] if prev[1] < p else p  # mantener pico más a la izquierda (opcional)
                prev[2] = max(prev[2], e)
            else:
                merged.append([s, p, e])

    # --- crear slices DataFrame ---
    rep_slices = []
    ranges = []
    for s, p, e in merged:
        rep_slices.append(df.iloc[s:e + 1].copy().reset_index(drop=True))
        ranges.append((int(s), int(p), int(e)))

    return (rep_slices, ranges) if return_ranges else rep_slices

def crear_bilstm_analizador(n_timesteps, n_features):
    # Entrada: Ventana de frames con tus ángulos (L_knee, R_hip, torso, etc.)
    inputs = Input(shape=(n_timesteps, n_features))
    
    # Cuerpo común de la red (BiLSTM)
    x = Bidirectional(LSTM(128, return_sequences=True))(inputs)
    x = Dropout(0.3)(x)
    x = Bidirectional(LSTM(64))(x) # Capa que resume la secuencia
    
    # --- CABEZAS DE SALIDA (Multi-Output) ---
    
    # 1. Cabeza de Profundidad (0: Infra, 0.5: OK, 1: Excesiva)
    out_depth = Dense(1, activation='sigmoid', name='depth')(x)
    
    # 2. Cabeza de Simetría (1: Perfecta, 0: Asimétrica)
    out_symmetry = Dense(1, activation='sigmoid', name='symmetry')(x)
    
    # 3. Cabeza de Alineación (Tronco/Espalda)
    out_alignment = Dense(1, activation='sigmoid', name='alignment')(x)
    
    # 4. Cabeza de Velocidad (Tempo)
    out_speed = Dense(1, activation='sigmoid', name='speed')(x)

    # Construcción del modelo
    model = tf.keras.Model(inputs=inputs, 
                           outputs=[out_depth, out_symmetry, out_alignment, out_speed])
    
    model.compile(optimizer='adam', loss='mse') # Mean Squared Error para scores 0-1
    return model