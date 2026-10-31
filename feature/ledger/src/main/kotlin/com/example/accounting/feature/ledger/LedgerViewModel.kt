package com.example.accounting.feature.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accounting.core.data.repo.CommittedDoc
import com.example.accounting.core.data.repo.LedgerLine
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.data.repo.ReversalLink
import com.example.accounting.core.domain.Account
import com.example.accounting.core.persian.JalaliDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the ledger tabs: journal, account picker, account ledger and the document sheet.
 * Reads only through [LedgerRepository]. Committed documents are never edited or deleted here; the only write is
 * [confirmReverse], which posts a reversal dated today.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val ledger: LedgerRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today: LocalDate get() = LocalDate.now(clock)
    private val currentYear: Int = JalaliDate.from(LocalDate.now(clock)).year

    private val _filter = MutableStateFlow(LedgerFilter(year = currentYear))
    val filter: StateFlow<LedgerFilter> = _filter.asStateFlow()

    private val _journalQuery = MutableStateFlow("")
    val journalQuery: StateFlow<String> = _journalQuery.asStateFlow()

    private val _accountQuery = MutableStateFlow("")
    val accountQuery: StateFlow<String> = _accountQuery.asStateFlow()

    private val _hideZero = MutableStateFlow(false)
    val hideZeroBalances: StateFlow<Boolean> = _hideZero.asStateFlow()

    private val _selectedAccount = MutableStateFlow<String?>(null)
    val selectedAccount: StateFlow<String?> = _selectedAccount.asStateFlow()

    private val refreshTick = MutableStateFlow(0)

    private val _detail = MutableStateFlow<DocumentDetailUi?>(null)
    val detail: StateFlow<DocumentDetailUi?> = _detail.asStateFlow()

    val fiscalYears: StateFlow<List<Int>> = ledger.fiscalYears()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accounts: StateFlow<List<Account>> = ledger.accounts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accountNames: StateFlow<Map<String, String>> = accounts
        .map { list -> list.associate { it.code to it.nameFa } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val yearFlow: Flow<Int> = _filter.map { it.year }.distinctUntilChanged()

    /** Applied range: preset or custom; fiscal year bounds when nothing else applies. */
    private val appliedRange: StateFlow<DateRange> = _filter
        .map { f -> rangeFor(f.preset, f.year, today, f.custom) ?: fiscalRange(f.year) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, fiscalRange(currentYear))

    private val journalDocs: StateFlow<List<CommittedDoc>> = combine(yearFlow, appliedRange) { year, range ->
        year to range
    }
        .distinctUntilChanged()
        .flatMapLatest { (year, range) -> ledger.journal(year, range.from, range.to) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Every reversal link in the ledger, unfiltered, so a link shows even when its other half is outside the range. */
    private val reversalLinks: StateFlow<List<ReversalLink>> = ledger.reversalLinks()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Journal list, newest first, filtered by the search query. */
    val journal: StateFlow<List<JournalRow>> = combine(journalDocs, _journalQuery, accountNames, reversalLinks) {
            docs, query, names, links ->
        buildJournalRows(docs, query, links) { code -> names[code] ?: code }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Net (debit minus credit) per account for the selected fiscal year. Reloads on [refresh]. */
    private val balances: StateFlow<Map<String, Long>> = combine(yearFlow, refreshTick) { year, _ -> year }
        .mapLatest { year ->
            ledger.trialBalance(year).rows.associate { it.code to (it.debit - it.credit) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Picker rows: tree when the query is empty, flat matches otherwise. */
    val accountRows: StateFlow<List<AccountRow>> = combine(accounts, balances, _accountQuery, _hideZero) {
            list, net, query, hide ->
        buildAccountRows(list, net, query, hide)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Ledger of the selected account for the applied range. Null when no account is selected. */
    val accountLedger: StateFlow<AccountLedgerUi?> = combine(_selectedAccount, appliedRange) { code, range ->
        code to range
    }
        .distinctUntilChanged()
        .flatMapLatest { (code, range) ->
            if (code == null) {
                flowOf<AccountLedgerUi?>(null)
            } else {
                ledger.ledger(code, range.from, range.to).map { lines -> toAccountLedgerUi(code, lines) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // The current fiscal year must exist before any commit or reversal dated today.
        viewModelScope.launch { ledger.ensureFiscalYear(currentYear) }
    }

    fun selectYear(year: Int) {
        _filter.update { it.copy(year = year, dateError = false) }
    }

    fun selectPreset(preset: DatePreset) {
        _filter.update { it.copy(preset = preset, dateError = false) }
    }

    fun onFromTextChange(text: String) {
        applyCustom(from = text, to = _filter.value.toText)
    }

    fun onToTextChange(text: String) {
        applyCustom(from = _filter.value.fromText, to = text)
    }

    /** Keeps the last valid custom range. Typed text that is not valid sets [LedgerFilter.dateError]. */
    private fun applyCustom(from: String, to: String) {
        val start = parseJalaliInput(from)
        val end = parseJalaliInput(to)
        val valid = start != null && end != null && !end.isBefore(start)
        _filter.update { current ->
            current.copy(
                fromText = from,
                toText = to,
                custom = if (start != null && end != null && valid) DateRange(start, end) else current.custom,
                dateError = !valid && (from.isNotBlank() || to.isNotBlank()),
            )
        }
    }

    fun onJournalQuery(query: String) {
        _journalQuery.value = query
    }

    fun onAccountQuery(query: String) {
        _accountQuery.value = query
    }

    fun setHideZeroBalances(hide: Boolean) {
        _hideZero.value = hide
    }

    fun selectAccount(code: String?) {
        _selectedAccount.value = code
    }

    /** Reloads balances (trial balance is not a Flow). Called when the accounts tab opens and after a reversal. */
    fun refresh() {
        refreshTick.update { it + 1 }
    }

    fun openDocument(docId: String) {
        viewModelScope.launch {
            _detail.value = ledger.document(docId)?.let { DocumentDetailUi(it) }
        }
    }

    fun closeDocument() {
        _detail.value = null
    }

    fun askReverse(show: Boolean) {
        _detail.update { it?.copy(confirmReverse = show) }
    }

    /** Posts the reversal dated today. Ignored unless the sheet shows a document that can be reversed. */
    fun confirmReverse() {
        val current = _detail.value ?: return
        if (current.reversing || !current.canReverse) return
        _detail.value = current.copy(reversing = true, confirmReverse = false, error = null)
        viewModelScope.launch {
            try {
                val reversal = ledger.reverse(current.doc.id, today)
                refresh()
                _detail.value = ledger.document(current.doc.id)
                    ?.let { DocumentDetailUi(it, reversedNumber = reversal.docNumber) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _detail.update { it?.copy(reversing = false, error = DetailError.REVERSE_FAILED) }
            }
        }
    }

    private fun toAccountLedgerUi(code: String, lines: List<LedgerLine>): AccountLedgerUi = AccountLedgerUi(
        code = code,
        name = accountNames.value[code] ?: code,
        rows = lines.map {
            LedgerRowUi(it.entryId, it.date, it.docNumber, it.memo, it.debit, it.credit, it.balance)
        },
        totalDebit = lines.sumOf { it.debit },
        totalCredit = lines.sumOf { it.credit },
        closing = lines.lastOrNull()?.balance ?: 0L,
    )
}
