package com.smartspacecleaner.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
        Radar(filesSeen)
        Text("Пока только смотрю, ничего не удаляю", style = MaterialTheme.typography.titleMedium)
    }
}

/** Радар: бегущий луч, расходящиеся круги и счётчик просмотренных файлов в центре. */
@Composable
private fun Radar(filesSeen: Int) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "sweep",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "pulse",
    )
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.18f), Color.Transparent)), radius = r)
            // Неподвижная сетка: три кольца и перекрестие.
            for (k in 1..3) drawCircle(primary.copy(alpha = 0.25f), radius = r * k / 3, style = Stroke(2f))
            drawLine(primary.copy(alpha = 0.15f), Offset(center.x - r, center.y), Offset(center.x + r, center.y), 2f)
            drawLine(primary.copy(alpha = 0.15f), Offset(center.x, center.y - r), Offset(center.x, center.y + r), 2f)
            // Две волны, идущие от центра со сдвигом в полпериода.
            for (shift in listOf(0f, 0.5f)) {
                val t = (pulse + shift) % 1f
                drawCircle(tertiary.copy(alpha = (1f - t) * 0.6f), radius = r * t, style = Stroke(4f))
            }
            // Луч с затухающим «хвостом».
            rotate(sweep) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.75f to Color.Transparent,
                        1f to primary.copy(alpha = 0.55f),
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = true,
                )
                drawLine(primary, center, Offset(center.x + r, center.y), 5f, cap = StrokeCap.Round)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$filesSeen", style = MaterialTheme.typography.headlineMedium)
            Text("файлов просмотрено", style = MaterialTheme.typography.bodySmall)
        }
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
