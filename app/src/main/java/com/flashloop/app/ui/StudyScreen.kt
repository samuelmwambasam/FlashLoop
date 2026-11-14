package com.flashloop.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashloop.app.FlashLoopApp
import com.flashloop.app.data.Deck
import com.flashloop.app.data.Flashcard
import com.flashloop.app.data.LearningStatus
import com.flashloop.app.data.ReviewMode
import com.flashloop.app.data.ReviewResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StudyFilter(val label: String) {
    DUE("Due"), ALL("All"), NEW("New"), LEARNED("Learned")
}

enum class Phase { SETUP, SESSION, SUMMARY }

data class StudyUi(
    val phase: Phase = Phase.SETUP,
    val filter: StudyFilter = StudyFilter.DUE,
    val deckId: Long = 0,
    val shuffle: Boolean = true,
    val queue: List<Flashcard> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val remembered: Int = 0,
    val forgotten: Int = 0
)

class StudyViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as FlashLoopApp
    private val _ui = MutableStateFlow(StudyUi())
    val ui: StateFlow<StudyUi> = _ui.asStateFlow()

    val cards: StateFlow<List<Flashcard>> =
        a.repository.cards.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val decks: StateFlow<List<Deck>> =
        a.repository.decks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun available(ui: StudyUi, all: List<Flashcard>): List<Flashcard> {
        val now = System.currentTimeMillis()
        return all.filter { c ->
            (ui.deckId == 0L || c.deckId == ui.deckId) && when (ui.filter) {
                StudyFilter.DUE -> c.nextReviewAt <= now
                StudyFilter.ALL -> true
                StudyFilter.NEW -> c.learningStatus == LearningStatus.NEW
                StudyFilter.LEARNED -> c.learningStatus != LearningStatus.NEW
            }
        }
    }

    fun setFilter(f: StudyFilter) = _ui.update { it.copy(filter = f) }
    fun setDeck(id: Long) = _ui.update { it.copy(deckId = id) }
    fun setShuffle(v: Boolean) = _ui.update { it.copy(shuffle = v) }

    fun start() {
        val s = _ui.value
        val list = available(s, cards.value).let { l ->
            if (s.shuffle) l.shuffled() else l.sortedBy { it.nextReviewAt }
        }
        if (list.isEmpty()) return
        _ui.update {
            it.copy(phase = Phase.SESSION, queue = list, index = 0, revealed = false, remembered = 0, forgotten = 0)
        }
    }

    fun reveal() = _ui.update { it.copy(revealed = true) }

    fun answer(result: ReviewResult) {
        val s = _ui.value
        val card = s.queue.getOrNull(s.index) ?: return
        viewModelScope.launch { a.repository.review(card.id, result, ReviewMode.MANUAL) }
        _ui.update {
            val next = it.index + 1
            it.copy(
                index = next,
                revealed = false,
                remembered = it.remembered + if (result == ReviewResult.REMEMBERED) 1 else 0,
                forgotten = it.forgotten + if (result == ReviewResult.FORGOTTEN) 1 else 0,
                phase = if (next >= it.queue.size) Phase.SUMMARY else it.phase
            )
        }
    }

    fun reset() = _ui.update {
        StudyUi(filter = it.filter, deckId = it.deckId, shuffle = it.shuffle)
    }
}

@Composable
fun StudyScreen(vm: StudyViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        when (ui.phase) {
            Phase.SETUP -> SetupPane(ui, vm.available(ui, cards).size, decks, vm)
            Phase.SESSION -> SessionPane(ui, vm)
            Phase.SUMMARY -> SummaryPane(ui, vm)
        }
    }
}

@Composable
private fun SetupPane(ui: StudyUi, availableCount: Int, decks: List<Deck>, vm: StudyViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Study", style = MaterialTheme.typography.headlineMedium)

        Text("Which cards?", style = MaterialTheme.typography.titleSmall)
        ChipRow {
            StudyFilter.values().forEach { f ->
                FilterChip(selected = ui.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
            }
        }

        Text("Deck", style = MaterialTheme.typography.titleSmall)
        ChipRow {
            FilterChip(selected = ui.deckId == 0L, onClick = { vm.setDeck(0L) }, label = { Text("All decks") })
            decks.forEach { d ->
                FilterChip(selected = ui.deckId == d.id, onClick = { vm.setDeck(d.id) }, label = { Text(d.name) })
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Shuffle", Modifier.weight(1f))
            Switch(checked = ui.shuffle, onCheckedChange = { vm.setShuffle(it) })
        }

        Text("$availableCount card(s) available", style = MaterialTheme.typography.bodyMedium)
        if (availableCount == 0) {
            Text(
                "Nothing to study with these settings. Try \"All\", or create some flashcards.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = { vm.start() }, enabled = availableCount > 0, modifier = Modifier.fillMaxWidth()) {
            Text("Start")
        }
    }
}

@Composable
private fun SessionPane(ui: StudyUi, vm: StudyViewModel) {
    val card = ui.queue.getOrNull(ui.index) ?: return
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LinearProgressIndicator(
            progress = { ui.index.toFloat() / ui.queue.size },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Card ${ui.index + 1} of ${ui.queue.size}", style = MaterialTheme.typography.bodySmall)
        FlashcardFace(card.question, card.answer, ui.revealed, Modifier.weight(1f))
        AnswerControls(
            revealed = ui.revealed,
            onReveal = { vm.reveal() },
            onRemembered = { vm.answer(ReviewResult.REMEMBERED) },
            onForgot = { vm.answer(ReviewResult.FORGOTTEN) }
        )
    }
}

@Composable
private fun SummaryPane(ui: StudyUi, vm: StudyViewModel) {
    val reviewed = ui.remembered + ui.forgotten
    val pct = if (ui.queue.isEmpty()) 0 else reviewed * 100 / ui.queue.size
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Session complete", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text("Cards reviewed: $reviewed")
        Text("Remembered: ${ui.remembered}")
        Text("Forgotten: ${ui.forgotten}")
        Text("Completion: $pct%")
        Button(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
