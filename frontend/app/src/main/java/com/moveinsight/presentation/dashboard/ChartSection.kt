package com.moveinsight.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moveinsight.domain.model.Incident
import com.moveinsight.domain.model.SessionDetail
import com.moveinsight.presentation.theme.*
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollState
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.compose.style.currentChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.chart.copy
import com.patrykandpatrick.vico.core.component.shape.ShapeComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.ChartEntryModel
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.core.scroll.InitialScroll
import java.time.LocalDate

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun String.toShortDate(): String = try {
    val parts = this.take(10).split("-")
    if (parts.size == 3) "${parts[2]}/${parts[1]}" else this.take(5)
} catch (_: Exception) { "" }

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this.take(10)) }.getOrNull()

/** Offset del eje Y de Vico — alinea las píldoras de fecha con el área de trazado. */
private val VICO_Y_AXIS_WIDTH = 38.dp
private val CHART_HEIGHT      = 180.dp
private val CHART_RIGHT_PAD   = 8.dp

// ─────────────────────────────────────────────────────────────────────────────
// Cálculo de rangos de lesión sobre índices de sesión
// ─────────────────────────────────────────────────────────────────────────────

private data class SessionInjuryRange(
    // Posiciones en "unidades de sesión"; admiten fracción para poder dibujar la
    // banda en el hueco entre dos sesiones cuando la lesión fue con reposo total.
    val firstIdx : Float,
    val lastIdx  : Float,
    val isActive : Boolean,
)

