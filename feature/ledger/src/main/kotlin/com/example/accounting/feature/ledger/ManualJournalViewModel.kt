package com.example.accounting.feature.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.data.repo.PartyRepository
import com.example.accounting.core.domain.Account
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.ManualAccount
import com.example.accounting.core.domain.ManualJournal
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.partyKey
import com.example.accounting.core.persian.JalaliDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Free-form journal editor (template MANUAL). Only write path: [save], which creates any new party معین and then
 * commits through [LedgerRepository.commit]. Nothing is persisted before save, and a second save is ignored.
 * Blocking party resolution and the commit run on Dispatchers.IO.
 */
@HiltViewModel
class ManualJournalViewModel @Inject constructor(
    private val ledger: LedgerRepository,
    private val parties: PartyRepository,
    private val clock: Clock,
) : ViewModel() {

    private var nextLineId = 1L

    private val accounts: StateFlow<List<Account>> = ledger.accounts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val openYears: StateFlow<List<Int>> = ledger.fiscalYears()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _draft = MutableStateFlow(blankDraft())

    val ui: StateFlow<ManualJournalUi> = combine(_draft, accounts, openYears) { draft, accts, years ->
        buildManualUi(draft, accts, years)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, buildManualUi(_draft.value, emptyList(), emptyList()))

    /** Blank document with two lines (one debit, one credit), or a prefill from [seed]. */
    fun start(seed: JournalEntry?) {
        _draft.value = if (seed == null) blankDraft() else seededDraft(seed) { nextLineId++ }
    }

    fun onAction(action: ManualAction) {
        // While a save is in flight only the save itself may change anything.
        if (_draft.value.saving && action != ManualAction.Save) return
        when (action) {
            is ManualAction.SetDate -> _draft.update { it.copy(dateText = action.text) }
            is ManualAction.SetMemo -> _draft.update { it.copy(memo = action.text) }
            ManualAction.AddLine -> {
                val line = newLine(Side.DEBIT)
                _draft.update { it.copy(lines = it.lines + line) }
            }
            is ManualAction.RemoveLine -> _draft.update { d ->
                d.copy(
                    lines = d.lines.filterNot { it.id == action.id },
                    pickerLine = d.pickerLine.takeUnless { it == action.id },
                )
            }
            is ManualAction.SetSide -> editLine(action.id) { it.copy(side = action.side) }
            is ManualAction.SetAmount -> editLine(action.id) { it.copy(amountText = action.text) }
            is ManualAction.OpenPicker -> _draft.update { it.copy(pickerLine = action.id, query = "") }
            ManualAction.ClosePicker -> _draft.update { it.copy(pickerLine = null, query = "") }
            is ManualAction.SetQuery -> _draft.update { it.copy(query = action.text) }
            is ManualAction.PickAccount -> setAccount(ManualAccount.Existing(action.code))
            is ManualAction.PickNewParty -> {
                val name = ManualJournal.partyDisplayName(_draft.value.query) ?: return
                setAccount(ManualAccount.NewParty(action.kind, name))
            }
            ManualAction.Save -> save()
        }
    }

    private fun blankDraft(): ManualDraft = ManualDraft(
        dateText = JalaliDate.from(LocalDate.now(clock)).toString(),
        lines = listOf(newLine(Side.DEBIT), newLine(Side.CREDIT)),
    )

    private fun newLine(side: Side): ManualLineDraft = ManualLineDraft(id = nextLineId++, side = side)

    private fun editLine(id: Long, transform: (ManualLineDraft) -> ManualLineDraft) {
        _draft.update { d -> d.copy(lines = d.lines.map { if (it.id == id) transform(it) else it }) }
    }

    private fun setAccount(account: ManualAccount) {
        val target = _draft.value.pickerLine ?: return
        _draft.update { d ->
            d.copy(
                lines = d.lines.map { if (it.id == target) it.copy(account = account) else it },
                pickerLine = null,
                query = "",
            )
        }
    }

    private fun save() {
        val snapshot = _draft.value
        if (snapshot.saving || snapshot.savedDocNumber != null) return
        if (!buildManualUi(snapshot, accounts.value, openYears.value).canSave) return
        _draft.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { persist(snapshot) }
            _draft.update { current ->
                when (outcome) {
                    is SaveOutcome.Saved -> current.copy(saving = false, savedDocNumber = outcome.docNumber)
                    is SaveOutcome.Failed -> current.copy(saving = false, error = outcome.error)
                }
            }
        }
    }

    /** Runs off the main thread: party resolution blocks, and the commit is a Room transaction. */
    private suspend fun persist(snapshot: ManualDraft): SaveOutcome {
        val date = parseJalaliInput(snapshot.dateText)
            ?: return SaveOutcome.Failed(ManualError.SAVE_FAILED)
        val resolved = try {
            snapshot.lines.map { it.toDomain() }.map { line ->
                val account = line.account
                if (account is ManualAccount.NewParty) {
                    val name = checkNotNull(ManualJournal.partyDisplayName(account.name))
                    line.copy(account = ManualAccount.Existing(parties.resolve(account.kind, partyKey(name), name)))
                } else {
                    line
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SaveOutcome.Failed(ManualError.PARTY_FAILED)
        }
        return try {
            val entry = ManualJournal.build(
                id = UUID.randomUUID().toString(),
                date = date,
                memo = snapshot.memo,
                lines = resolved,
                accounts = ledger.accounts().first(),
            )
            SaveOutcome.Saved(ledger.commit(entry).docNumber)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SaveOutcome.Failed(ManualError.SAVE_FAILED)
        }
    }

    private sealed interface SaveOutcome {
        data class Saved(val docNumber: Int) : SaveOutcome
        data class Failed(val error: ManualError) : SaveOutcome
    }
}
