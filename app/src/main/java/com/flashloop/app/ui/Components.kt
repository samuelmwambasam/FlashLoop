package com.flashloop.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flashloop.app.data.LearningStatus

fun LearningStatus.label(): String = when (this) {
    LearningStatus.NEW -> "New"
    LearningStatus.LEARNING -> "Learning"
    LearningStatus.REVIEWING -> "Reviewing"
    LearningStatus.WELL_REMEMBERED -> "Well remembered"
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun FlashcardFace(question: String, answer: String, revealed: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(question, style = MaterialTheme.typography.headlineSmall)
            if (revealed) {
                HorizontalDivider()
                Text(answer, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun AnswerControls(
    revealed: Boolean,
    onReveal: () -> Unit,
    onRemembered: () -> Unit,
    onForgot: () -> Unit
) {
    if (!revealed) {
        Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) { Text("Reveal Answer") }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onForgot, modifier = Modifier.weight(1f)) { Text("Didn't Remember") }
            Button(onClick = onRemembered, modifier = Modifier.weight(1f)) { Text("Remembered") }
        }
    }
}
