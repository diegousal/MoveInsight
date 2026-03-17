# report_generator.py
import os
import numpy as np
import matplotlib.pyplot as plt
from datetime import datetime

# Colores para la terminal
class Colors:
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    RED = "\033[91m"
    RESET = "\033[0m"
    BOLD = "\033[1m"

def color_by_score(score):
    if score >= 0.75: return Colors.GREEN
    elif score >= 0.5: return Colors.YELLOW
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

def _detect_reps_from_phase(phase_seq):
    reps = []
    in_rep, start = False, None
    for i in range(1, len(phase_seq)):
        cur, prev = int(phase_seq[i]), int(phase_seq[i-1])
        if not in_rep and cur == 1:
            in_rep, start = True, i
        elif in_rep and cur == 1 and prev in (2, 3):
            reps.append((start, i - 1))
            start = i
        elif in_rep and cur == 4 and prev == 3:
            reps.append((start, i))
            in_rep = False
    if in_rep: reps.append((start, len(phase_seq)-1))
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
    # 1. Procesamiento de datos
    phase_pred = np.asarray(predicciones[0])[0] if np.asarray(predicciones[0]).ndim == 3 else np.asarray(predicciones[0])
    kpi_pred = np.asarray(predicciones[1])[0] if np.asarray(predicciones[1]).ndim == 3 else np.asarray(predicciones[1])
    
    T = phase_pred.shape[0]
    slen = min(seq_len, T) if seq_len else T
    
    phase_seq = _smooth_sequence(np.argmax(phase_pred, axis=-1)[:slen], min_run_length)
    kpi_seq = kpi_pred[:slen, :]
    
    rep_ranges = _detect_reps_from_phase(phase_seq)
    reps_data = _aggregate_kpis(kpi_seq, rep_ranges)

    # 2. IMPRESIÓN POR TERMINAL (ESTILO ANTIGUO)
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
    print(f"Puntuación global: {color_by_score(overall_score)}{overall_score:.3f}{Colors.RESET}")
    print(line + "\n")

    # 3. GENERACIÓN DE GRÁFICO PNG
    os.makedirs(output_dir, exist_ok=True)
    # Nombre simplificado según tu solicitud: nombre_reporte.png
    base_name = os.path.splitext(video_name)[0]
    output_path = os.path.join(output_dir, f"{base_name}_reporte.png")

    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 9), gridspec_kw={'height_ratios': [1, 2]})
    
    # Gráfica de Fases
    ax1.step(range(len(phase_seq)), phase_seq, where='post', color='#2c3e50', linewidth=1.5)
    ax1.set_yticks([0, 1, 2, 3, 4])
    ax1.set_yticklabels(['0:None', '1:Bottom', '2:Down', '3:Up', '4:Stand'])
    ax1.set_title(f"Análisis de Movimiento: {video_name}")
    ax1.grid(True, alpha=0.3)

    # Gráfica de KPIs
    labels = ["Profundidad", "Torso", "Estabilidad", "Rodillas", "Ritmo"]
    colors = ['#27ae60', '#e67e22', '#2980b9', '#8e44ad', '#c0392b']
    for i in range(5):
        ax2.plot(kpi_seq[:, i], label=labels[i], color=colors[i], alpha=0.8)
    
    ax2.axhline(y=0.5, color='r', linestyle='--', alpha=0.5)
    ax2.set_ylim(-0.05, 1.05)
    ax2.set_ylabel("Calidad (0-1)")
    ax2.legend(loc='upper right', fontsize='small')
    ax2.grid(True, alpha=0.3)

    plt.tight_layout()
    plt.savefig(output_path, dpi=150)
    plt.close()
    
    return output_path