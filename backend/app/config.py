from pathlib import Path
from pydantic_settings import BaseSettings

BASE_DIR = Path(__file__).resolve().parent.parent  # → backend/


class Settings(BaseSettings):
    DATABASE_URL: str
    JWT_SECRET_KEY: str
    JWT_ALGORITHM: str  = "HS256"
    JWT_EXPIRE_HOURS: int         = 1   # access token → corto (1 h)
    JWT_REFRESH_EXPIRE_DAYS: int  = 30  # refresh token → largo (30 días)

    BASE_DIR: Path = BASE_DIR

    UPLOAD_DIR: Path = BASE_DIR / "uploads"
    REPORT_DIR: Path = BASE_DIR / "reportes"
    MODEL_DIR:  Path = BASE_DIR / "modelos" / "v16"

    # ── Email ─────────────────────────────────────────────────────────────
    EMAIL_HOST:     str = "smtp.gmail.com"
    EMAIL_PORT:     int = 587
    EMAIL_USERNAME: str = ""
    EMAIL_PASSWORD: str = ""
    EMAIL_FROM:     str = ""

    class Config:
        env_file = BASE_DIR / ".env"


settings = Settings()
