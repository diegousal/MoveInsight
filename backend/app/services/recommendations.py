"""
Motor de recomendaciones para MoveInsight (F6).

Catálogo estático de recomendaciones + motor de triggers que evalúa
el estado actual del usuario para activar las pertinentes.

NO es diagnóstico — es orientativo para mejorar el entrenamiento.
"""

from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Optional

from app.services.readiness import (
    DEFAULT_BODY_WEIGHT_KG,
    _acwr_score,
    _days_ago,
)


# ── Catálogo de recomendaciones ───────────────────────────────────────────────

@dataclass
class RecommendationDef:
    id           : str
    category     : str    # "technique" | "load" | "recovery" | "progression"
    title        : str
    body         : str
    priority     : int    # 1 = más alta
    video_url    : Optional[str] = None
    trigger_desc : str = ""


CATALOG: list[RecommendationDef] = [

    # ── Técnica ──────────────────────────────────────────────────────────────
    RecommendationDef(
        id           = "tech_depth",
        category     = "technique",
        title        = "Trabaja la profundidad de la sentadilla",
        body         = (
            "Tu puntuación de profundidad es inferior a 5/10. "
            "Practica sentadillas con pausa en el punto más bajo "
            "y trabaja la movilidad de cadera y tobillo con estiramientos diarios."
        ),
        priority     = 1,
        video_url    = "https://www.youtube.com/watch?v=ultWZbUMPL8",
        trigger_desc = "Puntuación de profundidad < 5/10",
    ),
    RecommendationDef(
        id           = "tech_knees",
        category     = "technique",
        title        = "Alineación de rodillas en la sentadilla",
        body         = (
            "Tus rodillas tienden a caer hacia dentro (valgo dinámico). "
            "Activa glúteos con ejercicios de abducción antes de cada sesión "
            "y trabaja el fortalecimiento de glúteo medio."
        ),
        priority     = 2,
        video_url    = "https://www.youtube.com/watch?v=MeIiIdhvXT4",
        trigger_desc = "Puntuación de rodillas < 5/10",
    ),
    RecommendationDef(
        id           = "tech_torso",
        category     = "technique",
        title        = "Mantén el torso erguido",
        body         = (
            "Tu puntuación de torso es inferior a 5/10, indicando inclinación excesiva. "
            "Trabaja los erectores de columna con buenos-días con barra ligera "
            "y practica sentadillas con palo en overhead."
        ),
        priority     = 3,
        video_url    = "https://www.youtube.com/watch?v=HKbOECHcgFs",
        trigger_desc = "Puntuación de torso < 5/10",
    ),

    # ── Carga ────────────────────────────────────────────────────────────────
    RecommendationDef(
        id           = "load_reduce",
        category     = "load",
        title        = "Reduce la carga esta semana",
        body         = (
            "Tu ratio de carga aguda/crónica supera 1.5 — zona de riesgo según la evidencia "
            "(Gabbett 2016). Realiza sesiones ligeras o de movilidad "
            "y recupera antes de volver a aumentar la intensidad."
        ),
        priority     = 1,
        video_url    = "https://www.youtube.com/watch?v=m0n2AdKZMgA",
        trigger_desc = "ACWR > 1.5 (carga aguda muy superior a la media crónica)",
    ),

    # ── Recuperación ─────────────────────────────────────────────────────────
    RecommendationDef(
        id           = "recovery_pain",
        category     = "recovery",
        title        = "Gestiona el dolor antes de entrenar",
        body         = (
            "Tu EVA reciente es elevado (> 6/10). "
            "Prioriza técnicas de recuperación activa: hielo, compresión, movilidad suave. "
            "Si el dolor persiste más de 48 h, consulta a un profesional sanitario."
        ),
        priority     = 1,
        video_url    = "https://www.youtube.com/watch?v=NyNjq6l9GJE",
        trigger_desc = "EVA reciente > 6/10",
    ),
    RecommendationDef(
        id           = "recovery_inactivity",
        category     = "recovery",
        title        = "Retoma el entrenamiento gradualmente",
        body         = (
            "Llevas más de 10 días sin registrar sesiones. "
            "Vuelve con cargas un 20-30 % menores que tu última sesión "
            "y prioriza la técnica sobre el peso durante las primeras 2 semanas."
        ),
        priority     = 2,
        video_url    = "https://www.youtube.com/watch?v=Dy28eq2BBdM",
        trigger_desc = "Sin actividad registrada durante más de 10 días",
    ),
    RecommendationDef(
        id           = "recovery_fatigue",
        category     = "recovery",
        title        = "Semana de descarga recomendada",
        body         = (
            "Tu readiness es bajo y acumulas varias sesiones intensas. "
            "Una semana de descarga (≈50 % de la carga habitual) "
            "favorece la supercompensación y la mejora a largo plazo."
        ),
        priority     = 2,
        video_url    = "https://www.youtube.com/watch?v=PXzuwl0QLKM",
        trigger_desc = "Readiness < 40 (fatiga acumulada elevada)",
    ),

    # ── Progresión ───────────────────────────────────────────────────────────
    RecommendationDef(
        id           = "progression_increase",
        category     = "progression",
        title        = "Es momento de progresar",
        body         = (
            "Tu técnica es sólida (≥ 7/10) y tu esfuerzo percibido es bajo (Borg < 3). "
            "Puedes aumentar la carga o el volumen de forma segura esta semana: "
            "incrementos de un 5-10 % son suficientes para progresar sin elevar el riesgo."
        ),
        priority     = 2,
        video_url    = "https://www.youtube.com/watch?v=FpnbCLn93Pk",
        trigger_desc = "Borg medio < 3/10 y técnica ≥ 7/10",
    ),
]

