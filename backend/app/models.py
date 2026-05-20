from sqlalchemy import (
    Column, Integer, String, Float, Enum, ForeignKey, Text, TIMESTAMP, Boolean, Date
)
from sqlalchemy.sql import func
from sqlalchemy.orm import relationship

from app.database import Base


class User(Base):
    __tablename__ = "users"

    id              = Column(Integer, primary_key=True, autoincrement=True)
    email           = Column(String(255), unique=True, nullable=False, index=True)
    full_name       = Column(String(255), nullable=False)
    hashed_password = Column(String(255), nullable=False)
    is_verified     = Column(Boolean, default=False, nullable=False)
    created_at      = Column(TIMESTAMP, server_default=func.now())

    # ── Perfil de onboarding ──────────────────────────────────────────────
    age                  = Column(Integer, nullable=True)
    body_weight_kg       = Column(Float, nullable=True)
    level                = Column(
        Enum("beginner", "intermediate", "advanced"), nullable=True
    )
    objective            = Column(
        Enum("technique", "progression", "pain_monitoring", "health"), nullable=True
    )
    onboarding_completed = Column(Boolean, default=False, nullable=False)

    sessions = relationship("Session", back_populates="user")


class EmailCode(Base):
    """Códigos de 6 dígitos para verificación de email y reseteo de contraseña."""
    __tablename__ = "email_codes"

    id         = Column(Integer, primary_key=True, autoincrement=True)
    email      = Column(String(255), nullable=False, index=True)
    code       = Column(String(6), nullable=False)
    purpose    = Column(Enum("verify_email", "reset_password"), nullable=False)
    expires_at = Column(TIMESTAMP, nullable=False)
    used       = Column(Boolean, default=False, nullable=False)
    created_at = Column(TIMESTAMP, server_default=func.now())


class Session(Base):
    __tablename__ = "sessions"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    status = Column(
        Enum("queued", "processing", "completed", "failed"),
        default="queued",
    )
    message = Column(String(500), default="")
    video_path = Column(String(500), nullable=False)
    weight_kg = Column(Float)
    borg_score = Column(Integer)
    user_notes          = Column(Text, default="")
    skeleton_video_path = Column(String(500), nullable=True)
    created_at          = Column(TIMESTAMP, server_default=func.now())

    user = relationship("User", back_populates="sessions")
    results = relationship("SessionResult", back_populates="session")
    pain_checkins = relationship("PainCheckIn", back_populates="session")


class SessionResult(Base):
    __tablename__ = "session_results"

    id = Column(Integer, primary_key=True, autoincrement=True)
    session_id = Column(Integer, ForeignKey("sessions.id"), nullable=False)
    rep_number = Column(Integer, nullable=False)
    phase_data = Column(Text)
    depth_score = Column(Float)
    torso_score = Column(Float)
    stability_score = Column(Float)
    knees_score = Column(Float)
    rhythm_score = Column(Float)
    overall_score = Column(Float)
    report_image_path = Column(String(500))

    session = relationship("Session", back_populates="results")


class Incident(Base):
    """Incidencias / molestias registradas por el usuario en el apartado Bienestar."""
    __tablename__ = "incidents"

    id            = Column(Integer, primary_key=True, autoincrement=True)
    user_id       = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    body_zone     = Column(String(50), nullable=False)         # ej. "rodilla_izq", "lumbar"
    severity      = Column(Integer, nullable=False)            # 0-10
    incident_type = Column(
        Enum("molestia", "dolor_agudo", "fatiga", "contractura", "lesion", "otro"),
        default="molestia", nullable=False
    )
    start_date    = Column(Date, nullable=False)
    end_date      = Column(Date, nullable=True)                # NULL = activa
    notes         = Column(Text, default="")
    created_at    = Column(TIMESTAMP, server_default=func.now())

    user = relationship("User")


class PainCheckIn(Base):
    __tablename__ = "pain_checkins"

    id          = Column(Integer, primary_key=True, autoincrement=True)
    session_id  = Column(Integer, ForeignKey("sessions.id"), nullable=False)
    eva_score   = Column(Integer, nullable=False)      # 0-10
    hours_after = Column(Integer, nullable=False)      # 24 o 48
    body_zones  = Column(Text, default="")             # zonas separadas por coma
    notes       = Column(Text, default="")             # notas libres opcionales
    created_at  = Column(TIMESTAMP, server_default=func.now())

    session = relationship("Session", back_populates="pain_checkins")
