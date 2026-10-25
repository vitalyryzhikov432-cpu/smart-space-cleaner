package com.smartspacecleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.report.HistoryPreview
import com.smartspacecleaner.core.text.displayPath
import com.smartspacecleaner.core.text.formatBytes
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SESSION_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru")).withZone(ZoneId.systemDefault())

@Composable
fun HistoryScreen(
    records: List<ActionRecord>,
    onRestore: (Long) -> Unit,
    onPurge: (List<Long>) -> Unit,
    onOpen: (ActionRecord) -> Unit,
    onBack: () -> Unit,
) {
    var confirmPurge by remember { mutableStateOf<List<Long>?>(null) }

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Text("← Назад") }
        if (records.isEmpty()) {
            Text("История пуста", modifier = Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            records.groupBy { it.sessionId }.forEach { (session, sessionRecords) ->
                item(key = "s-$session") {
                    Text(
                        "Сессия ${SESSION_FORMAT.format(sessionRecords.first().createdAt)}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                    )
                }
                sessionRecords.groupBy { it.groupId }.forEach { (groupId, groupRecords) ->
                    val purgeable = groupRecords.filter { it.status == ActionStatus.DONE && it.kind == ActionKind.TRASH }
                    item(key = "g-$session-$groupId") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${groupRecords.first().groupTitle} · ${formatBytes(groupRecords.sumOf { it.size })}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            if (purgeable.size > 1) {
                                TextButton(onClick = { confirmPurge = purgeable.map { it.id } }) { Text("Удалить группу сейчас") }
                            }
                        }
                    }
                    items(groupRecords, key = { it.id }) { record ->
                        HistoryRow(record, onRestore, { onOpen(record) }) { confirmPurge = listOf(record.id) }
                    }
                }
            }
        }
    }

    confirmPurge?.let { ids ->
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
            title = { Text("Удалить окончательно?") },
            text = { Text("Файлов: ${ids.size}. После этого вернуть их будет нельзя, зато место освободится сразу.") },
            confirmButton = {
                TextButton(onClick = {
                    onPurge(ids)
                    confirmPurge = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmPurge = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun HistoryRow(record: ActionRecord, onRestore: (Long) -> Unit, onOpen: () -> Unit, onPurge: () -> Unit) {
    val content = HistoryPreview.contentPath(record)
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = content != null, onClick = onOpen)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Thumbnail(content, name = record.originalPath)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(record.originalPath.substringAfterLast('/'))
            Text("откуда: ${HistoryPreview.origin(record)} · ${formatBytes(record.size)}", style = MaterialTheme.typography.bodySmall)
            Text(statusText(record), style = MaterialTheme.typography.bodySmall)
            record.keptOriginalPath?.let {
                Text("оригинал: ${displayPath(it)}", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                if (record.status == ActionStatus.DONE && record.kind != ActionKind.DELETE_APK) {
                    TextButton(onClick = { onRestore(record.id) }) { Text("Вернуть") }
                }
                if (record.status == ActionStatus.DONE && record.kind == ActionKind.TRASH) {
                    TextButton(onClick = onPurge) { Text("Удалить сейчас") }
                }
            }
        }
    }
}

private fun statusText(r: ActionRecord): String = when (r.status) {
    ActionStatus.PENDING -> "не завершено"
    ActionStatus.FAILED -> "не выполнено: ${r.reason ?: "причина неизвестна"}"
    ActionStatus.RESTORED -> "возвращён" + (r.reason?.let { " ($it)" } ?: "")
    ActionStatus.PURGED -> "удалён окончательно"
    ActionStatus.DONE -> when (r.kind) {
        ActionKind.TRASH -> "в корзине до ${r.expiresAt?.let { DAY_FORMAT.format(it) } ?: "—"}"
        ActionKind.DELETE_DUPLICATE -> "удалён"
        ActionKind.DELETE_APK -> "удалён окончательно — при необходимости скачайте заново"
    }
}
