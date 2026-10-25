package com.smartspacecleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartspacecleaner.core.report.Summary

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Нужен доступ ко всем файлам", style = MaterialTheme.typography.headlineSmall)
        Text("Чтобы найти дубли, старые скриншоты, установщики и крупные файлы во всех папках, приложению нужно видеть всю память телефона.")
        Text("Приложение ничего не удаляет само: сначала только смотрит, потом показывает план, и вы решаете по каждой группе. Удалённое 7 дней лежит в корзине, и его можно вернуть.")
        Text("Без этого разрешения работать не получится: ограниченный доступ не видит загрузки и папки мессенджеров.")
        Button(onClick = onGrant) { Text("Открыть настройку доступа") }
    }
}

@Composable
fun ScanningScreen(filesSeen: Int) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("Пока только смотрю, ничего не удаляю", style = MaterialTheme.typography.titleMedium)
        Text("Просмотрено файлов: $filesSeen")
    }
}

@Composable
fun SummaryScreen(summary: Summary, scanMillis: Long, onShowPlan: () -> Unit, onHistory: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${summary.severity.emoji} Память: ${summary.severity.label}", style = MaterialTheme.typography.headlineSmall)
        summary.lines.forEach { Text(it) }
        Text("Скан занял ${scanMillis / 1000} с", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onShowPlan) { Text("Показать план") }
        OutlinedButton(onClick = onHistory) { Text("История") }
    }
}

@Composable
fun ExecutingScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("Выполняю только то, что вы отметили…")
    }
}
