# ── Imagen del backend (FastAPI + pipeline ML) ───────────────────────────────
FROM python:3.10-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    TF_CPP_MIN_LOG_LEVEL=3 \
    MPLBACKEND=Agg

# Dependencias de sistema:
#   libgl1, libglib2.0-0 → necesarias para OpenCV / MediaPipe (libGL.so.1)
#   ffmpeg               → lectura/escritura robusta de vídeo
#   curl                 → healthcheck del contenedor
RUN apt-get update && apt-get install -y --no-install-recommends \
        libgl1 \
        libglib2.0-0 \
        ffmpeg \
        curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 1) Dependencias Python (capa cacheable: solo se reinstala si cambia requirements)
COPY requirements.txt .
RUN pip install --upgrade pip && pip install -r requirements.txt

# 2) Código de la aplicación + modelos
COPY . .

# 3) Carpetas de datos (se montan como volúmenes en producción para persistir)
RUN mkdir -p /app/uploads /app/reportes

EXPOSE 8000

# Uvicorn con varios procesos (workers). El nº se controla con WEB_CONCURRENCY
# (definido en .env). Forma shell para que se expanda la variable de entorno.
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers ${WEB_CONCURRENCY:-2}"]