private fun computeInjurySessionRanges(
    sessions  : List<SessionDetail>,
    incidents : List<Incident>,
): List<SessionInjuryRange> {
    if (sessions.isEmpty() || incidents.isEmpty()) return emptyList()
    val today = LocalDate.now()
    val sessionDates = sessions.map { it.createdAt.toLocalDateOrNull() }

    return incidents.mapNotNull { inc ->
        val start = inc.startDate.toLocalDateOrNull() ?: return@mapNotNull null
        val end   = inc.endDate?.toLocalDateOrNull() ?: today
        if (end.isBefore(start)) return@mapNotNull null

        val firstIdx = sessionDates.indexOfFirst { d ->
            d != null && !d.isBefore(start) && !d.isAfter(end)
        }
        if (firstIdx >= 0) {
            val lastIdx = sessionDates.indexOfLast { d ->
                d != null && !d.isBefore(start) && !d.isAfter(end)
            }
            return@mapNotNull SessionInjuryRange(firstIdx.toFloat(), lastIdx.toFloat(), inc.isActive)
        }
        // Sin sesiones dentro del periodo (reposo completo): la banda se dibuja
        // en el hueco entre la última sesión anterior y la primera posterior.
        val before = sessionDates.indexOfLast  { d -> d != null && d.isBefore(start) }
        val after  = sessionDates.indexOfFirst { d -> d != null && d.isAfter(end) }
        when {
            before >= 0 && after >= 0 -> SessionInjuryRange(before + 0.35f, after - 0.35f, inc.isActive)
            before >= 0               -> SessionInjuryRange(before + 0.35f, before + 0.9f, inc.isActive)
            after  >= 0               -> SessionInjuryRange(after - 0.9f, after - 0.35f, inc.isActive)
            else                      -> null
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cálculo del rango visible (función pura → fácil de razonar/testear)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Devuelve el rango de índices de sesión visibles en el viewport.
 *
 * Se basa en cómo Vico distribuye las entradas:
 *  - Si scrollMax ≤ 0 → todo cabe sin scroll → rango completo.
 *  - Si hay scroll → cada entrada ocupa un "segmento" centrado de ancho
 *    `(plotWidth + scrollMax) / N`. Mapeamos scroll → índice.
 */
private fun computeVisibleRange(
    scrollValue   : Float,
    scrollMax     : Float,
    viewportWidth : Int,
    yAxisPx       : Float,
    rightPx       : Float,
    totalEntries  : Int,
): IntRange {
    if (totalEntries <= 0) return IntRange.EMPTY
    if (totalEntries == 1 || viewportWidth <= 0) return 0..(totalEntries - 1)

    val plotWidth = (viewportWidth - yAxisPx - rightPx).coerceAtLeast(1f)
    if (scrollMax <= 0.5f) return 0..(totalEntries - 1)   // todo entra

    val segmentWidth = (scrollMax + plotWidth) / totalEntries
    val firstIdx = (scrollValue / segmentWidth).toInt().coerceIn(0, totalEntries - 1)
    val lastIdx  = ((scrollValue + plotWidth) / segmentWidth).toInt()
        .coerceIn(firstIdx, totalEntries - 1)
    return firstIdx..lastIdx
}

// ─────────────────────────────────────────────────────────────────────────────
// Gráficos públicos
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TechniqueLineChart(
    sessions  : List<SessionDetail>,
    incidents : List<Incident> = emptyList(),
    modifier  : Modifier = Modifier,
) {
    val data = sessions.filter { it.isCompleted && it.overallScore != null }.reversed()
    if (data.isEmpty()) { EmptyChartMessage("Sin datos de técnica todavía", modifier); return }

    ScrollableLineChart(
        title       = "Puntuación de Técnica",
        accentColor = CyanPrimary,
        data        = data,
        valueOf     = { it.overallScore ?: 0f },
        incidents   = incidents,
        modifier    = modifier,
    )
}

@Composable
fun LoadProgressionChart(
    sessions  : List<SessionDetail>,
    incidents : List<Incident> = emptyList(),
    modifier  : Modifier = Modifier,
) {
    val data = sessions.reversed()
    if (data.isEmpty()) { EmptyChartMessage("Sin sesiones registradas", modifier); return }

    ScrollableLineChart(
        title       = "Progresión de Carga",
        accentColor = OrangePower,
        data        = data,
        valueOf     = { it.weightKg },
        incidents   = incidents,
        modifier    = modifier,
    )
}

@Composable
fun BorgScaleChart(
    sessions  : List<SessionDetail>,
    incidents : List<Incident> = emptyList(),
    modifier  : Modifier = Modifier,
) {
    val data = sessions.reversed()
    if (data.isEmpty()) { EmptyChartMessage("Sin datos de esfuerzo todavía", modifier); return }

    ScrollableLineChart(
        title       = "Escala de Borg (Esfuerzo)",
        accentColor = YellowCaution,
        data        = data,
        valueOf     = { it.borgScore.toFloat() },
        incidents   = incidents,
        modifier    = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable interno común — tracking de scroll + overlay + fechas dinámicas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScrollableLineChart(
    title       : String,
    accentColor : Color,
    data        : List<SessionDetail>,
    valueOf     : (SessionDetail) -> Float,
    incidents   : List<Incident>,
    modifier    : Modifier = Modifier,
) {
    val producer = remember(data) {
        ChartEntryModelProducer(data.mapIndexed { i, s -> entryOf(i.toFloat(), valueOf(s)) })
    }
    val injuryRanges = remember(data, incidents) { computeInjurySessionRanges(data, incidents) }

    // ── Scroll state (Vico) — abre mostrando las últimas sesiones ────────
    val scrollState = rememberChartScrollState()
    // El parámetro de tipo es necesario porque AutoScrollCondition.Never es genérica
    // en Model y el compilador no puede inferirlo desde initialScroll por sí solo.
    val scrollSpec  = rememberChartScrollSpec<ChartEntryModel>(
        initialScroll = InitialScroll.End,
    )

    // ── Tamaño del viewport para mapear scroll → índices ─────────────────
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val yAxisPx = with(density) { VICO_Y_AXIS_WIDTH.toPx() }
    val rightPx = with(density) { CHART_RIGHT_PAD.toPx() }

    // ── Rango visible reactivo: se recalcula al hacer scroll o resize ────
    val visibleRange by remember(data.size) {
        derivedStateOf {
            computeVisibleRange(
                scrollValue   = scrollState.value,
                scrollMax     = scrollState.maxValue,
                viewportWidth = viewportSize.width,
                yAxisPx       = yAxisPx,
                rightPx       = rightPx,
                totalEntries  = data.size,
            )
        }
    }

    // Fechas dinámicas: primera y última de las sesiones VISIBLES
    val firstDate = data.getOrNull(visibleRange.first)?.createdAt?.toShortDate() ?: ""
    val lastDate  = data.getOrNull(visibleRange.last)?.createdAt?.toShortDate()  ?: ""

    ChartContainer(
        title       = title,
        firstDate   = firstDate,
        lastDate    = lastDate,
        accentColor = accentColor,
        modifier    = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .onSizeChanged { viewportSize = it }
        ) {
            // 1. Overlay de bandas de lesión — debajo del gráfico, solo segmentos visibles
            if (injuryRanges.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawInjuryBands(
                        ranges       = injuryRanges,
                        totalPoints  = data.size,
                        scrollValue  = scrollState.value,
                        scrollMax    = scrollState.maxValue,
                        yAxisPx      = yAxisPx,
                        rightPx      = rightPx,
                    )
                }
            }

            // 2. Gráfico Vico encima
            ProvideChartStyle(currentChartStyle.copy(
                lineChart = currentChartStyle.lineChart.copy(lines = listOf(
                    currentChartStyle.lineChart.lines.first().copy(
                        lineColor   = accentColor.toArgb(),
                        point       = ShapeComponent(Shapes.pillShape, accentColor.toArgb()),
                        pointSizeDp = 7f
                    )
                ))
            )) {
                Chart(
                    chart              = lineChart(),
                    chartModelProducer = producer,
                    chartScrollSpec    = scrollSpec,
                    chartScrollState   = scrollState,
                    startAxis  = rememberStartAxis(itemPlacer = remember { AxisItemPlacer.Vertical.default(5) }),
                    bottomAxis = rememberBottomAxis(),
                    modifier   = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dibujo de bandas de lesión (consciente del scroll)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Mapeo de índices de sesión → píxeles del viewport considerando el scroll de Vico:
 *
 *    posiciónAbsoluta(idx) = (idx + 0.5) · segmentWidth
 *    posiciónViewport(idx) = posiciónAbsoluta(idx) − scrollValue + yAxisPx
 *
 * Donde segmentWidth = (plotWidth + scrollMax) / N.
 *
 * El band se recorta al área visible; si está totalmente fuera del viewport
 * no se dibuja nada. El borde lateral solo se pinta si su X correspondiente
 * cae dentro del área de trazado (evita "bordes fantasma" en los recortes).
 */
private fun DrawScope.drawInjuryBands(
    ranges      : List<SessionInjuryRange>,
    totalPoints : Int,
    scrollValue : Float,
    scrollMax   : Float,
    yAxisPx     : Float,
    rightPx     : Float,
) {
    if (ranges.isEmpty() || totalPoints <= 0) return
    val plotWidth = (size.width - yAxisPx - rightPx).coerceAtLeast(1f)
    val plotLeft  = yAxisPx
    val plotRight = yAxisPx + plotWidth

    val isScrollable = scrollMax > 0.5f && totalPoints > 1
    val totalContent = if (isScrollable) plotWidth + scrollMax else plotWidth

    fun xAbsolute(pos: Float): Float = when {
        totalPoints == 1 -> totalContent / 2f
        isScrollable     -> (pos + 0.5f) * (totalContent / totalPoints)
        else             -> (pos / (totalPoints - 1)) * plotWidth
    }

    val expand   = 10f
    val ribbonH  = 6f

    ranges.forEach { range ->
        val absLeft  = xAbsolute(range.firstIdx) - expand
        val absRight = xAbsolute(range.lastIdx)  + expand

        // Conversión a coordenadas del viewport. En modo "no scrollable" el offset
        // sobre el plot es ya `xAbsolute(idx)`; en modo scrollable hay que restar el scroll.
        val offset = plotLeft - (if (isScrollable) scrollValue else 0f)
        val viewportLeft  = offset + absLeft
        val viewportRight = offset + absRight

        // Recortar al área de trazado
        val left  = viewportLeft.coerceAtLeast(plotLeft)
        val right = viewportRight.coerceAtMost(plotRight)

        // Saltar si está totalmente fuera o el ancho recortado es ínfimo
        if (right <= plotLeft + 0.5f || left >= plotRight - 0.5f) return@forEach
        if (right - left < 4f) return@forEach
        val width = right - left

        // Fondo + ribbon superior
        drawRect(
            color   = RedAlert.copy(alpha = 0.15f),
            topLeft = Offset(left, 0f),
            size    = Size(width, size.height)
        )
        drawRect(
            color   = RedAlert.copy(alpha = 0.90f),
            topLeft = Offset(left, 0f),
            size    = Size(width, ribbonH)
        )
        // Bordes laterales solo si NO están recortados (es decir, el inicio/fin
        // real de la lesión sí cae dentro del viewport)
        if (viewportLeft >= plotLeft - 0.5f) {
            drawLine(
                color = RedAlert,
                start = Offset(left, 0f),
                end   = Offset(left, size.height),
                strokeWidth = 1.5f
            )
        }
        if (viewportRight <= plotRight + 0.5f) {
            drawLine(
                color = RedAlert.copy(alpha = if (range.isActive) 0.55f else 0.85f),
                start = Offset(right, 0f),
                end   = Offset(right, size.height),
                strokeWidth = 1.5f
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ChartContainer — título + fila de fechas (dinámicas) + gráfico
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChartContainer(
    title       : String,
    firstDate   : String,
    lastDate    : String,
    accentColor : Color,
    modifier    : Modifier = Modifier,
    content     : @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        DateRow(firstDate = firstDate, lastDate = lastDate, accentColor = accentColor)
        Spacer(Modifier.height(1.dp))
        content()
    }
}

@Composable
private fun DateRow(firstDate: String, lastDate: String, accentColor: Color) {
    if (firstDate.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = VICO_Y_AXIS_WIDTH, end = 4.dp),
        horizontalArrangement = if (firstDate != lastDate)
            Arrangement.SpaceBetween else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DatePill(firstDate, if (firstDate != lastDate) TextSecondary else accentColor)
        if (firstDate != lastDate) {
            DatePill(lastDate, accentColor)
        }
    }
}

@Composable
private fun DatePill(date: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.13f),
        shape = RoundedCornerShape(3.dp)
    ) {
        Text(
            text     = date,
            style    = MaterialTheme.typography.labelSmall.copy(
                fontSize   = 9.sp,
                fontWeight = FontWeight.Medium
            ),
            color    = color.copy(alpha = 0.80f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EmptyChartMessage(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier         = modifier.fillMaxWidth().height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
