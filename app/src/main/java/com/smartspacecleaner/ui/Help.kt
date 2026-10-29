package com.smartspacecleaner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartspacecleaner.core.help.HelpTexts
import com.smartspacecleaner.core.help.HintId
import com.smartspacecleaner.core.help.InfoTopic

fun Screen.hint(): HintId? = when (this) {
    Screen.Summary -> HintId.SUMMARY
    Screen.Plan -> HintId.PLAN
    Screen.Report -> HintId.REPORT
    Screen.History -> HintId.HISTORY
    else -> null
}

/** Плашка-подсказка; после крестика больше не показывается. */
@Composable
fun HintBanner(text: String, onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Text("💡 $text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            TextButton(onClick = onClose) { Text("✕") }
        }
    }
}

/** Значок «?»: по нажатию — короткое пояснение. */
@Composable
fun InfoIcon(topic: InfoTopic) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(32.dp)) {
        Text("?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
    if (open) {
        val page = HelpTexts.info(topic)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(page.title) },
            text = { Text(page.body) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Понятно") } },
        )
    }
}

@Composable
fun AppMenu(feedbackEnabled: Boolean, onHowTo: () -> Unit, onFeedback: () -> Unit, onResetHints: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("⋮", fontSize = 22.sp) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Как пользоваться") }, onClick = { open = false; onHowTo() })
            if (feedbackEnabled) {
                DropdownMenuItem(text = { Text("Оставить отзыв") }, onClick = { open = false; onFeedback() })
            }
            DropdownMenuItem(text = { Text("Сбросить подсказки") }, onClick = { open = false; onResetHints() })
        }
    }
}
