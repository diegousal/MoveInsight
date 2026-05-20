"""
Servicio de cálculo de readiness inteligente (semáforo).

Modelo:
  - 4 dimensiones con decaimiento temporal exponencial.
  - Cold-start escalado por madurez de los datos del usuario.
  - Justificación textual de cada componente.

Etapas según madurez del histórico:
  - cold_start  (< 7 días):    readiness = 100 (descansado por defecto).
  - early       (7-28 días):   Fatiga 50% / Dolor 30% / Técnica 20%.
  - established (≥ 28 días):   Fatiga 30% / ACWR 25% / Dolor 25% / Técnica 20%.

Todas las fechas se asumen en UTC.
"""

from datetime import datetime, timezone
from math import exp
from typing import Any

# ── Constantes del modelo ─────────────────────────────────────────────────────

HALF_LIFE_FATIGUE_DAYS = 3.0   # Half-life de la fatiga aguda
HALF_LIFE_PAIN_DAYS    = 4.0   # Half-life del dolor residual
ACUTE_WINDOW_DAYS      = 7
CHRONIC_WINDOW_DAYS    = 28
TECHNIQUE_LOOKBACK_N   = 5     # nº de sesiones para la media de técnica
DEFAULT_BODY_WEIGHT_KG = 75.0  # fallback si el usuario no introdujo peso


# ── Helpers ───────────────────────────────────────────────────────────────────

def _to_utc(dt: datetime) -> datetime:
    """Asegura datetime con tz UTC para comparar sin errores."""
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt


def _days_ago(dt: datetime, now: datetime) -> float:
    """Días entre `dt` y `now`, en formato decimal."""
    return (now - _to_utc(dt)).total_seconds() / 86400.0


def _session_load(session, body_weight: float) -> float:
    """
    Carga normalizada de una sesión (unidades arbitrarias).

    Heurística: peso × reps_estimadas × Borg / 1000.
    - Si la sentadilla es libre (sin peso), usa peso corporal.
    - Asumimos ~5 reps por sesión (valor razonable para sentadilla pesada).
    """
    weight = session.weight_kg if session.weight_kg else body_weight
    borg   = session.borg_score if session.borg_score else 5
    reps   = 5
    return (weight * reps * borg) / 1000.0


# ── Componentes ───────────────────────────────────────────────────────────────

def _fatigue_score(sessions, now: datetime, body_weight: float) -> tuple[int, str]:
    """
    Fatiga aguda (0-100). 100 = fresco, 0 = exhausto.
    Suma carga ponderada con decaimiento exponencial sobre los últimos 14 días.
    """
    if not sessions:
        return 100, "Sin actividad reciente — totalmente descansado."

    total = 0.0
    count_recent = 0
    for s in sessions:
        d = _days_ago(s.created_at, now)
        if d > 14:
            continue
        load  = _session_load(s, body_weight)
        decay = exp(-d / HALF_LIFE_FATIGUE_DAYS)
        total += load * decay
        if d <= 7:
            count_recent += 1

    # Mapeo total decay-weighted → score 0-100. Umbral 30 unidades = sobrecarga.
    score = max(0, min(100, 100 - (total / 30.0) * 100))

    if score >= 80:
        msg = "Buen estado de recuperación."
    elif score >= 60:
        msg = f"{count_recent} sesión(es) en últimos 7 días — fatiga ligera."
    elif score >= 40:
        msg = f"{count_recent} sesión(es) recientes — fatiga moderada acumulada."
    else:
        msg = "Carga elevada en los últimos días — fatiga alta."

    return int(round(score)), msg


