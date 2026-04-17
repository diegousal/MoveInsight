import os
import csv # Añadida la librería para manejar el archivo separado
import numpy as np
from datetime import datetime
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

class Colors:
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    RED = "\033[91m"
    RESET = "\033[0m"
    BOLD = "\033[1m"

def color_by_score(score):
    # Ajustado para la escala sobre 10
    if score >= 7.5: return Colors.GREEN
    elif score >= 5.0: return Colors.YELLOW
    else: return Colors.RED

# ---------------- UTILIDADES LÓGICAS ----------------
def _rle(arr):
    if len(arr) == 0: return np.array([]), np.array([]), np.array([])
    arr = np.asarray(arr)
    change_idx = np.where(np.diff(arr) != 0)[0] + 1
    starts = np.concatenate(([0], change_idx))
    ends = np.concatenate((change_idx, [len(arr)]))
    return arr[starts], ends - starts, starts

def _smooth_sequence(seq, min_run_length=3):
    seq = np.asarray(seq).copy()
    values, lengths, starts = _rle(seq)
    for val, ln, st in zip(values, lengths, starts):
        if ln < min_run_length:
            idx0, idx1 = st, st + ln
            if idx0 - 1 >= 0: seq[idx0:idx1] = seq[idx0 - 1]
            elif idx1 < len(seq): seq[idx0:idx1] = seq[idx1]
    return seq

def _detect_reps_from_phase(phase_seq, min_rep_frames=20):
    reps = []
    in_rep = False
    start = None
    has_reached_bottom = False
    
    seq = np.array(phase_seq, dtype=int)
    
    for i in range(len(seq)):
        cur = seq[i]
        prev = seq[i-1] if i > 0 else 0
        
        if not in_rep:
            if cur in [1, 2, 3] and prev in [0, 4]:
                in_rep = True
                start = i
                has_reached_bottom = (cur == 2)
        else:
            if cur == 2:
                has_reached_bottom = True
            
            if cur in [0, 4] and prev in [1, 2, 3]:
                end = i
                duration = end - start
                
                if duration >= min_rep_frames:
                    if has_reached_bottom or duration > 35: 
                        reps.append((start, end))
                
                in_rep = False
                has_reached_bottom = False
                
    return reps

def _aggregate_kpis(pred_kpis, rep_ranges):
    out = []
    for s, e in rep_ranges:
        seg = pred_kpis[s:e+1]
        avg = seg.mean(axis=0) if seg.shape[0] > 0 else np.zeros(5)
        out.append({
            "start": int(s), "end": int(e),
            "kpis": [float(v) for v in avg]
        })
    return out

