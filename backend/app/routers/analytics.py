from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session as DBSession
from sqlalchemy import func as sql_func
import io

from app.auth import get_current_user
from app.database import get_db
from app.models import User, Session, SessionResult
from app.schemas import AnalyticsSummary, InsightResponse

router = APIRouter()


@router.get("/analytics/summary", response_model=AnalyticsSummary)
def get_analytics_summary(
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    # Total sessions
    total = db.query(sql_func.count(Session.id)).filter(
        Session.user_id == current_user.id
    ).scalar() or 0

    # Avg technique (overall_score across all results)
    avg_technique = db.query(sql_func.avg(SessionResult.overall_score)).join(Session).filter(
        Session.user_id == current_user.id
    ).scalar()

    # Max weight
    max_weight = db.query(sql_func.max(Session.weight_kg)).filter(
        Session.user_id == current_user.id
    ).scalar()

    # Best technique score: best average session score
    session_avgs = db.query(
        sql_func.avg(SessionResult.overall_score).label("avg_score")
    ).join(Session).filter(
        Session.user_id == current_user.id
    ).group_by(SessionResult.session_id).all()
    best_technique = max(
        (row.avg_score for row in session_avgs if row.avg_score is not None),
        default=None
    )

    # Max reps in a single session
    rep_counts = db.query(
        SessionResult.session_id,
        sql_func.count(SessionResult.id).label("cnt")
    ).join(Session).filter(
        Session.user_id == current_user.id
    ).group_by(SessionResult.session_id).all()
    max_reps = max((row.cnt for row in rep_counts), default=None)

    # Readiness: based on recent borg scores (simple heuristic)
    recent_sessions = db.query(Session).filter(
        Session.user_id == current_user.id,
        Session.status == "completed"
    ).order_by(Session.created_at.desc()).limit(5).all()

    if recent_sessions:
        avg_borg = sum(s.borg_score or 5 for s in recent_sessions) / len(recent_sessions)
        # Lower borg = more ready. Scale: borg 0-3 -> 80-100, 4-6 -> 50-79, 7-10 -> 20-49
        readiness = max(10, min(100, int(100 - (avg_borg * 8))))
    else:
        # Sin sesiones = totalmente descansado → readiness máximo
        readiness = 100
        avg_borg = 0

    if readiness >= 70:
        label = "high"
    elif readiness >= 40:
        label = "medium"
    else:
        label = "low"

    # Overtraining risk: high borg + low readiness
    overtraining_risk = avg_borg > 7 and readiness < 40

    # Generate insights
    insights: list[InsightResponse] = []

    if total == 0:
        insights.append(InsightResponse(
            type="tip",
            title="Empieza a entrenar",
            message="Registra tu primera sesion para recibir analisis personalizados."
        ))
    else:
        # Technique insight
        if avg_technique is not None:
            avg_t = float(avg_technique)
            if avg_t >= 7.5:
                insights.append(InsightResponse(
                    type="achievement",
                    title="Gran tecnica",
                    message=f"Tu puntuacion media de tecnica es {avg_t:.1f}/10. Sigue asi!"
                ))
            elif avg_t >= 5.0:
                insights.append(InsightResponse(
                    type="tip",
                    title="Tecnica mejorable",
                    message=f"Tu puntuacion media es {avg_t:.1f}/10. Enfocate en profundidad y estabilidad."
                ))
            else:
                insights.append(InsightResponse(
                    type="warning",
                    title="Revisa tu tecnica",
                    message=f"Tu puntuacion media es {avg_t:.1f}/10. Reduce carga y trabaja la forma."
                ))

        # Borg insight
        if avg_borg > 7:
            insights.append(InsightResponse(
                type="warning",
                title="Fatiga elevada",
                message=f"Tu Borg medio reciente es {avg_borg:.1f}. Considera descansar o reducir intensidad."
            ))
        elif avg_borg < 3:
            insights.append(InsightResponse(
                type="tip",
                title="Puedes aumentar intensidad",
                message=f"Tu Borg medio es {avg_borg:.1f}. Podrias incrementar la carga gradualmente."
            ))

        # Volume insight
        completed = db.query(sql_func.count(Session.id)).filter(
            Session.user_id == current_user.id,
            Session.status == "completed"
        ).scalar() or 0
        if completed >= 10:
            insights.append(InsightResponse(
                type="achievement",
                title="Constancia",
                message=f"Llevas {completed} sesiones completadas. La consistencia es clave."
            ))
        elif completed >= 3:
            insights.append(InsightResponse(
                type="tip",
                title="Buen comienzo",
                message=f"Ya tienes {completed} sesiones. Sigue registrando para ver tendencias."
            ))

        # Weight progression insight
        if max_weight and max_weight > 0:
            insights.append(InsightResponse(
                type="tip",
                title="Carga maxima",
                message=f"Tu carga maxima registrada es {max_weight:.0f} kg."
            ))

        # Overtraining alert
        if overtraining_risk:
            insights.append(InsightResponse(
                type="warning",
                title="Riesgo de sobreentrenamiento",
                message=f"Borg medio {avg_borg:.1f} con readiness {readiness}%. Toma al menos 48h de descanso."
            ))

        # Personal records
        if best_technique is not None and best_technique > 0:
            insights.append(InsightResponse(
                type="achievement",
                title="Record de tecnica",
                message=f"Tu mejor sesion alcanzó {float(best_technique):.1f}/10 de media. ¡Supéralo!"
            ))

    return AnalyticsSummary(
        total_sessions=total,
        avg_technique_score=round(float(avg_technique), 1) if avg_technique else None,
        avg_velocity=None,
        max_weight_kg=round(float(max_weight), 1) if max_weight else None,
        best_technique_score=round(float(best_technique), 1) if best_technique else None,
        max_reps=int(max_reps) if max_reps else None,
        overtraining_risk=overtraining_risk,
        readiness_score=readiness,
        readiness_label=label,
        insights=insights,
    )


@router.get("/analytics/pdf")
def export_pdf(
    current_user: User = Depends(get_current_user),
    db: DBSession = Depends(get_db),
):
    """Generate a professional PDF training report."""
    try:
        from reportlab.lib.pagesizes import A4
        from reportlab.lib import colors
        from reportlab.lib.units import cm
        from reportlab.platypus import (
            SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle,
            HRFlowable, PageBreak, Image as RLImage
        )
        from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
        from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT
        from reportlab.platypus import KeepTogether
    except ImportError:
        raise HTTPException(status_code=501, detail="Exportacion PDF no disponible. Instala reportlab.")

    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        import matplotlib.ticker as ticker
        HAS_MPL = True
    except ImportError:
        HAS_MPL = False

    import datetime

    # ── Colour palette ────────────────────────────────────────────────────────
    NAV_DEEP   = colors.HexColor("#0D1B2A")
    NAV_MID    = colors.HexColor("#1A2E42")
    CYAN       = colors.HexColor("#00C8FF")
    ORANGE     = colors.HexColor("#FF6B35")
    YELLOW     = colors.HexColor("#F5C518")
    GREEN      = colors.HexColor("#4CAF50")
    TEXT_LIGHT = colors.HexColor("#E8F4FD")
    TEXT_DIM   = colors.HexColor("#8BA3BC")

    # ── Fetch data ────────────────────────────────────────────────────────────
    sessions = (
        db.query(Session)
        .filter(Session.user_id == current_user.id, Session.status == "completed")
        .order_by(Session.created_at.asc())
        .all()
    )

    session_data = []  # list of dicts with session + reps
    for s in sessions:
        reps = db.query(SessionResult).filter(SessionResult.session_id == s.id).order_by(SessionResult.rep_number).all()
        avg_score = sum(r.overall_score for r in reps) / len(reps) if reps else None
        session_data.append({"session": s, "reps": reps, "avg_score": avg_score})

    # ── Build analytics (same logic as summary endpoint) ──────────────────────
    total = len(sessions)
    avg_technique = db.query(sql_func.avg(SessionResult.overall_score)).join(Session).filter(
        Session.user_id == current_user.id).scalar()
    max_weight = db.query(sql_func.max(Session.weight_kg)).filter(
        Session.user_id == current_user.id).scalar()
    recent = sessions[-5:] if sessions else []
    if recent:
        avg_borg = sum(s.borg_score or 5 for s in recent) / len(recent)
        readiness = max(10, min(100, int(100 - (avg_borg * 8))))
    else:
        avg_borg = 5.0
        readiness = 50
    readiness_label = "Alta" if readiness >= 70 else ("Media" if readiness >= 40 else "Baja")

    # ── Generate matplotlib charts ────────────────────────────────────────────
    chart_images = []
    if HAS_MPL and sessions:
        indices = list(range(1, len(sessions) + 1))
        chart_defs = [
            {
                "title": "Puntuación de Técnica",
                "values": [sd["avg_score"] for sd in session_data],
                "ylabel": "Puntuación (/10)",
                "color": "#00C8FF",
                "ylim": (0, 10),
            },
            {
                "title": "Progresión de Carga",
                "values": [s.weight_kg or 0 for s in sessions],
                "ylabel": "Carga (kg)",
                "color": "#FF6B35",
                "ylim": None,
            },
            {
                "title": "Esfuerzo Percibido (Borg)",
                "values": [s.borg_score or 0 for s in sessions],
                "ylabel": "Borg (0-10)",
                "color": "#F5C518",
                "ylim": (0, 10),
            },
        ]
        for cd in chart_defs:
            vals = cd["values"]
            if not any(v is not None for v in vals):
                continue
            clean_x = [i for i, v in zip(indices, vals) if v is not None]
            clean_y = [v for v in vals if v is not None]

            fig, ax = plt.subplots(figsize=(7, 2.8))
            fig.patch.set_facecolor("#0D1B2A")
            ax.set_facecolor("#1A2E42")
            ax.plot(clean_x, clean_y, color=cd["color"], linewidth=2.2,
                    marker="o", markersize=6, markerfacecolor=cd["color"],
                    markeredgecolor="#0D1B2A", markeredgewidth=1.5)
            ax.set_title(cd["title"], color="#E8F4FD", fontsize=11, pad=8)
            ax.set_xlabel("Sesión", color="#8BA3BC", fontsize=9)
            ax.set_ylabel(cd["ylabel"], color="#8BA3BC", fontsize=9)
            ax.tick_params(colors="#8BA3BC", labelsize=8)
            for spine in ax.spines.values():
                spine.set_edgecolor("#1A2E42")
            ax.xaxis.set_major_locator(ticker.MaxNLocator(integer=True))
            if cd["ylim"]:
                ax.set_ylim(*cd["ylim"])
            ax.grid(axis="y", color="#2A3F55", linewidth=0.7, linestyle="--")
            plt.tight_layout(pad=0.6)

            buf = io.BytesIO()
            fig.savefig(buf, format="png", dpi=130, facecolor=fig.get_facecolor())
            plt.close(fig)
            buf.seek(0)
            chart_images.append((cd["title"], buf))

    # ── Build PDF with Platypus ───────────────────────────────────────────────
    pdf_buffer = io.BytesIO()
    doc = SimpleDocTemplate(
        pdf_buffer,
        pagesize=A4,
        leftMargin=2*cm, rightMargin=2*cm,
        topMargin=2.5*cm, bottomMargin=2*cm,
        title="MoveInsight – Informe de Entrenamiento",
        author=current_user.full_name,
    )

    styles = getSampleStyleSheet()

    def style(name, **kw):
        s = ParagraphStyle(name, parent=styles["Normal"], **kw)
        return s

    H1 = style("H1", fontSize=28, textColor=TEXT_LIGHT, spaceAfter=4,
               alignment=TA_CENTER, fontName="Helvetica-Bold")
    H2 = style("H2", fontSize=16, textColor=CYAN, spaceBefore=14, spaceAfter=6,
               fontName="Helvetica-Bold")
    H3 = style("H3", fontSize=12, textColor=TEXT_LIGHT, spaceBefore=10, spaceAfter=4,
               fontName="Helvetica-Bold")
    BODY = style("BODY", fontSize=10, textColor=TEXT_LIGHT, spaceAfter=4,
                 leading=14, fontName="Helvetica")
    DIM  = style("DIM", fontSize=9, textColor=TEXT_DIM, spaceAfter=2,
                 fontName="Helvetica")
    CENT = style("CENT", fontSize=9, textColor=TEXT_LIGHT, alignment=TA_CENTER,
                 fontName="Helvetica")

    story = []

    # ── Cover — athlete info only (title drawn via on_page callback) ───────────
    story.append(Spacer(1, 3.8*cm))   # Reserve space for manually-drawn title + line
    story.append(Paragraph(f"<b>Atleta:</b>  {current_user.full_name}", BODY))
    story.append(Paragraph(f"<b>Email:</b>   {current_user.email}", BODY))
    story.append(Paragraph(
        f"<b>Fecha de generación:</b>  {datetime.date.today().strftime('%d/%m/%Y')}", BODY))
    story.append(Spacer(1, 0.8*cm))

    # ── Summary table ─────────────────────────────────────────────────────────
    story.append(Paragraph("Resumen Global", H2))
    story.append(HRFlowable(width="100%", thickness=1, color=NAV_MID, spaceAfter=10))

    avg_t_str = f"{float(avg_technique):.1f}/10" if avg_technique else "—"
    max_w_str = f"{float(max_weight):.0f} kg" if max_weight else "—"
    summary_data = [
        ["Métrica", "Valor"],
        ["Sesiones completadas", str(total)],
        ["Puntuación técnica media", avg_t_str],
        ["Carga máxima registrada", max_w_str],
        ["Preparación (Readiness)", f"{readiness}% — {readiness_label}"],
    ]
    tw = doc.width
    st = TableStyle([
        ("BACKGROUND",  (0, 0), (-1,  0), NAV_MID),
        ("TEXTCOLOR",   (0, 0), (-1,  0), CYAN),
        ("FONTNAME",    (0, 0), (-1,  0), "Helvetica-Bold"),
        ("FONTSIZE",    (0, 0), (-1,  0), 10),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [NAV_DEEP, NAV_MID]),
        ("TEXTCOLOR",   (0, 1), (-1, -1), TEXT_LIGHT),
        ("FONTNAME",    (0, 1), (-1, -1), "Helvetica"),
        ("FONTSIZE",    (0, 1), (-1, -1), 10),
        ("GRID",        (0, 0), (-1, -1), 0.4, colors.HexColor("#2A3F55")),
        ("LEFTPADDING",  (0, 0), (-1, -1), 10),
        ("RIGHTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING",   (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING",(0, 0), (-1, -1), 7),
    ])
    t = Table(summary_data, colWidths=[tw*0.55, tw*0.45])
    t.setStyle(st)
    story.append(t)
    story.append(Spacer(1, 0.5*cm))

    # ── Insights ──────────────────────────────────────────────────────────────
    # Re-compute insights inline (mirror analytics summary logic)
    insights_list = []
    if total == 0:
        insights_list.append(("tip", "Empieza a entrenar",
            "Registra tu primera sesión para recibir análisis personalizados."))
    else:
        if avg_technique:
            at = float(avg_technique)
            if at >= 7.5:
                insights_list.append(("achievement", "Gran técnica",
                    f"Tu puntuación media de técnica es {at:.1f}/10. ¡Sigue así!"))
            elif at >= 5.0:
                insights_list.append(("tip", "Técnica mejorable",
                    f"Tu puntuación media es {at:.1f}/10. Enfócate en profundidad y estabilidad."))
            else:
                insights_list.append(("warning", "Revisa tu técnica",
                    f"Tu puntuación media es {at:.1f}/10. Reduce carga y trabaja la forma."))
        if avg_borg > 7:
            insights_list.append(("warning", "Fatiga elevada",
                f"Tu Borg medio reciente es {avg_borg:.1f}. Considera descansar o reducir intensidad."))
        elif avg_borg < 3:
            insights_list.append(("tip", "Puedes aumentar intensidad",
                f"Tu Borg medio es {avg_borg:.1f}. Podrías incrementar la carga gradualmente."))
        if total >= 10:
            insights_list.append(("achievement", "Constancia",
                f"Llevas {total} sesiones completadas. La consistencia es clave."))
        elif total >= 3:
            insights_list.append(("tip", "Buen comienzo",
                f"Ya tienes {total} sesiones. Sigue registrando para ver tendencias."))
        if max_weight and max_weight > 0:
            insights_list.append(("tip", "Carga máxima",
                f"Tu carga máxima registrada es {float(max_weight):.0f} kg."))

    if insights_list:
        story.append(Spacer(1, 0.4*cm))
        story.append(Paragraph("Insights Personalizados", H2))
        story.append(HRFlowable(width="100%", thickness=1, color=NAV_MID, spaceAfter=10))
        type_colors  = {"achievement": GREEN, "tip": CYAN, "warning": ORANGE}
        type_labels  = {"achievement": "LOGRO", "tip": "CONSEJO", "warning": "AVISO"}
        for ins_type, ins_title, ins_msg in insights_list:
            c        = type_colors.get(ins_type, CYAN)
            tag_text = type_labels.get(ins_type, ins_type[:6].upper())
            tag_style = style(f"tag_{ins_type}", fontSize=8, textColor=c,
                              fontName="Helvetica-Bold", spaceAfter=1)
            ins_data = [
                [Paragraph(tag_text, tag_style),
                 Paragraph(f"<b>{ins_title}</b>", style("ih", fontSize=10,
                     textColor=TEXT_LIGHT, fontName="Helvetica-Bold"))],
                ["",
                 Paragraph(ins_msg, style("ib", fontSize=9, textColor=TEXT_LIGHT,
                     fontName="Helvetica", leading=13))],
            ]
            ins_t = Table(ins_data, colWidths=[2.2*cm, tw - 2.2*cm])
            ins_t.setStyle(TableStyle([
                ("BACKGROUND",  (0, 0), (-1, -1), NAV_MID),
                ("LEFTPADDING",  (0, 0), (-1, -1), 8),
                ("RIGHTPADDING", (0, 0), (-1, -1), 8),
                ("TOPPADDING",   (0, 0), (0, 0), 8),
                ("BOTTOMPADDING",(0, -1),(-1, -1), 8),
                ("LINEAFTER",    (0, 0), (0, -1), 3, c),
                ("VALIGN",       (0, 0), (-1, -1), "MIDDLE"),
            ]))
            story.append(ins_t)
            story.append(Spacer(1, 6))

    # ── Charts ────────────────────────────────────────────────────────────────
    if chart_images:
        story.append(PageBreak())
        story.append(Paragraph("Gráficos de Progresión", H2))
        story.append(HRFlowable(width="100%", thickness=1, color=NAV_MID, spaceAfter=14))
        for chart_title, chart_buf in chart_images:
            story.append(Paragraph(chart_title, H3))
            img = RLImage(chart_buf, width=doc.width, height=doc.width * 0.38)
            story.append(img)
            story.append(Spacer(1, 0.6*cm))

    # ── Sessions detail ───────────────────────────────────────────────────────
    if session_data:
        story.append(PageBreak())
        story.append(Paragraph("Detalle de Sesiones", H2))
        story.append(HRFlowable(width="100%", thickness=1, color=NAV_MID, spaceAfter=12))

        for idx, sd in enumerate(session_data, start=1):
            s   = sd["session"]
            reps = sd["reps"]
            avg_s = sd["avg_score"]
            date_str = str(s.created_at)[:10] if s.created_at else "—"
            weight_str = f"{s.weight_kg:.0f} kg" if s.weight_kg else "Libre"
            avg_str = f"{avg_s:.1f}/10" if avg_s is not None else "—"

            # Session header
            hdr_data = [[
                Paragraph(f"Sesión {idx}  —  {date_str}", style("sh", fontSize=11,
                    textColor=CYAN, fontName="Helvetica-Bold")),
                Paragraph(f"Carga: {weight_str}   Borg: {s.borg_score or '—'}/10   "
                          f"Técnica: {avg_str}   Reps: {len(reps)}", style("sm", fontSize=9,
                    textColor=TEXT_DIM, fontName="Helvetica", alignment=TA_RIGHT)),
            ]]
            hdr_t = Table(hdr_data, colWidths=[tw*0.5, tw*0.5])
            hdr_t.setStyle(TableStyle([
                ("BACKGROUND",  (0, 0), (-1, -1), NAV_MID),
                ("LEFTPADDING",  (0, 0), (-1, -1), 10),
                ("RIGHTPADDING", (0, 0), (-1, -1), 10),
                ("TOPPADDING",   (0, 0), (-1, -1), 8),
                ("BOTTOMPADDING",(0, 0), (-1, -1), 8),
                ("LINEABOVE",    (0, 0), (-1, 0), 2, CYAN),
                ("VALIGN",       (0, 0), (-1, -1), "MIDDLE"),
            ]))
            story.append(hdr_t)

            if reps:
                rep_header = ["Rep", "Profundidad", "Torso", "Estabilidad",
                              "Rodillas", "Ritmo", "Global"]
                rep_rows = [rep_header]
                for r in reps:
                    rep_rows.append([
                        str(r.rep_number),
                        f"{r.depth_score:.1f}",
                        f"{r.torso_score:.1f}",
                        f"{r.stability_score:.1f}",
                        f"{r.knees_score:.1f}",
                        f"{r.rhythm_score:.1f}",
                        f"{r.overall_score:.1f}",
                    ])
                col_w = tw / len(rep_header)
                rep_t = Table(rep_rows, colWidths=[col_w] * len(rep_header))
                rep_t.setStyle(TableStyle([
                    ("BACKGROUND",  (0, 0), (-1,  0), NAV_DEEP),
                    ("TEXTCOLOR",   (0, 0), (-1,  0), TEXT_DIM),
                    ("FONTNAME",    (0, 0), (-1,  0), "Helvetica-Bold"),
                    ("FONTSIZE",    (0, 0), (-1, -1), 8),
                    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [NAV_DEEP, NAV_MID]),
                    ("TEXTCOLOR",   (0, 1), (-1, -1), TEXT_LIGHT),
                    ("FONTNAME",    (0, 1), (-1, -1), "Helvetica"),
                    ("GRID",        (0, 0), (-1, -1), 0.3, colors.HexColor("#1A2E42")),
                    ("ALIGN",       (0, 0), (-1, -1), "CENTER"),
                    ("TOPPADDING",   (0, 0), (-1, -1), 5),
                    ("BOTTOMPADDING",(0, 0), (-1, -1), 5),
                ]))
                story.append(rep_t)
            else:
                story.append(Paragraph("Sin datos de repeticiones.", DIM))

            story.append(Spacer(1, 0.4*cm))

    # ── Build ─────────────────────────────────────────────────────────────────
    # Dark background on every page via canvas callback
    def on_page(canvas_obj, doc_obj):
        canvas_obj.saveState()
        # Dark background on every page
        canvas_obj.setFillColor(NAV_DEEP)
        canvas_obj.rect(0, 0, A4[0], A4[1], fill=1, stroke=0)
        # Cover title — drawn only on page 1, directly on canvas to avoid any overlap
        if canvas_obj.getPageNumber() == 1:
            mid_x = A4[0] / 2.0
            canvas_obj.setFont("Helvetica-Bold", 32)
            canvas_obj.setFillColor(TEXT_LIGHT)
            canvas_obj.drawCentredString(mid_x, A4[1] - 2.6*cm, "MoveInsight")
            canvas_obj.setFont("Helvetica", 15)
            canvas_obj.setFillColor(CYAN)
            canvas_obj.drawCentredString(mid_x, A4[1] - 3.5*cm, "Informe de Entrenamiento")
            canvas_obj.setStrokeColor(CYAN)
            canvas_obj.setLineWidth(2)
            canvas_obj.line(2*cm, A4[1] - 4.1*cm, A4[0] - 2*cm, A4[1] - 4.1*cm)
        canvas_obj.restoreState()

    doc.build(story, onFirstPage=on_page, onLaterPages=on_page)
    pdf_buffer.seek(0)

    return StreamingResponse(
        pdf_buffer,
        media_type="application/pdf",
        headers={"Content-Disposition": "attachment; filename=moveinsight_report.pdf"},
    )
