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
import androidx.compose.ui.draw.rotate
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
            delay(4_000L)
            isVisible = false
            delay(500L)
            currentTipIndex = (currentTipIndex + 1) % squatTips.size
            isVisible = true
        }
    }

    // Pulsating glow
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        CyanPrimary.copy(alpha = glowAlpha),
                        NavyDeep
                    ),
                    radius = 800f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            // Spinner ring
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp),
                    color = CyanPrimary,
                    strokeWidth = 3.dp,
                    trackColor = CyanPrimary.copy(alpha = 0.1f)
                )
                Icon(
                    imageVector = Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = CyanPrimary.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.height(32.dp))

            // Status text
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = TextPrimary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Esto puede tardar unos segundos",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(Modifier.height(48.dp))

            // Tip label
            Text(
                text = "💡 CONSEJO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = CyanPrimary.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(12.dp))

            // Animated tip
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 },
                exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 4 }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NavyLight.copy(alpha = 0.5f))
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = squatTips[currentTipIndex],
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontStyle = FontStyle.Italic,
                            lineHeight = 24.sp
                        ),
                        color = TextPrimary.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Dots indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { i ->
                    val dotAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = i * 200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .alpha(dotAlpha)
                            .clip(CircleShape)
                            .background(CyanPrimary)
                    )
                }
            }
        }
    }
}