"""
Endpoints del apartado Bienestar:
  - GET  /wellness/timeline                       → puntos para gráfico maestro
  - GET  /wellness/incidents/{id}/factors         → factores observados pre-incidencia
"""
from datetime import datetime, timedelta, timezone, date
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session as DBSession

from app.auth import get_current_user
from app.database import get_db
from app.models import Incident, PainCheckIn, Session, SessionResult, User
from app.schemas import (
    IncidentFactors, IncidentResponse, ObservedFactor,
    TimelinePoint, ValidationDetail, ValidationResult, WellnessTimeline,
)
from app.services.readiness import (
    DEFAULT_BODY_WEIGHT_KG, _session_load, calculate_readiness,
)

router = APIRouter()


# ── Helpers ──────────────────────────────────────────────────────────────────

def _attach_avg_technique(db: DBSession, sessions: list[Session]) -> None:
    """Añade `avg_technique` a cada sesión consultando SessionResult."""
    for s in sessions:
        results = db.query(SessionResult).filter(SessionResult.session_id == s.id).all()
        s.avg_technique = (sum(r.overall_score for r in results) / len(results)) if results else None


def _to_incident_dict(i: Incident) -> dict:
    return {
        "id"            : i.id,
        "body_zone"     : i.body_zone,
        "severity"      : i.severity,
        "incident_type" : i.incident_type,
        "start_date"    : i.start_date,
        "end_date"      : i.end_date,
        "notes"         : i.notes or "",
        "created_at"    : i.created_at,
        "is_active"     : i.end_date is None,
    }


# ── Timeline maestro (F4) ────────────────────────────────────────────────────

