package com.moveinsight.presentation.wellness

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moveinsight.domain.model.Incident
import com.moveinsight.domain.model.TimelinePoint
import com.moveinsight.presentation.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Gráfico de Readiness con bandas de incidencias (versión mejorada).
 *
 * Mejoras respecto a la anterior:
 *  - Una única serie (Readiness) — los demás KPIs viven en la pestaña Gráficos.
 *  - Curva suavizada con interpolación cúbica + relleno gradiente bajo la línea.
 *  - Bandas horizontales sutiles para zonas de readiness (rojo / amarillo / verde).
 *  - Bandas verticales con borde redondeado para periodos de incidencia + etiqueta.
 *  - Eje Y con cuatro tags (0 / 25 / 50 / 75 / 100) y eje X con tres fechas.
 *  - Altura mayor (260 dp) y padding más generoso para legibilidad.
 *  - Puntos circulares con halo (anillo) para destacar cada sesión.
 */
@Composable
fun WellnessChart(
    points    : List<TimelinePoint>,
    incidents : List<Incident>,
    rangeDays : Int = 90,
    modifier  : Modifier = Modifier,
) {
    val today      = remember { LocalDate.now() }
    val firstDate  = remember(rangeDays) { today.minusDays(rangeDays.toLong()) }
    val incidentsInRange = remember(incidents, firstDate, today) {
        incidents.filter { isInRange(it, firstDate, today) }
    }

    Column(modifier = modifier) {
        // ── Header con leyenda ────────────────────────────────────────────
        ChartLegend(hasIncidents = incidentsInRange.isNotEmpty())

        Spacer(Modifier.height(12.dp))

        // ── Canvas del gráfico ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NavyMid)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawReadinessChart(
                    points    = points,
                    incidents = incidentsInRange,
                    firstDate = firstDate,
                    today     = today,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Eje X: 3 etiquetas de fecha ───────────────────────────────────
        XAxisLabels(firstDate, today, rangeDays)

        // ── Lista de periodos de incidencia visibles ──────────────────────
        if (incidentsInRange.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            IncidentPeriodsList(incidentsInRange, today)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Leyenda
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChartLegend(hasIncidents: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        LegendItem(label = "Estado físico diario", color = CyanPrimary, isLine = true)
        if (hasIncidents) {
            LegendItem(label = "Periodo de lesión", color = RedAlert, isLine = false)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, isLine: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isLine) {
            Box(
                modifier = Modifier
                    .size(width = 14.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        } else {
            // Mismo estilo que las bandas del gráfico: ribbon sólido arriba + relleno
            Column(
                modifier = Modifier.size(width = 14.dp, height = 12.dp)
                    .clip(RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(color)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(color.copy(alpha = 0.22f))
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Eje X
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun XAxisLabels(firstDate: LocalDate, today: LocalDate, rangeDays: Int) {
    val fmt = remember {
        // Fechas más cortas para rangos largos
        if (rangeDays > 180) DateTimeFormatter.ofPattern("MMM yy")
        else                  DateTimeFormatter.ofPattern("dd MMM")
    }
    val mid = firstDate.plusDays(rangeDays.toLong() / 2)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(firstDate.format(fmt), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(mid.format(fmt),       style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(today.format(fmt),     style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lista de periodos de lesión (debajo del gráfico)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IncidentPeriodsList(incidents: List<Incident>, today: LocalDate) {
    val fmt = remember { DateTimeFormatter.ofPattern("dd MMM") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        incidents.take(4).forEach { inc ->
            val start = runCatching { LocalDate.parse(inc.startDate) }.getOrNull()
            val end   = inc.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val periodText = when {
                start != null && end != null -> "${start.format(fmt)} → ${end.format(fmt)}"
                start != null && inc.isActive -> "${start.format(fmt)} → hoy (activa)"
                start != null                -> start.format(fmt)
                else                          -> "—"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(RedAlert.copy(alpha = if (inc.isActive) 0.85f else 0.45f))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = displayBodyZone(inc.bodyZone),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextPrimary, fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text  = periodText,
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
            }
        }
        if (incidents.size > 4) {
            Text(
                text  = "+ ${incidents.size - 4} más en la lista de incidencias",
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lógica de dibujo
// ─────────────────────────────────────────────────────────────────────────────

private fun isInRange(incident: Incident, first: LocalDate, today: LocalDate): Boolean {
    val start = runCatching { LocalDate.parse(incident.startDate) }.getOrNull() ?: return false
    val end = incident.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
    return !(end.isBefore(first) || start.isAfter(today))
}

/**
 * Calcula un rango Y "bonito" con MÁS zoom basado en los datos:
 *  - Redondeo a múltiplos de 5 (no de 10) → más cercano al dato real.
 *  - Margen de solo 2 puntos (antes 5) para apretar la vista.
 *  - Anclaje superior 100 solo si rawMax ≥ 90 (más estricto que antes).
 *  - Rango mínimo 15 (antes 30) → línea visible incluso con datos muy estables.
 *
 * Ejemplos:
 *   datos en 88-94  →  [85, 100]   (rango 15)
 *   datos en 75-82  →  [70, 85]    (rango 15)
 *   datos en 50-65  →  [45, 70]    (rango 25)
 */
private fun computeYRange(values: List<Float>): Pair<Float, Float> {
    if (values.isEmpty()) return 0f to 100f
    val rawMin = values.min()
    val rawMax = values.max()

    // Redondeo a múltiplos de 5 con margen pequeño
    val yMax: Float = if (rawMax >= 90f) 100f
    else (kotlin.math.ceil((rawMax + 2f) / 5f) * 5f).coerceAtMost(100f)

    var yMin: Float = (kotlin.math.floor((rawMin - 2f) / 5f) * 5f).coerceAtLeast(0f)

    // Rango mínimo: 15 puntos (zoom más cercano que antes)
    if (yMax - yMin < 15f) {
        yMin = (yMax - 15f).coerceAtLeast(0f)
    }
    return yMin to yMax
}

private fun DrawScope.drawReadinessChart(
    points    : List<TimelinePoint>,
    incidents : List<Incident>,
    firstDate : LocalDate,
    today     : LocalDate,
) {
    val totalDays = ChronoUnit.DAYS.between(firstDate, today).coerceAtLeast(1L)
    val padLeft   = 46f
    val padRight  = 14f
    val padTop    = 26f   // espacio para el "ribbon" de las lesiones
    val padBottom = 16f
    val w = size.width  - padLeft - padRight
    val h = size.height - padTop  - padBottom

    // ── Auto-scale del eje Y ──────────────────────────────────────────────
    val readinessValues = points.map { it.readiness.toFloat() }
    val (yMin, yMax) = computeYRange(readinessValues)
    val yRange = (yMax - yMin).coerceAtLeast(1f)

    fun xFor(date: LocalDate): Float {
        val days = ChronoUnit.DAYS.between(firstDate, date).toFloat()
        return padLeft + (days / totalDays) * w
    }
    fun yFor(value: Float): Float {
        val v = value.coerceIn(yMin, yMax)
        return padTop + h - ((v - yMin) / yRange) * h
    }
    /** Dibuja un rectángulo de zona horizontal recortado al rango visible [yMin, yMax]. */
    fun drawZoneBand(zoneTop: Float, zoneBottom: Float, color: Color) {
        val visibleTop    = zoneTop.coerceAtMost(yMax)
        val visibleBottom = zoneBottom.coerceAtLeast(yMin)
        if (visibleTop <= visibleBottom) return  // fuera de rango
        drawRect(
            color   = color,
            topLeft = Offset(padLeft, yFor(visibleTop)),
            size    = Size(w, yFor(visibleBottom) - yFor(visibleTop))
        )
    }

    // ── 1. Bandas horizontales por zona de readiness (sutiles, recortadas al zoom) ──
    val zoneAlpha = 0.06f
    drawZoneBand(40f, 0f,  RedAlert.copy(alpha = zoneAlpha))      // 0-40
    drawZoneBand(70f, 40f, YellowCaution.copy(alpha = zoneAlpha)) // 40-70
    drawZoneBand(100f, 70f, GreenReady.copy(alpha = zoneAlpha))   // 70-100

    // ── 2. Líneas guía Y dinámicas (4 ticks dentro del rango visible) ────
    val gridColor = Color(0xFF2A3F55)
    val tickStep = niceTickStep(yRange)
    val tickStart = (kotlin.math.ceil(yMin / tickStep) * tickStep).toInt()
    val tickValues = generateSequence(tickStart) { it + tickStep.toInt() }
        .takeWhile { it.toFloat() <= yMax }
        .toList()
    tickValues.forEach { v ->
        val y = yFor(v.toFloat())
        drawLine(
            color  = gridColor,
            start  = Offset(padLeft, y),
            end    = Offset(padLeft + w, y),
            strokeWidth = 1f,
            pathEffect  = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
        )
    }

    // ── 3. Bandas verticales de incidencias (más visibles) ───────────────
    val ribbonHeight  = 8f          // barra sólida arriba de la banda
    val labelPadV     = 5f
    val ribbonPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 22f
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    incidents.forEachIndexed { idx, incident ->
        val start = runCatching { LocalDate.parse(incident.startDate) }.getOrNull() ?: return@forEachIndexed
        val end   = incident.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
        val sClamped = if (start.isBefore(firstDate)) firstDate else start
        val eClamped = if (end.isAfter(today)) today else end
        if (eClamped.isBefore(sClamped)) return@forEachIndexed
        val xStart = xFor(sClamped)
        val xEnd   = xFor(eClamped)
        val width  = (xEnd - xStart).coerceAtLeast(3f)

        // 3a. Fondo de la banda (más saturado que antes)
        drawRect(
            color   = RedAlert.copy(alpha = 0.20f),
            topLeft = Offset(xStart, padTop),
            size    = Size(width, h)
        )

        // 3b. "Ribbon" sólido en la parte superior (la marca distintiva)
        drawRect(
            color   = RedAlert.copy(alpha = 0.95f),
            topLeft = Offset(xStart, padTop - ribbonHeight),
            size    = Size(width, ribbonHeight)
        )

        // 3c. Borde lateral izquierdo grueso
        drawLine(
            color  = RedAlert,
            start  = Offset(xStart, padTop - ribbonHeight),
            end    = Offset(xStart, padTop + h),
            strokeWidth = 2.5f
        )
        // 3d. Borde derecho: sólido si terminó, discontinuo si sigue activa hasta hoy
        val ended = incident.endDate != null && end.isBefore(today)
        drawLine(
            color  = RedAlert.copy(alpha = if (ended) 0.85f else 0.55f),
            start  = Offset(xEnd, padTop - ribbonHeight),
            end    = Offset(xEnd, padTop + h),
            strokeWidth = 2f,
            pathEffect = if (ended) null else PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        )

        // 3e. Etiqueta del cuerpo dentro del ribbon (solo si cabe)
        if (width > 70f) {
            val label = displayBodyZone(incident.bodyZone)
            // Truncar texto si el ancho disponible es justito
            val maxChars = ((width - 12f) / 11f).toInt().coerceAtLeast(3)
            val shown = if (label.length > maxChars) label.take(maxChars - 1) + "…" else label
            drawContext.canvas.nativeCanvas.drawText(
                shown,
                xStart + 6f,
                padTop - labelPadV,
                ribbonPaint
            )
        }
    }

    // ── 4. Línea de Readiness con relleno gradiente ──────────────────────
    val pts = points.mapNotNull { p ->
        val date = runCatching { LocalDate.parse(p.date) }.getOrNull() ?: return@mapNotNull null
        Offset(xFor(date), yFor(p.readiness.toFloat()))
    }.sortedBy { it.x }

    if (pts.isNotEmpty()) {
        // 4a. Gradiente bajo la línea (área)
        if (pts.size >= 2) {
            val areaPath = Path().apply {
                moveTo(pts[0].x, padTop + h) // empieza en la base
                lineTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    smoothLineTo(this, pts[i - 1], pts[i])
                }
                lineTo(pts.last().x, padTop + h)
                close()
            }
            drawPath(
                path  = areaPath,
                brush = Brush.verticalGradient(
                    colors      = listOf(CyanPrimary.copy(alpha = 0.30f), CyanPrimary.copy(alpha = 0f)),
                    startY      = padTop,
                    endY        = padTop + h
                )
            )
        }

        // 4b. Línea suavizada
        if (pts.size >= 2) {
            val linePath = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    smoothLineTo(this, pts[i - 1], pts[i])
                }
            }
            drawPath(
                path  = linePath,
                color = CyanPrimary,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
        }

        // 4c. Puntos con halo (anillo + centro)
        pts.forEach { pt ->
            // Halo exterior
            drawCircle(color = CyanPrimary.copy(alpha = 0.25f), radius = 7f, center = pt)
            // Anillo
            drawCircle(
                color = CyanPrimary,
                radius = 5f,
                center = pt,
                style  = Stroke(width = 2f)
            )
            // Centro relleno (color de fondo del card para "vacío")
            drawCircle(color = NavyMid, radius = 3f, center = pt)
        }
    }

    // ── 5. Etiquetas Y dinámicas (en cada tick visible + extremos) ───────
    val labelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(220, 139, 163, 188)
        textSize = 26f
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    // Etiqueta inferior (yMin) y superior (yMax) siempre — más todos los ticks intermedios
    val allLabels = (listOf(yMin.toInt(), yMax.toInt()) + tickValues).toSet().sorted()
    allLabels.forEach { v ->
        val y = yFor(v.toFloat()) + 9f
        drawContext.canvas.nativeCanvas.drawText("$v", 6f, y, labelPaint)
    }
}

/**
 * Devuelve un paso "bonito" para los ticks del eje Y según el rango total visible.
 * Rango ≤20: paso 5; rango ≤50: paso 10; rango grande: paso 25.
 */
private fun niceTickStep(range: Float): Float = when {
    range <= 20f -> 5f
    range <= 50f -> 10f
    else         -> 25f
}

/**
 * Línea suavizada usando interpolación cúbica entre dos puntos consecutivos.
 * Las tangentes se calculan tomando 1/3 del segmento horizontal entre puntos
 * → resultado: curvas naturales sin overshooting.
 */
private fun smoothLineTo(path: Path, prev: Offset, current: Offset) {
    val dx = (current.x - prev.x) / 3f
    path.cubicTo(
        prev.x + dx,    prev.y,
        current.x - dx, current.y,
        current.x,      current.y
    )
}
