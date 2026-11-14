package com.flashloop.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashloop.app.FlashLoopApp
import com.flashloop.app.data.Deck
import com.flashloop.app.data.Flashcard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as FlashLoopApp

    val decks: StateFlow<List<Deck>> =
        a.repository.decks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun load(id: Long): Flashcard? = a.db.flashcardDao().getById(id)

    fun save(id: Long, question: String, answer: String, deckId: Long?, newDeckName: String, onDone: () -> Unit) {
        viewModelScope.launch {
            var finalDeck = deckId
            val name = newDeckName.trim()
            if (name.isNotEmpty()) finalDeck = a.repository.getOrCreateDeck(name)
            if (id == 0L) {
                a.repository.addCard(question.trim(), answer.trim(), finalDeck)
            } else {
                a.repository.updateCard(id, question.trim(), answer.trim(), finalDeck)
            }
            onDone()
        }
    }
}

@Composable
fun EditScreen(id: Long, onDone: () -> Unit, vm: EditViewModel = viewModel()) {
    val decks by vm.decks.collectAsStateWithLifecycle()
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var deckId by remember { mutableStateOf<Long?>(null) }
    var newDeck by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) {
            vm.load(id)?.let {
                question = it.question
                answer = it.answer
                deckId = it.deckId
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(if (id == 0L) "New Flashcard" else "Edit Flashcard", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = question, onValueChange = { question = it; error = null },
            label = { Text("Question") }, minLines = 2, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = answer, onValueChange = { answer = it; error = null },
            label = { Text("Answer") }, minLines = 3, modifier = Modifier.fillMaxWidth()
        )

        Text("Deck (optional)", style = MaterialTheme.typography.titleSmall)
        ChipRow {
            FilterChip(selected = deckId == null, onClick = { deckId = null }, label = { Text("No deck") })
            decks.forEach { d ->
                FilterChip(selected = deckId == d.id, onClick = { deckId = d.id }, label = { Text(d.name) })
            }
        }
        OutlinedTextField(
            value = newDeck, onValueChange = { newDeck = it },
            label = { Text("Or create a new deck") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onDone) { Text("Cancel") }
            Button(
                enabled = !saving,
                onClick = {
                    if (question.isBlank() || answer.isBlank()) {
                        error = "Both the question and the answer are required."
                    } else {
                        saving = true
                        vm.save(id, question, answer, deckId, newDeck, onDone)
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Save") }
        }
    }
}
