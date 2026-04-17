package com.moveinsight.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moveinsight.presentation.theme.*
import kotlinx.coroutines.delay

private val squatTips = listOf(
    "Mantén los pies a la anchura de los hombros",
    "Empuja las rodillas hacia fuera en línea con los pies",
    "Mantén el pecho erguido durante toda la sentadilla",
    "Desciende hasta que el muslo esté paralelo al suelo",
    "Activa el core antes de iniciar el movimiento",
    "Distribuye el peso entre talón y metatarso",
    "Controla la fase excéntrica: baja en 2-3 segundos",
    "Exhala al subir, inhala al bajar",
    "Evita que las rodillas sobrepasen excesivamente los pies",
    "Mantén la mirada al frente, no mires al suelo",
    "Aprieta glúteos al llegar arriba del todo",
    "Calienta con sentadillas sin peso antes de cargar",
    "La espalda baja debe mantener su curvatura natural",
    "Entrena la movilidad de tobillo para mejorar profundidad",
    "Una buena sentadilla empieza rompiendo cadera, no rodilla",
    "Usa calzado plano o zapatillas de halterofilia",
    "Graba tus series para identificar fallos técnicos",
    "La velocidad de subida indica tu nivel de fatiga",
    "Descansa 2-3 minutos entre series pesadas",
    "La consistencia supera a la intensidad a largo plazo"
)

@Composable
fun LoadingWithTips(
    statusMessage: String = "Analizando tu técnica…",
    modifier: Modifier = Modifier
) {
    var currentTipIndex by remember { mutableIntStateOf((squatTips.indices).random()) }
    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(8_000L)
            isVisible = false
            delay(600L)
            currentTipIndex = (currentTipIndex + 1) % squatTips.size
            isVisible = true
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.07f,
        targetValue   = 0.20f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(CyanPrimary.copy(alpha = glowAlpha), NavyDeep),
                    radius = 900f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 36.dp)
        ) {

            // ── Spinner + icon ─────────────────────────────────────────
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(84.dp),
                    color       = CyanPrimary,
                    strokeWidth = 5.dp,
                    trackColor  = CyanPrimary.copy(alpha = 0.12f)
                )
                Icon(
                    imageVector        = Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint               = CyanPrimary,
                    modifier           = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── Título principal ───────────────────────────────────────
            Text(
                text  = statusMessage,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    fontSize      = 20.sp
                ),
                color     = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // ── Subtítulo — pill style con acento naranja ──────────────
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(OrangePower.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text  = "Procesando en el servidor…",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize   = 12.sp
                    ),
                    color = OrangePower.copy(alpha = 0.85f)
                )
            }

            Spacer(Modifier.height(48.dp))

            // ── Cabecera fija "Sabías que…" con líneas laterales ───────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(NavyLight, CyanPrimary.copy(alpha = 0.4f))
                            )
                        )
                )
                Text(
                    text  = "Mejora tu sentadilla",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight    = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        fontSize      = 11.sp
                    ),
                    color = TextSecondary
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(CyanPrimary.copy(alpha = 0.4f), NavyLight)
                            )
                        )
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Solo la tarjeta se anima (desliza hacia abajo al salir) ─
            AnimatedContent(
                targetState  = currentTipIndex,
                transitionSpec = {
                    (fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 5 })
                        .togetherWith(
                            fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 2 }
                        )
                },
                label = "tipCard"
            ) { tipIdx ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NavyMid)
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text      = squatTips[tipIdx],
                        style     = MaterialTheme.typography.bodyLarge.copy(
                            fontStyle  = FontStyle.Italic,
                            lineHeight = 26.sp,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color     = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Dots ──────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                repeat(3) { i ->
                    val dotAlpha by infiniteTransition.animateFloat(
                        initialValue  = 0.25f,
                        targetValue   = 1f,
                        animationSpec = infiniteRepeatable(
                            animation  = tween(700, delayMillis = i * 230, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .alpha(dotAlpha)
                            .clip(CircleShape)
                            .background(CyanPrimary)
                    )
                }
            }
        }
    }
}
