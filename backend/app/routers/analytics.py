from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session as DBSession
from sqlalchemy import func as sql_func
import datetime
import io

from app.auth import get_current_user
from app.database import get_db
from app.models import Incident, PainCheckIn, Session, SessionResult, User
from app.schemas import AnalyticsSummary, InsightResponse, ReadinessBreakdown
from app.services.readiness import (
    DEFAULT_BODY_WEIGHT_KG,
    _session_load,
    calculate_readiness,
)
from app.services.recommendations import evaluate_recommendations


# ──────────────────────────────────────────────────────────────────────────────
# Helpers compartidos
# ──────────────────────────────────────────────────────────────────────────────

def _load_sessions_with_technique(db: DBSession, user_id: int) -> list[Session]:
    """
    Carga sesiones completadas del usuario y adjunta `avg_technique` a cada una
    (atributo dinámico para alimentar el cálculo del semáforo).
    """
    sessions = db.query(Session).filter(
        Session.user_id == user_id,
        Session.status  == "completed",
    ).order_by(Session.created_at.desc()).all()

    for s in sessions:
        results = db.query(SessionResult).filter(
            SessionResult.session_id == s.id
        ).all()
        valid = [r.overall_score for r in results if r.overall_score is not None]
        s.avg_technique = (sum(valid) / len(valid)) if valid else None

    return sessions


def _load_recent_checkins(db: DBSession, user_id: int) -> list[PainCheckIn]:
    """Devuelve todos los check-ins de dolor del usuario, ordenados por fecha desc."""
    return db.query(PainCheckIn).join(Session).filter(
        Session.user_id == user_id
    ).order_by(PainCheckIn.created_at.desc()).all()


router = APIRouter()


# ──────────────────────────────────────────────────────────────────────────────
# GET /analytics/summary
# ──────────────────────────────────────────────────────────────────────────────