# ---------------- FUNCIÓN PRINCIPAL ----------------
def save_visual_report(predicciones, video_source, seq_len=None, output_dir="./reportes", min_run_length=3, fps=30.0):
    # 1. Procesamiento
    phase_pred = np.asarray(predicciones[0])[0] if np.asarray(predicciones[0]).ndim == 3 else np.asarray(predicciones[0])
    kpi_pred = np.asarray(predicciones[1])[0] if np.asarray(predicciones[1]).ndim == 3 else np.asarray(predicciones[1])
    
    T = phase_pred.shape[0]
    slen = min(seq_len, T) if seq_len else T
    
    phase_seq = _smooth_sequence(np.argmax(phase_pred, axis=-1)[:slen], min_run_length)
    
    # ¡AQUÍ ESTÁ LA MAGIA!: Multiplicamos por 10 para cambiar la escala a 1-10
    kpi_seq = kpi_pred[:slen, :] * 10.0
    
    rep_ranges = _detect_reps_from_phase(phase_seq)
    reps_data = _aggregate_kpis(kpi_seq, rep_ranges)

    # 2. Terminal
    video_name = os.path.basename(video_source)
    n_reps = len(reps_data)
    sum_kpis = np.zeros(5)
    
    line = "+" + "-"*74 + "+"
    print("\n" + line)
    print(f"| Informe de Repeticiones - {video_name:<50} |")
    print(line)
    
    header = f"{'Rep':>3} | {'Start':>6} - {'End':>6} | {'Depth':>5} {'Torso':>6} {'Stable':>7} {'Knees':>6} {'Ritmo':>6}"
    print(header)
    print("-" * len(header))
    
    for i, r in enumerate(reps_data, 1):
        k = r["kpis"]
        sum_kpis += np.array(k)
        print(f"{i:3d} | {r['start']:6} - {r['end']:6} | {k[0]:.2f}  {k[1]:.2f}    {k[2]:.2f}    {k[3]:.2f}   {k[4]:.2f}")
    
    avg_kpis = (sum_kpis / n_reps) if n_reps > 0 else np.zeros(5)
    overall_score = np.mean(avg_kpis)
    
    print("-" * len(header))
    print(f"Promedio KPI: Depth {avg_kpis[0]:.2f} Torso {avg_kpis[1]:.2f} Stability {avg_kpis[2]:.2f} Knees {avg_kpis[3]:.2f} Ritmo {avg_kpis[4]:.2f}")
    print(f"Puntuación global: {color_by_score(overall_score)}{overall_score:.2f}{Colors.RESET}")
    print(line + "\n")

    # 3. Gráfico
    os.makedirs(output_dir, exist_ok=True)
    base_name = os.path.splitext(video_name)[0]
    output_path = os.path.join(output_dir, f"{base_name}_reporte.png")

    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 9), gridspec_kw={'height_ratios': [1, 2]})
    
    # Fases: 1:Down, 2:Bottom, 3:Up, 4:Stand
    ax1.step(range(len(phase_seq)), phase_seq, where='post', color='#2c3e50', linewidth=1.5)
    ax1.set_yticks([0, 1, 2, 3, 4])
    ax1.set_yticklabels(['0:None', '1:Down', '2:Bottom', '3:Up', '4:Stand'])
    ax1.set_title(f"Análisis de Movimiento: {video_name}")
    ax1.grid(True, alpha=0.3)

    # KPIs
    labels = ["Profundidad", "Torso", "Estabilidad", "Rodillas", "Ritmo"]
    colors = ['#27ae60', '#e67e22', '#2980b9', '#8e44ad', '#c0392b']
    for i in range(5):
        ax2.plot(kpi_seq[:, i], label=labels[i], color=colors[i], alpha=0.8)
    
    # Ajustado a la escala 1-10
    ax2.axhline(y=5.0, color='r', linestyle='--', alpha=0.5)
    ax2.set_ylim(-0.5, 10.5)
    ax2.set_ylabel("Calidad (1-10)")
    ax2.legend(loc='upper right', fontsize='small')
    ax2.grid(True, alpha=0.3)

    plt.tight_layout()
    plt.savefig(output_path, dpi=150)
    plt.close()
    
    # 4. Guardar histórico en CSV de forma persistente
    csv_filename = os.path.join(output_dir, "historial_predicciones.csv")
    file_exists = os.path.isfile(csv_filename)
    
    # Abrimos en modo 'a' (append) para no sobreescribir
    with open(csv_filename, mode='a', newline='', encoding='utf-8') as csvfile:
        writer = csv.writer(csvfile)
        
        # Si el archivo no existía, le ponemos la cabecera
        if not file_exists:
            writer.writerow(["Fecha", "Video", "Repeticion", "Start_Frame", "End_Frame", 
                             "Profundidad", "Torso", "Estabilidad", "Rodillas", "Ritmo"])
            
        # Añadimos una fila por cada repetición detectada
        fecha_actual = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        for i, r in enumerate(reps_data, 1):
            k = r["kpis"]
            writer.writerow([
                fecha_actual,
                video_name,
                i,
                r['start'],
                r['end'],
                f"{k[0]:.2f}", f"{k[1]:.2f}", f"{k[2]:.2f}", f"{k[3]:.2f}", f"{k[4]:.2f}"
            ])
            
        return {
        "report_path": output_path,
        "reps": reps_data,
        "avg_kpis": avg_kpis.tolist(),
        "overall_score": float(overall_score),
    }