package com.example.accounting.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.accounting.core.persian.JalaliDate
import com.example.accounting.core.persian.JalaliPickerState
import com.example.accounting.core.persian.PersianDigits
import com.example.accounting.core.ui.R

/** Test tag prefix of each day cell: `jalali_day_<day>`. */
const val JALALI_DAY_TAG_PREFIX = "jalali_day_"

/** Saves the picker state (year, month, day) across recomposition and rotation. */
private val PickerStateSaver: Saver<JalaliPickerState, Any> = listSaver<JalaliPickerState, Int>(
    save = { listOf(it.year, it.month, it.day) },
    restore = { JalaliPickerState(it[0], it[1], it[2]) },
)

/**
 * Jalali calendar picker. Year chips, previous/next month, a Saturday-first day grid, and an explicit confirm.
 * All date logic (leap years, clamping) is in [JalaliPickerState]. This composable only renders it.
 * Confirming returns the chosen [JalaliDate]; dismissing changes nothing.
 */
@Composable
fun JalaliDatePickerDialog(
    initial: JalaliDate,
    onConfirm: (JalaliDate) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.picker_title_default),
) {
    var picker by rememberSaveable(stateSaver = PickerStateSaver) {
        mutableStateOf(JalaliPickerState.of(initial))
    }
    val weekdays = stringArrayResource(R.array.picker_weekdays_short)
    val weekdaysFull = stringArrayResource(R.array.picker_weekdays_full)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.picker_selected, picker.toDate().toString()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { picker.previousMonth()?.let { picker = it } },
                        enabled = picker.previousMonth() != null,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.picker_prev_month)) }
                    Text(
                        text = JalaliDate.MONTH_NAMES[picker.month - 1] + " " +
                            PersianDigits.toPersian(picker.year.toString()),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { picker.nextMonth()?.let { picker = it } },
                        enabled = picker.nextMonth() != null,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.picker_next_month)) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    picker.yearOptions().forEach { year ->
                        FilterChip(
                            selected = year == picker.year,
                            onClick = { picker = picker.withYear(year) },
                            label = { Text(PersianDigits.toPersian(year.toString())) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdays.forEachIndexed { index, short ->
                        Box(
                            modifier = Modifier.weight(1f).heightIn(min = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = short,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { contentDescription = weekdaysFull[index] },
                            )
                        }
                    }
                }
                DayGrid(picker = picker, onPick = { picker = picker.withDay(it) })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(picker.toDate()) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.picker_cancel))
            }
        },
    )
}

/** Day cells in weeks of seven. Leading and trailing cells are empty boxes. */
@Composable
private fun DayGrid(picker: JalaliPickerState, onPick: (Int) -> Unit) {
    val cells: List<Int?> = List(picker.leadingBlanks()) { null } + (1..picker.daysInMonth).toList()
    val padded = cells + List((7 - cells.size % 7) % 7) { null }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        padded.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Box(modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                    } else {
                        DayCell(
                            modifier = Modifier.weight(1f),
                            day = day,
                            selected = day == picker.day,
                            onClick = { onPick(day) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(modifier: Modifier, day: Int, selected: Boolean, onClick: () -> Unit) {
    val text = PersianDigits.toPersian(day.toString())
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .selectable(selected = selected, onClick = onClick, role = Role.Button)
            .testTag(JALALI_DAY_TAG_PREFIX + day),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}
