package com.moveinsight.presentation.dashboard


import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.moveinsight.domain.model.Insight
import com.moveinsight.presentation.theme.*

@Composable
fun InsightCard(
    insight  : Insight,
    modifier : Modifier = Modifier
) {
    val (icon, iconColor, bgColor) = when (insight.type) {
        "warning"     -> Triple(Icons.Filled.Warning,     RedAlert,      RedAlert.copy(alpha = 0.10f))
        "achievement" -> Triple(Icons.Filled.EmojiEvents, GreenReady,    GreenReady.copy(alpha = 0.10f))
        else          -> Triple(Icons.Filled.Info,        CyanPrimary,   CyanPrimary.copy(alpha = 0.08f))
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color    = bgColor,
        shape    = MaterialTheme.shapes.large
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = iconColor,
                modifier           = Modifier.size(24.dp).padding(top = 2.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text  = insight.title,
                    style = MaterialTheme.typography.titleLarge.copy(color = iconColor)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = insight.message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}