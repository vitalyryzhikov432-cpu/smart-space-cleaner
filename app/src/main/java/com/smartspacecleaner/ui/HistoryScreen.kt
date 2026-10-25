package com.smartspacecleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.report.HistoryPreview
import com.smartspacecleaner.core.report.TrashContents
import com.smartspacecleaner.core.text.displayPath
import com.smartspacecleaner.core.text.formatBytes
import com.smartspacecleaner.core.text.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    onRestoreAll: (List<Long>) -> Unit,
    onOpen: (ActionRecord) -> Unit,
    readDetails: (ActionRecord) -> List<Pair<String, String>>?,
    onBack: () -> Unit,
) {
    var confirmPurge by remember { mutableStateOf<List<Long>?>(null) }
    var confirmRestoreAll by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<ActionRecord?>(null) }
    val trash = TrashContents.of(records)

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Text("← Назад") }
        TrashHeader(
            trash = trash,
            onRestoreAll = { confirmRestoreAll = true },
            onPurgeAll = { confirmPurge = trash.ids },
        )
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
                        HistoryRow(record, onRestore, { onOpen(record) }, { details = record }) { confirmPurge = listOf(record.id) }
                    }
                }
            }
        }
    }

    if (confirmRestoreAll) {
        AlertDialog(
            onDismissRequest = { confirmRestoreAll = false },
            title = { Text("Вернуть все файлы?") },
            text = { Text("Файлов: ${trash.ids.size} (${formatBytes(trash.bytes)}). Они вернутся на прежние места.") },
            confirmButton = {
                TextButton(onClick = {
                    onRestoreAll(trash.ids)
                    confirmRestoreAll = false
                }) { Text("Вернуть") }
            },
            dismissButton = { TextButton(onClick = { confirmRestoreAll = false }) { Text("Отмена") } },
        )
    }

    details?.let { record -> DetailsDialog(record, readDetails) { details = null } }

    confirmPurge?.let { ids ->
        val bytes = records.filter { it.id in ids }.sumOf { it.size }
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
            title = { Text("Удалить окончательно?") },
            text = { Text("Файлов: ${ids.size} (${formatBytes(bytes)}). После этого вернуть их будет нельзя, зато место освободится сразу.") },
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
private fun TrashHeader(trash: TrashContents, onRestoreAll: () -> Unit, onPurgeAll: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Корзина", style = MaterialTheme.typography.headlineSmall)
        val freesBy = trash.freesBy
        if (trash.ids.isEmpty() || freesBy == null) {
            Text("Корзина пуста", style = MaterialTheme.typography.bodyMedium)
        } else {
            val count = trash.ids.size
            Text(
                "$count ${plural(count, "файл", "файла", "файлов")} · ${formatBytes(trash.bytes)} · освободится ${DAY_FORMAT.format(freesBy)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRestoreAll) { Text("Вернуть все") }
                Button(onClick = onPurgeAll) { Text("Удалить все") }
            }
        }
    }
}

/** Свои «Сведения»: Галерея файлы из корзины не видит, поэтому показывает пустое окно. */
@Composable
private fun DetailsDialog(record: ActionRecord, readDetails: (ActionRecord) -> List<Pair<String, String>>?, onClose: () -> Unit) {
    val lines by produceState<List<Pair<String, String>>?>(initialValue = null, record) {
        value = withContext(Dispatchers.IO) { readDetails(record) }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Сведения") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val shown = lines
                if (shown == null) {
                    Text("Файла уже нет или его не удалось прочитать")
                } else {
                    shown.forEach { (label, value) ->
                        Row {
                            Text(label + ":", color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(110.dp))
                            Text(value, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
    )
}

@Composable
private fun HistoryRow(record: ActionRecord, onRestore: (Long) -> Unit, onOpen: () -> Unit, onDetails: () -> Unit, onPurge: () -> Unit) {
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
                    TextButton(onClick = onDetails) { Text("Сведения") }
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
