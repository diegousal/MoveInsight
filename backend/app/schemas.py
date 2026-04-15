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


class SessionDetail(BaseModel):
    id: int
    status: str
    message: str = ""
    weight_kg: float | None = None
    borg_score: int | None = None
    created_at: datetime | None = None
    results: list[RepResult] = []

    class Config:
        from_attributes = True

class SessionListResponse(BaseModel):
    id: int
    status: str
    message: str = ""
    weight_kg: float | None = None
    borg_score: int | None = None
    created_at: datetime | None = None

    class Config:
        from_attributes = True