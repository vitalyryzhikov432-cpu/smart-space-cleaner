package com.smartspacecleaner.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartspacecleaner.R
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.report.HistoryPreview
import com.smartspacecleaner.core.report.HistorySections
import com.smartspacecleaner.core.report.TrashContents
import com.smartspacecleaner.core.text.displayPath
import com.smartspacecleaner.core.text.formatBytes
import com.smartspacecleaner.core.text.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SESSION_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru")).withZone(ZoneId.systemDefault())

private val RESTORE_GREEN = Color(0xFF2E7D32)
private val DELETE_RED = Color(0xFFC62828)
private val TRASH_ORANGE = Color(0xFFE65100)

@Composable
fun HistoryScreen(
    records: List<ActionRecord>,
    onRestore: (Long) -> Unit,
    onPurge: (List<Long>) -> Unit,
    onRestoreMany: (List<Long>) -> Unit,
    onClearEarlier: () -> Unit,
    onOpen: (ActionRecord) -> Unit,
    readDetails: (ActionRecord) -> List<Pair<String, String>>?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val sections = HistorySections.of(records)
    val trash = TrashContents.of(records)
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    // Файл мог уйти из корзины (вернули, удалили) — отметка с него снимается сама.
    val selected = picked intersect trash.ids.toSet()
    var confirmPurge by remember { mutableStateOf<List<Long>?>(null) }
    var confirmRestore by remember { mutableStateOf<List<Long>?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<ActionRecord?>(null) }

    val target = if (selected.isEmpty()) trash.ids else trash.ids.filter { it in selected }
    fun whenNotEmpty(action: () -> Unit) {
        if (target.isEmpty()) {
            Toast.makeText(context, "Корзина пуста — возвращать и удалять нечего", Toast.LENGTH_SHORT).show()
        } else {
            action()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Text("← Назад") }
        TrashHeader(
            trash = trash,
            selectedCount = selected.size,
            onRestore = { whenNotEmpty { confirmRestore = target } },
            onPurge = { whenNotEmpty { confirmPurge = target } },
        )
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            item(key = "trash-title") {
                SectionTitle("В корзине (${sections.inTrash.size})") {
                    if (sections.inTrash.isNotEmpty()) {
                        val all = selected.size == trash.ids.size
                        TextButton(onClick = { picked = if (all) emptySet() else trash.ids.toSet() }) {
                            Text(if (all) "Снять выбор" else "Выбрать все")
                        }
                    }
                }
            }
            if (sections.inTrash.isEmpty()) {
                item(key = "trash-empty") { Hint("Здесь появятся файлы, которые приложение уберёт в корзину") }
            }
            items(sections.inTrash.asReversed(), key = { it.id }) { record ->
                HistoryRow(
                    record = record,
                    checked = record.id in selected,
                    onCheck = { picked = if (record.id in picked) picked - record.id else picked + record.id },
                    onRestore = onRestore,
                    onOpen = { onOpen(record) },
                    onDetails = { details = record },
                    onPurge = { confirmPurge = listOf(record.id) },
                )
            }
            item(key = "earlier-title") {
                HorizontalDivider(Modifier.padding(top = 16.dp))
                SectionTitle("Ранее") {
                    if (sections.earlier.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) { Text("Очистить историю") }
                    }
                }
                Hint("Возвращённые и удалённые файлы. Записи хранятся 15 дней.")
            }
            sections.earlier.asReversed().groupBy { it.sessionId }.forEach { (session, sessionRecords) ->
                item(key = "s-$session") {
                    Text(
                        "Сессия ${SESSION_FORMAT.format(sessionRecords.first().createdAt)}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                    )
                }
                items(sessionRecords, key = { it.id }) { record ->
                    HistoryRow(record, null, {}, onRestore, { onOpen(record) }, { details = record }, {})
                }
            }
        }
    }

    confirmRestore?.let { ids ->
        val bytes = records.filter { it.id in ids }.sumOf { it.size }
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Вернуть файлы?") },
            text = { Text("Файлов: ${ids.size} (${formatBytes(bytes)}). Они вернутся на прежние места.") },
            confirmButton = {
                TextButton(onClick = {
                    onRestoreMany(ids)
                    picked = emptySet()
                    confirmRestore = null
                }) { Text("Вернуть") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Отмена") } },
        )
    }

    confirmPurge?.let { ids ->
        val bytes = records.filter { it.id in ids }.sumOf { it.size }
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
            title = { Text("Удалить окончательно?") },
            text = { Text("Файлов: ${ids.size} (${formatBytes(bytes)}). После этого вернуть их будет нельзя, зато место освободится сразу.") },
            confirmButton = {
                TextButton(onClick = {
                    onPurge(ids)
                    picked = emptySet()
                    confirmPurge = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmPurge = null }) { Text("Отмена") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить историю?") },
            text = { Text("Сотрутся только записи раздела «Ранее». Файлы на телефоне и в корзине останутся как есть.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearEarlier()
                    confirmClear = false
                }) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } },
        )
    }

    details?.let { record -> DetailsDialog(record, readDetails) { details = null } }
}

