from sqlalchemy import (
    Column, Integer, String, Float, Enum, ForeignKey, Text, TIMESTAMP
)
from sqlalchemy.sql import func
from sqlalchemy.orm import relationship

from app.database import Base


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, autoincrement=True)
    email = Column(String(255), unique=True, nullable=False, index=True)
    full_name = Column(String(255), nullable=False)
    hashed_password = Column(String(255), nullable=False)
    created_at = Column(TIMESTAMP, server_default=func.now())

    sessions = relationship("Session", back_populates="user")


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
    created_at = Column(TIMESTAMP, server_default=func.now())

    user = relationship("User", back_populates="sessions")
    results = relationship("SessionResult", back_populates="session")


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
