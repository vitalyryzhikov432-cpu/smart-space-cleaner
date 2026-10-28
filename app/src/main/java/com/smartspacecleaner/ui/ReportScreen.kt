package com.smartspacecleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartspacecleaner.core.report.Report
import com.smartspacecleaner.core.text.formatBytes
import com.smartspacecleaner.core.text.plural
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val DAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")).withZone(ZoneId.systemDefault())

@Composable
fun ReportScreen(report: Report, onHistory: () -> Unit, onDone: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Готово", style = MaterialTheme.typography.headlineSmall) }
        item {
            Text("Было свободно ${formatBytes(report.freeBefore)} → стало ${formatBytes(report.freeAfter)}")
            Text("Освобождено сейчас: ${formatBytes(report.freedNow)}", style = MaterialTheme.typography.titleMedium)
        }
        val freesOn = report.trashFreesOn
        if (report.inTrashBytes > 0 && freesOn != null) {
            item {
                Text(
                    "Ещё ${formatBytes(report.inTrashBytes)} освободится ${DAY_FORMAT.format(freesOn)}. " +
                        "До этого файлы можно вернуть или удалить сразу в «Истории».",
                )
            }
        }
        if (report.done.isNotEmpty()) {
            item { Text("Сделано", style = MaterialTheme.typography.titleMedium) }
            items(report.done) { line ->
                Text("${line.groupTitle}: ${line.count} ${plural(line.count, "файл", "файла", "файлов")} · ${formatBytes(line.bytes)}")
            }
        }
        if (report.failed.isNotEmpty()) {
            item { Text("Не сделано", style = MaterialTheme.typography.titleMedium) }
            items(report.failed) { line ->
                Text("${line.path.substringAfterLast('/')}: ${line.reason}")
                Text(line.manualHint, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (report.notSelected.isNotEmpty()) {
            item {
                Text("Не отмечено вами", style = MaterialTheme.typography.titleMedium)
                Text(report.notSelected.joinToString(", "))
            }
        }
        report.cacheNote?.let { note -> item { Text(note) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDone) { Text("Просканировать снова") }
                OutlinedButton(onClick = onHistory) { Text("История") }
            }
        }
    }
}
