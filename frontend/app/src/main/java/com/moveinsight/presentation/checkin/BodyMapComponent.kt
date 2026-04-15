package com.moveinsight.presentation.checkin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moveinsight.domain.model.BodyZone
import com.moveinsight.presentation.theme.*

/**
 * Mapa corporal simplificado: grid de zonas clicables organizadas
 * con separación frontal / posterior.
 *
 * @param selectedZones  conjunto de zonas ya marcadas
 * @param showFront      true = vista frontal, false = vista posterior
 * @param onZoneToggle   callback al pulsar una zona
 */
@Composable
fun BodyMapComponent(
    selectedZones : Set<BodyZone>,
    showFront     : Boolean,
    onZoneToggle  : (BodyZone) -> Unit,
    onViewToggle  : () -> Unit,
    modifier      : Modifier = Modifier
) {
    val zones = BodyZone.entries.filter { it.isFront == showFront }

    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Toggle frontal / posterior ────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            SegmentedButton(
                selected  = showFront,
                label     = "Frontal",
                onClick   = { if (!showFront) onViewToggle() }
            )
            Spacer(Modifier.width(8.dp))
            SegmentedButton(
                selected  = !showFront,
                label     = "Posterior",
                onClick   = { if (showFront) onViewToggle() }
            )
        }

        Spacer(Modifier.height(16.dp))

        // Indicador de zonas seleccionadas
        val selectedInView = selectedZones.count { it.isFront == showFront }
        if (selectedInView > 0) {
            Text(
                text  = "$selectedInView zona${if (selectedInView > 1) "s" else ""} marcada${if (selectedInView > 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium.copy(color = OrangePower)
            )
            Spacer(Modifier.height(8.dp))
        }

        // ── Grid de zonas ─────────────────────────────────────────────────
        LazyVerticalGrid(
            columns             = GridCells.Fixed(2),
            verticalArrangement  = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier             = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),    // limita altura para no romper el scroll padre
            userScrollEnabled    = false    // el scroll lo gestiona la columna padre
        ) {
            items(zones) { zone ->
                val isSelected = zone in selectedZones
                ZoneChip(
                    label      = zone.label,
                    isSelected = isSelected,
                    onClick    = { onZoneToggle(zone) }
                )
            }
        }
    }
}

@Composable
private fun ZoneChip(
    label      : String,
    isSelected : Boolean,
    onClick    : () -> Unit
) {
    val bgColor     = if (isSelected) OrangePower.copy(alpha = 0.20f) else NavyLight
    val borderColor = if (isSelected) OrangePower else DividerColor
    val textColor   = if (isSelected) OrangePower else TextPrimary

    Surface(
        onClick      = onClick,
        color        = bgColor,
        border       = BorderStroke(1.5.dp, borderColor),
        shape        = MaterialTheme.shapes.medium,
        modifier     = Modifier.fillMaxWidth()
    ) {
        Text(
            text      = label,
            style     = MaterialTheme.typography.bodyMedium.copy(color = textColor),
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
        )
    }
}

@Composable
private fun SegmentedButton(
    selected : Boolean,
    label    : String,
    onClick  : () -> Unit
) {
    Button(
        onClick  = onClick,
        modifier = Modifier.defaultMinSize(minWidth = 120.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor         = if (selected) CyanPrimary else NavyLight,
            contentColor           = if (selected) NavyDeep    else TextSecondary,
            disabledContainerColor = NavyLight
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}