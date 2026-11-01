package com.smartspacecleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.smartspacecleaner.core.help.InfoTopic
import com.smartspacecleaner.core.sort.SortCategory
import com.smartspacecleaner.core.sort.SortItem
import com.smartspacecleaner.core.sort.SortPlan
import com.smartspacecleaner.core.sort.SortSection
import com.smartspacecleaner.core.sort.SortTexts
import com.smartspacecleaner.core.sort.SourceFolder
import com.smartspacecleaner.core.text.plural

private val REVIEW_YELLOW = Color(0xFFFFF59D)

private fun files(n: Int) = "$n ${plural(n, "файл", "файла", "файлов")}"

@Composable
fun SortSourcesScreen(
    sources: List<SourceFolder>?,
    picked: Set<String>,
    onToggle: (String) -> Unit,
    onAnalyze: () -> Unit,
    onQuick: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Text("← Назад") }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Откуда разложить", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            InfoIcon(InfoTopic.SORT_SOURCES)
        }
        when {
            sources == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            sources.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) { Text("Фото и видео для раскладки не найдено") }
            else -> LazyColumn(Modifier.weight(1f)) {
                items(sources, key = { it.path }) { folder ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(folder.path) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = folder.path in picked, onCheckedChange = { onToggle(folder.path) })
                        Text(SortTexts.folderLine(folder))
                    }
                }
            }
        }
        val count = sources.orEmpty().filter { it.path in picked }.sumOf { it.files.size }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAnalyze, enabled = count > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Анализировать и показать план (${files(count)})")
            }
            OutlinedButton(onClick = onQuick, enabled = count > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Разложить сразу, без проверки (${files(count)})")
            }
        }
    }
}

@Composable
fun SortAnalyzingScreen(
    done: Int,
    total: Int,
    quickText: String?,
    onCancel: () -> Unit,
    onQuickMove: () -> Unit,
    onQuickReview: () -> Unit,
) {
    KeepScreenOn()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Radar("$done из $total", "просмотрено")
        Text("Смотрю фото, ничего не переношу", style = MaterialTheme.typography.titleMedium)
        if (quickText == null) TextButton(onClick = onCancel) { Text("Отмена") }
    }
    if (quickText != null) {
        AlertDialog(
            onDismissRequest = {},
            text = { Text(quickText) },
            confirmButton = { TextButton(onClick = onQuickMove) { Text("Разложить") } },
            dismissButton = { TextButton(onClick = onQuickReview) { Text("Посмотреть план") } },
        )
    }
}

@Composable
fun SortPlanScreen(
    plan: SortPlan,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onToggleSection: (SortSection) -> Unit,
    onReassign: (String, SortCategory) -> Unit,
    onOpenFile: (path: String, name: String) -> Unit,
    onMove: () -> Unit,
    onBack: () -> Unit,
) {
    var opened by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val count = plan.moves(selected).size
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text("План раскладки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            InfoIcon(InfoTopic.SORT_PLAN)
        }
        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            plan.sections().forEach { section ->
                item(key = "h-${section.category}", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(section, selected, onToggleSection)
                }
                items(section.items, key = { it.path }) { item ->
                    SortTile(item, checked = item.path in selected, onToggle = { onToggle(item.path) }, onOpen = { opened = item.path })
                }
            }
        }
        Button(onClick = { confirm = true }, enabled = count > 0, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Разложить (${files(count)})")
        }
    }
    opened?.let { path ->
        plan.items.firstOrNull { it.path == path }?.let { item ->
            SortItemDialog(item, onReassign = { c -> onReassign(path, c); opened = null }, onOpenFile = onOpenFile, onClose = { opened = null })
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(SortTexts.confirmMove(count)) },
            confirmButton = { TextButton(onClick = { confirm = false; onMove() }) { Text("Перенести") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SectionHeader(section: SortSection, selected: Set<String>, onToggleSection: (SortSection) -> Unit) {
    val review = section.category == null
    Surface(
        color = if (review) REVIEW_YELLOW else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                SortTexts.sectionTitle(section),
                style = MaterialTheme.typography.titleSmall,
                color = if (review) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // В «Проверьте» отмечать нечего: без категории файл всё равно не переедет.
            if (!review) {
                val all = section.items.all { it.path in selected }
                TextButton(onClick = { onToggleSection(section) }) { Text(if (all) "Снять все" else "Выбрать все") }
            }
        }
    }
}

@Composable
private fun SortTile(item: SortItem, checked: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Box(Modifier.padding(2.dp).clickable(onClick = onOpen)) {
        Thumbnail(item.path, size = 84.dp, px = 192)
        if (item.decision.category != null) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() }, modifier = Modifier.align(Alignment.TopEnd))
        }
        if (item.decision.category == SortCategory.VIDEO) {
            Text("▶", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun SortItemDialog(item: SortItem, onReassign: (SortCategory) -> Unit, onOpenFile: (String, String) -> Unit, onClose: () -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(item.path.substringAfterLast('/')) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().clickable { onOpenFile(item.path, item.path) }, contentAlignment = Alignment.Center) {
                    Thumbnail(item.path, size = 260.dp, px = 1024)
                }
                Text("Почему здесь: ${item.decision.reason}", style = MaterialTheme.typography.bodySmall)
                if (choosing) {
                    SortCategory.entries.forEach { c ->
                        TextButton(onClick = { onReassign(c) }) { Text(c.title) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = !choosing }) { Text("Перенести в…") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
    )
}

@Composable
fun SortMovingScreen(done: Int, total: Int) {
    KeepScreenOn()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("Переношу: $done из $total")
    }
}

@Composable
fun SortResultScreen(text: String, canReview: Boolean, onReview: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Готово", style = MaterialTheme.typography.headlineSmall)
        Text(text)
        Text("Вернуть всё на место можно в «Истории».", style = MaterialTheme.typography.bodySmall)
        if (canReview) OutlinedButton(onClick = onReview) { Text("Разобрать оставшиеся") }
        Button(onClick = onDone) { Text("Готово") }
    }
}

/** Пока идёт долгая работа, экран не гаснет: иначе система может остановить приложение. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