CATALOG_BY_ID: dict[str, RecommendationDef] = {r.id: r for r in CATALOG}


# ── Motor de triggers ─────────────────────────────────────────────────────────

def evaluate_recommendations(
    user,
    sessions,           # list[Session] DESC por created_at, con avg_technique opcional
    checkins,           # list[PainCheckIn]
    readiness_breakdown : dict[str, Any],
    latest_results,     # list[SessionResult] de la sesión más reciente
) -> list[dict[str, Any]]:
    """
    Evalúa los triggers y devuelve las recomendaciones activas ordenadas por prioridad.
    Cada elemento del resultado es un dict compatible con el schema Recommendation.
    """
    now         = datetime.now(timezone.utc)
    body_weight = user.body_weight_kg or DEFAULT_BODY_WEIGHT_KG
    active_ids: set[str] = set()

    # ── Técnica: profundidad, rodillas, torso ────────────────────────────────
    if latest_results:
        depths = [r.depth_score  for r in latest_results if r.depth_score  is not None]
        knees  = [r.knees_score  for r in latest_results if r.knees_score  is not None]
        torsos = [r.torso_score  for r in latest_results if r.torso_score  is not None]

        if depths and (sum(depths) / len(depths)) < 5:
            active_ids.add("tech_depth")
        if knees  and (sum(knees)  / len(knees))  < 5:
            active_ids.add("tech_knees")
        if torsos and (sum(torsos) / len(torsos)) < 5:
            active_ids.add("tech_torso")

    # ── Carga: ACWR > 1.5 ────────────────────────────────────────────────────
    if sessions:
        _, acwr_ratio, _ = _acwr_score(sessions, now, body_weight)
        if acwr_ratio is not None and acwr_ratio > 1.5:
            active_ids.add("load_reduce")

    # ── Dolor reciente: EVA > 6 en los últimos 7 días ────────────────────────
    recent_checkins = [c for c in checkins if _days_ago(c.created_at, now) <= 7]
    if recent_checkins:
        max_eva = max(c.eva_score for c in recent_checkins)
        if max_eva > 6:
            active_ids.add("recovery_pain")

    # ── Inactividad > 10 días ────────────────────────────────────────────────
    if sessions:
        days_since_last = _days_ago(sessions[0].created_at, now)
        if days_since_last > 10:
            active_ids.add("recovery_inactivity")

    # ── Readiness bajo < 40 ──────────────────────────────────────────────────
    if readiness_breakdown.get("score", 100) < 40:
        active_ids.add("recovery_fatigue")

    # ── Progresión: Borg bajo + técnica buena ────────────────────────────────
    if sessions:
        recent_5 = sessions[:5]
        avg_borg = sum(s.borg_score or 5 for s in recent_5) / len(recent_5)
        tech_vals = [
            s.avg_technique for s in recent_5
            if getattr(s, "avg_technique", None) is not None
        ]
        avg_tech = sum(tech_vals) / len(tech_vals) if tech_vals else None
        if avg_borg < 3 and avg_tech is not None and avg_tech >= 7:
            active_ids.add("progression_increase")

    # ── Construir respuesta ───────────────────────────────────────────────────
    result: list[dict[str, Any]] = []
    for rec_id in active_ids:
        r = CATALOG_BY_ID.get(rec_id)
        if r:
            result.append({
                "id"       : r.id,
                "category" : r.category,
                "title"    : r.title,
                "body"     : r.body,
                "priority" : r.priority,
                "video_url": r.video_url,
                "trigger"  : r.trigger_desc,
            })

    result.sort(key=lambda x: x["priority"])
    return result
