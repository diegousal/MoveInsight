from pydantic import BaseModel, EmailStr
from datetime import datetime   


# --- Auth ---
class UserCreate(BaseModel):
    email: str
    password: str
    full_name: str


class UserResponse(BaseModel):
    id: int
    email: str
    full_name: str

    class Config:
        from_attributes = True


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"


# --- Sessions ---
class SessionResponse(BaseModel):
    id: int
    status: str
    message: str = ""

    class Config:
        from_attributes = True
        
# --- Session results (para GET) ---
class RepResult(BaseModel):
    rep_number: int
    depth_score: float
    torso_score: float
    stability_score: float
    knees_score: float
    rhythm_score: float
    overall_score: float

    class Config:
        from_attributes = True


class CheckInSummary(BaseModel):
    id          : int
    eva_score   : int
    hours_after : int
    body_zones  : list[str] = []
    notes       : str = ""
    created_at  : datetime | None = None

    class Config:
        from_attributes = True


class SessionDetail(BaseModel):
    id: int
    status: str
    message: str = ""
    weight_kg: float | None = None
    borg_score: int | None = None
    created_at: datetime | None = None
    results: list[RepResult] = []
    checkins: list[CheckInSummary] = []

    class Config:
        from_attributes = True

class SessionListResponse(BaseModel):
    id: int
    status: str
    message: str = ""
    weight_kg: float | None = None
    borg_score: int | None = None
    created_at: datetime | None = None
    overall_score: float | None = None
    rep_count: int | None = None
    checkins: list[CheckInSummary] = []

    class Config:
        from_attributes = True


# --- Analytics ---
class InsightResponse(BaseModel):
    type: str      # "warning" | "tip" | "achievement"
    title: str
    message: str


class AnalyticsSummary(BaseModel):
    total_sessions: int
    avg_technique_score: float | None = None
    avg_velocity: float | None = None
    max_weight_kg: float | None = None
    readiness_score: int = 50
    readiness_label: str = "medium"
    insights: list[InsightResponse] = []


# --- Pain Check-Ins ---
class PainCheckInRequest(BaseModel):
    session_id  : int
    eva_score   : int           # 0-10
    hours_after : int           # 24 o 48
    body_zones  : list[str] = []
    notes       : str = ""      # notas libres opcionales


class PainCheckInResponse(BaseModel):
    id          : int
    session_id  : int
    eva_score   : int
    hours_after : int
    body_zones  : list[str] = []
    notes       : str = ""
    created_at  : datetime | None = None

    class Config:
        from_attributes = True