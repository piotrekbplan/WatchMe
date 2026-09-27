package pl.watchme.feature.ranking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RankingTimePickerDialog(
    zone: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (Instant) -> Unit,
) {
    val now = remember(zone) { LocalTime.now(zone) }
    val pickerState = rememberTimePickerState(initialHour = now.hour, initialMinute = now.minute, is24Hour = true)
    var tomorrow by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ranking_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !tomorrow,
                        onClick = { tomorrow = false },
                        label = { Text(stringResource(R.string.ranking_picker_today)) },
                    )
                    FilterChip(
                        selected = tomorrow,
                        onClick = { tomorrow = true },
                        label = { Text(stringResource(R.string.ranking_picker_tomorrow)) },
                    )
                }
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val day = LocalDate.now(zone).plusDays(if (tomorrow) 1 else 0)
                    val time = LocalTime.of(pickerState.hour, pickerState.minute)
                    onConfirm(day.atTime(time).atZone(zone).toInstant())
                },
            ) { Text(stringResource(R.string.ranking_picker_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ranking_picker_cancel)) }
        },
    )
}
