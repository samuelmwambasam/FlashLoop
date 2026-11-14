package com.flashloop.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashloop.app.FlashLoopApp
import com.flashloop.app.data.Deck
import com.flashloop.app.data.Flashcard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CardsViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as FlashLoopApp
    val query = MutableStateFlow("")
    val deckFilter = MutableStateFlow(0L)

    val decks: StateFlow<List<Deck>> =
        a.repository.decks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val items: StateFlow<List<Flashcard>> =
        combine(a.repository.cards, query, deckFilter) { cards, q, d ->
            cards.filter { c ->
                (d == 0L || c.deckId == d) &&
                    (q.isBlank() || c.question.contains(q, ignoreCase = true) || c.answer.contains(q, ignoreCase = true))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteCard(id: Long) { viewModelScope.launch { a.repository.deleteCard(id) } }
    fun addDeck(name: String) { viewModelScope.launch { a.repository.getOrCreateDeck(name.trim()) } }
    fun deleteDeck(id: Long) {
        viewModelScope.launch {
            a.repository.deleteDeck(id)
            if (deckFilter.value == id) deckFilter.value = 0L
        }
    }
}

@Composable
fun CardsScreen(onAdd: () -> Unit, onEdit: (Long) -> Unit, vm: CardsViewModel = viewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val deckFilter by vm.deckFilter.collectAsStateWithLifecycle()

    var cardToDelete by remember { mutableStateOf<Flashcard?>(null) }
    var showNewDeck by remember { mutableStateOf(false) }
    var deckToDelete by remember { mutableStateOf<Deck?>(null) }
    val deckNames = decks.associate { it.id to it.name }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("My Cards", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            ChipRow {
                FilterChip(selected = deckFilter == 0L, onClick = { vm.deckFilter.value = 0L }, label = { Text("All") })
                decks.forEach { d ->
                    FilterChip(
                        selected = deckFilter == d.id,
                        onClick = { vm.deckFilter.value = d.id },
                        label = { Text(d.name) }
                    )
                }
                FilterChip(selected = false, onClick = { showNewDeck = true }, label = { Text("+ Deck") })
            }
            val activeDeck = decks.firstOrNull { it.id == deckFilter }
            if (activeDeck != null) {
                TextButton(onClick = { deckToDelete = activeDeck }) { Text("Delete deck \"${activeDeck.name}\"") }
            }

            if (items.isEmpty()) {
                Text(
                    "No flashcards here yet. Tap + to create one.",
                    modifier = Modifier.padding(top = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
                items(items, key = { it.id }) { card ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(card.question, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val deck = card.deckId?.let { deckNames[it] }
                                val sub = if (deck != null) "$deck · ${card.learningStatus.label()}" else card.learningStatus.label()
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onEdit(card.id) }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton(onClick = { cardToDelete = card }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(Icons.Default.Add, "Add flashcard")
        }
    }

    cardToDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { cardToDelete = null },
            title = { Text("Delete this flashcard?") },
            text = { Text("\"${card.question}\" and its review history will be permanently removed.") },
            confirmButton = { TextButton(onClick = { vm.deleteCard(card.id); cardToDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { cardToDelete = null }) { Text("Cancel") } }
        )
    }

    deckToDelete?.let { deck ->
        AlertDialog(
            onDismissRequest = { deckToDelete = null },
            title = { Text("Delete deck?") },
            text = { Text("The deck \"${deck.name}\" is removed. Its cards are kept, just without a deck.") },
            confirmButton = { TextButton(onClick = { vm.deleteDeck(deck.id); deckToDelete = null }) { Text("Delete deck") } },
            dismissButton = { TextButton(onClick = { deckToDelete = null }) { Text("Cancel") } }
        )
    }

    if (showNewDeck) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewDeck = false },
            title = { Text("New deck") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Deck name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { vm.addDeck(name); showNewDeck = false }
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewDeck = false }) { Text("Cancel") } }
        )
    }
}