@router.get("/analytics/summary", response_model=AnalyticsSummary)
def get_analytics_summary(
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    # Total sessions
    total = db.query(sql_func.count(Session.id)).filter(
        Session.user_id == current_user.id
    ).scalar() or 0

    # Avg technique (overall_score across all results)
    avg_technique = db.query(sql_func.avg(SessionResult.overall_score)).join(Session).filter(
        Session.user_id == current_user.id
    ).scalar()

    # Max weight
    max_weight = db.query(sql_func.max(Session.weight_kg)).filter(
        Session.user_id == current_user.id
    ).scalar()

    # Best technique score: best average session score
    session_avgs = db.query(
        sql_func.avg(SessionResult.overall_score).label("avg_score")
    ).join(Session).filter(
        Session.user_id == current_user.id
    ).group_by(SessionResult.session_id).all()
    best_technique = max(
        (row.avg_score for row in session_avgs if row.avg_score is not None),
        default=None
    )

    # Max reps in a single session
    rep_counts = db.query(
        SessionResult.session_id,
        sql_func.count(SessionResult.id).label("cnt")
    ).join(Session).filter(
        Session.user_id == current_user.id
    ).group_by(SessionResult.session_id).all()
    max_reps = max((row.cnt for row in rep_counts), default=None)

    # ── Readiness inteligente ─────────────────────────────────────────────
    sessions_for_readiness = _load_sessions_with_technique(db, current_user.id)
    checkins_for_readiness = _load_recent_checkins(db, current_user.id)
    breakdown = calculate_readiness(current_user, sessions_for_readiness, checkins_for_readiness)
    readiness = breakdown["score"]
    label     = breakdown["label"]

    # Borg medio reciente (solo para los insights de abajo)
    recent_sessions = sessions_for_readiness[:5] if sessions_for_readiness else []
    if recent_sessions:
        avg_borg = sum(s.borg_score or 5 for s in recent_sessions) / len(recent_sessions)
    else:
        avg_borg = 0

    overtraining_risk = breakdown["stage"] == "established" and readiness < 40

    # ── Insights ──────────────────────────────────────────────────────────
    insights: list[InsightResponse] = []

    if total == 0:
        insights.append(InsightResponse(
            type="tip",
            title="Empieza a entrenar",
            message="Registra tu primera sesion para recibir analisis personalizados."
        ))
    else:
        if avg_technique is not None:
            avg_t = float(avg_technique)
            if avg_t >= 7.5:
                insights.append(InsightResponse(
                    type="achievement", title="Gran tecnica",
                    message=f"Tu puntuacion media de tecnica es {avg_t:.1f}/10. Sigue asi!"
                ))
            elif avg_t >= 5.0:
                insights.append(InsightResponse(
                    type="tip", title="Tecnica mejorable",
                    message=f"Tu puntuacion media es {avg_t:.1f}/10. Enfocate en profundidad y control postural."
                ))
            else:
                insights.append(InsightResponse(
                    type="warning", title="Revisa tu tecnica",
                    message=f"Tu puntuacion media es {avg_t:.1f}/10. Reduce carga y trabaja la forma."
                ))

        if avg_borg > 7:
            insights.append(InsightResponse(
                type="warning", title="Fatiga elevada",
                message=f"Tu Borg medio reciente es {avg_borg:.1f}. Considera descansar o reducir intensidad."
            ))
        elif avg_borg < 3:
            insights.append(InsightResponse(
                type="tip", title="Puedes aumentar intensidad",
                message=f"Tu Borg medio es {avg_borg:.1f}. Podrias incrementar la carga gradualmente."
            ))

        completed = db.query(sql_func.count(Session.id)).filter(
            Session.user_id == current_user.id,
            Session.status == "completed"
        ).scalar() or 0
        if completed >= 10:
            insights.append(InsightResponse(
                type="achievement", title="Constancia",
                message=f"Llevas {completed} sesiones completadas. La consistencia es clave."
            ))
        elif completed >= 3:
            insights.append(InsightResponse(
                type="tip", title="Buen comienzo",
                message=f"Ya tienes {completed} sesiones. Sigue registrando para ver tendencias."
            ))

        if max_weight and max_weight > 0:
            insights.append(InsightResponse(
                type="tip", title="Carga maxima",
                message=f"Tu carga maxima registrada es {max_weight:.0f} kg."
            ))

        if overtraining_risk:
            insights.append(InsightResponse(
                type="warning", title="Riesgo de sobreentrenamiento",
                message=f"Borg medio {avg_borg:.1f} con readiness {readiness}%. Toma al menos 48h de descanso."
            ))

        if best_technique is not None and best_technique > 0:
            insights.append(InsightResponse(
                type="achievement", title="Record de tecnica",
                message=f"Tu mejor sesion alcanzó {float(best_technique):.1f}/10 de media. ¡Supéralo!"
            ))

    return AnalyticsSummary(
        total_sessions=total,
        avg_technique_score=round(float(avg_technique), 1) if avg_technique else None,
        avg_velocity=None,
        max_weight_kg=round(float(max_weight), 1) if max_weight else None,
        best_technique_score=round(float(best_technique), 1) if best_technique else None,
        max_reps=int(max_reps) if max_reps else None,
        overtraining_risk=overtraining_risk,
        readiness_score=readiness,
        readiness_label=label,
        insights=insights,
    )


# ──────────────────────────────────────────────────────────────────────────────
# GET /analytics/readiness
# ──────────────────────────────────────────────────────────────────────────────

@router.get("/analytics/readiness", response_model=ReadinessBreakdown)
def get_readiness_breakdown(
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    """Devuelve el desglose detallado del semáforo de readiness."""
    sessions = _load_sessions_with_technique(db, current_user.id)
    checkins = _load_recent_checkins(db, current_user.id)
    breakdown = calculate_readiness(current_user, sessions, checkins)
    return breakdown


# ──────────────────────────────────────────────────────────────────────────────
# GET /analytics/pdf — Informe profesional completo
# ──────────────────────────────────────────────────────────────────────────────

# Mapas de traducción (usados en varias secciones del PDF)
_BODY_ZONE_LABELS = {
    "rodilla_izq": "Rodilla izquierda",
    "rodilla_der": "Rodilla derecha",
    "lumbar"     : "Lumbar",
    "cervical"   : "Cervical",
    "cadera_izq" : "Cadera izquierda",
    "cadera_der" : "Cadera derecha",
    "tobillo_izq": "Tobillo izquierdo",
    "tobillo_der": "Tobillo derecho",
    "hombro_izq" : "Hombro izquierdo",
    "hombro_der" : "Hombro derecho",
    "general"    : "General",
}
_INCIDENT_TYPE_LABELS = {
    "molestia"    : "Molestia",
    "dolor_agudo" : "Dolor agudo",
    "fatiga"      : "Fatiga",
    "contractura" : "Contractura",
    "lesion"      : "Lesión",
    "otro"        : "Otro",
}
_RECOMMENDATION_CATEGORY_LABELS = {
    "technique"  : "Técnica",
    "load"       : "Carga",
    "recovery"   : "Recuperación",
    "progression": "Progresión",
}

# Colores compartidos PDF/matplotlib
_HEX_NAV_DEEP   = "#0D1B2A"
_HEX_NAV_MID    = "#1A2E42"
_HEX_CYAN       = "#00C8FF"
_HEX_ORANGE     = "#FF6B35"
_HEX_YELLOW     = "#F5C518"
_HEX_GREEN      = "#4CAF50"
_HEX_RED        = "#EF476F"
_HEX_PURPLE     = "#9D4EDD"
_HEX_TEXT_LIGHT = "#E8F4FD"
_HEX_TEXT_DIM   = "#8BA3BC"
_HEX_GRID       = "#2A3F55"


def _body_zone_display(code: str) -> str:
    return _BODY_ZONE_LABELS.get(code, code.replace("_", " ").capitalize())


def _incident_type_display(code: str) -> str:
    return _INCIDENT_TYPE_LABELS.get(code, code.replace("_", " ").capitalize())


def _category_display(code: str) -> str:
    return _RECOMMENDATION_CATEGORY_LABELS.get(code, code.capitalize())


def _compute_personal_records(sessions: list[Session], session_data: list[dict]) -> dict:
    """
    Calcula los récords personales del usuario:
      - Mejor técnica (sesión con mejor media)
      - Carga máxima registrada
      - Repeticiones máximas en una sola sesión
      - Mejores puntuaciones en cada KPI individual (depth, knees/valgo, torso, symmetry, rhythm)
      - Mejor combinación Borg bajo + técnica alta
    Devuelve un dict con `value`, `date`, `extra` por cada PR (cuando aplica).
    """
    prs: dict = {}

    # ── Mejor técnica (media de sesión) ─────────────────────────────────
    best_tech = None
    for sd in session_data:
        if sd["avg_score"] is None:
            continue
        if best_tech is None or sd["avg_score"] > best_tech["value"]:
            best_tech = {
                "value": sd["avg_score"],
                "date": sd["session"].created_at,
                "session_id": sd["session"].id,
            }
    if best_tech:
        prs["best_technique"] = best_tech

    # ── Carga máxima ─────────────────────────────────────────────────────
    sessions_with_weight = [s for s in sessions if s.weight_kg]
    if sessions_with_weight:
        max_w = max(sessions_with_weight, key=lambda s: s.weight_kg)
        prs["max_weight"] = {
            "value": max_w.weight_kg,
            "date": max_w.created_at,
            "session_id": max_w.id,
        }

    # ── Reps máximas en sesión ───────────────────────────────────────────
    sessions_with_reps = [(sd, len(sd["reps"])) for sd in session_data if sd["reps"]]
    if sessions_with_reps:
        max_r = max(sessions_with_reps, key=lambda x: x[1])
        prs["max_reps"] = {
            "value": max_r[1],
            "date": max_r[0]["session"].created_at,
            "session_id": max_r[0]["session"].id,
        }

    # ── Mejores KPIs individuales (mejor rep, no media) ─────────────────
    kpi_fields = [
        ("best_depth"    , "depth_score"    , "Profundidad"),
        ("best_knees"    , "knees_score"    , "Valgo de rodilla"),
        ("best_torso"    , "torso_score"    , "Torso"),
        ("best_symmetry" , "symmetry_score" , "Simetría"),
        ("best_rhythm"   , "rhythm_score"   , "Ritmo"),
    ]
    for pr_key, attr, _label in kpi_fields:
        best_kpi = None
        for sd in session_data:
            for r in sd["reps"]:
                v = getattr(r, attr, None)
                if v is None:
                    continue
                if best_kpi is None or v > best_kpi["value"]:
                    best_kpi = {
                        "value": v,
                        "date": sd["session"].created_at,
                        "session_id": sd["session"].id,
                    }
        if best_kpi:
            prs[pr_key] = best_kpi

    return prs


def _compute_session_injury_indices(
    sessions: list[Session],
    incidents: list[Incident],
) -> list[tuple[int, int, bool]]:
    """
    Para cada incidencia, encuentra el primer y último índice de sesión
    (en orden cronológico ascendente) que cae dentro del periodo.
    Devuelve [(first_idx, last_idx, is_active), ...].
    """
    if not sessions or not incidents:
        return []

    today = datetime.date.today()
    session_dates = [s.created_at.date() if s.created_at else None for s in sessions]
    out: list[tuple[int, int, bool]] = []

    for inc in incidents:
        if not inc.start_date:
            continue
        end = inc.end_date or today
        if end < inc.start_date:
            continue

        first_idx = None
        last_idx = None
        for i, d in enumerate(session_dates):
            if d is None:
                continue
            if inc.start_date <= d <= end:
                if first_idx is None:
                    first_idx = i
                last_idx = i

        if first_idx is not None and last_idx is not None:
            out.append((first_idx, last_idx, inc.end_date is None))
            continue

        # Reposo completo (sin sesiones dentro del periodo): la banda se dibuja
        # en el hueco entre la sesión anterior y la posterior a la lesión.
        before = None
        after = None
        for i, d in enumerate(session_dates):
            if d is None:
                continue
            if d < inc.start_date:
                before = i
            if d > end and after is None:
                after = i
        if before is not None and after is not None:
            out.append((before + 0.35, after - 0.35, inc.end_date is None))
        elif before is not None:
            out.append((before + 0.35, before + 0.9, inc.end_date is None))
        elif after is not None:
            out.append((after - 0.9, after - 0.35, inc.end_date is None))

    return out


def _adaptive_chart_params(n: int) -> dict:
    """
    Devuelve parámetros de estilo para que el gráfico se vea bien
    independientemente del número de sesiones.

    Las claves expuestas son las que necesitan los renderizadores:
    `marker_size`, `line_width`, `date_step`, `rotation`, `date_fontsize`.
    """
    if n <= 8:
        return {"marker_size": 6, "line_width": 2.2, "date_step": 1,
                "rotation": 0,  "date_fontsize": 8}
    if n <= 16:
        return {"marker_size": 5, "line_width": 2.0, "date_step": 1,
                "rotation": 30, "date_fontsize": 7}
    if n <= 30:
        return {"marker_size": 4, "line_width": 1.8, "date_step": 2,
                "rotation": 45, "date_fontsize": 6}
    if n <= 60:
        return {"marker_size": 3, "line_width": 1.5, "date_step": max(2, n // 12),
                "rotation": 45, "date_fontsize": 6}
    return {"marker_size": 0, "line_width": 1.3, "date_step": max(2, n // 15),
            "rotation": 45, "date_fontsize": 5}


def _draw_injury_overlays(ax, injury_ranges: list[tuple[int, int, bool]]) -> None:
    """Pinta bandas verticales rojas + ribbon superior por cada periodo de lesión."""
    if not injury_ranges:
        return
    from matplotlib.patches import Rectangle  # import puntual: matplotlib.patches no es auto-import

    y_top = ax.get_ylim()[1]
    y_bot = ax.get_ylim()[0]
    ribbon_h = (y_top - y_bot) * 0.04

    for first, last, is_active in injury_ranges:
        x_left = first - 0.45
        x_right = last + 0.45
        # Banda translúcida cubriendo todo el alto
        ax.axvspan(x_left, x_right, color=_HEX_RED, alpha=0.18, zorder=0)
        # Ribbon sólido arriba
        ax.add_patch(Rectangle(
            (x_left, y_top - ribbon_h), x_right - x_left, ribbon_h,
            facecolor=_HEX_RED, edgecolor="none", alpha=0.95, zorder=2
        ))
        # Borde izquierdo sólido
        ax.axvline(x_left, ymin=0, ymax=1, color=_HEX_RED, linewidth=1.5,
                    alpha=0.85, zorder=1)
        # Borde derecho sólido (si terminó) o discontinuo (si activa)
        ax.axvline(x_right, ymin=0, ymax=1, color=_HEX_RED, linewidth=1.5,
                    alpha=0.55 if is_active else 0.85,
                    linestyle="--" if is_active else "-", zorder=1)


def _create_progression_chart(
    title: str, dates: list[str], values: list,
    ylabel: str, color: str, ylim: tuple | None = None,
    injury_ranges: list[tuple[int, int, bool]] | None = None,
) -> io.BytesIO | None:
    """
    Crea un gráfico de progresión adaptativo (auto-escala según el nº de sesiones)
    con fechas en el eje X y bandas opcionales para periodos de lesión.

    Devuelve un BytesIO con la imagen PNG, o None si no hay datos válidos.

    NOTA: NO filtramos None en los datos, ya que eso desincronizaría los índices
    respecto a `injury_ranges` (que se calculan sobre la lista completa). Usamos
    `nan` para que matplotlib renderice los huecos como gaps en la línea.
    """
    import matplotlib.pyplot as plt
    import math

    # Verificación: al menos un valor válido
    if not any(v is not None for v in values):
        return None

    # None → NaN para mantener alineación de índices
    clean_dates  = list(dates)
    clean_values = [float(v) if v is not None else math.nan for v in values]
    n = len(clean_values)

    p = _adaptive_chart_params(n)

    fig, ax = plt.subplots(figsize=(7, 3.0))
    fig.patch.set_facecolor(_HEX_NAV_DEEP)
    ax.set_facecolor(_HEX_NAV_MID)

    indices = list(range(n))

    # Línea + puntos (markers solo si caben)
    marker_kwargs = {}
    if p["marker_size"] > 0:
        marker_kwargs = dict(
            marker="o", markersize=p["marker_size"],
            markerfacecolor=color, markeredgecolor=_HEX_NAV_DEEP,
            markeredgewidth=1.0,
        )
    ax.plot(indices, clean_values, color=color,
            linewidth=p["line_width"], **marker_kwargs)

    ax.set_title(title, color=_HEX_TEXT_LIGHT, fontsize=11, pad=8)
    ax.set_ylabel(ylabel, color=_HEX_TEXT_DIM, fontsize=9)
    ax.tick_params(colors=_HEX_TEXT_DIM, labelsize=p["date_fontsize"])
    for spine in ax.spines.values():
        spine.set_edgecolor(_HEX_NAV_MID)

    if ylim:
        ax.set_ylim(*ylim)

    ax.grid(axis="y", color=_HEX_GRID, linewidth=0.7, linestyle="--")

    # Etiquetas X: fechas (sub-muestreadas si son muchas)
    if clean_dates:
        step = p["date_step"]
        tick_positions = [i for i in range(n) if i % step == 0]
        if (n - 1) not in tick_positions:
            tick_positions.append(n - 1)
        tick_labels = [clean_dates[i] for i in tick_positions]
        ax.set_xticks(tick_positions)
        ax.set_xticklabels(
            tick_labels,
            rotation=p["rotation"],
            ha="right" if p["rotation"] > 0 else "center",
            fontsize=p["date_fontsize"],
        )

    # Overlay de lesiones (después de fijar ylim para que el ribbon use ylim correctos)
    if injury_ranges:
        _draw_injury_overlays(ax, injury_ranges)

    plt.tight_layout(pad=0.6)
    buf = io.BytesIO()
    fig.savefig(buf, format="png", dpi=130, facecolor=fig.get_facecolor(),
                bbox_inches="tight")
    plt.close(fig)
    buf.seek(0)
    return buf


def _create_readiness_timeline_chart(
    sessions_asc: list[Session],
    user: User,
    all_checkins_asc: list[PainCheckIn],
    incidents: list[Incident],
) -> io.BytesIO | None:
    """
    Genera el gráfico maestro del apartado Bienestar: readiness en el tiempo
    con bandas verticales por periodos de lesión.

    Calcula readiness retroactivo punto a punto (igual que /wellness/timeline).
    """
    import matplotlib.pyplot as plt

    if not sessions_asc:
        return None

    # ── Readiness retroactivo por sesión ──────────────────────────────────
    points: list[tuple[datetime.date, float]] = []
    for s in sessions_asc:
        if not s.created_at:
            continue
        prior_s = [x for x in sessions_asc if x.created_at <= s.created_at]
        prior_c = [c for c in all_checkins_asc if c.created_at <= s.created_at]
        prior_s_desc = sorted(prior_s, key=lambda x: x.created_at, reverse=True)
        prior_c_desc = sorted(prior_c, key=lambda x: x.created_at, reverse=True)
        rd = calculate_readiness(user, prior_s_desc, prior_c_desc)
        points.append((s.created_at.date(), float(rd["score"])))

    if not points:
        return None

    n = len(points)
    p = _adaptive_chart_params(n)

    # ── Auto-scale Y (similar al WellnessChart de la app) ────────────────
    values = [v for _, v in points]
    raw_min, raw_max = min(values), max(values)
    import math
    y_max = 100.0 if raw_max >= 90 else min(100.0, math.ceil((raw_max + 2) / 5) * 5)
    y_min = max(0.0, math.floor((raw_min - 2) / 5) * 5)
    if y_max - y_min < 15:
        y_min = max(0.0, y_max - 15)

    fig, ax = plt.subplots(figsize=(7, 3.2))
    fig.patch.set_facecolor(_HEX_NAV_DEEP)
    ax.set_facecolor(_HEX_NAV_MID)

    indices = list(range(n))

    # Bandas horizontales por zona (recortadas al rango visible)
    def _zone_band(top: float, bot: float, color: str):
        vt = min(top, y_max); vb = max(bot, y_min)
        if vt <= vb:
            return
        ax.axhspan(vb, vt, color=color, alpha=0.06, zorder=0)
    _zone_band(40,   0, _HEX_RED)
    _zone_band(70,  40, _HEX_YELLOW)
    _zone_band(100, 70, _HEX_GREEN)

    # Bandas de incidencia mapeadas a índices de sesión
    point_dates = [d for d, _ in points]
    today = datetime.date.today()
    injury_ranges_for_chart: list[tuple[int, int, bool]] = []
    for inc in incidents:
        if not inc.start_date:
            continue
        end = inc.end_date or today
        first_idx = None; last_idx = None
        for i, d in enumerate(point_dates):
            if inc.start_date <= d <= end:
                if first_idx is None:
                    first_idx = i
                last_idx = i
        if first_idx is not None and last_idx is not None:
            injury_ranges_for_chart.append((first_idx, last_idx, inc.end_date is None))

    # Relleno bajo la línea (área degradada con poly)
    ax.fill_between(indices, [y_min] * n, values,
                     color=_HEX_CYAN, alpha=0.18, zorder=1)

    marker_kwargs = {}
    if p["marker_size"] > 0:
        marker_kwargs = dict(
            marker="o", markersize=p["marker_size"],
            markerfacecolor=_HEX_NAV_MID, markeredgecolor=_HEX_CYAN,
            markeredgewidth=1.5,
        )
    ax.plot(indices, values, color=_HEX_CYAN,
            linewidth=p["line_width"] + 0.3, zorder=3, **marker_kwargs)

    ax.set_ylim(y_min, y_max)
    ax.set_title("Evolución del Readiness", color=_HEX_TEXT_LIGHT, fontsize=11, pad=8)
    ax.set_ylabel("Readiness (0-100)", color=_HEX_TEXT_DIM, fontsize=9)
    ax.tick_params(colors=_HEX_TEXT_DIM, labelsize=p["date_fontsize"])
    for spine in ax.spines.values():
        spine.set_edgecolor(_HEX_NAV_MID)
    ax.grid(axis="y", color=_HEX_GRID, linewidth=0.7, linestyle="--")

    # Etiquetas X (fechas dd/mm)
    date_labels = [d.strftime("%d/%m") for d in point_dates]
    step = p["date_step"]
    tick_positions = [i for i in range(n) if i % step == 0]
    if (n - 1) not in tick_positions:
        tick_positions.append(n - 1)
    ax.set_xticks(tick_positions)
    ax.set_xticklabels(
        [date_labels[i] for i in tick_positions],
        rotation=p["rotation"],
        ha="right" if p["rotation"] > 0 else "center",
        fontsize=p["date_fontsize"],
    )

    # Bandas de lesiones encima
    _draw_injury_overlays(ax, injury_ranges_for_chart)

    plt.tight_layout(pad=0.6)
    buf = io.BytesIO()
    fig.savefig(buf, format="png", dpi=130, facecolor=fig.get_facecolor(),
                bbox_inches="tight")
    plt.close(fig)
    buf.seek(0)
    return buf


@router.get("/analytics/pdf")
def export_pdf(
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    """Genera un informe PDF profesional del entrenamiento del usuario."""
    try:
        from reportlab.lib.pagesizes import A4
        from reportlab.lib import colors
        from reportlab.lib.units import cm
        from reportlab.platypus import (
            SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle,
            HRFlowable, PageBreak, Image as RLImage, KeepTogether,
        )
        from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
        from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT
    except ImportError:
        raise HTTPException(status_code=501,
            detail="Exportación PDF no disponible. Instala reportlab.")

    try:
        import matplotlib
        matplotlib.use("Agg")
        HAS_MPL = True
    except ImportError:
        HAS_MPL = False

    # ── Paleta ReportLab ─────────────────────────────────────────────────
    NAV_DEEP   = colors.HexColor(_HEX_NAV_DEEP)
    NAV_MID    = colors.HexColor(_HEX_NAV_MID)
    CYAN       = colors.HexColor(_HEX_CYAN)
    ORANGE     = colors.HexColor(_HEX_ORANGE)
    YELLOW     = colors.HexColor(_HEX_YELLOW)
    GREEN      = colors.HexColor(_HEX_GREEN)
    RED        = colors.HexColor(_HEX_RED)
    PURPLE     = colors.HexColor(_HEX_PURPLE)
    TEXT_LIGHT = colors.HexColor(_HEX_TEXT_LIGHT)
    TEXT_DIM   = colors.HexColor(_HEX_TEXT_DIM)

    # ── Carga de datos ────────────────────────────────────────────────────
    sessions_asc = (
        db.query(Session)
        .filter(Session.user_id == current_user.id, Session.status == "completed")
        .order_by(Session.created_at.asc())
        .all()
    )

    session_data = []
    for s in sessions_asc:
        reps = db.query(SessionResult).filter(
            SessionResult.session_id == s.id
        ).order_by(SessionResult.rep_number).all()
        _valid = [r.overall_score for r in reps if r.overall_score is not None]
        avg_score = (sum(_valid) / len(_valid)) if _valid else None
        session_data.append({"session": s, "reps": reps, "avg_score": avg_score})

    # avg_technique como atributo dinámico (necesario para readiness)
    for sd in session_data:
        sd["session"].avg_technique = sd["avg_score"]

    incidents = db.query(Incident).filter(
        Incident.user_id == current_user.id
    ).order_by(Incident.start_date.desc()).all()

    checkins_desc = db.query(PainCheckIn).join(Session).filter(
        Session.user_id == current_user.id
    ).order_by(PainCheckIn.created_at.desc()).all()
    checkins_asc = list(reversed(checkins_desc))

    sessions_desc = list(reversed(sessions_asc))
    breakdown = calculate_readiness(current_user, sessions_desc, checkins_desc)

    # Recomendaciones (basadas en últimos resultados)
    latest_results = []
    if sessions_desc:
        latest_results = db.query(SessionResult).filter(
            SessionResult.session_id == sessions_desc[0].id
        ).all()
    recommendations = evaluate_recommendations(
        current_user, sessions_desc, checkins_desc, breakdown, latest_results
    )

    # PRs
    prs = _compute_personal_records(sessions_asc, session_data)

    # Agregados generales
    total = len(sessions_asc)
    avg_technique = db.query(sql_func.avg(SessionResult.overall_score)).join(Session).filter(
        Session.user_id == current_user.id).scalar()
    max_weight = db.query(sql_func.max(Session.weight_kg)).filter(
        Session.user_id == current_user.id).scalar()
    if sessions_desc[:5]:
        avg_borg = sum(s.borg_score or 5 for s in sessions_desc[:5]) / len(sessions_desc[:5])
    else:
        avg_borg = 0.0

    readiness_score = breakdown["score"]
    readiness_label_es = {"high": "Alta", "medium": "Media", "low": "Baja"}.get(
        breakdown["label"], "Media"
    )

    # ── Pre-cálculo de gráficos (ahora — antes del PDF) ─────────────────
    chart_images: list[tuple[str, io.BytesIO]] = []
    wellness_chart: io.BytesIO | None = None

    if HAS_MPL and sessions_asc:
        injury_ranges = _compute_session_injury_indices(sessions_asc, incidents)
        date_labels = [s.created_at.strftime("%d/%m") if s.created_at else "" for s in sessions_asc]

        # Gráfico Bienestar (readiness retroactivo)
        wellness_chart = _create_readiness_timeline_chart(
            sessions_asc, current_user, checkins_asc, incidents
        )

        # 3 gráficos de progresión
        progression_defs = [
            ("Puntuación de Técnica",
             [sd["avg_score"] for sd in session_data],
             "Puntuación (/10)", _HEX_CYAN, (0, 10)),
            ("Progresión de Carga",
             [s.weight_kg or None for s in sessions_asc],
             "Carga (kg)", _HEX_ORANGE, None),
            ("Esfuerzo Percibido (Borg)",
             [s.borg_score or None for s in sessions_asc],
             "Borg (0-10)", _HEX_YELLOW, (0, 10)),
        ]
        for title, values, ylabel, color, ylim in progression_defs:
            buf = _create_progression_chart(
                title, date_labels, values, ylabel, color, ylim,
                injury_ranges=injury_ranges,
            )
            if buf is not None:
                chart_images.append((title, buf))

    # ── Construcción del PDF ─────────────────────────────────────────────
    pdf_buffer = io.BytesIO()
    doc = SimpleDocTemplate(
        pdf_buffer,
        pagesize=A4,
        leftMargin=2*cm, rightMargin=2*cm,
        topMargin=2.5*cm, bottomMargin=2*cm,
        title="MoveInsight – Informe de Entrenamiento",
        author=current_user.full_name,
    )

    styles = getSampleStyleSheet()

    def _style(name, **kw):
        return ParagraphStyle(name, parent=styles["Normal"], **kw)

    H1   = _style("H1", fontSize=28, textColor=TEXT_LIGHT, spaceAfter=4,
                  alignment=TA_CENTER, fontName="Helvetica-Bold")
    H2   = _style("H2", fontSize=16, textColor=CYAN, spaceBefore=14, spaceAfter=6,
                  fontName="Helvetica-Bold")
    H3   = _style("H3", fontSize=12, textColor=TEXT_LIGHT, spaceBefore=10, spaceAfter=4,
                  fontName="Helvetica-Bold")
    BODY = _style("BODY", fontSize=10, textColor=TEXT_LIGHT, spaceAfter=4,
                  leading=14, fontName="Helvetica")
    DIM  = _style("DIM", fontSize=9, textColor=TEXT_DIM, spaceAfter=2,
                  fontName="Helvetica")
    SMALL = _style("SMALL", fontSize=8, textColor=TEXT_DIM, fontName="Helvetica",
                   leading=10)

    tw = doc.width
    story = []

    # ── 0. Portada ──────────────────────────────────────────────────────
    story.append(Spacer(1, 3.8*cm))
    story.append(Paragraph(f"<b>Atleta:</b>  {current_user.full_name}", BODY))
    story.append(Paragraph(f"<b>Email:</b>   {current_user.email}", BODY))
    story.append(Paragraph(
        f"<b>Fecha de generación:</b>  {datetime.date.today().strftime('%d/%m/%Y')}", BODY))
    if current_user.level or current_user.objective:
        level_map = {"beginner": "Principiante", "intermediate": "Intermedio", "advanced": "Avanzado"}
        obj_map = {"technique": "Mejorar técnica", "progression": "Progresión",
                   "pain_monitoring": "Control del dolor", "health": "Salud general"}
        if current_user.level:
            story.append(Paragraph(
                f"<b>Nivel:</b>  {level_map.get(current_user.level, current_user.level)}", BODY))
        if current_user.objective:
            story.append(Paragraph(
                f"<b>Objetivo:</b>  {obj_map.get(current_user.objective, current_user.objective)}", BODY))
    story.append(Spacer(1, 0.8*cm))

    # ── 1. Resumen Global ───────────────────────────────────────────────
    story.append(Paragraph("Resumen Global", H2))
    story.append(HRFlowable(width="100%", thickness=1, color=NAV_MID, spaceAfter=10))

    summary_rows = [
        ["Métrica", "Valor"],
        ["Sesiones completadas", str(total)],
        ["Puntuación técnica media",
         f"{float(avg_technique):.1f}/10" if avg_technique else "—"],
        ["Carga máxima registrada",
         f"{float(max_weight):.0f} kg" if max_weight else "—"],
        ["Borg medio reciente (5 últimas)",
         f"{avg_borg:.1f}/10" if avg_borg > 0 else "—"],
        ["Incidencias registradas", str(len(incidents))],
        ["Readiness actual", f"{readiness_score}% — {readiness_label_es}"],
    ]
    summary_table = Table(summary_rows, colWidths=[tw*0.55, tw*0.45])
    summary_table.setStyle(TableStyle([
        ("BACKGROUND",     (0, 0), (-1,  0), NAV_MID),
        ("TEXTCOLOR",      (0, 0), (-1,  0), CYAN),
        ("FONTNAME",       (0, 0), (-1,  0), "Helvetica-Bold"),
        ("FONTSIZE",       (0, 0), (-1,  0), 10),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [NAV_DEEP, NAV_MID]),
        ("TEXTCOLOR",      (0, 1), (-1, -1), TEXT_LIGHT),
        ("FONTNAME",       (0, 1), (-1, -1), "Helvetica"),
        ("FONTSIZE",       (0, 1), (-1, -1), 10),
        ("GRID",           (0, 0), (-1, -1), 0.4, colors.HexColor(_HEX_GRID)),
        ("LEFTPADDING",    (0, 0), (-1, -1), 10),
        ("RIGHTPADDING",   (0, 0), (-1, -1), 10),
        ("TOPPADDING",     (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING",  (0, 0), (-1, -1), 7),
    ]))
    story.append(summary_table)

    # ── 2. Récords Personales ───────────────────────────────────────────
    if prs:
        story.append(Spacer(1, 0.4*cm))
        story.append(Paragraph("Récords Personales (PRs)", H2))
        story.append(HRFlowable(width="100%", thickness=1, color=NAV_MID, spaceAfter=10))

        def _fmt_pr_date(dt) -> str:
            try:
                return dt.strftime("%d/%m/%Y") if dt else "—"
            except Exception:
                return "—"

        pr_rows = [["Récord", "Valor", "Fecha"]]
        if "best_technique" in prs:
            pr_rows.append([
                "Mejor técnica (media de sesión)",
                f"{prs['best_technique']['value']:.1f}/10",
                _fmt_pr_date(prs["best_technique"]["date"]),
            ])
        if "max_weight" in prs:
            pr_rows.append([
                "Carga máxima",
                f"{prs['max_weight']['value']:.0f} kg",
                _fmt_pr_date(prs["max_weight"]["date"]),
            ])
        if "max_reps" in prs:
            pr_rows.append([
                "Más repeticiones en una sesión",
                f"{prs['max_reps']['value']} reps",
                _fmt_pr_date(prs["max_reps"]["date"]),
            ])
        for key, label in (("best_depth", "Mejor profundidad (rep)"),
                            ("best_knees", "Mejor alineación rodillas / valgo (rep)"),
                            ("best_torso", "Mejor control de torso (rep)"),
                            ("best_symmetry", "Mejor simetría L/R (rep)"),
                            ("best_rhythm", "Mejor ritmo (rep)")):
            if key in prs:
                pr_rows.append([
                    label,
                    f"{prs[key]['value']:.1f}/10",
                    _fmt_pr_date(prs[key]["date"]),
                ])

        pr_table = Table(pr_rows, colWidths=[tw*0.50, tw*0.25, tw*0.25])
        pr_table.setStyle(TableStyle([
            ("BACKGROUND",     (0, 0), (-1,  0), NAV_MID),
            ("TEXTCOLOR",      (0, 0), (-1,  0), GREEN),
            ("FONTNAME",       (0, 0), (-1,  0), "Helvetica-Bold"),
            ("FONTSIZE",       (0, 0), (-1,  0), 10),
            ("ROWBACKGROUNDS", (0, 1), (-1, -1), [NAV_DEEP, NAV_MID]),
            ("TEXTCOLOR",      (0, 1), (-1, -1), TEXT_LIGHT),
            ("FONTNAME",       (0, 1), (-1, -1), "Helvetica"),
            ("FONTSIZE",       (0, 1), (-1, -1), 9),
            ("GRID",           (0, 0), (-1, -1), 0.4, colors.HexColor(_HEX_GRID)),
            ("LEFTPADDING",    (0, 0), (-1, -1), 8),
            ("RIGHTPADDING",   (0, 0), (-1, -1), 8),
            ("TOPPADDING",     (0, 0), (-1, -1), 6),
            ("BOTTOMPADDING",  (0, 0), (-1, -1), 6),
            ("ALIGN",          (1, 1), (-1, -1), "CENTER"),
        ]))
        story.append(pr_table)

    # ── 3. Estado actual: Readiness ─────────────────────────────────────
    if total > 0:
        story.append(Spacer(1, 0.4*cm))
        story.append(Paragraph("Estado Actual — Readiness", H2))
        story.append(HRFlowable(width="100%", thickness=1, color=NAV_MID, spaceAfter=10))

        story.append(Paragraph(breakdown["summary"], BODY))
        story.append(Spacer(1, 4))
        if breakdown.get("main_warning"):
            warning_table = Table([[
                Paragraph("<b>⚠ AVISO</b>", _style("warn_t", fontSize=9, textColor=ORANGE,
                                                   fontName="Helvetica-Bold")),
                Paragraph(breakdown["main_warning"], _style("warn_b", fontSize=9,
                                                              textColor=TEXT_LIGHT, leading=12)),
            ]], colWidths=[2.2*cm, tw - 2.2*cm])
            warning_table.setStyle(TableStyle([
                ("BACKGROUND",  (0, 0), (-1, -1), NAV_MID),
                ("LINEAFTER",   (0, 0), (0, -1),  3, ORANGE),
                ("LEFTPADDING", (0, 0), (-1, -1), 8),
                ("RIGHTPADDING",(0, 0), (-1, -1), 8),
                ("TOPPADDING",  (0, 0), (-1, -1), 6),
                ("BOTTOMPADDING",(0, 0), (-1, -1), 6),
                ("VALIGN",      (0, 0), (-1, -1), "MIDDLE"),
            ]))
            story.append(warning_table)
            story.append(Spacer(1, 6))

        if breakdown.get("components"):
            comp_rows = [["Componente", "Peso", "Valor", "Explicación"]]
            for c in breakdown["components"]:
                comp_rows.append([
                    c["name"],
                    f"{int(c['weight'] * 100)}%",
                    f"{c['value']}/100",
                    Paragraph(c["explanation"],
                              _style("ce", fontSize=8, textColor=TEXT_LIGHT, leading=11)),
                ])
            comp_table = Table(comp_rows, colWidths=[
                tw*0.27, tw*0.10, tw*0.13, tw*0.50
            ])
            comp_table.setStyle(TableStyle([
                ("BACKGROUND",     (0, 0), (-1,  0), NAV_MID),
                ("TEXTCOLOR",      (0, 0), (-1,  0), CYAN),
                ("FONTNAME",       (0, 0), (-1,  0), "Helvetica-Bold"),
                ("FONTSIZE",       (0, 0), (-1,  0), 9),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [NAV_DEEP, NAV_MID]),
                ("TEXTCOLOR",      (0, 1), (-1, -1), TEXT_LIGHT),
                ("FONTNAME",       (0, 1), (-1, -1), "Helvetica"),
                ("FONTSIZE",       (0, 1), (-1, -1), 9),
                ("GRID",           (0, 0), (-1, -1), 0.4, colors.HexColor(_HEX_GRID)),
                ("ALIGN",          (1, 1), (2, -1), "CENTER"),
                ("VALIGN",         (0, 0), (-1, -1), "MIDDLE"),
                ("LEFTPADDING",    (0, 0), (-1, -1), 6),
                ("RIGHTPADDING",   (0, 0), (-1, -1), 6),
                ("TOPPADDING",     (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING",  (0, 0), (-1, -1), 5),
            ]))
            story.append(comp_table)

    # ── 4. Insights Personalizados ──────────────────────────────────────
    insights_list: list[tuple[str, str, str]] = []
    if total == 0:
        insights_list.append(("tip", "Empieza a entrenar",
            "Registra tu primera sesión para recibir análisis personalizados."))
    else:
        if avg_technique:
            at = float(avg_technique)
            if at >= 7.5:
                insights_list.append(("achievement", "Gran técnica",
                    f"Tu puntuación media de técnica es {at:.1f}/10. ¡Sigue así!"))
            elif at >= 5.0:
                insights_list.append(("tip", "Técnica mejorable",
                    f"Tu puntuación media es {at:.1f}/10. Enfócate en profundidad y control postural."))
            else:
                insights_list.append(("warning", "Revisa tu técnica",
                    f"Tu puntuación media es {at:.1f}/10. Reduce carga y trabaja la forma."))
        if avg_borg > 7:
            insights_list.append(("warning", "Fatiga elevada",
                f"Tu Borg medio reciente es {avg_borg:.1f}. Considera descansar."))
        elif 0 < avg_borg < 3:
            insights_list.append(("tip", "Puedes aumentar intensidad",
                f"Tu Borg medio es {avg_borg:.1f}. Podrías incrementar la carga."))
        if total >= 10:
            insights_list.append(("achievement", "Constancia",
                f"Llevas {total} sesiones completadas. La consistencia es clave."))
        elif total >= 3:
            insights_list.append(("tip", "Buen comienzo",
                f"Ya tienes {total} sesiones. Sigue registrando para ver tendencias."))
        if max_weight and max_weight > 0:
            insights_list.append(("tip", "Carga máxima",
                f"Tu carga máxima registrada es {float(max_weight):.0f} kg."))

    if insights_list:
        story.append(Spacer(1, 0.4*cm))
        story.append(Paragraph("Insights Personalizados", H2))
        story.append(HRFlowable(width="100%", thickness=1, color=NAV_MID, spaceAfter=10))
        type_colors  = {"achievement": GREEN, "tip": CYAN, "warning": ORANGE}
        type_labels  = {"achievement": "LOGRO", "tip": "CONSEJO", "warning": "AVISO"}
        for ins_type, ins_title, ins_msg in insights_list:
            c = type_colors.get(ins_type, CYAN)
            tag_text = type_labels.get(ins_type, ins_type[:6].upper())
            tag_style = _style(f"tag_{ins_type}", fontSize=8, textColor=c,
                                fontName="Helvetica-Bold", spaceAfter=1)
            ins_data = [
                [Paragraph(tag_text, tag_style),
                 Paragraph(f"<b>{ins_title}</b>", _style("ih", fontSize=10,
                     textColor=TEXT_LIGHT, fontName="Helvetica-Bold"))],
                ["",
                 Paragraph(ins_msg, _style("ib", fontSize=9, textColor=TEXT_LIGHT,
                     fontName="Helvetica", leading=13))],
            ]
            ins_t = Table(ins_data, colWidths=[2.2*cm, tw - 2.2*cm])
            ins_t.setStyle(TableStyle([
                ("BACKGROUND",  (0, 0), (-1, -1), NAV_MID),
                ("LEFTPADDING",  (0, 0), (-1, -1), 8),
                ("RIGHTPADDING", (0, 0), (-1, -1), 8),
                ("TOPPADDING",   (0, 0), (0, 0), 8),
                ("BOTTOMPADDING",(0, -1),(-1, -1), 8),
                ("LINEAFTER",    (0, 0), (0, -1), 3, c),
                ("VALIGN",       (0, 0), (-1, -1), "MIDDLE"),
            ]))
            story.append(ins_t)
            story.append(Spacer(1, 6))

    # ── 5. Recomendaciones Activas ──────────────────────────────────────
    if recommendations:
        story.append(Spacer(1, 0.4*cm))
        story.append(Paragraph("Recomendaciones Activas", H2))
        story.append(HRFlowable(width="100%", thickness=1, color=NAV_MID, spaceAfter=10))
        story.append(Paragraph(
            "Estas recomendaciones se han activado automáticamente según tu estado "
            "actual. <i>No constituyen consejo médico</i> — son orientativas para "
            "mejorar tu entrenamiento.",
            DIM
        ))
        story.append(Spacer(1, 6))

        cat_colors = {
            "technique": CYAN, "load": ORANGE,
            "recovery": YELLOW, "progression": GREEN,
        }
        for rec in recommendations:
            cat_color = cat_colors.get(rec["category"], CYAN)
            cat_label = _category_display(rec["category"]).upper()

            rec_data = [
                [Paragraph(f"<b>{cat_label}</b>",
                            _style("rc", fontSize=8, textColor=cat_color,
                                   fontName="Helvetica-Bold")),
                 Paragraph(f"<b>{rec['title']}</b>",
                            _style("rt", fontSize=10, textColor=TEXT_LIGHT,
                                   fontName="Helvetica-Bold"))],
                ["",
                 Paragraph(rec["body"],
                            _style("rb", fontSize=9, textColor=TEXT_LIGHT,
                                   fontName="Helvetica", leading=13))],
                ["",
                 Paragraph(f"<i>Activado por: {rec.get('trigger', '')}</i>",
                            _style("rg", fontSize=8, textColor=TEXT_DIM,
                                   fontName="Helvetica-Oblique"))],
            ]
            rec_t = Table(rec_data, colWidths=[2.5*cm, tw - 2.5*cm])
            rec_t.setStyle(TableStyle([
                ("BACKGROUND",  (0, 0), (-1, -1), NAV_MID),
                ("LEFTPADDING",  (0, 0), (-1, -1), 8),
                ("RIGHTPADDING", (0, 0), (-1, -1), 8),
                ("TOPPADDING",   (0, 0), (0, 0), 8),
                ("BOTTOMPADDING",(0, -1),(-1, -1), 8),
                ("LINEAFTER",    (0, 0), (0, -1), 3, cat_color),
                ("VALIGN",       (0, 0), (-1, -1), "MIDDLE"),
            ]))
            story.append(rec_t)
            story.append(Spacer(1, 6))

    # ── 6. Bienestar — Timeline + Incidencias ───────────────────────────
    if wellness_chart is not None or incidents:
        story.append(PageBreak())
        story.append(Paragraph("Bienestar — Evolución y Lesiones", H2))
        story.append(HRFlowable(width="100%", thickness=1, color=NAV_MID, spaceAfter=10))

        if wellness_chart is not None:
            story.append(Paragraph(
                "Evolución del semáforo de readiness con los periodos de lesión "
                "registrados superpuestos como bandas rojas.",
                DIM
            ))
            story.append(Spacer(1, 4))
            img = RLImage(wellness_chart, width=doc.width, height=doc.width * 0.42)
            story.append(img)
            story.append(Spacer(1, 0.4*cm))

        if incidents:
            story.append(Paragraph("Histórico de incidencias", H3))
            inc_rows = [["Zona", "Tipo", "EVA", "Inicio", "Fin", "Estado"]]
            for inc in incidents:
                inc_rows.append([
                    _body_zone_display(inc.body_zone),
                    _incident_type_display(inc.incident_type),
                    f"{inc.severity}/10" if inc.severity is not None else "—",
                    inc.start_date.strftime("%d/%m/%Y") if inc.start_date else "—",
                    inc.end_date.strftime("%d/%m/%Y") if inc.end_date else "—",
                    "Activa" if inc.end_date is None else "Recuperada",
                ])
            inc_table = Table(inc_rows, colWidths=[
                tw*0.22, tw*0.18, tw*0.10, tw*0.16, tw*0.16, tw*0.18,
            ])
            inc_table.setStyle(TableStyle([
                ("BACKGROUND",     (0, 0), (-1,  0), NAV_MID),
                ("TEXTCOLOR",      (0, 0), (-1,  0), RED),
                ("FONTNAME",       (0, 0), (-1,  0), "Helvetica-Bold"),
                ("FONTSIZE",       (0, 0), (-1,  0), 9),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [NAV_DEEP, NAV_MID]),
                ("TEXTCOLOR",      (0, 1), (-1, -1), TEXT_LIGHT),
                ("FONTNAME",       (0, 1), (-1, -1), "Helvetica"),
                ("FONTSIZE",       (0, 1), (-1, -1), 8),
                ("GRID",           (0, 0), (-1, -1), 0.4, colors.HexColor(_HEX_GRID)),
                ("ALIGN",          (0, 1), (-1, -1), "CENTER"),
                ("LEFTPADDING",    (0, 0), (-1, -1), 4),
                ("RIGHTPADDING",   (0, 0), (-1, -1), 4),
                ("TOPPADDING",     (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING",  (0, 0), (-1, -1), 5),
            ]))
            story.append(inc_table)

            # Notas si existen
            inc_with_notes = [i for i in incidents if i.notes and i.notes.strip()]
            if inc_with_notes:
                story.append(Spacer(1, 0.3*cm))
                story.append(Paragraph("Notas de las incidencias", H3))
                for inc in inc_with_notes:
                    label = f"<b>{_body_zone_display(inc.body_zone)}</b> ({inc.start_date.strftime('%d/%m/%Y') if inc.start_date else '—'})"
                    story.append(Paragraph(label, BODY))
                    story.append(Paragraph(inc.notes.strip(), DIM))
                    story.append(Spacer(1, 4))

    # ── 7. Gráficos de Progresión ───────────────────────────────────────
    if chart_images:
        story.append(PageBreak())
        story.append(Paragraph("Gráficos de Progresión", H2))
        story.append(HRFlowable(width="100%", thickness=1, color=NAV_MID, spaceAfter=10))
        story.append(Paragraph(
            "Cada gráfico muestra todas tus sesiones cronológicamente. Las bandas "
            "rojas indican periodos en los que registraste una lesión activa.",
            DIM
        ))
        story.append(Spacer(1, 6))
        for chart_title, chart_buf in chart_images:
            story.append(Paragraph(chart_title, H3))
            img = RLImage(chart_buf, width=doc.width, height=doc.width * 0.42)
            story.append(img)
            story.append(Spacer(1, 0.4*cm))

    # ── 8. Detalle de Sesiones ──────────────────────────────────────────
    if session_data:
        story.append(PageBreak())
        story.append(Paragraph("Detalle de Sesiones", H2))
        story.append(HRFlowable(width="100%", thickness=1, color=NAV_MID, spaceAfter=10))

        for idx, sd in enumerate(session_data, start=1):
            s    = sd["session"]
            reps = sd["reps"]
            avg_s = sd["avg_score"]
            date_str = s.created_at.strftime("%d/%m/%Y") if s.created_at else "—"
            weight_str = f"{s.weight_kg:.0f} kg" if s.weight_kg else "Libre"
            avg_str = f"{avg_s:.1f}/10" if avg_s is not None else "—"

            hdr_data = [[
                Paragraph(f"Sesión {idx}  —  {date_str}",
                          _style("sh", fontSize=11, textColor=CYAN, fontName="Helvetica-Bold")),
                Paragraph(
                    f"Carga: {weight_str}   Borg: {s.borg_score or '—'}/10   "
                    f"Técnica: {avg_str}   Reps: {len(reps)}",
                    _style("sm", fontSize=9, textColor=TEXT_DIM,
                           fontName="Helvetica", alignment=TA_RIGHT)),
            ]]
            hdr_t = Table(hdr_data, colWidths=[tw*0.5, tw*0.5])
            hdr_t.setStyle(TableStyle([
                ("BACKGROUND",   (0, 0), (-1, -1), NAV_MID),
                ("LEFTPADDING",  (0, 0), (-1, -1), 10),
                ("RIGHTPADDING", (0, 0), (-1, -1), 10),
                ("TOPPADDING",   (0, 0), (-1, -1), 8),
                ("BOTTOMPADDING",(0, 0), (-1, -1), 8),
                ("LINEABOVE",    (0, 0), (-1, 0), 2, CYAN),
                ("VALIGN",       (0, 0), (-1, -1), "MIDDLE"),
            ]))
            story.append(hdr_t)

            if reps:
                # "N/D" para KPIs no medibles en el perfil de la sesión (valgo/simetría en lateral)
                def _kpi(v):
                    return f"{v:.1f}" if v is not None else "N/D"

                rep_header = ["Rep", "Profundidad", "Torso", "Valgo",
                              "Simetría", "Ritmo", "Global"]
                rep_rows = [rep_header]
                for r in reps:
                    rep_rows.append([
                        str(r.rep_number),
                        _kpi(r.depth_score),
                        _kpi(r.torso_score),
                        _kpi(r.knees_score),
                        _kpi(r.symmetry_score),
                        _kpi(r.rhythm_score),
                        _kpi(r.overall_score),
                    ])
                col_w = tw / len(rep_header)
                rep_t = Table(rep_rows, colWidths=[col_w] * len(rep_header))
                rep_t.setStyle(TableStyle([
                    ("BACKGROUND",     (0, 0), (-1,  0), NAV_DEEP),
                    ("TEXTCOLOR",      (0, 0), (-1,  0), TEXT_DIM),
                    ("FONTNAME",       (0, 0), (-1,  0), "Helvetica-Bold"),
                    ("FONTSIZE",       (0, 0), (-1, -1), 8),
                    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [NAV_DEEP, NAV_MID]),
                    ("TEXTCOLOR",      (0, 1), (-1, -1), TEXT_LIGHT),
                    ("FONTNAME",       (0, 1), (-1, -1), "Helvetica"),
                    ("GRID",           (0, 0), (-1, -1), 0.3, colors.HexColor(_HEX_NAV_MID)),
                    ("ALIGN",          (0, 0), (-1, -1), "CENTER"),
                    ("TOPPADDING",     (0, 0), (-1, -1), 5),
                    ("BOTTOMPADDING",  (0, 0), (-1, -1), 5),
                ]))
                story.append(rep_t)
            else:
                story.append(Paragraph("Sin datos de repeticiones.", DIM))

            # Notas del usuario
            if s.user_notes and s.user_notes.strip():
                story.append(Spacer(1, 4))
                story.append(Paragraph(f"<i>Notas:</i> {s.user_notes.strip()}", SMALL))

            # Check-ins de dolor de esta sesión
            sess_checkins = [c for c in checkins_desc if c.session_id == s.id]
            if sess_checkins:
                story.append(Spacer(1, 4))
                checkin_lines = []
                for c in sorted(sess_checkins, key=lambda x: x.hours_after):
                    ev_str = f"EVA {c.eva_score}/10 a las {c.hours_after}h"
                    # body_zones es Text con códigos separados por comas
                    if c.body_zones:
                        zone_codes = [z.strip() for z in c.body_zones.split(",") if z.strip()]
                        if zone_codes:
                            zones_text = ", ".join(_body_zone_display(z) for z in zone_codes)
                            ev_str += f" — {zones_text}"
                    if c.notes and c.notes.strip():
                        ev_str += f" — {c.notes.strip()}"
                    checkin_lines.append(ev_str)
                story.append(Paragraph(
                    "<i>Dolor post-entreno:</i> " + " · ".join(checkin_lines), SMALL))

            story.append(Spacer(1, 0.4*cm))

    # ── 9. Pie / fondo en cada página ───────────────────────────────────
    def on_page(canvas_obj, doc_obj):
        canvas_obj.saveState()
        canvas_obj.setFillColor(NAV_DEEP)
        canvas_obj.rect(0, 0, A4[0], A4[1], fill=1, stroke=0)

        # Portada — solo en la página 1
        if canvas_obj.getPageNumber() == 1:
            mid_x = A4[0] / 2.0
            canvas_obj.setFont("Helvetica-Bold", 32)
            canvas_obj.setFillColor(TEXT_LIGHT)
            canvas_obj.drawCentredString(mid_x, A4[1] - 2.6*cm, "MoveInsight")
            canvas_obj.setFont("Helvetica", 15)
            canvas_obj.setFillColor(CYAN)
            canvas_obj.drawCentredString(mid_x, A4[1] - 3.5*cm, "Informe de Entrenamiento")
            canvas_obj.setStrokeColor(CYAN)
            canvas_obj.setLineWidth(2)
            canvas_obj.line(2*cm, A4[1] - 4.1*cm, A4[0] - 2*cm, A4[1] - 4.1*cm)

        # Pie con número de página (excepto portada)
        page_num = canvas_obj.getPageNumber()
        if page_num > 1:
            canvas_obj.setFont("Helvetica", 8)
            canvas_obj.setFillColor(TEXT_DIM)
            canvas_obj.drawCentredString(A4[0] / 2.0, 1.0*cm,
                f"MoveInsight  ·  {current_user.full_name}  ·  Página {page_num}")
        canvas_obj.restoreState()

    doc.build(story, onFirstPage=on_page, onLaterPages=on_page)
    pdf_buffer.seek(0)

    return StreamingResponse(
        pdf_buffer,
        media_type="application/pdf",
        headers={"Content-Disposition": "attachment; filename=moveinsight_report.pdf"},
    )
