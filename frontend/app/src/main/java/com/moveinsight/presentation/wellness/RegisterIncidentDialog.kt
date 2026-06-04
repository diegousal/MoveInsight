package com.moveinsight.presentation.wellness

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.moveinsight.domain.model.IncidentType
import com.moveinsight.presentation.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Diálogo para registrar una incidencia / molestia / lesión.
 *
 * Novedades respecto a la versión anterior:
 *  - Tipo "Lesión" añadido al selector de tipo.
 *  - Selector de fecha de inicio (puede ser en el pasado).
 *  - Toggle "Ya ha finalizado" + selector de fecha de fin opcional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterIncidentDialog(
    isSaving  : Boolean,
    onDismiss : () -> Unit,
    onSave    : (zone: String, severity: Int, type: String,
                 startDate: String, endDate: String?, notes: String) -> Unit
) {
    var zone     by remember { mutableStateOf("") }
    var severity by remember { mutableIntStateOf(3) }
    var type     by remember { mutableStateOf(IncidentType.MOLESTIA.apiValue) }
    var notes    by remember { mutableStateOf("") }

    val today = remember { LocalDate.now() }
    val isoFmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }

    // ── Fechas ──────────────────────────────────────────────────────────────
    var startDate  by remember { mutableStateOf(today) }
    var hasEndDate by remember { mutableStateOf(false) }
    var endDate    by remember { mutableStateOf(today) }

    // DatePicker states
    val startPickerState = rememberDatePickerState(
        initialSelectedDateMillis = today.toEpochMilli()
    )
    val endPickerState = rememberDatePickerState(
        initialSelectedDateMillis = today.toEpochMilli()
    )

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker   by remember { mutableStateOf(false) }

    // Sincronizar estados del picker con variables locales
    LaunchedEffect(startPickerState.selectedDateMillis) {
        startPickerState.selectedDateMillis?.let { millis ->
            startDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
        }
    }
    LaunchedEffect(endPickerState.selectedDateMillis) {
        endPickerState.selectedDateMillis?.let { millis ->
            endDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
        }
    }

    val isValid = zone.isNotBlank() &&
            (!hasEndDate || !endDate.isBefore(startDate))

    // ── DatePicker modales ───────────────────────────────────────────────────
    if (showStartPicker) {
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancelar") }
            },
            colors = DatePickerDefaults.colors(containerColor = NavyMid)
        ) {
            DatePicker(
                state  = startPickerState,
                colors = DatePickerDefaults.colors(
                    containerColor           = NavyMid,
                    titleContentColor        = TextSecondary,
                    headlineContentColor     = TextPrimary,
                    weekdayContentColor      = TextSecondary,
                    selectedDayContainerColor = CyanPrimary,
                    selectedDayContentColor  = NavyDeep,
                    todayDateBorderColor     = CyanPrimary,
                    todayContentColor        = CyanPrimary,
                )
            )
        }
    }

    if (showEndPicker) {
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancelar") }
            },
            colors = DatePickerDefaults.colors(containerColor = NavyMid)
        ) {
            DatePicker(
                state  = endPickerState,
                colors = DatePickerDefaults.colors(
                    containerColor            = NavyMid,
                    titleContentColor         = TextSecondary,
                    headlineContentColor      = TextPrimary,
                    weekdayContentColor       = TextSecondary,
                    selectedDayContainerColor = CyanPrimary,
                    selectedDayContentColor   = NavyDeep,
                    todayDateBorderColor      = CyanPrimary,
                    todayContentColor         = CyanPrimary,
                )
            )
        }
    }

    // ── Dialog principal ─────────────────────────────────────────────────────
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NavyMid)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text  = "Registrar incidencia",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = TextPrimary, fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Hoy: ${today.format(isoFmt)}",
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )
            Spacer(Modifier.height(20.dp))

            // ── Zona corporal ────────────────────────────────────────────
            SectionLabel("Zona corporal")
            Spacer(Modifier.height(8.dp))
            BodyZoneSelector(selected = zone, onSelect = { zone = it })
            Spacer(Modifier.height(16.dp))

            // ── Tipo ─────────────────────────────────────────────────────
            SectionLabel("Tipo")
            Spacer(Modifier.height(8.dp))
            TypeSelector(selected = type, onSelect = { type = it })
            Spacer(Modifier.height(16.dp))

            // ── Severidad ────────────────────────────────────────────────
            SectionLabel("Severidad: $severity / 10")
            Spacer(Modifier.height(8.dp))
            Slider(
                value         = severity.toFloat(),
                onValueChange = { severity = it.toInt() },
                valueRange    = 0f..10f,
                steps         = 9,
                colors        = SliderDefaults.colors(
                    thumbColor         = CyanPrimary,
                    activeTrackColor   = CyanPrimary,
                    inactiveTrackColor = NavyLight
                )
            )
            Spacer(Modifier.height(16.dp))

            // ── Fechas ───────────────────────────────────────────────────
            SectionLabel("Fechas")
            Spacer(Modifier.height(8.dp))

            // Fecha de inicio
            DateField(
                label    = "Fecha de inicio",
                date     = startDate,
                icon     = Icons.Filled.CalendarToday,
                onClick  = { showStartPicker = true }
            )
            Spacer(Modifier.height(10.dp))

            // Toggle "ya ha finalizado"
            Row(
                modifier      = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Incidencia ya finalizada",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                    )
                    Text(
                        "Registra el período completo de la lesión",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary, lineHeight = 15.sp
                        )
                    )
                }
                Switch(
                    checked         = hasEndDate,
                    onCheckedChange = { hasEndDate = it },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor       = NavyDeep,
                        checkedTrackColor       = CyanPrimary,
                        uncheckedThumbColor     = TextSecondary,
                        uncheckedTrackColor     = NavyLight
                    )
                )
            }

            // Fecha de fin (solo si el toggle está activo)
            if (hasEndDate) {
                Spacer(Modifier.height(10.dp))
                DateField(
                    label   = "Fecha de fin",
                    date    = endDate,
                    icon    = Icons.Filled.CalendarMonth,
                    onClick = { showEndPicker = true }
                )
                // Aviso si la fecha de fin es anterior al inicio
                if (endDate.isBefore(startDate)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "La fecha de fin no puede ser anterior al inicio.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RedAlert, lineHeight = 15.sp
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Notas ────────────────────────────────────────────────────
            OutlinedTextField(
                value         = notes,
                onValueChange = { notes = it },
                label         = { Text("Notas (opcional)") },
                singleLine    = false,
                minLines      = 2,
                modifier      = Modifier.fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = CyanPrimary,
                    unfocusedBorderColor = NavyLight,
                    focusedLabelColor    = CyanPrimary,
                    unfocusedLabelColor  = TextSecondary,
                    cursorColor          = CyanPrimary
                )
            )

            Spacer(Modifier.height(20.dp))

            // ── Acciones ─────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text("Cancelar", color = TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            zone,
                            severity,
                            type,
                            startDate.format(isoFmt),
                            if (hasEndDate) endDate.format(isoFmt) else null,
                            notes
                        )
                    },
                    enabled = isValid && !isSaving,
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor   = NavyDeep
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            color       = NavyDeep,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Campo de fecha clickable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DateField(
    label  : String,
    date   : LocalDate,
    icon   : androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val fmt = DateTimeFormatter.ofPattern("dd / MM / yyyy")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NavyDeep)
            .border(1.dp, NavyLight, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )
            Text(
                text  = date.format(fmt),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary, fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-componentes (sin cambios respecto a la versión anterior)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelMedium.copy(
            color = TextSecondary, fontWeight = FontWeight.SemiBold
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BodyZoneSelector(selected: String, onSelect: (String) -> Unit) {
    val zones = listOf(
        "rodilla_izq" to "Rodilla I",
        "rodilla_der" to "Rodilla D",
        "lumbar"      to "Lumbar",
        "cervical"    to "Cervical",
        "cadera_izq"  to "Cadera I",
        "cadera_der"  to "Cadera D",
        "tobillo_izq" to "Tobillo I",
        "tobillo_der" to "Tobillo D",
        "hombro_izq"  to "Hombro I",
        "hombro_der"  to "Hombro D",
        "general"     to "General"
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement   = Arrangement.spacedBy(6.dp)
    ) {
        zones.forEach { (code, label) ->
            Chip(
                label    = label,
                selected = selected == code,
                onClick  = { onSelect(code) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeSelector(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement   = Arrangement.spacedBy(6.dp)
    ) {
        IncidentType.entries.forEach { type ->
            // "Lesión" destaca con un borde especial cuando está seleccionado
            val isLesion = type == IncidentType.LESION
            Chip(
                label       = type.display,
                selected    = selected == type.apiValue,
                onClick     = { onSelect(type.apiValue) },
                accentColor = if (isLesion) RedAlert else CyanPrimary
            )
        }
    }
}

@Composable
private fun Chip(
    label       : String,
    selected    : Boolean,
    onClick     : () -> Unit,
    accentColor : androidx.compose.ui.graphics.Color = CyanPrimary
) {
    val borderColor = if (selected) accentColor else NavyLight
    val bgColor     = if (selected) accentColor.copy(alpha = 0.14f) else NavyDeep
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color      = if (selected) accentColor else TextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize   = 12.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension helper
// ─────────────────────────────────────────────────────────────────────────────

private fun LocalDate.toEpochMilli(): Long =
    this.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