def _acwr_score(sessions, now: datetime, body_weight: float) -> tuple[int | None, float | None, str | None]:
    """
    Acute:Chronic Workload Ratio (Gabbett 2016).
    Sweet spot 0.8-1.3 → score 100. Fuera del rango se penaliza.
    Devuelve (score, ratio, mensaje) o (None, None, None) si no hay carga crónica.
    """
    acute = 0.0
    chronic = 0.0
    for s in sessions:
        d = _days_ago(s.created_at, now)
        if d > CHRONIC_WINDOW_DAYS:
            continue
        load = _session_load(s, body_weight)
        if d <= ACUTE_WINDOW_DAYS:
            acute += load
        chronic += load

    chronic_avg = chronic / 4.0     # promedio semanal en ventana 28d
    if chronic_avg < 0.5:
        return None, None, None     # carga crónica insuficiente

    ratio = acute / chronic_avg

    if 0.8 <= ratio <= 1.3:
        score = 100.0
        msg   = f"Carga ajustada a tu rutina (ACWR {ratio:.2f})."
    elif ratio < 0.8:
        # Subentrenado — score 60-90 (no peligroso pero subóptimo)
        score = 60 + (ratio / 0.8) * 30
        pct   = int((1 - ratio) * 100)
        msg   = f"Carga reciente {pct}% por debajo de tu media — entrena con seguridad."
    elif ratio <= 1.5:
        # Cargado pero manejable — score 100 → 60
        score = 100 - ((ratio - 1.3) / 0.2) * 40
        pct   = int((ratio - 1) * 100)
        msg   = f"Carga {pct}% por encima de tu media — vigila la fatiga."
    else:
        # Sobrecarga — score 60 → 20
        score = max(20, 60 - (ratio - 1.5) * 40)
        pct   = int((ratio - 1) * 100)
        msg   = f"Carga {pct}% por encima — riesgo de sobrecarga, considera reducir."

    return int(round(score)), float(ratio), msg


def _pain_score(checkins, now: datetime) -> tuple[int, str]:
    """
    Dolor residual (0-100). 100 = sin dolor.
    Toma el max EVA de los últimos 14 días con decaimiento exponencial.
    """
    if not checkins:
        return 100, "Sin check-ins de dolor recientes."

    max_decayed = 0.0
    max_eva     = 0
    for c in checkins:
        d = _days_ago(c.created_at, now)
        if d > 14:
            continue
        decayed = c.eva_score * exp(-d / HALF_LIFE_PAIN_DAYS)
        if decayed > max_decayed:
            max_decayed = decayed
            max_eva     = c.eva_score

    score = max(0, 100 - (max_decayed * 10))

    if score >= 90:
        msg = "Sin dolor relevante."
    elif score >= 70:
        msg = f"Última molestia leve (EVA {max_eva}/10)."
    elif score >= 40:
        msg = f"Molestia moderada residual (EVA {max_eva}/10)."
    else:
        msg = f"Dolor elevado (EVA {max_eva}/10) — considera descansar."

    return int(round(score)), msg


def _technique_score(technique_values: list[float]) -> tuple[int | None, str | None]:
    """
    Media móvil de las últimas N puntuaciones de técnica → 0-100.
    """
    if not technique_values:
        return None, None

    recent = technique_values[:TECHNIQUE_LOOKBACK_N]
    avg = sum(recent) / len(recent)
    score = avg * 10

    if score >= 75:
        msg = f"Técnica sólida ({avg:.1f}/10)."
    elif score >= 50:
        msg = f"Técnica intermedia ({avg:.1f}/10) — hay margen de mejora."
    else:
        msg = f"Técnica baja ({avg:.1f}/10) — reduce carga y trabaja la forma."

    return int(round(score)), msg


# ── Cálculo principal ─────────────────────────────────────────────────────────

