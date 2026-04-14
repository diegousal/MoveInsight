package com.moveinsight.presentation.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.moveinsight.presentation.theme.CyanPrimary
import com.moveinsight.presentation.theme.TextPrimary

/**
 * Logo tipográfico de MoveInsight.
 * "Move" en blanco · "Insight" en cian.
 *
 * Uso en Splash  → MoveInsightLogo(fontSize = 40.sp)
 * Uso en Login   → MoveInsightLogo(fontSize = 36.sp)
 * Uso en Header  → MoveInsightLogo(fontSize = 24.sp)
 */
@Composable
fun MoveInsightLogo(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 40.sp
) {
    val wordmark = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color      = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize   = fontSize
            )
        ) { append("Move") }
        withStyle(
            SpanStyle(
                color      = CyanPrimary,
                fontWeight = FontWeight.Black,
                fontSize   = fontSize
            )
        ) { append("Insight") }
    }
    Text(
        text     = wordmark,
        style    = TextStyle.Default,
        modifier = modifier
    )
}