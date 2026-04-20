import os
import shutil
from datetime import datetime

from fastapi import APIRouter, Depends, File, Form, UploadFile, HTTPException, BackgroundTasks
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session as DBSession
from sqlalchemy import func as sql_func

from app.auth import get_current_user
from app.config import settings
from app.database import get_db
from app.models import User, Session, SessionResult, PainCheckIn
from app.schemas import SessionResponse, SessionDetail, RepResult, SessionListResponse, CheckInSummary
from app.services.video import process_video

router = APIRouter()


@router.post("/upload", response_model=SessionResponse, status_code=202)
def upload_session(
    background_tasks: BackgroundTasks,
    video: UploadFile = File(...),
    weight_kg: float = Form(...),
    borg_score: int = Form(...),
    user_notes: str = Form(default=""),
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    # 1. Guardar vídeo en disco
    user_dir = settings.UPLOAD_DIR / str(current_user.id)
    user_dir.mkdir(parents=True, exist_ok=True)

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"{timestamp}_{video.filename}"
    video_path = user_dir / filename

    with open(video_path, "wb") as f:
        shutil.copyfileobj(video.file, f)

    # 2. Crear registro en BD
    session = Session(
        user_id=current_user.id,
        status="queued",
        message="Vídeo recibido, en cola de procesamiento",
        video_path=str(video_path),
        weight_kg=weight_kg,
        borg_score=borg_score,
        user_notes=user_notes,
    )
    db.add(session)
    db.commit()
    db.refresh(session)

    # 3. Lanzar procesamiento en background
    background_tasks.add_task(process_video, session.id, str(video_path))

    return session


@router.delete("/history", status_code=204)
def clear_history(
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    """
    Deletes ALL sessions, results and check-ins for the current user.
    """
    from app.models import PainCheckIn

    sessions = db.query(Session).filter(Session.user_id == current_user.id).all()
    session_ids = [s.id for s in sessions]

    if session_ids:
        db.query(PainCheckIn).filter(PainCheckIn.session_id.in_(session_ids)).delete(
            synchronize_session=False
        )
        db.query(SessionResult).filter(SessionResult.session_id.in_(session_ids)).delete(
            synchronize_session=False
        )
        db.query(Session).filter(Session.id.in_(session_ids)).delete(
            synchronize_session=False
        )
        db.commit()


@router.get("/{session_id}", response_model=SessionDetail)
def get_session(
    session_id: int,
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    session = db.query(Session).filter(
        Session.id == session_id,
        Session.user_id == current_user.id,
    ).first()

    if not session:
        raise HTTPException(status_code=404, detail="Sesión no encontrada")

    results = db.query(SessionResult).filter(
        SessionResult.session_id == session_id
    ).order_by(SessionResult.rep_number).all()

    checkins = db.query(PainCheckIn).filter(
        PainCheckIn.session_id == session_id
    ).order_by(PainCheckIn.hours_after).all()

    return SessionDetail(
        id=session.id,
        status=session.status,
        message=session.message,
        weight_kg=session.weight_kg,
        borg_score=session.borg_score,
        user_notes=session.user_notes or "",
        created_at=str(session.created_at) if session.created_at else None,
        results=[RepResult(
            rep_number=r.rep_number,
            depth_score=r.depth_score,
            torso_score=r.torso_score,
            stability_score=r.stability_score,
            knees_score=r.knees_score,
            rhythm_score=r.rhythm_score,
            overall_score=r.overall_score,
        ) for r in results],
        checkins=[CheckInSummary(
            id=c.id,
            eva_score=c.eva_score,
            hours_after=c.hours_after,
            body_zones=c.body_zones.split(",") if c.body_zones else [],
            notes=c.notes or "",
            created_at=c.created_at,
        ) for c in checkins],
    )


@router.get("/{session_id}/skeleton-video")
def get_skeleton_video(
    session_id   : int,
    current_user : User     = Depends(get_current_user),
    db           : DBSession = Depends(get_db),
):
    """
    Devuelve el vídeo con el esqueleto de MediaPipe superpuesto.
    Retorna 404 si aún no se ha generado o si la sesión no pertenece al usuario.
    """
    session = db.query(Session).filter(
        Session.id      == session_id,
        Session.user_id == current_user.id
    ).first()

    if not session:
        raise HTTPException(status_code=404, detail="Sesión no encontrada")

    skel_path = session.skeleton_video_path
    if not skel_path or not os.path.exists(skel_path):
        raise HTTPException(status_code=404, detail="Vídeo de esqueleto no disponible aún")

    return FileResponse(
        path         = skel_path,
        media_type   = "video/mp4",
        filename     = f"skeleton_{session_id}.mp4",
        headers      = {"Accept-Ranges": "bytes"}
    )


@router.get("/", response_model=list[SessionListResponse])
def list_sessions(
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    sessions = db.query(Session).filter(
        Session.user_id == current_user.id
    ).order_by(Session.created_at.desc()).all()

    result = []
    for s in sessions:
        rep_count = db.query(sql_func.count(SessionResult.id)).filter(
            SessionResult.session_id == s.id
        ).scalar() or 0

        avg_score = db.query(sql_func.avg(SessionResult.overall_score)).filter(
            SessionResult.session_id == s.id
        ).scalar()

        session_checkins = db.query(PainCheckIn).filter(
            PainCheckIn.session_id == s.id
        ).order_by(PainCheckIn.hours_after).all()

        result.append(SessionListResponse(
            id=s.id,
            status=s.status,
            message=s.message,
            weight_kg=s.weight_kg,
            borg_score=s.borg_score,
            user_notes=s.user_notes or "",
            created_at=str(s.created_at) if s.created_at else None,
            overall_score=round(float(avg_score), 2) if avg_score else None,
            rep_count=rep_count if rep_count > 0 else None,
            checkins=[CheckInSummary(
                id=c.id,
                eva_score=c.eva_score,
                hours_after=c.hours_after,
                body_zones=c.body_zones.split(",") if c.body_zones else [],
                notes=c.notes or "",
                created_at=c.created_at,
            ) for c in session_checkins],
        ))

    return result
