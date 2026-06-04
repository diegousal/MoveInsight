"""
rep_report.py — Generación del reporte visual (PNG) para el pipeline XGBoost.

A diferencia de la versión anterior (Bi-LSTM multisalida con 5 KPIs en escala
continua), aquí los KPIs llegan ya predichos por los modelos XGBoost como
clases categóricas 1/2/3 mapeadas a 0-10 (Opción B). Algunos KPIs pueden ser
None cuando no son medibles en el perfil de cámara (valgo/simetría en lateral).

Función pública:
    save_visual_report(reps_results, video_source, perfil, output_dir, fps)
        → {"report_path": str, "overall_score": float}
"""

import os
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from processing.rep_features import KPI_NAMES, KPI_ES, LABEL_TEXT

# ── Paleta (coherente con la app) ──────────────────────────────────────────────
_BG_DEEP   = "#0D1B2A"
_BG_MID    = "#1A2E42"
_TEXT      = "#E8F4FD"
_TEXT_DIM  = "#8BA3BC"
_GRID      = "#2A3F55"

# Color por KPI
_KPI_COLORS = {
    "depth":    "#00C8FF",   # cian
    "torso":    "#FF6B35",   # naranja
    "knees":    "#9D4EDD",   # morado (valgo)
    "symmetry": "#4CAF50",   # verde
    "rhythm":   "#F5C518",   # amarillo
}


def save_visual_report(reps_results, video_source, perfil, output_dir, fps=30.0):
    """
    Genera un PNG con la evolución de los 5 KPIs por repetición.

    Parameters
    ----------
    reps_results : list[dict]
        Cada dict: {"start", "end", "scores": {kpi: 0-10|None}, "overall": float}
    video_source : str  — ruta del vídeo (se usa solo para el nombre del fichero)
    perfil       : str  — "frontal" | "lateral"
    output_dir   : str  — carpeta destino
    fps          : float

    Returns
    -------
    dict {"report_path": str, "overall_score": float}
    """
    os.makedirs(output_dir, exist_ok=True)
    video_name = os.path.basename(video_source)
    base_name  = os.path.splitext(video_name)[0]
    output_path = os.path.join(output_dir, f"{base_name}_reporte.png")

    n_reps = len(reps_results)
    rep_x  = list(range(1, n_reps + 1))

    overall_vals = [r["overall"] for r in reps_results if r.get("overall") is not None]
    overall_score = float(np.mean(overall_vals)) if overall_vals else 0.0

    fig, ax = plt.subplots(figsize=(11, 5))
    fig.patch.set_facecolor(_BG_DEEP)
    ax.set_facecolor(_BG_MID)

    # Banda de referencia (5 = umbral aprobado)
    ax.axhline(y=5.0, color="#3A536B", linestyle="--", linewidth=1, alpha=0.7)

    if n_reps > 0:
        for kpi in KPI_NAMES:
            ys = [r["scores"].get(kpi) for r in reps_results]
            # Solo dibujar el KPI si tiene al menos un valor medible
            if all(v is None for v in ys):
                continue
            xs_plot = [x for x, v in zip(rep_x, ys) if v is not None]
            ys_plot = [v for v in ys if v is not None]
            ax.plot(
                xs_plot, ys_plot,
                marker="o", markersize=6, linewidth=2.2,
                color=_KPI_COLORS.get(kpi, "#FFFFFF"),
                markeredgecolor=_BG_DEEP, markeredgewidth=1.2,
                label=KPI_ES.get(kpi, kpi),
            )

    ax.set_title(
        f"Análisis de Sentadilla — {video_name}   ·   Perfil: {perfil.upper()}",
        color=_TEXT, fontsize=12, pad=10,
    )
    ax.set_xlabel("Repetición", color=_TEXT_DIM, fontsize=10)
    ax.set_ylabel("Puntuación (0-10)", color=_TEXT_DIM, fontsize=10)
    ax.set_ylim(-0.5, 10.5)
    if n_reps > 0:
        ax.set_xticks(rep_x)
    ax.tick_params(colors=_TEXT_DIM, labelsize=9)
    for spine in ax.spines.values():
        spine.set_edgecolor(_BG_MID)
    ax.grid(axis="y", color=_GRID, linewidth=0.7, linestyle="--", alpha=0.6)

    leg = ax.legend(loc="lower right", fontsize=9, ncol=2, framealpha=0.0)
    for txt in leg.get_texts():
        txt.set_color(_TEXT)

    # Nota sobre KPIs no medibles en este perfil
    if perfil == "lateral":
        nota = "Simetría y valgo no medibles en vista lateral (N/D)."
    else:
        nota = "Para evaluar el torso con más precisión graba también de lado."
    fig.text(0.013, 0.015, nota, color=_TEXT_DIM, fontsize=8)

    plt.tight_layout(rect=(0, 0.03, 1, 1))
    plt.savefig(output_path, dpi=150, facecolor=fig.get_facecolor())
    plt.close(fig)

    return {"report_path": output_path, "overall_score": overall_score}
