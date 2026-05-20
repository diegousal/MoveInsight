from pydantic import BaseModel, EmailStr, Field
from datetime import datetime, date


# --- Auth ---
class UserCreate(BaseModel):
    email: str
    password: str
    full_name: str


class UserResponse(BaseModel):
    id                   : int
    email                : str
    full_name            : str
    age                  : int   | None = None
    body_weight_kg       : float | None = None
    level                : str   | None = None
    objective            : str   | None = None
    onboarding_completed : bool         = False

    class Config:
        from_attributes = True


class OnboardingRequest(BaseModel):
    # None = campo no rellenado (opcional). Si se proporciona, se valida el rango.
    age            : int   | None = Field(default=None, ge=10, le=99)
    body_weight_kg : float | None = Field(default=None, ge=30.0, le=300.0)
    level          : str          # 'beginner' | 'intermediate' | 'advanced'
    objective      : str          # 'technique' | 'progression' | 'pain_monitoring' | 'health'


class Token(BaseModel):
    access_token:  str
    refresh_token: str
    token_type:    str = "bearer"


class RefreshRequest(BaseModel):
    refresh_token: str


class MessageResponse(BaseModel):
    message: str


class VerifyEmailRequest(BaseModel):
    email: str
    code: str


class ResendVerificationRequest(BaseModel):
    email: str


class ForgotPasswordRequest(BaseModel):
    email: str


class ResetPasswordRequest(BaseModel):
    email: str
    code: str
    new_password: str


class ChangePasswordRequest(BaseModel):
    current_password: str
    new_password: str


# --- Sessions ---
class SessionResponse(BaseModel):
    id: int
    status: str
    message: str = ""
    user_notes: str = ""

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
    user_notes: str = ""
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
    user_notes: str = ""
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
    best_technique_score: float | None = None
    max_reps: int | None = None
    overtraining_risk: bool = False
    readiness_score: int = 50
    readiness_label: str = "medium"
    insights: list[InsightResponse] = []


# --- Readiness breakdown ---
class ReadinessComponent(BaseModel):
    name        : str
    value       : int               # 0-100
    weight      : float             # 0.0-1.0
    explanation : str


class ReadinessBreakdown(BaseModel):
    score        : int              # 0-100
    label        : str              # "high" | "medium" | "low"
    stage        : str              # "cold_start" | "early" | "established"
    components   : list[ReadinessComponent] = []
    summary      : str              # texto explicativo general
    main_warning : str | None = None  # advertencia principal si la hay


# --- Incidencias / Bienestar ---
class IncidentCreate(BaseModel):
    body_zone     : str
    severity      : int = Field(ge=0, le=10)
    incident_type : str = "molestia"
    start_date    : date
    end_date      : date | None = None    # permite registrar lesiones ya superadas
    notes         : str = ""


class IncidentUpdate(BaseModel):
    body_zone     : str | None = None
    severity      : int | None = Field(default=None, ge=0, le=10)
    incident_type : str | None = None
    end_date      : date | None = None       # asignar para marcar recuperada
    notes         : str | None = None


class IncidentResponse(BaseModel):
    id            : int
    body_zone     : str
    severity      : int
    incident_type : str
    start_date    : date
    end_date      : date | None = None
    notes         : str = ""
    created_at    : datetime | None = None
    is_active     : bool

    class Config:
        from_attributes = True


# --- Wellness timeline (F4) ---
class TimelinePoint(BaseModel):
    date       : date
    session_id : int
    readiness  : int          # 0-100, calculado al momento de esa sesión
    load       : float        # carga normalizada de la sesión
    technique  : float | None = None   # 0-10
    borg       : int | None   = None   # 0-10
    weight_kg  : float | None = None


class WellnessTimeline(BaseModel):
    points     : list[TimelinePoint]    = []
    incidents  : list[IncidentResponse] = []


# --- Factores observados (F5) ---
class ObservedFactor(BaseModel):
    name       : str          # "Carga", "Técnica", "Dolor", "Fatiga"
    value      : float        # valor en el periodo pre-incidencia
    baseline   : float        # valor medio del usuario fuera del periodo
    delta_pct  : float        # % de desviación vs baseline
    severity   : str          # "low" | "moderate" | "high"
    description: str          # explicación textual


class IncidentFactors(BaseModel):
    incident_id    : int
    window_days    : int
    sample_size    : int                       # nº sesiones en el periodo
    factors        : list[ObservedFactor] = []
    note           : str                       # disclaimer
    timeline_points: list[TimelinePoint] = []  # puntos del periodo pre-incidencia


# --- Recomendaciones (F6) ---
class Recommendation(BaseModel):
    id          : str
    category    : str           # "technique" | "load" | "recovery" | "progression"
    title       : str
    body        : str
    priority    : int           # 1 = más alta
    video_url   : str | None = None
    trigger     : str           # descripción del motivo de activación


class RecommendationList(BaseModel):
    recommendations: list[Recommendation] = []


# --- Validación retrospectiva (F8) ---
class ValidationDetail(BaseModel):
    incident_id      : int
    body_zone        : str
    readiness_before : int | None = None   # readiness medio en los 7-14d previos
    readiness_at     : int | None = None   # readiness en los 7d antes del inicio
    delta            : int | None = None   # readiness_at - readiness_before
    trend            : str                 # "declining" | "stable" | "improving"


class ValidationResult(BaseModel):
    incidents_analyzed  : int
    early_warning_count : int
    early_warning_rate  : float
    details             : list[ValidationDetail] = []
    summary             : str


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