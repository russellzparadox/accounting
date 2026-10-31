package com.example.accounting.feature.ledger

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.accounting.core.persian.JalaliDate
import com.example.accounting.core.persian.PersianDigits
import com.example.accounting.core.ui.components.JalaliDatePickerDialog
import java.time.LocalDate

/** Date presets. Ranges are Jalali months (or the whole fiscal year). CUSTOM uses the typed dates. */
enum class DatePreset(val labelRes: Int) {
    ALL(R.string.range_all),
    THIS_MONTH(R.string.range_this_month),
    PREVIOUS_MONTH(R.string.range_previous_month),
    CUSTOM(R.string.range_custom),
}

/** Inclusive Gregorian bounds (stored form). */
data class DateRange(val from: LocalDate, val to: LocalDate) {
    init {
        require(!to.isBefore(from)) { "range ends before it starts" }
    }
}

/** Whole fiscal year (Jalali year bounds, inclusive). */
internal fun fiscalRange(year: Int): DateRange {
    val (start, end) = JalaliDate.fiscalYear(year)
    return DateRange(start, end)
}

/** One Jalali month, first to last day. */
internal fun monthRange(year: Int, month: Int): DateRange {
    val days = JalaliDate.daysInMonth(year, month)
    return DateRange(JalaliDate(year, month, 1).toGregorian(), JalaliDate(year, month, days).toGregorian())
}

/**
 * Range for a preset. THIS_MONTH and PREVIOUS_MONTH take today's Jalali month number inside [year].
 * Null only for CUSTOM without a valid custom range; callers then fall back to the fiscal year.
 */
fun rangeFor(preset: DatePreset, year: Int, today: LocalDate, custom: DateRange?): DateRange? {
    val thisMonth = JalaliDate.from(today).month
    return when (preset) {
        DatePreset.ALL -> fiscalRange(year)
        DatePreset.THIS_MONTH -> monthRange(year, thisMonth)
        DatePreset.PREVIOUS_MONTH ->
            if (thisMonth == 1) monthRange(year - 1, 12) else monthRange(year, thisMonth - 1)
        DatePreset.CUSTOM -> custom
    }
}

/**
 * Parses a typed Jalali date such as «۱۴۰۵/۰۱/۰۱» (Persian or Latin digits, / or - separators).
 * Returns null for anything that is not a valid Jalali date.
 */
fun parseJalaliInput(text: String): LocalDate? {
    val parts = PersianDigits.toLatin(text.trim()).split('/', '-').map { it.trim() }
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return try {
        JalaliDate(year, month, day).toGregorian()
    } catch (e: IllegalArgumentException) {
        null
    }
}

/** Preset chips, plus two typed Jalali dates when CUSTOM is selected. Horizontal scroll keeps chips reachable at 200%. */
@Composable
fun DateFilterBar(
    preset: DatePreset,
    fromText: String,
    toText: String,
    dateError: Boolean,
    onPreset: (DatePreset) -> Unit,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DatePreset.entries.forEach { option ->
                FilterChip(
                    // 48dp minimum touch height (DESIGN 5.7), the chip itself is 32dp.
                    modifier = Modifier.heightIn(min = 48.dp),
                    selected = preset == option,
                    onClick = { onPreset(option) },
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }
        if (preset == DatePreset.CUSTOM) {
            var pickFrom by rememberSaveable { mutableStateOf(false) }
            var pickTo by rememberSaveable { mutableStateOf(false) }
            OutlinedTextField(
                value = fromText,
                onValueChange = onFromChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.range_from_label)) },
                placeholder = { Text(stringResource(R.string.range_hint)) },
                singleLine = true,
                isError = dateError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = { pickFrom = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.range_pick_calendar))
            }
            OutlinedTextField(
                value = toText,
                onValueChange = onToChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.range_to_label)) },
                placeholder = { Text(stringResource(R.string.range_hint)) },
                singleLine = true,
                isError = dateError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = { pickTo = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.range_pick_calendar))
            }
            if (pickFrom) {
                JalaliDatePickerDialog(
                    initial = pickerInitial(fromText),
                    title = stringResource(R.string.range_from_label),
                    onConfirm = {
                        onFromChange(it.toString())
                        pickFrom = false
                    },
                    onDismiss = { pickFrom = false },
                )
            }
            if (pickTo) {
                JalaliDatePickerDialog(
                    initial = pickerInitial(toText),
                    title = stringResource(R.string.range_to_label),
                    onConfirm = {
                        onToChange(it.toString())
                        pickTo = false
                    },
                    onDismiss = { pickTo = false },
                )
            }
            if (dateError) {
                Text(
                    text = stringResource(R.string.range_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Calendar starting point: the typed date when it parses, otherwise today. */
private fun pickerInitial(text: String): JalaliDate =
    parseJalaliInput(text)?.let { JalaliDate.from(it) } ?: JalaliDate.from(LocalDate.now())
