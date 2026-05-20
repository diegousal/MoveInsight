"""
Endpoints CRUD para incidencias / molestias del usuario.
Pertenece al apartado "Bienestar" del frontend.
"""
from datetime import date
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session as DBSession

from app.auth import get_current_user
from app.database import get_db
from app.models import Incident, User
from app.schemas import IncidentCreate, IncidentResponse, IncidentUpdate

router = APIRouter()


def _to_response(incident: Incident) -> dict:
    """Convierte Incident a dict con `is_active` calculado."""
    return {
        "id"            : incident.id,
        "body_zone"     : incident.body_zone,
        "severity"      : incident.severity,
        "incident_type" : incident.incident_type,
        "start_date"    : incident.start_date,
        "end_date"      : incident.end_date,
        "notes"         : incident.notes or "",
        "created_at"    : incident.created_at,
        "is_active"     : incident.end_date is None,
    }


# ── Lista ────────────────────────────────────────────────────────────────────

@router.get("/incidents", response_model=list[IncidentResponse])
def list_incidents(
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    """Devuelve todas las incidencias del usuario, ordenadas por fecha desc."""
    rows = db.query(Incident).filter(
        Incident.user_id == current_user.id
    ).order_by(Incident.start_date.desc()).all()
    return [_to_response(r) for r in rows]


# ── Detalle ──────────────────────────────────────────────────────────────────

@router.get("/incidents/{incident_id}", response_model=IncidentResponse)
def get_incident(
    incident_id: int,
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    incident = db.query(Incident).filter(
        Incident.id == incident_id,
        Incident.user_id == current_user.id,
    ).first()
    if not incident:
        raise HTTPException(404, "Incidencia no encontrada.")
    return _to_response(incident)


# ── Crear ────────────────────────────────────────────────────────────────────

@router.post("/incidents", response_model=IncidentResponse, status_code=201)
def create_incident(
    data: IncidentCreate,
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    incident = Incident(
        user_id       = current_user.id,
        body_zone     = data.body_zone,
        severity      = data.severity,
        incident_type = data.incident_type,
        start_date    = data.start_date,
        end_date      = data.end_date,   # None = activa; fecha = ya superada
        notes         = data.notes,
    )
    db.add(incident)
    db.commit()
    db.refresh(incident)
    return _to_response(incident)


# ── Actualizar ───────────────────────────────────────────────────────────────

@router.patch("/incidents/{incident_id}", response_model=IncidentResponse)
def update_incident(
    incident_id: int,
    data: IncidentUpdate,
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    incident = db.query(Incident).filter(
        Incident.id == incident_id,
        Incident.user_id == current_user.id,
    ).first()
    if not incident:
        raise HTTPException(404, "Incidencia no encontrada.")

    if data.body_zone     is not None: incident.body_zone     = data.body_zone
    if data.severity      is not None: incident.severity      = data.severity
    if data.incident_type is not None: incident.incident_type = data.incident_type
    if data.end_date      is not None: incident.end_date      = data.end_date
    if data.notes         is not None: incident.notes         = data.notes

    db.commit()
    db.refresh(incident)
    return _to_response(incident)


# ── Marcar recuperada ────────────────────────────────────────────────────────

@router.post("/incidents/{incident_id}/recover", response_model=IncidentResponse)
def mark_recovered(
    incident_id: int,
    end_date: date | None = None,
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    """Atajo para marcar una incidencia como recuperada (end_date = hoy si no se especifica)."""
    incident = db.query(Incident).filter(
        Incident.id == incident_id,
        Incident.user_id == current_user.id,
    ).first()
    if not incident:
        raise HTTPException(404, "Incidencia no encontrada.")
    incident.end_date = end_date or date.today()
    db.commit()
    db.refresh(incident)
    return _to_response(incident)


# ── Eliminar ─────────────────────────────────────────────────────────────────

@router.delete("/incidents/{incident_id}", status_code=204)
def delete_incident(
    incident_id: int,
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    incident = db.query(Incident).filter(
        Incident.id == incident_id,
        Incident.user_id == current_user.id,
    ).first()
    if not incident:
        raise HTTPException(404, "Incidencia no encontrada.")
    db.delete(incident)
    db.commit()
