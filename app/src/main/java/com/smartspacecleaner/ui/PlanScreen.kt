package com.smartspacecleaner.ui

import android.media.ThumbnailUtils
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.GroupCheck
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.PlannedItem
import com.smartspacecleaner.core.model.RiskLevel
import com.smartspacecleaner.core.model.checkState
import com.smartspacecleaner.core.text.displayPath
import com.smartspacecleaner.core.text.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PlanScreen(
    plan: Plan,
    selected: Set<String>,
    clearCache: Boolean,
    onToggleGroup: (Group) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCache: () -> Unit,
    onExecute: () -> Unit,
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            RiskLevel.entries.forEach { level ->
                val groups = plan.groups.filter { it.level == level }
                if (groups.isEmpty()) return@forEach
                item(key = "level-$level") { LevelHeader(level) }
                groups.forEach { group ->
                    val isCache = group.kind == GroupKind.APP_CACHE
                    val check = if (isCache) {
                        if (clearCache) GroupCheck.ALL else GroupCheck.NONE
                    } else {
                        group.checkState(selected)
                    }
                    val isExpanded = expanded[group.id] == true
                    item(key = group.id) {
                        GroupCard(
                            group = group,
                            check = check,
                            expanded = isExpanded,
                            onToggle = { if (isCache) onToggleCache() else onToggleGroup(group) },
                            onExpand = { expanded[group.id] = !isExpanded },
                        )
                    }
                    if (isExpanded) {
                        items(group.items, key = { "${group.id}|${it.path}" }) { item ->
                            ItemRow(item, item.path in selected) { onToggleItem(item.path) }
                        }
                    }
                }
            }
        }
        val bytes = plan.selectedBytes(selected)
        Button(
            onClick = onExecute,
            enabled = bytes > 0 || clearCache,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text("Выполнить отмеченное (${formatBytes(bytes)}${if (clearCache) " + кэш" else ""})")
        }
    }
}

@Composable
private fun LevelHeader(level: RiskLevel) {
    val text = when (level) {
        RiskLevel.SAFE -> "Уровень 0 · Безопасное"
        RiskLevel.EXACT -> "Уровень 1 · Точные дубли и установщики"
        RiskLevel.PERSONAL -> "Уровень 2 · Личное — решаете поштучно"
    }
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun GroupCard(group: Group, check: GroupCheck, expanded: Boolean, onToggle: () -> Unit, onExpand: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.padding(12.dp)) {
            TriStateCheckbox(
                state = when (check) {
                    GroupCheck.ALL -> ToggleableState.On
                    GroupCheck.SOME -> ToggleableState.Indeterminate
                    GroupCheck.NONE -> ToggleableState.Off
                },
                onClick = onToggle,
            )
            Column(Modifier.weight(1f)) {
                val title = if (group.kind == GroupKind.APP_CACHE) group.title else "${group.title} — ${formatBytes(group.totalBytes)}"
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text("Удалится: ${group.willRemove}", style = MaterialTheme.typography.bodySmall)
                Text("Останется: ${group.willKeep}", style = MaterialTheme.typography.bodySmall)
                Text("Почему: ${group.why}", style = MaterialTheme.typography.bodySmall)
                if (group.items.isNotEmpty()) {
                    TextButton(onClick = onExpand) {
                        Text(if (expanded) "Скрыть файлы" else "Показать файлы (${group.items.size})")
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: PlannedItem, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 32.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Thumbnail(item.path)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(item.path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatBytes(item.size), style = MaterialTheme.typography.bodySmall)
            item.keptOriginalPath?.let {
                Text("оригинал: ${displayPath(it)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "gif")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "3gp", "webm", "avi")

@Composable
private fun Thumbnail(path: String) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { loadThumbnail(path) }
    }
    Box(Modifier.size(48.dp)) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

private fun loadThumbnail(path: String): ImageBitmap? = try {
    val file = File(path)
    val size = Size(128, 128)
    when (path.substringAfterLast('.', "").lowercase()) {
        in IMAGE_EXTENSIONS -> ThumbnailUtils.createImageThumbnail(file, size, null)
        in VIDEO_EXTENSIONS -> ThumbnailUtils.createVideoThumbnail(file, size, null)
        else -> null
    }?.asImageBitmap()
} catch (e: Exception) {
    null
}