@Composable
private fun TrashHeader(trash: TrashContents, selectedCount: Int, onRestore: () -> Unit, onPurge: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_trash),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Text("Корзина", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp))
        }
        val freesBy = trash.freesBy
        Text(
            if (trash.ids.isEmpty() || freesBy == null) {
                "Корзина пуста"
            } else {
                val count = trash.ids.size
                "$count ${plural(count, "файл", "файла", "файлов")} · ${formatBytes(trash.bytes)} · освободится ${DAY_FORMAT.format(freesBy)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        // Кнопки всегда цветные; при пустой корзине нажатие объясняет, почему ничего не происходит.
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRestore,
                colors = ButtonDefaults.buttonColors(containerColor = RESTORE_GREEN),
                modifier = Modifier.weight(1f),
            ) { Text(if (selectedCount > 0) "Вернуть выбранные ($selectedCount)" else "Вернуть все") }
            Button(
                onClick = onPurge,
                colors = ButtonDefaults.buttonColors(containerColor = DELETE_RED),
                modifier = Modifier.weight(1f),
            ) { Text(if (selectedCount > 0) "Удалить выбранные ($selectedCount)" else "Удалить все") }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
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

/** [checked] = null — строка журнала «Ранее», без галочки и без действий над файлом. */
@Composable
private fun HistoryRow(
    record: ActionRecord,
    checked: Boolean?,
    onCheck: () -> Unit,
    onRestore: (Long) -> Unit,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onPurge: () -> Unit,
) {
    val content = HistoryPreview.contentPath(record)
    // Файл мог убрать кто-то другой уже после нас (например, WhatsApp чистит свою папку).
    val present by produceState(initialValue = true, content) {
        value = content != null && withContext(Dispatchers.IO) { File(content).exists() }
    }
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = content != null && present, onClick = onOpen)
            .padding(start = if (checked != null) 4.dp else 16.dp, end = 16.dp, top = 8.dp),
    ) {
        if (checked != null) Checkbox(checked = checked, onCheckedChange = { onCheck() })
        Thumbnail(content.takeIf { present }, name = record.originalPath)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(record.originalPath.substringAfterLast('/'))
            Text("откуда: ${HistoryPreview.origin(record)} · ${formatBytes(record.size)}", style = MaterialTheme.typography.bodySmall)
            Text(
                statusText(record),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor(record),
            )
            if (content != null && !present) {
                Text("файла сейчас нет на месте", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            record.keptOriginalPath?.let {
                Text("оригинал: ${displayPath(it)}", style = MaterialTheme.typography.bodySmall)
            }
            if (checked != null) {
                Row {
                    TextButton(onClick = { onRestore(record.id) }) { Text("Вернуть") }
                    TextButton(onClick = onPurge) { Text("Удалить сейчас") }
                    TextButton(onClick = onDetails) { Text("Сведения") }
                }
            }
        }
    }
}

private fun statusColor(r: ActionRecord): Color = when (r.status) {
    ActionStatus.RESTORED -> RESTORE_GREEN
    ActionStatus.DONE -> if (r.kind == ActionKind.TRASH) TRASH_ORANGE else DELETE_RED
    ActionStatus.PURGED, ActionStatus.FAILED -> DELETE_RED
    ActionStatus.PENDING -> TRASH_ORANGE
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
