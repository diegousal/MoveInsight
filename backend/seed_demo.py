"""
seed_demo.py — Datos de demostración para el manual de usuario de MoveInsight.

Crea (si no existe) una cuenta demo YA VERIFICADA y le siembra un historial
realista —sesiones + KPIs + check-ins de dolor + 1 incidencia— repartido en el
tiempo y TERMINANDO HOY, para que se vean poblados: historial, gráficos de
evolución, récords, resumen semanal, semáforo de readiness, ACWR, indicador de
fatiga y el apartado de Bienestar.

Ejecutar en el SERVIDOR, desde la carpeta del repo (con los contenedores arriba):

    docker compose exec -T api python - < backend/seed_demo.py
"""
from datetime import datetime, timedelta, timezone
import random

from app.database import SessionLocal
from app.auth import hash_password
from app.models import User, Session, SessionResult, PainCheckIn, Incident

# ───────── CONFIGURA ─────────
EMAIL             = "moveinsight@gmail.com"
PASSWORD          = "diego2004"
FULL_NAME         = "Usuario de demostración"
WIPE_EXISTING     = True       # borra el historial previo de ESTA cuenta antes de sembrar
N_WEEKS           = 10
SESSIONS_PER_WEEK = 3
ADD_INCIDENT      = True        # incidencia de rodilla para el apartado de Bienestar
# ─────────────────────────────
random.seed(42)
clamp = lambda v: round(max(0.0, min(10.0, v)), 1)

db = SessionLocal()

# ── Cuenta demo (creada ya verificada; no pasa por el correo) ────────────────
user = db.query(User).filter(User.email == EMAIL).first()
if not user:
    user = User(email=EMAIL, full_name=FULL_NAME, hashed_password=hash_password(PASSWORD),
                is_verified=True)
    db.add(user)
    db.commit()
    db.refresh(user)
    print(f"Cuenta creada: {EMAIL} / {PASSWORD}")
else:
    user.hashed_password = hash_password(PASSWORD)   # asegura la contraseña conocida
    user.is_verified = True
    print(f"Cuenta existente reutilizada: {EMAIL}")

user.body_weight_kg = user.body_weight_kg or 78.0
user.level = user.level or "intermediate"
user.objective = user.objective or "progression"
user.onboarding_completed = True
db.commit()

# ── Limpieza opcional del historial previo ───────────────────────────────────
if WIPE_EXISTING:
    ids = [s.id for s in db.query(Session).filter(Session.user_id == user.id)]
    if ids:
        db.query(PainCheckIn).filter(PainCheckIn.session_id.in_(ids)).delete(synchronize_session=False)
        db.query(SessionResult).filter(SessionResult.session_id.in_(ids)).delete(synchronize_session=False)
        db.query(Session).filter(Session.id.in_(ids)).delete(synchronize_session=False)
    db.query(Incident).filter(Incident.user_id == user.id).delete(synchronize_session=False)
    db.commit()

# ── Siembra del historial ────────────────────────────────────────────────────
now, total = datetime.now(timezone.utc), N_WEEKS * SESSIONS_PER_WEEK
start, step = now - timedelta(weeks=N_WEEKS), 7.0 / SESSIONS_PER_WEEK
inc_start = (now - timedelta(days=33)).date() if ADD_INCIDENT else None
inc_dt = datetime.combine(inc_start, datetime.min.time(), tzinfo=timezone.utc) if inc_start else None

n = 0
for i in range(total):
    frac = i / (total - 1)
    dt = start + timedelta(days=i * step, hours=random.randint(8, 20))
    if dt > now:
        dt = now - timedelta(hours=3)
    pre = bool(inc_dt and timedelta(0) <= (inc_dt - dt) <= timedelta(days=12))

    weight = round(60 + 28 * frac + random.uniform(-3, 3), 1)
    borg = max(4, min(10, int(round(6 + 2 * frac + random.uniform(-1, 1))) + (2 if pre else 0)))

    s = Session(user_id=user.id, status="completed", message="Análisis completado",
                video_path=f"/app/uploads/{user.id}/demo_{i}.mp4",
                weight_kg=weight, borg_score=borg, user_notes="", created_at=dt)
    db.add(s); db.flush(); n += 1

    lateral = (i % 3 == 2)                      # 1 de cada 3 en perfil lateral (sin valgo/simetría)
    base = 6.3 + 2.2 * frac - (1.0 if pre else 0)
    for rep in range(1, random.randint(4, 6) + 1):
        depth, torso, rhythm = (clamp(base + random.uniform(-1, 1)) for _ in range(3))
        knees = None if lateral else clamp(base + random.uniform(-1.2, 1))
        symm  = None if lateral else clamp(base + random.uniform(-1, 1))
        meas = [v for v in (depth, torso, knees, symm, rhythm) if v is not None]
        db.add(SessionResult(session_id=s.id, rep_number=rep, depth_score=depth,
                             torso_score=torso, knees_score=knees, symmetry_score=symm,
                             rhythm_score=rhythm, overall_score=round(sum(meas) / len(meas), 1),
                             report_image_path=f"/app/reportes/demo_{s.id}.png"))

    if pre and random.random() < 0.7:
        db.add(PainCheckIn(session_id=s.id, eva_score=random.randint(5, 7),
                           hours_after=random.choice([24, 48]), body_zones="rodilla_der",
                           created_at=dt + timedelta(hours=24)))
    elif random.random() < 0.25:
        db.add(PainCheckIn(session_id=s.id, eva_score=random.randint(2, 4),
                           hours_after=random.choice([24, 48]), body_zones="lumbar",
                           created_at=dt + timedelta(hours=24)))

if ADD_INCIDENT:
    db.add(Incident(user_id=user.id, body_zone="rodilla_der", severity=5,
                    incident_type="molestia", start_date=inc_start,
                    end_date=inc_start + timedelta(days=9), notes="Molestia tras subir carga."))

db.commit()
print(f"Sembradas {n} sesiones para {EMAIL}" + (" + 1 incidencia" if ADD_INCIDENT else ""))
print(f"Inicia sesión en la app con:  {EMAIL}  /  {PASSWORD}")
