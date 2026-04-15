from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.config import settings
from app.routers import auth, sessions
from app.database import engine, Base


# --- Lifespan: se ejecuta al arrancar y al parar el servidor ---
@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: crea las tablas si no existen
    Base.metadata.create_all(bind=engine)
    settings.UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    settings.REPORT_DIR.mkdir(parents=True, exist_ok=True)
    yield
    # Shutdown: aquí podrías cerrar conexiones si hiciera falta


app = FastAPI(
    title="MoveInsight API",
    version="1.0.0",
    lifespan=lifespan,
)

# --- CORS ---
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # En producción, restringir a tu dominio
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- Archivos estáticos (reportes generados) ---
app.mount("/static/reportes", StaticFiles(directory=settings.REPORT_DIR), name="reportes")

# --- Routers ---
app.include_router(auth.router, prefix="/api/v1/auth", tags=["Auth"])
app.include_router(sessions.router, prefix="/api/v1/sessions", tags=["Sessions"])


@app.get("/health")
async def health():
    return {"status": "ok"}
