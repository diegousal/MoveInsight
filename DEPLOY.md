# Despliegue de MoveInsight en el servidor USAL

Guía paso a paso para dejar el backend funcionando en `prodiasv07.fis.usal.es`
(Ubuntu 24) con Docker, MariaDB, Nginx y HTTPS.

> **Requisito de red:** el acceso SSH solo funciona desde la red de la USAL
> (aulas/wifi) o mediante **eduVPN** (https://eduvpn.usal.es). Los puertos 80 y
> 443 sí son accesibles desde cualquier sitio.

### Consideraciones de las normas del servidor (USAL)

- **Cambia la contraseña de `tfg` en el primer acceso** (obligatorio).
- Eres **administrador** del equipo: la configuración de servicios, **cortafuegos**
  y base de datos es responsabilidad tuya (y forma parte del proyecto documentarla).
- **No modifiques la configuración de red** del servidor (IP/hostname): está prohibido.
- **No apagues el servidor.** Reiniciar sí se puede; pero si se apaga del todo no se
  puede encender en remoto → habría que avisar a `andres@usal.es`.
- Puertos visibles desde fuera de la USAL: **22 (ssh), 80 (http), 443 (https)**.
  Otros (p. ej. 8080) están cerrados por el CPD; nuestro diseño solo usa 80/443.
- Consola de emergencia: por **VNC** a través del anfitrión XEN, solo desde la red USAL
  (útil si te quedas sin SSH; no necesaria para el despliegue normal).
- Haz **copias de seguridad** de tu trabajo: el servidor puede formatearse tras la
  lectura del TFG.

---

## Arquitectura

```
   App Android ──HTTPS──>  Nginx (80/443)  ──proxy──>  api (FastAPI/Gunicorn:8000)
                              │                              │
                       certificado TLS                      └──> db (MariaDB:3306)
                       (Let's Encrypt)                            [red interna Docker]
```

Solo Nginx está expuesto a internet. La base de datos nunca publica su puerto.

---

## FASE 0 · En tu PC (antes de subir nada)

Ya están creados en el repo: `docker-compose.yml`, `.env.example`,
`backend/Dockerfile`, `backend/.dockerignore`, `nginx/conf.d/app.conf` y
`nginx/app-ssl.conf`. No subas la carpeta `backend/venv`, `uploads/` ni
`reportes/` (son locales y enormes).

---

## FASE 1 · Acceso y preparación del servidor

```bash
# 1. Conéctate (desde red USAL o eduVPN)
ssh tfg@prodiasv07.fis.usal.es

# 2. Cambia la contraseña inicial
passwd

# 3. Actualiza el sistema
sudo apt update && sudo apt upgrade -y
```

### Instalar Docker

```bash
# Instala Docker Engine + plugin compose (script oficial)
curl -fsSL https://get.docker.com | sudo sh

# Permite usar docker sin sudo (cierra y reabre sesión después)
sudo usermod -aG docker tfg
newgrp docker

# Comprueba
docker --version
docker compose version
```

### Cortafuegos (ufw)

```bash
sudo ufw allow OpenSSH      # puerto 22
sudo ufw allow 80/tcp       # http
sudo ufw allow 443/tcp      # https
sudo ufw enable
sudo ufw status
```

---

## FASE 2 · Subir el proyecto al servidor

**Opción A — git (recomendada, si tienes el repo en GitHub/GitLab):**
```bash
cd ~
git clone <URL_DE_TU_REPO> TFG
cd TFG
```

**Opción B — copia directa (scp/WinSCP desde tu PC, en otra terminal):**
```powershell
# Desde PowerShell en tu PC (estando en red USAL/eduVPN).
# Excluye venv/uploads/reportes para no copiar gigas innecesarios.
scp -r C:\Users\Usuario\Desktop\TFG tfg@prodiasv07.fis.usal.es:~/TFG
```

---

## FASE 3 · Configurar secretos

```bash
cd ~/TFG
cp .env.example .env
nano .env
```

Rellena en `.env`:
- `MYSQL_PASSWORD` y `MYSQL_ROOT_PASSWORD` → contraseñas fuertes.
- `DATABASE_URL` → la MISMA contraseña que `MYSQL_PASSWORD`, host `db`.
- `JWT_SECRET_KEY` → clave aleatoria larga.

Genera valores fuertes con:
```bash
openssl rand -hex 32      # para JWT_SECRET_KEY
openssl rand -base64 24   # para las contraseñas
```

> **Aviso sobre el correo:** las normas advierten de que el servidor **puede no
> enviar correo externo**. Las funciones de la app que dependen de email
> (verificación de cuenta, recuperación de contraseña) podrían no funcionar.
> Deja los campos `EMAIL_*` vacíos si no vas a usarlas y compruébalo pronto;
> si la verificación de email bloquea el registro, habrá que relajarla para la demo.

---

## FASE 4 · Arrancar (primero en HTTP)

```bash
cd ~/TFG
docker compose up -d --build
```

> La primera construcción descarga TensorFlow y demás: tarda varios minutos.

Comprueba que todo está arriba y los logs de la API:
```bash
docker compose ps
docker compose logs -f api      # Ctrl+C para salir
```

Prueba la API (desde el propio servidor o desde un navegador):
```bash
curl http://localhost:8000/health           # dentro del servidor
# y desde fuera:  http://prodiasv07.fis.usal.es/health  → {"status":"ok"}
```

Si responde `{"status":"ok"}`, el backend, la base de datos y el proxy funcionan.

---

## FASE 5 · Activar HTTPS (Let's Encrypt)

```bash
# 1. Obtén el certificado (cambia el correo por el tuyo)
docker compose run --rm certbot certonly \
  --webroot -w /var/www/certbot \
  -d prodiasv07.fis.usal.es \
  --email TU_CORREO@usal.es --agree-tos --no-eff-email

# 2. Cambia la config de Nginx a la versión con TLS
cp nginx/app-ssl.conf nginx/conf.d/app.conf

# 3. Recarga Nginx
docker compose exec nginx nginx -s reload
```

Comprueba: `https://prodiasv07.fis.usal.es/health` → debe cargar con candado 🔒.

### Renovación automática del certificado

Los certificados Let's Encrypt caducan a los 90 días. Crea una tarea cron:
```bash
crontab -e
```
Añade esta línea (renueva cada noche si toca y recarga Nginx):
```
0 3 * * * cd /home/tfg/TFG && docker compose run --rm certbot renew --webroot -w /var/www/certbot && docker compose exec nginx nginx -s reload
```

---

## FASE 6 · La app Android

1. La `BASE_URL` de *release* ya apunta a `https://prodiasv07.fis.usal.es/`.
2. En Android Studio: **Build → Generate Signed Bundle / APK → APK** (release).
3. Pasa el `.apk` al móvil e instálalo (hay que permitir "orígenes desconocidos").
4. Regístrate y prueba un análisis completo de extremo a extremo.

---

## Comandos útiles de mantenimiento

```bash
docker compose ps                 # estado de los contenedores
docker compose logs -f api        # logs de la API en vivo
docker compose restart api        # reiniciar solo la API
docker compose down               # parar todo (los volúmenes PERSISTEN)
docker compose up -d --build      # reconstruir y relanzar tras cambios
docker compose exec db mariadb -u root -p moveinsight   # entrar a la BD
```

Los datos (BD, vídeos, informes) viven en volúmenes Docker y **sobreviven** a
`down`/`up` y a reinicios del servidor. Para borrarlos del todo: `docker compose
down -v` (¡cuidado, elimina la base de datos!).