def calculate_readiness(user, sessions, checkins) -> dict[str, Any]:
    """
    Devuelve un breakdown completo del readiness.

    Args:
        user: instancia de User (lee body_weight_kg, level)
        sessions: list[Session] ordenadas DESC por created_at, con `avg_technique` opcional
        checkins: list[PainCheckIn] del usuario

    Returns:
        dict con: score (int), label (str), stage (str), components (list),
                  summary (str), main_warning (str|None).
    """
    now = datetime.now(timezone.utc)
    body_weight = user.body_weight_kg or DEFAULT_BODY_WEIGHT_KG

    # ── Caso sin datos ─────────────────────────────────────────────────
    if not sessions:
        return {
            "score": 100,
            "label": "high",
            "stage": "cold_start",
            "components": [],
            "summary": "Comienza a registrar entrenamientos para recibir tu análisis personalizado.",
            "main_warning": None,
        }

    oldest = sessions[-1]
    days_active = _days_ago(oldest.created_at, now)

    # ── Componentes universales ────────────────────────────────────────
    fatigue, fatigue_msg = _fatigue_score(sessions, now, body_weight)
    pain, pain_msg       = _pain_score(checkins, now)

    technique_values = [s.avg_technique for s in sessions if getattr(s, "avg_technique", None) is not None]
    tech, tech_msg = _technique_score(technique_values)
    if tech is None:
        tech, tech_msg = 70, "Aún no hay puntuaciones de técnica disponibles."

    # ── Etapa según madurez ───────────────────────────────────────────
    if days_active < 7 or len(sessions) < 3:
        # Cold start: solo fatiga aguda, sin pesos complejos
        stage = "cold_start"
        score = fatigue
        components = [
            {"name": "Fatiga aguda", "value": fatigue, "weight": 1.0, "explanation": fatigue_msg},
        ]
        summary = (
            f"Análisis preliminar (solo {len(sessions)} sesión(es)). "
            "Registra más entrenamientos para enriquecer tu semáforo."
        )
        main_warning = None

    elif days_active < CHRONIC_WINDOW_DAYS:
        # Early: 50% fatiga, 30% dolor, 20% técnica
        stage = "early"
        score = fatigue * 0.50 + pain * 0.30 + tech * 0.20
        components = [
            {"name": "Fatiga aguda",   "value": fatigue, "weight": 0.50, "explanation": fatigue_msg},
            {"name": "Dolor residual", "value": pain,    "weight": 0.30, "explanation": pain_msg},
            {"name": "Técnica",        "value": tech,    "weight": 0.20, "explanation": tech_msg},
        ]
        summary = "Análisis intermedio — basado en tus últimas semanas de actividad."
        lowest = min(components, key=lambda c: c["value"])
        main_warning = lowest["explanation"] if lowest["value"] < 50 else None

    else:
        # Established: 30% fatiga, 25% ACWR, 25% dolor, 20% técnica
        stage = "established"
        acwr_val, acwr_ratio, acwr_msg = _acwr_score(sessions, now, body_weight)
        if acwr_val is None:
            # Aunque tenga >28 días, si no hay carga crónica suficiente cae a early
            stage = "early"
            score = fatigue * 0.50 + pain * 0.30 + tech * 0.20
            components = [
                {"name": "Fatiga aguda",   "value": fatigue, "weight": 0.50, "explanation": fatigue_msg},
                {"name": "Dolor residual", "value": pain,    "weight": 0.30, "explanation": pain_msg},
                {"name": "Técnica",        "value": tech,    "weight": 0.20, "explanation": tech_msg},
            ]
            summary = "Pocas sesiones recientes — usando análisis intermedio."
        else:
            score = fatigue * 0.30 + acwr_val * 0.25 + pain * 0.25 + tech * 0.20
            components = [
                {"name": "Fatiga aguda",          "value": fatigue,  "weight": 0.30, "explanation": fatigue_msg},
                {"name": "Carga relativa (ACWR)", "value": acwr_val, "weight": 0.25, "explanation": acwr_msg},
                {"name": "Dolor residual",        "value": pain,     "weight": 0.25, "explanation": pain_msg},
                {"name": "Técnica reciente",      "value": tech,     "weight": 0.20, "explanation": tech_msg},
            ]
            summary = f"Análisis completo basado en {len(sessions)} sesiones registradas."

        lowest = min(components, key=lambda c: c["value"])
        main_warning = lowest["explanation"] if lowest["value"] < 50 else None

    score = int(round(max(0, min(100, score))))
    label = "high" if score >= 70 else "medium" if score >= 40 else "low"

    return {
        "score": score,
        "label": label,
        "stage": stage,
        "components": components,
        "summary": summary,
        "main_warning": main_warning,
    }
