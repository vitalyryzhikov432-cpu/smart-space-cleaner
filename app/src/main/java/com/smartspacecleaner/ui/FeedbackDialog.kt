package com.smartspacecleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.smartspacecleaner.core.feedback.FeedbackCheck
import com.smartspacecleaner.core.feedback.FeedbackDraft
import com.smartspacecleaner.core.feedback.FeedbackSource
import com.smartspacecleaner.core.feedback.FeedbackValidator

private val STAR_GOLD = Color(0xFFF9A825)

@Composable
fun FeedbackDialog(
    source: FeedbackSource,
    initialName: String,
    onSend: (FeedbackDraft) -> Unit,
    onLater: () -> Unit,
    onOptOut: () -> Unit,
) {
    // rememberSaveable: оценка и текст переживают поворот экрана и сворачивание.
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(initialName) }
    val draft = FeedbackDraft(rating, text, name)
    val check = FeedbackValidator.check(draft)

    AlertDialog(
        onDismissRequest = onLater,
        // Случайное касание вне окна или «Назад» не должны стирать набранный текст — закрыть можно только кнопками.
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Помогите улучшить приложение") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Как вам очистка?")
                Row {
                    (1..5).forEach { star ->
                        TextButton(onClick = { rating = star }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp)) {
                            Text(if (star <= rating) "★" else "☆", fontSize = 30.sp, color = STAR_GOLD)
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= FeedbackValidator.MAX_TEXT) text = it },
                    label = { Text("Что понравилось, что мешало, что непонятно?") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= FeedbackValidator.MAX_NAME) name = it },
                    label = { Text("Имя (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (rating > 0 && check is FeedbackCheck.Invalid) {
                    Text(check.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Отправятся: ваш текст, оценка, имя, версия приложения, модель телефона и версия Android. " +
                        "Файлы и их названия не отправляются.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (source == FeedbackSource.AFTER_CLEANUP) {
                    TextButton(onClick = onOptOut) { Text("Больше не спрашивать") }
                }
            }
        },
        confirmButton = { Button(onClick = { onSend(draft) }, enabled = check is FeedbackCheck.Ok) { Text("Отправить") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Позже") } },
    )
}
