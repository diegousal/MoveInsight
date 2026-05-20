"""
Endpoints de recomendaciones (F6):
  - GET /recommendations  → lista de recomendaciones activas para el usuario
"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session as DBSession

from app.auth import get_current_user
from app.database import get_db
from app.models import Session, SessionResult, User
from app.routers.analytics import _load_recent_checkins, _load_sessions_with_technique
from app.schemas import RecommendationList
from app.services.readiness import calculate_readiness
from app.services.recommendations import evaluate_recommendations

router = APIRouter()


@router.get("/recommendations", response_model=RecommendationList)
def get_recommendations(
    current_user: User        = Depends(get_current_user),
    db          : DBSession   = Depends(get_db),
):
    """
    Evalúa el estado actual del usuario y devuelve las recomendaciones activas
    ordenadas por prioridad (1 = más urgente).

    Triggers activos:
      - Profundidad / rodillas / torso < 5/10 en la última sesión
      - ACWR > 1.5 (sobrecarga aguda)
      - EVA reciente > 6/10
      - Readiness < 40
      - Sin sesión en más de 10 días
      - Borg < 3 con técnica ≥ 7/10 (listo para progresar)
    """
    sessions = _load_sessions_with_technique(db, current_user.id)
    checkins = _load_recent_checkins(db, current_user.id)
    breakdown = calculate_readiness(current_user, sessions, checkins)

    # Resultados de la sesión más reciente
    latest_results = []
    if sessions:
        latest_results = (
            db.query(SessionResult)
            .filter(SessionResult.session_id == sessions[0].id)
            .all()
        )

    recs = evaluate_recommendations(
        current_user, sessions, checkins, breakdown, latest_results
    )
    return RecommendationList(recommendations=recs)
