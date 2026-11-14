package com.flashloop.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashloop.app.FlashLoopApp
import com.flashloop.app.data.Flashcard
import com.flashloop.app.data.ReviewMode
import com.flashloop.app.data.ReviewResult
import com.flashloop.app.scheduling.Scheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUi(
    val loading: Boolean = true,
    val card: Flashcard? = null,
    val revealed: Boolean = false,
    val done: Boolean = false
)

/** The in-app version of the pending review (opened from the notification). */
class ReviewViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as FlashLoopApp
    private val _ui = MutableStateFlow(ReviewUi())
    val ui: StateFlow<ReviewUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val s = a.settings.get()
            val card = if (s.pendingCardId != 0L) a.db.flashcardDao().getById(s.pendingCardId) else null
            _ui.value = ReviewUi(loading = false, card = card)
        }
    }

    fun reveal() = _ui.update { it.copy(revealed = true) }

    fun answer(result: ReviewResult) {
        val card = _ui.value.card ?: return
        viewModelScope.launch {
            a.repository.review(card.id, result, ReviewMode.AUTOMATIC)
            _ui.update { it.copy(done = true) }
        }
    }

    fun snooze(minutes: Int) {
        viewModelScope.launch {
            Scheduler.snooze(a, minutes)
            _ui.update { it.copy(done = true) }
        }
    }
}

@Composable
fun ReviewScreen(onClose: () -> Unit, vm: ReviewViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val card = ui.card
        when {
            ui.loading -> CircularProgressIndicator()
            card == null || ui.done -> {
                Text(
                    if (ui.done) "All done." else "No flashcard is waiting for you.",
                    style = MaterialTheme.typography.headlineSmall
                )
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
            else -> {
                Text("Flashcard time", style = MaterialTheme.typography.headlineMedium)
                FlashcardFace(card.question, card.answer, ui.revealed, Modifier.weight(1f))
                AnswerControls(
                    revealed = ui.revealed,
                    onReveal = { vm.reveal() },
                    onRemembered = { vm.answer(ReviewResult.REMEMBERED) },
                    onForgot = { vm.answer(ReviewResult.FORGOTTEN) }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.snooze(5) }, modifier = Modifier.weight(1f)) { Text("Snooze 5 min") }
                    OutlinedButton(onClick = { vm.snooze(15) }, modifier = Modifier.weight(1f)) { Text("Snooze 15 min") }
                }
            }
        }
    }
}
