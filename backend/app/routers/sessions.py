import shutil
from datetime import datetime

from fastapi import APIRouter, Depends, File, Form, UploadFile, HTTPException, BackgroundTasks
from sqlalchemy.orm import Session as DBSession

from app.auth import get_current_user
from app.config import settings
from app.database import get_db
from app.models import User, Session, SessionResult
from app.schemas import SessionResponse, SessionDetail, RepResult, SessionListResponse
from app.services.video import process_video

router = APIRouter()


@router.post("/upload", response_model=SessionResponse, status_code=202)
def upload_session(
    background_tasks: BackgroundTasks,
    video: UploadFile = File(...),
    weight_kg: float = Form(...),
    borg_score: int = Form(...),
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
    )
    db.add(session)
    db.commit()
    db.refresh(session)

    # 3. Lanzar procesamiento en background
    background_tasks.add_task(process_video, session.id, str(video_path))

    return session


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

    return SessionDetail(
        id=session.id,
        status=session.status,
        message=session.message,
        weight_kg=session.weight_kg,
        borg_score=session.borg_score,
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
    )


@router.get("/", response_model=list[SessionListResponse])
def list_sessions(
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    return db.query(Session).filter(
        Session.user_id == current_user.id
    ).order_by(Session.created_at.desc()).all()
