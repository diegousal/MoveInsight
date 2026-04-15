package com.moveinsight.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.moveinsight.domain.model.SessionDetail
import com.moveinsight.presentation.theme.*
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollState
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.compose.style.currentChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.chart.copy
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

/**
 * Gráfico de línea: Técnica (%) a lo largo de las sesiones.
 */
@Composable
fun TechniqueLineChart(
    sessions : List<SessionDetail>,
    modifier : Modifier = Modifier
) {
    val completed = sessions.filter { it.isCompleted && it.techniqueScore != null }
    if (completed.isEmpty()) {
        EmptyChartMessage("Sin datos de técnica todavía", modifier)
        return
    }

    val entries = completed.mapIndexed { index, s ->
        entryOf(index.toFloat(), s.techniqueScore!!)
    }
    val producer = remember(completed) {
        ChartEntryModelProducer(entries)
    }

    ChartContainer(title = "Puntuación de Técnica", modifier = modifier) {
        ProvideChartStyle(
            chartStyle = currentChartStyle.copy(
                lineChart = currentChartStyle.lineChart.copy(
                    lines = listOf(
                        currentChartStyle.lineChart.lines.first().copy(
                            lineColor = CyanPrimary.toArgb()
                        )
                    )
                )
            )
        ) {
            Chart(
                chart       = lineChart(),
                chartModelProducer = producer,
                startAxis   = rememberStartAxis(
                    title        = "Puntuación",
                    itemPlacer = remember { AxisItemPlacer.Vertical.default(maxItemCount = 5) }
                ),
                bottomAxis  = rememberBottomAxis(title = "Sesión"),
                modifier    = Modifier.fillMaxWidth().height(180.dp),
            )
        }
    }
}

/**
 * Gráfico de línea: Carga (kg) a lo largo de las sesiones.
 */
@Composable
fun LoadProgressionChart(
    sessions : List<SessionDetail>,
    modifier : Modifier = Modifier
) {
    if (sessions.isEmpty()) {
        EmptyChartMessage("Sin sesiones registradas", modifier)
        return
    }

    val entries = sessions.mapIndexed { index, s ->
        entryOf(index.toFloat(), s.weightKg)
    }
    val producer = remember(sessions) { ChartEntryModelProducer(entries) }

    ChartContainer(title = "Progresión de Carga", modifier = modifier) {
        ProvideChartStyle(
            chartStyle = currentChartStyle.copy(
                lineChart = currentChartStyle.lineChart.copy(
                    lines = listOf(
                        currentChartStyle.lineChart.lines.first().copy(
                            lineColor = OrangePower.toArgb()
                        )
                    )
                )
            )
        ) {
            Chart(
                chart              = lineChart(),
                chartModelProducer = producer,
                startAxis          = rememberStartAxis(itemPlacer = remember { AxisItemPlacer.Vertical.default(maxItemCount = 5) }),
                bottomAxis         = rememberBottomAxis(title = "Sesión"),
                modifier           = Modifier.fillMaxWidth().height(180.dp),
            )
        }
    }
}

/**
 * Gráfico de línea: Velocidad media (m/s) vs Sesión.
 */
@Composable
fun VelocityChart(
    sessions : List<SessionDetail>,
    modifier : Modifier = Modifier
) {
    val completed = sessions.filter { it.isCompleted && it.avgVelocity != null }
    if (completed.isEmpty()) {
        EmptyChartMessage("Sin datos de velocidad todavía", modifier)
        return
    }

    val entries = completed.mapIndexed { i, s -> entryOf(i.toFloat(), s.avgVelocity!!) }
    val producer = remember(completed) { ChartEntryModelProducer(entries) }

    ChartContainer(title = "Velocidad Media (m/s)", modifier = modifier) {
        ProvideChartStyle(
            chartStyle = currentChartStyle.copy(
                lineChart = currentChartStyle.lineChart.copy(
                    lines = listOf(
                        currentChartStyle.lineChart.lines.first().copy(
                            lineColor = GreenReady.toArgb()
                        )
                    )
                )
            )
        ) {
            Chart(
                chart              = lineChart(),
                chartModelProducer = producer,
                startAxis          = rememberStartAxis(itemPlacer = remember { AxisItemPlacer.Vertical.default(maxItemCount = 4) }),
                bottomAxis         = rememberBottomAxis(title = "Sesión"),
                modifier           = Modifier.fillMaxWidth().height(180.dp),
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────

@Composable
private fun ChartContainer(
    title    : String,
    modifier : Modifier = Modifier,
    content  : @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun EmptyChartMessage(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier              = modifier.fillMaxWidth().height(100.dp),
        contentAlignment      = androidx.compose.ui.Alignment.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}