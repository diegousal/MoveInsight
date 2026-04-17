package com.moveinsight.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moveinsight.domain.model.SessionDetail
import com.moveinsight.presentation.theme.*
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.compose.style.currentChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.chart.copy
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.component.shape.ShapeComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

// ── Helper: "2024-12-15T..." → "15/12" ──────────────────────────────────────
private fun String.toShortDate(): String = try {
    val parts = this.take(10).split("-")
    if (parts.size == 3) "${parts[2]}/${parts[1]}" else this.take(5)
} catch (_: Exception) { "" }

/**
 * Offset que ocupa el eje Y de Vico (aprox. 38 dp para valores "10.0" / "7.5").
 * Se usa para alinear horizontalmente las píldoras de fecha con el área de trazado.
 */
private val VICO_Y_AXIS_WIDTH = 38.dp

// ─────────────────────────────────────────────────────────────────────────────
// Gráficos públicos
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TechniqueLineChart(sessions: List<SessionDetail>, modifier: Modifier = Modifier) {
    val data = sessions.filter { it.isCompleted && it.overallScore != null }.reversed()
    if (data.isEmpty()) { EmptyChartMessage("Sin datos de técnica todavía", modifier); return }

    val producer = remember(data) {
        ChartEntryModelProducer(data.mapIndexed { i, s -> entryOf(i.toFloat(), s.overallScore!!) })
    }
    ChartContainer(
        title       = "Puntuación de Técnica",
        firstDate   = data.first().createdAt.toShortDate(),
        lastDate    = data.last().createdAt.toShortDate(),
        accentColor = CyanPrimary,
        modifier    = modifier
    ) {
        ProvideChartStyle(currentChartStyle.copy(
            lineChart = currentChartStyle.lineChart.copy(lines = listOf(
                currentChartStyle.lineChart.lines.first().copy(
                    lineColor   = CyanPrimary.toArgb(),
                    point       = ShapeComponent(Shapes.pillShape, CyanPrimary.toArgb()),
                    pointSizeDp = 7f
                )
            ))
        )) {
            Chart(
                chart = lineChart(), chartModelProducer = producer,
                startAxis  = rememberStartAxis(itemPlacer = remember { AxisItemPlacer.Vertical.default(5) }),
                bottomAxis = rememberBottomAxis(),
                modifier   = Modifier.fillMaxWidth().height(180.dp)
            )
        }
    }
}

@Composable
fun LoadProgressionChart(sessions: List<SessionDetail>, modifier: Modifier = Modifier) {
    val data = sessions.reversed()
    if (data.isEmpty()) { EmptyChartMessage("Sin sesiones registradas", modifier); return }

    val producer = remember(data) {
        ChartEntryModelProducer(data.mapIndexed { i, s -> entryOf(i.toFloat(), s.weightKg) })
    }
    ChartContainer(
        title       = "Progresión de Carga",
        firstDate   = data.first().createdAt.toShortDate(),
        lastDate    = data.last().createdAt.toShortDate(),
        accentColor = OrangePower,
        modifier    = modifier
    ) {
        ProvideChartStyle(currentChartStyle.copy(
            lineChart = currentChartStyle.lineChart.copy(lines = listOf(
                currentChartStyle.lineChart.lines.first().copy(
                    lineColor   = OrangePower.toArgb(),
                    point       = ShapeComponent(Shapes.pillShape, OrangePower.toArgb()),
                    pointSizeDp = 7f
                )
            ))
        )) {
            Chart(
                chart = lineChart(), chartModelProducer = producer,
                startAxis  = rememberStartAxis(itemPlacer = remember { AxisItemPlacer.Vertical.default(5) }),
                bottomAxis = rememberBottomAxis(),
                modifier   = Modifier.fillMaxWidth().height(180.dp)
            )
        }
    }
}

@Composable
fun BorgScaleChart(sessions: List<SessionDetail>, modifier: Modifier = Modifier) {
    val data = sessions.reversed()
    if (data.isEmpty()) { EmptyChartMessage("Sin datos de esfuerzo todavía", modifier); return }

    val producer = remember(data) {
        ChartEntryModelProducer(data.mapIndexed { i, s -> entryOf(i.toFloat(), s.borgScore.toFloat()) })
    }
    ChartContainer(
        title       = "Escala de Borg (Esfuerzo)",
        firstDate   = data.first().createdAt.toShortDate(),
        lastDate    = data.last().createdAt.toShortDate(),
        accentColor = YellowCaution,
        modifier    = modifier
    ) {
        ProvideChartStyle(currentChartStyle.copy(
            lineChart = currentChartStyle.lineChart.copy(lines = listOf(
                currentChartStyle.lineChart.lines.first().copy(
                    lineColor   = YellowCaution.toArgb(),
                    point       = ShapeComponent(Shapes.pillShape, YellowCaution.toArgb()),
                    pointSizeDp = 7f
                )
            ))
        )) {
            Chart(
                chart = lineChart(), chartModelProducer = producer,
                startAxis  = rememberStartAxis(itemPlacer = remember { AxisItemPlacer.Vertical.default(5) }),
                bottomAxis = rememberBottomAxis(),
                modifier   = Modifier.fillMaxWidth().height(180.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ChartContainer — título + fila de fechas + gráfico
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Layout del gráfico:
 *
 *  Título
 *  ────────────────── (línea divisora sutil)
 *  [16/12]      [15/04]      ← píldoras FUERA del Box del gráfico
 *  [Chart Vico]
 *
 * Las píldoras tienen padding-start = VICO_Y_AXIS_WIDTH para alinearse
 * con el inicio del área de trazado del eje Y.
 */
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
        // Título
        Text(title, style = MaterialTheme.typography.titleLarge)

        // Separador sutil + fechas: dan aire entre título y gráfico sin desperdiciar espacio
        Spacer(Modifier.height(6.dp))
        DateRow(firstDate = firstDate, lastDate = lastDate, accentColor = accentColor)
        Spacer(Modifier.height(4.dp))

        // Gráfico
        content()
    }
}

/**
 * Fila de fechas alineada con el área de trazado del gráfico.
 * • Dos sesiones → primera (gris) a la izquierda, última (acento) a la derecha
 * • Una sesión   → centrada en el área de trazado
 */
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

/** Píldora compacta de fecha: fondo de color tenue + texto 9sp. */
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
