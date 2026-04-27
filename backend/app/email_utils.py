"""Utilidades de envío de correo electrónico para MoveInsight.

Usa smtplib estándar de Python con TLS (Gmail App Password).
Configuración en .env:  EMAIL_HOST, EMAIL_PORT, EMAIL_USERNAME, EMAIL_PASSWORD, EMAIL_FROM
"""
import random
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from app.config import settings


def generate_code() -> str:
    """Genera un código numérico de 6 dígitos con ceros a la izquierda."""
    return f"{random.randint(0, 999999):06d}"


def _send(to: str, subject: str, html: str) -> None:
    """Envía un correo HTML mediante SMTP con STARTTLS."""
    msg = MIMEMultipart("alternative")
    msg["Subject"] = subject
    msg["From"]    = settings.EMAIL_FROM
    msg["To"]      = to
    msg.attach(MIMEText(html, "html", "utf-8"))

    with smtplib.SMTP(settings.EMAIL_HOST, settings.EMAIL_PORT, timeout=10) as smtp:
        smtp.ehlo()
        smtp.starttls()
        smtp.login(settings.EMAIL_USERNAME, settings.EMAIL_PASSWORD)
        smtp.sendmail(settings.EMAIL_FROM, to, msg.as_string())


def send_verification_email(to: str, code: str) -> None:
    subject = "Verifica tu cuenta en MoveInsight"
    html = f"""
    <div style="font-family:sans-serif;max-width:480px;margin:auto;padding:32px;
                background:#0D1B2A;color:#E0E0E0;border-radius:12px;">
      <h2 style="color:#00E5FF;margin-top:0">MoveInsight</h2>
      <p>Gracias por registrarte. Introduce este código en la app para verificar tu cuenta:</p>
      <div style="font-size:40px;font-weight:bold;letter-spacing:12px;
                  color:#00E5FF;text-align:center;padding:16px 0">{code}</div>
      <p style="color:#888;font-size:13px">Expira en <b>15 minutos</b>.
         Si no creaste esta cuenta, ignora este correo.</p>
    </div>"""
    _send(to, subject, html)


def send_reset_email(to: str, code: str) -> None:
    subject = "Restablecer contraseña — MoveInsight"
    html = f"""
    <div style="font-family:sans-serif;max-width:480px;margin:auto;padding:32px;
                background:#0D1B2A;color:#E0E0E0;border-radius:12px;">
      <h2 style="color:#00E5FF;margin-top:0">MoveInsight</h2>
      <p>Recibimos una solicitud para restablecer tu contraseña.
         Usa este código en la app:</p>
      <div style="font-size:40px;font-weight:bold;letter-spacing:12px;
                  color:#00E5FF;text-align:center;padding:16px 0">{code}</div>
      <p style="color:#888;font-size:13px">Expira en <b>10 minutos</b>.
         Si no lo solicitaste, ignora este correo — tu contraseña no cambiará.</p>
    </div>"""
    _send(to, subject, html)
