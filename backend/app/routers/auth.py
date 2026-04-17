from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session as DBSession

from app.database import get_db
from app.models import User
from app.schemas import UserCreate, UserResponse, Token
from app.auth import hash_password, verify_password, create_access_token, get_current_user

router = APIRouter()


@router.post("/register", response_model=UserResponse, status_code=201)
def register(data: UserCreate, db: DBSession = Depends(get_db)):
    if db.query(User).filter(User.email == data.email).first():
        raise HTTPException(status_code=409, detail="El email ya está registrado")

    user = User(
        email=data.email,
        full_name=data.full_name,
        hashed_password=hash_password(data.password),
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@router.post("/token", response_model=Token)
def login(
    form_data: OAuth2PasswordRequestForm = Depends(),
    db: DBSession = Depends(get_db),
):
    user = db.query(User).filter(User.email == form_data.username).first()

    if not user or not verify_password(form_data.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Credenciales incorrectas",
        )

    return Token(access_token=create_access_token(user.email))


@router.get("/me", response_model=UserResponse)
def get_me(
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    """Returns the current authenticated user's info."""
    return current_user


@router.delete("/me", status_code=204)
def delete_account(
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    """
    Permanently deletes the user account and ALL associated data
    (sessions, session_results, pain_checkins).
    """
    from app.models import Session, SessionResult, PainCheckIn

    # 1. Get all sessions belonging to this user
    sessions = db.query(Session).filter(Session.user_id == current_user.id).all()
    session_ids = [s.id for s in sessions]

    if session_ids:
        # 2. Delete pain_checkins
        db.query(PainCheckIn).filter(PainCheckIn.session_id.in_(session_ids)).delete(
            synchronize_session=False
        )
        # 3. Delete session_results
        db.query(SessionResult).filter(SessionResult.session_id.in_(session_ids)).delete(
            synchronize_session=False
        )
        # 4. Delete sessions
        db.query(Session).filter(Session.id.in_(session_ids)).delete(
            synchronize_session=False
        )

    # 5. Delete the user
    db.delete(current_user)
    db.commit()
    # 204 No Content — no return value
