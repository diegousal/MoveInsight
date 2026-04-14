from fastapi import FastAPI, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from pydantic import BaseModel
import uvicorn

app = FastAPI(title="MoveInsight API")

# --- SIMULACIÓN DE BASE DE DATOS ---
# (Más adelante cambiaremos esto por PostgreSQL o SQLite)
fake_users_db = {}

# --- MODELOS PYDANTIC (Deben coincidir con los de Android) ---
class UserCreate(BaseModel):
    email: str
    password: str
    full_name: str

class UserResponse(BaseModel):
    id: int
    email: str
    full_name: str

class Token(BaseModel):
    access_token: str
    token_type: str

# --- ENDPOINT 1: REGISTRO ---
@app.post("/api/v1/auth/register", response_model=UserResponse)
async def register(user: UserCreate):
    if user.email in fake_users_db:
        raise HTTPException(status_code=409, detail="El usuario ya existe")
    
    # Guardamos en nuestra "base de datos" simulada
    user_id = len(fake_users_db) + 1
    fake_users_db[user.email] = {
        "id": user_id,
        "email": user.email,
        "password": user.password, # ¡Ojo! En producción esto irá encriptado
        "full_name": user.full_name
    }
    
    return UserResponse(id=user_id, email=user.email, full_name=user.full_name)

# --- ENDPOINT 2: LOGIN ---
@app.post("/api/v1/auth/token", response_model=Token)
async def login(form_data: OAuth2PasswordRequestForm = Depends()):
    # FastAPI guarda el email en 'form_data.username' por el estándar OAuth2
    user = fake_users_db.get(form_data.username)
    
    if not user or user["password"] != form_data.password:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Credenciales incorrectas"
        )
    
    # Si es correcto, generamos un Token falso de prueba
    fake_token = f"token_generado_para_{user['email']}"
    
    return Token(access_token=fake_token, token_type="bearer")

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)