package com.flashloop.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashloop.app.FlashLoopApp
import com.flashloop.app.data.LearningStatus
import com.flashloop.app.data.summary
import android.text.format.DateFormat
import kotlinx.coroutines.delay
import java.util.Date
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val total: Int = 0,
    val due: Int = 0,
    val learned: Int = 0,
    val summary: String = "",
    val enabled: Boolean = true,
    val pausedUntil: Long = 0,
    val nextAlarmAt: Long = 0
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as FlashLoopApp

    val state: StateFlow<HomeState> = combine(a.repository.cards, a.settings.flow) { cards, settings ->
        val now = System.currentTimeMillis()
        HomeState(
            total = cards.size,
            due = cards.count { it.nextReviewAt <= now },
            learned = cards.count { it.learningStatus == LearningStatus.WELL_REMEMBERED },
            summary = settings.summary(),
            enabled = settings.enabled,
            pausedUntil = settings.pausedUntil,
            nextAlarmAt = settings.nextAlarmAt
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

@Composable
fun HomeScreen(
    onStudy: () -> Unit,
    onCreate: () -> Unit,
    onCards: () -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val paused = s.pausedUntil > now
    val remaining = (s.nextAlarmAt - now) / 1000
    val countdown = when {
        !s.enabled -> "Interruptions are off"
        paused -> "Paused"
        s.nextAlarmAt == 0L -> "Nothing scheduled (create a card)"
        remaining <= 0 -> "Any moment now…"
        else -> "%d:%02d:%02d".format(remaining / 3600, (remaining % 3600) / 60, remaining % 60)
    }
    val atText = if (s.enabled && !paused && s.nextAlarmAt > now) {
        "at ${DateFormat.getTimeFormat(ctx).format(Date(s.nextAlarmAt))} (Android may delay it a little)"
    } else null

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("FlashLoop", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Cards", s.total, Modifier.weight(1f))
            StatCard("Due", s.due, Modifier.weight(1f))
            StatCard("Learned", s.learned, Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Next flashcard in", style = MaterialTheme.typography.titleSmall)
                Text(countdown, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (atText != null) Text(atText, style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Interruptions", style = MaterialTheme.typography.titleSmall)
                Text(s.summary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Button(onClick = onStudy, enabled = s.total > 0, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Study Now")
        }
        OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Create Flashcard")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCards) { Text("My Flashcards") }
            TextButton(onClick = onSettings) { Text("Settings") }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