@router.get("/wellness/timeline", response_model=WellnessTimeline)
def get_timeline(
    days: int = 90,
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    """
    Devuelve el timeline del usuario para el gráfico maestro.

    `days` indica el número de días hacia atrás (default 90).
    Cada punto incluye readiness calculado *al momento* de esa sesión
    (solo con datos previos), para mostrar cómo evolucionó.
    """
    cutoff = datetime.now(timezone.utc) - timedelta(days=days)

    sessions = db.query(Session).filter(
        Session.user_id == current_user.id,
        Session.status  == "completed",
    ).order_by(Session.created_at.asc()).all()

    _attach_avg_technique(db, sessions)

    body_weight = current_user.body_weight_kg or DEFAULT_BODY_WEIGHT_KG

    # Check-ins del usuario
    all_checkins = db.query(PainCheckIn).join(Session).filter(
        Session.user_id == current_user.id
    ).order_by(PainCheckIn.created_at.asc()).all()

    points: list[TimelinePoint] = []
    for s in sessions:
        if s.created_at and s.created_at.replace(tzinfo=timezone.utc) < cutoff:
            continue
        # Subset hasta el momento actual
        prior_sessions = [x for x in sessions if x.created_at <= s.created_at]
        prior_checkins = [c for c in all_checkins if c.created_at <= s.created_at]
        # Readiness retroactivo (el orden esperado por calculate_readiness es DESC)
        prior_sessions_desc = sorted(prior_sessions, key=lambda x: x.created_at, reverse=True)
        prior_checkins_desc = sorted(prior_checkins, key=lambda x: x.created_at, reverse=True)
        rd = calculate_readiness(current_user, prior_sessions_desc, prior_checkins_desc)

        points.append(TimelinePoint(
            date       = s.created_at.date(),
            session_id = s.id,
            readiness  = rd["score"],
            load       = round(_session_load(s, body_weight), 2),
            technique  = round(s.avg_technique, 2) if s.avg_technique is not None else None,
            borg       = s.borg_score,
            weight_kg  = s.weight_kg,
        ))

    # Incidencias del periodo (incluimos también las que empezaron antes pero siguen activas)
    cutoff_date = cutoff.date()
    incidents_rows = db.query(Incident).filter(
        Incident.user_id == current_user.id,
    ).order_by(Incident.start_date.asc()).all()
    incidents = [
        _to_incident_dict(i) for i in incidents_rows
        if (i.end_date is None) or (i.end_date >= cutoff_date) or (i.start_date >= cutoff_date)
    ]

    return WellnessTimeline(points=points, incidents=incidents)


# ── Factores observados (F5) ─────────────────────────────────────────────────

def _classify_severity(delta_pct: float, threshold_high: float = 25.0,
                        threshold_mod: float = 10.0) -> str:
    abs_pct = abs(delta_pct)
    if abs_pct >= threshold_high: return "high"
    if abs_pct >= threshold_mod:  return "moderate"
    return "low"


@router.get("/wellness/incidents/{incident_id}/factors", response_model=IncidentFactors)
def get_incident_factors(
    incident_id: int,
    window_days: int = 14,
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    """
    Devuelve los factores observados en la ventana pre-incidencia.

    NO afirma causalidad — solo muestra qué métricas se desviaron de la baseline
    del usuario en los `window_days` previos al inicio de la incidencia.
    """
    incident = db.query(Incident).filter(
        Incident.id == incident_id,
        Incident.user_id == current_user.id,
    ).first()
    if not incident:
        raise HTTPException(404, "Incidencia no encontrada.")

    body_weight = current_user.body_weight_kg or DEFAULT_BODY_WEIGHT_KG

    # ── Carga sesiones ────────────────────────────────────────────────────
    all_sessions = db.query(Session).filter(
        Session.user_id == current_user.id,
        Session.status  == "completed",
    ).order_by(Session.created_at.asc()).all()
    _attach_avg_technique(db, all_sessions)

    # ── Particionar: pre-incidencia vs baseline (resto) ──────────────────
    incident_dt = datetime.combine(incident.start_date, datetime.min.time(), tzinfo=timezone.utc)
    pre_cutoff  = incident_dt - timedelta(days=window_days)

    pre_sessions       : list[Session] = []
    baseline_sessions  : list[Session] = []
    for s in all_sessions:
        s_dt = s.created_at.replace(tzinfo=timezone.utc) if s.created_at and s.created_at.tzinfo is None else s.created_at
        if s_dt is None: continue
        if pre_cutoff <= s_dt < incident_dt:
            pre_sessions.append(s)
        elif s_dt < pre_cutoff or s_dt > incident_dt + timedelta(days=window_days):
            # Excluimos la ventana pre-incidencia y un margen post-incidencia para limpiar baseline
            baseline_sessions.append(s)

    factors: list[ObservedFactor] = []

    # ── Helper para agregar un factor ────────────────────────────────────
    def _add_factor(name: str, value: float, baseline: float, description_template: str):
        if baseline == 0 and value == 0:
            return
        delta = value - baseline
        pct = (delta / baseline * 100) if baseline > 0 else (100.0 if value > 0 else 0.0)
        sev = _classify_severity(pct)
        descr = description_template.format(
            value    = f"{value:.1f}",
            baseline = f"{baseline:.1f}",
            delta    = f"{abs(pct):.0f}",
            sign     = "más" if pct > 0 else "menos",
        )
        factors.append(ObservedFactor(
            name        = name,
            value       = round(value, 2),
            baseline    = round(baseline, 2),
            delta_pct   = round(pct, 1),
            severity    = sev,
            description = descr,
        ))

    # ── Carga media ──────────────────────────────────────────────────────
    if pre_sessions and baseline_sessions:
        pre_load = sum(_session_load(s, body_weight) for s in pre_sessions) / len(pre_sessions)
        bl_load  = sum(_session_load(s, body_weight) for s in baseline_sessions) / len(baseline_sessions)
        _add_factor(
            "Carga media por sesión",
            pre_load, bl_load,
            "{value} unidades vs baseline {baseline} ({delta}% {sign})."
        )

    # ── Frecuencia de entrenamiento ──────────────────────────────────────
    if window_days > 0:
        pre_freq = len(pre_sessions) * (7.0 / window_days)
        # baseline freq = sesiones / días totales × 7
        if baseline_sessions:
            first_dt = baseline_sessions[0].created_at.replace(tzinfo=timezone.utc)
            last_dt  = baseline_sessions[-1].created_at.replace(tzinfo=timezone.utc)
            span_days = max(1, (last_dt - first_dt).days)
            bl_freq = len(baseline_sessions) * (7.0 / span_days)
            if pre_freq > 0 or bl_freq > 0:
                _add_factor(
                    "Frecuencia (sesiones/semana)",
                    pre_freq, bl_freq,
                    "{value} ses/sem vs baseline {baseline} ({delta}% {sign})."
                )

    # ── Técnica media ────────────────────────────────────────────────────
    pre_tech = [s.avg_technique for s in pre_sessions if s.avg_technique is not None]
    bl_tech  = [s.avg_technique for s in baseline_sessions if s.avg_technique is not None]
    if pre_tech and bl_tech:
        pre_avg = sum(pre_tech) / len(pre_tech)
        bl_avg  = sum(bl_tech) / len(bl_tech)
        _add_factor(
            "Puntuación técnica",
            pre_avg, bl_avg,
            "{value}/10 vs baseline {baseline}/10 ({delta}% {sign})."
        )

    # ── Borg medio ───────────────────────────────────────────────────────
    pre_borg = [s.borg_score for s in pre_sessions if s.borg_score is not None]
    bl_borg  = [s.borg_score for s in baseline_sessions if s.borg_score is not None]
    if pre_borg and bl_borg:
        pre_avg = sum(pre_borg) / len(pre_borg)
        bl_avg  = sum(bl_borg) / len(bl_borg)
        _add_factor(
            "Esfuerzo (Borg)",
            pre_avg, bl_avg,
            "{value}/10 vs baseline {baseline}/10 ({delta}% {sign})."
        )

    # ── EVA medio en el periodo pre-incidencia ───────────────────────────
    pre_session_ids = [s.id for s in pre_sessions]
    bl_session_ids  = [s.id for s in baseline_sessions]
    if pre_session_ids and bl_session_ids:
        pre_eva_rows = db.query(PainCheckIn).filter(
            PainCheckIn.session_id.in_(pre_session_ids)
        ).all()
        bl_eva_rows  = db.query(PainCheckIn).filter(
            PainCheckIn.session_id.in_(bl_session_ids)
        ).all()
        if pre_eva_rows and bl_eva_rows:
            pre_avg = sum(c.eva_score for c in pre_eva_rows) / len(pre_eva_rows)
            bl_avg  = sum(c.eva_score for c in bl_eva_rows) / len(bl_eva_rows)
            _add_factor(
                "Dolor post-entreno (EVA)",
                pre_avg, bl_avg,
                "{value}/10 vs baseline {baseline}/10 ({delta}% {sign})."
            )

    # ── Puntos del periodo pre-incidencia (para el gráfico) ──────────────
    timeline_points: list[TimelinePoint] = []
    for s in pre_sessions:
        timeline_points.append(TimelinePoint(
            date       = s.created_at.date(),
            session_id = s.id,
            readiness  = 0,   # no calculamos retroactivo aquí (lo da el timeline general si interesa)
            load       = round(_session_load(s, body_weight), 2),
            technique  = round(s.avg_technique, 2) if s.avg_technique is not None else None,
            borg       = s.borg_score,
            weight_kg  = s.weight_kg,
        ))

    note = (
        "Estos son los factores observados en los días previos al inicio de tu incidencia. "
        "NO implica relación causal — solo describen qué métricas se desviaron de tu media habitual."
    )

    return IncidentFactors(
        incident_id     = incident.id,
        window_days     = window_days,
        sample_size     = len(pre_sessions),
        factors         = factors,
        note            = note,
        timeline_points = timeline_points,
    )


# ── Validación retrospectiva (F8) ─────────────────────────────────────────────

@router.get("/wellness/validation", response_model=ValidationResult)
def get_validation(
    current_user: User      = Depends(get_current_user),
    db: DBSession           = Depends(get_db),
):
    """
    Validación retrospectiva: comprueba si el readiness descendió ≥ 10 puntos
    en los 7 días previos al inicio de cada incidencia registrada.

    Útil para cuantificar la capacidad predictiva del semáforo (TFG).
    """
    incidents = db.query(Incident).filter(
        Incident.user_id == current_user.id,
    ).order_by(Incident.start_date.asc()).all()

    if not incidents:
        return ValidationResult(
            incidents_analyzed  = 0,
            early_warning_count = 0,
            early_warning_rate  = 0.0,
            details             = [],
            summary             = "No hay incidencias registradas para analizar.",
        )

    all_sessions = db.query(Session).filter(
        Session.user_id == current_user.id,
        Session.status  == "completed",
    ).order_by(Session.created_at.asc()).all()
    _attach_avg_technique(db, all_sessions)

    all_checkins = db.query(PainCheckIn).join(Session).filter(
        Session.user_id == current_user.id,
    ).order_by(PainCheckIn.created_at.asc()).all()

    def _tz(dt: datetime) -> datetime:
        return dt.replace(tzinfo=timezone.utc) if dt and dt.tzinfo is None else dt

    def _calc_readiness_for_window(
        session_list: list[Session],
        checkin_list: list[PainCheckIn],
    ) -> int | None:
        if not session_list:
            return None
        sorted_s = sorted(session_list, key=lambda x: x.created_at, reverse=True)
        sorted_c = sorted(checkin_list, key=lambda x: x.created_at, reverse=True)
        rd = calculate_readiness(current_user, sorted_s, sorted_c)
        return rd["score"]

    details: list[ValidationDetail] = []
    early_warning_count = 0

    for incident in incidents:
        incident_dt = datetime.combine(
            incident.start_date, datetime.min.time(), tzinfo=timezone.utc
        )

        # Ventana baseline: 14→7 días antes del inicio
        bl_start = incident_dt - timedelta(days=14)
        bl_end   = incident_dt - timedelta(days=7)

        # Ventana pre-incidencia: 7→0 días antes del inicio
        pre_start = incident_dt - timedelta(days=7)

        bl_sessions  = [s for s in all_sessions  if _tz(s.created_at) and bl_start  <= _tz(s.created_at) < bl_end]
        pre_sessions = [s for s in all_sessions  if _tz(s.created_at) and pre_start <= _tz(s.created_at) < incident_dt]
        bl_checkins  = [c for c in all_checkins  if _tz(c.created_at) and bl_start  <= _tz(c.created_at) < bl_end]
        pre_checkins = [c for c in all_checkins  if _tz(c.created_at) and pre_start <= _tz(c.created_at) < incident_dt]

        readiness_before = _calc_readiness_for_window(bl_sessions,  bl_checkins)
        readiness_at     = _calc_readiness_for_window(pre_sessions, pre_checkins)

        if readiness_before is not None and readiness_at is not None:
            delta = readiness_at - readiness_before
            if delta <= -10:
                trend = "declining"
                early_warning_count += 1
            elif delta >= 10:
                trend = "improving"
            else:
                trend = "stable"
        else:
            delta = None
            trend = "stable"

        details.append(ValidationDetail(
            incident_id      = incident.id,
            body_zone        = incident.body_zone,
            readiness_before = readiness_before,
            readiness_at     = readiness_at,
            delta            = delta,
            trend            = trend,
        ))

    total = len(incidents)
    rate  = early_warning_count / total if total > 0 else 0.0

    if total == 0:
        summary = "No hay incidencias registradas."
    elif early_warning_count == 0:
        summary = (
            f"De {total} incidencia(s) analizadas, el semáforo no mostró "
            "descensos previos claros (< 10 puntos)."
        )
    else:
        pct = int(rate * 100)
        summary = (
            f"En {early_warning_count} de {total} incidencia(s) ({pct}%) "
            f"el readiness descendió ≥ 10 puntos en los 7 días previos."
        )

    return ValidationResult(
        incidents_analyzed  = total,
        early_warning_count = early_warning_count,
        early_warning_rate  = round(rate, 2),
        details             = details,
        summary             = summary,
    )
