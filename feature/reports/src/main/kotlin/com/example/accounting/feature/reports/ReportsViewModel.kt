package com.example.accounting.feature.reports

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.domain.report.JournalDoc
import com.example.accounting.core.domain.report.JournalDocLine
import com.example.accounting.core.domain.report.JournalReport
import com.example.accounting.core.domain.report.LedgerHistoryLine
import com.example.accounting.core.domain.report.LedgerReport
import com.example.accounting.core.domain.report.ReportRange
import com.example.accounting.core.domain.report.ReportTable
import com.example.accounting.core.domain.report.TrialAccount
import com.example.accounting.core.domain.report.TrialBalanceReport
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.JalaliDate
import com.example.accounting.core.persian.PersianDigits
import com.example.accounting.feature.ledger.DateRange
import com.example.accounting.feature.ledger.DatePreset
import com.example.accounting.feature.ledger.parseJalaliInput
import com.example.accounting.feature.ledger.rangeFor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReportKind(@StringRes val labelRes: Int) {
    TRIAL(R.string.kind_trial),
    JOURNAL(R.string.kind_journal),
    LEDGER(R.string.kind_ledger),
}

/** Screen state. Money stays rial in the report; [AmountUnit] is applied when rendering. */
data class ReportsUi(
    val kind: ReportKind = ReportKind.TRIAL,
    val year: Int,
    val years: List<Int> = emptyList(),
    val preset: DatePreset = DatePreset.ALL,
    val fromText: String = "",
    val toText: String = "",
    val custom: DateRange? = null,
    val dateError: Boolean = false,
    val accountCode: String = "",
    val busy: Boolean = false,
    val table: ReportTable? = null,
    @StringRes val message: Int? = null,
)

/**
 * Builds report tables from committed data. Read-only: nothing here writes to the ledger.
 * Unit is fixed to TOMAN until the settings screen (T12) supplies it.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val ledger: LedgerRepository,
    private val clock: Clock,
) : ViewModel() {
    private val today: LocalDate get() = LocalDate.now(clock)
    private val _ui = MutableStateFlow(ReportsUi(year = JalaliDate.from(LocalDate.now(clock)).year))
    val ui: StateFlow<ReportsUi> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            ledger.fiscalYears().collect { years -> _ui.value = _ui.value.copy(years = years) }
        }
        refresh()
    }

    fun selectKind(kind: ReportKind) = update { it.copy(kind = kind) }

    fun selectYear(year: Int) = update { it.copy(year = year) }

    fun selectPreset(preset: DatePreset) = update { it.copy(preset = preset) }

    fun onFromText(text: String) = onDates(text, _ui.value.toText)

    fun onToText(text: String) = onDates(_ui.value.fromText, text)

    fun onAccountCode(text: String) = update { it.copy(accountCode = text) }

    private fun onDates(from: String, to: String) {
        val f = parseJalaliInput(from)
        val t = parseJalaliInput(to)
        val custom = if (f != null && t != null && !t.isBefore(f)) DateRange(f, t) else null
        update {
            it.copy(
                fromText = from,
                toText = to,
                custom = custom,
                dateError = (from.isNotBlank() || to.isNotBlank()) && custom == null,
                preset = DatePreset.CUSTOM,
            )
        }
    }

    private fun update(change: (ReportsUi) -> ReportsUi) {
        _ui.value = change(_ui.value)
        refresh()
    }

    private fun refresh() {
        job?.cancel()
        val s = _ui.value
        if (s.dateError) {
            _ui.value = s.copy(table = null, busy = false, message = R.string.report_bad_dates)
            return
        }
        val range = rangeFor(s.preset, s.year, today, s.custom)
        if (range == null || !insideFiscalYear(range, s.year)) {
            _ui.value = s.copy(table = null, busy = false, message = R.string.report_no_range)
            return
        }
        val code = PersianDigits.toLatin(s.accountCode).filterNot { it.isWhitespace() }
        if (s.kind == ReportKind.LEDGER && code.isEmpty()) {
            _ui.value = s.copy(table = null, busy = false, message = R.string.report_account_required)
            return
        }
        _ui.value = s.copy(busy = true, message = null)
        job = viewModelScope.launch {
            val table = withContext(Dispatchers.IO) { build(s.kind, s.year, range, code) }
            val current = _ui.value
            _ui.value = current.copy(
                busy = false,
                table = table,
                message = if (table == null) R.string.report_account_not_found else null,
            )
        }
    }

    /** Null only when a ledger account code is unknown. */
    private suspend fun build(kind: ReportKind, year: Int, range: DateRange, code: String): ReportTable? {
        val names = ledger.accounts().first().associate { it.code to it.nameFa }
        val reportRange = ReportRange(range.from, range.to)
        return when (kind) {
            ReportKind.TRIAL -> {
                val tb = ledger.trialBalance(year, range.to)
                TrialBalanceReport.build(
                    year,
                    range.to,
                    tb.rows.map { TrialAccount(it.code, it.name, it.debit, it.credit) },
                    UNIT,
                )
            }
            ReportKind.JOURNAL -> {
                val docs = ledger.journal(year, range.from, range.to).first()
                JournalReport.build(
                    reportRange,
                    docs.map { d ->
                        JournalDoc(
                            date = d.entry.date,
                            docNumber = d.docNumber,
                            memo = d.entry.memo,
                            lines = d.entry.lines.map {
                                JournalDocLine(it.accountCode, names[it.accountCode].orEmpty(), it.side, it.amount.rial)
                            },
                        )
                    },
                    UNIT,
                )
            }
            ReportKind.LEDGER -> {
                val name = names[code] ?: return null
                val lines = ledger.ledger(code).first()
                LedgerReport.build(
                    code,
                    name,
                    reportRange,
                    lines.map { LedgerHistoryLine(it.date, it.fiscalYear, it.docNumber, it.memo, it.debit, it.credit) },
                    UNIT,
                )
            }
        }
    }

    companion object {
        val UNIT: AmountUnit = AmountUnit.TOMAN

        /** [range] must sit inside the Jalali fiscal year [year] (bounds from T05). */
        fun insideFiscalYear(range: DateRange, year: Int): Boolean {
            val (start, end) = JalaliDate.fiscalYear(year)
            return !range.from.isBefore(start) && !range.to.isAfter(end)
        }
    }
}
