from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session as DBSession

from app.auth import get_current_user
from app.database import get_db
from app.models import User, Session, PainCheckIn
from app.schemas import PainCheckInRequest, PainCheckInResponse

router = APIRouter()


@router.post("/checkins", response_model=PainCheckInResponse, status_code=201)
def submit_checkin(
    body         : PainCheckInRequest,
    current_user : User        = Depends(get_current_user),
    db           : DBSession   = Depends(get_db),
):
    # Verify session belongs to the current user
    session = db.query(Session).filter(
        Session.id      == body.session_id,
        Session.user_id == current_user.id,
    ).first()
    if not session:
        raise HTTPException(status_code=404, detail="Sesión no encontrada")

    # Validate eva_score range
    if not (0 <= body.eva_score <= 10):
        raise HTTPException(status_code=422, detail="eva_score debe estar entre 0 y 10")

    # Prevent duplicate check-ins for the same session + hours slot
    existing = db.query(PainCheckIn).filter(
        PainCheckIn.session_id  == body.session_id,
        PainCheckIn.hours_after == body.hours_after,
    ).first()
    if existing:
        raise HTTPException(
            status_code=409,
            detail=f"Ya existe un check-in de {body.hours_after}h para esta sesión."
        )

    checkin = PainCheckIn(
        session_id  = body.session_id,
        eva_score   = body.eva_score,
        hours_after = body.hours_after,
        body_zones  = ",".join(body.body_zones),
        notes       = body.notes,
    )
    db.add(checkin)
    db.commit()
    db.refresh(checkin)

    return PainCheckInResponse(
        id          = checkin.id,
        session_id  = checkin.session_id,
        eva_score   = checkin.eva_score,
        hours_after = checkin.hours_after,
        body_zones  = checkin.body_zones.split(",") if checkin.body_zones else [],
        notes       = checkin.notes or "",
        created_at  = checkin.created_at,
    )


@router.get("/checkins/{session_id}", response_model=list[PainCheckInResponse])
def get_checkins_for_session(
    session_id   : int,
    current_user : User      = Depends(get_current_user),
    db           : DBSession = Depends(get_db),
):
    # Verify session belongs to the current user
    session = db.query(Session).filter(
        Session.id      == session_id,
        Session.user_id == current_user.id,
    ).first()
    if not session:
        raise HTTPException(status_code=404, detail="Sesión no encontrada")

    checkins = (
        db.query(PainCheckIn)
        .filter(PainCheckIn.session_id == session_id)
        .order_by(PainCheckIn.hours_after)
        .all()
    )
    return [
        PainCheckInResponse(
            id          = c.id,
            session_id  = c.session_id,
            eva_score   = c.eva_score,
            hours_after = c.hours_after,
            body_zones  = c.body_zones.split(",") if c.body_zones else [],
            notes       = c.notes or "",
            created_at  = c.created_at,
        )
        for c in checkins
    ]


