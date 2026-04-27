from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session as DBSession

from app.auth import (
    get_current_user, hash_password, verify_password, create_access_token
)
from app.database import get_db
from app.email_utils import generate_code, send_reset_email, send_verification_email
from app.models import EmailCode, User
from app.schemas import (
    ChangePasswordRequest, ForgotPasswordRequest, MessageResponse,
    ResetPasswordRequest, ResendVerificationRequest, Token,
    UserCreate, UserResponse, VerifyEmailRequest,
)

router = APIRouter()

# ── Helpers ───────────────────────────────────────────────────────────────────

def _create_code(db: DBSession, email: str, purpose: str, minutes: int) -> str:
    """Invalida códigos anteriores del mismo propósito y crea uno nuevo."""
    db.query(EmailCode).filter(
        EmailCode.email == email,
        EmailCode.purpose == purpose,
        EmailCode.used == False,
    ).update({"used": True})

    code = generate_code()
    db.add(EmailCode(
        email      = email,
        code       = code,
        purpose    = purpose,
        expires_at = datetime.now(timezone.utc) + timedelta(minutes=minutes),
    ))
    db.commit()
    return code


def _validate_code(db: DBSession, email: str, code: str, purpose: str) -> EmailCode:
    """Devuelve el EmailCode si es válido; lanza 400 en caso contrario."""
    record = db.query(EmailCode).filter(
        EmailCode.email   == email,
        EmailCode.code    == code,
        EmailCode.purpose == purpose,
        EmailCode.used    == False,
    ).order_by(EmailCode.created_at.desc()).first()

    if not record:
        raise HTTPException(400, "Código incorrecto o ya utilizado.")
    if datetime.now(timezone.utc) > record.expires_at.replace(tzinfo=timezone.utc):
        raise HTTPException(400, "El código ha expirado. Solicita uno nuevo.")

    record.used = True
    db.commit()
    return record


# ── Registro ──────────────────────────────────────────────────────────────────

@router.post("/register", response_model=UserResponse, status_code=201)
def register(
    data: UserCreate,
    background_tasks: BackgroundTasks,
    db: DBSession = Depends(get_db),
):
    if db.query(User).filter(User.email == data.email).first():
        raise HTTPException(409, "El email ya está registrado.")

    user = User(
        email           = data.email,
        full_name       = data.full_name,
        hashed_password = hash_password(data.password),
        is_verified     = False,
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    code = _create_code(db, data.email, "verify_email", minutes=15)
    background_tasks.add_task(send_verification_email, data.email, code)
    return user


# ── Verificación de email ─────────────────────────────────────────────────────

@router.post("/verify-email", response_model=MessageResponse)
def verify_email(data: VerifyEmailRequest, db: DBSession = Depends(get_db)):
    user = db.query(User).filter(User.email == data.email).first()
    if not user:
        raise HTTPException(404, "Usuario no encontrado.")

    _validate_code(db, data.email, data.code, "verify_email")

    user.is_verified = True
    db.commit()
    return {"message": "Email verificado correctamente. Ya puedes iniciar sesión."}


@router.post("/resend-verification", response_model=MessageResponse)
def resend_verification(
    data: ResendVerificationRequest,
    background_tasks: BackgroundTasks,
    db: DBSession = Depends(get_db),
):
    user = db.query(User).filter(User.email == data.email).first()
    if not user:
        raise HTTPException(404, "Usuario no encontrado.")
    if user.is_verified:
        raise HTTPException(400, "La cuenta ya está verificada.")

    code = _create_code(db, data.email, "verify_email", minutes=15)
    background_tasks.add_task(send_verification_email, data.email, code)
    return {"message": "Código reenviado. Revisa tu correo."}


# ── Login ─────────────────────────────────────────────────────────────────────

@router.post("/token", response_model=Token)
def login(
    form_data: OAuth2PasswordRequestForm = Depends(),
    db: DBSession = Depends(get_db),
):
    user = db.query(User).filter(User.email == form_data.username).first()

    if not user or not verify_password(form_data.password, user.hashed_password):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Credenciales incorrectas.")

    if not user.is_verified:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "email_not_verified")

    return Token(access_token=create_access_token(user.email))


# ── Contraseña olvidada ───────────────────────────────────────────────────────

@router.post("/forgot-password", response_model=MessageResponse)
def forgot_password(
    data: ForgotPasswordRequest,
    background_tasks: BackgroundTasks,
    db: DBSession = Depends(get_db),
):
    user = db.query(User).filter(User.email == data.email).first()
    # Respuesta genérica para no revelar si el email existe
    if not user or not user.is_verified:
        return {"message": "Si el email existe recibirás un código en breve."}

    code = _create_code(db, data.email, "reset_password", minutes=10)
    background_tasks.add_task(send_reset_email, data.email, code)
    return {"message": "Si el email existe recibirás un código en breve."}


@router.post("/reset-password", response_model=MessageResponse)
def reset_password(data: ResetPasswordRequest, db: DBSession = Depends(get_db)):
    if len(data.new_password) < 8:
        raise HTTPException(400, "La contraseña debe tener al menos 8 caracteres.")

    user = db.query(User).filter(User.email == data.email).first()
    if not user:
        raise HTTPException(404, "Usuario no encontrado.")

    _validate_code(db, data.email, data.code, "reset_password")

    user.hashed_password = hash_password(data.new_password)
    db.commit()
    return {"message": "Contraseña restablecida correctamente. Ya puedes iniciar sesión."}


# ── Cambiar contraseña (autenticado) ─────────────────────────────────────────

@router.put("/change-password", response_model=MessageResponse)
def change_password(
    data: ChangePasswordRequest,
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    if not verify_password(data.current_password, current_user.hashed_password):
        raise HTTPException(400, "La contraseña actual es incorrecta.")
    if len(data.new_password) < 8:
        raise HTTPException(400, "La nueva contraseña debe tener al menos 8 caracteres.")
    if data.current_password == data.new_password:
        raise HTTPException(400, "La nueva contraseña debe ser diferente a la actual.")

    current_user.hashed_password = hash_password(data.new_password)
    db.commit()
    return {"message": "Contraseña actualizada correctamente."}


# ── Perfil ────────────────────────────────────────────────────────────────────

@router.get("/me", response_model=UserResponse)
def get_me(current_user: User = Depends(get_current_user)):
    return current_user


@router.delete("/me", status_code=204)
def delete_account(
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    from app.models import Session, SessionResult, PainCheckIn

    sessions    = db.query(Session).filter(Session.user_id == current_user.id).all()
    session_ids = [s.id for s in sessions]

    if session_ids:
        db.query(PainCheckIn).filter(
            PainCheckIn.session_id.in_(session_ids)
        ).delete(synchronize_session=False)
        db.query(SessionResult).filter(
            SessionResult.session_id.in_(session_ids)
        ).delete(synchronize_session=False)
        db.query(Session).filter(
            Session.id.in_(session_ids)
        ).delete(synchronize_session=False)

    db.delete(current_user)
    db.commit()
