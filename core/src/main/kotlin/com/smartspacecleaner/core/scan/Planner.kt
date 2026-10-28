package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.PlannedItem
import com.smartspacecleaner.core.model.RiskLevel
import com.smartspacecleaner.core.text.formatBytes
import com.smartspacecleaner.core.text.plural

private const val TRASH_NOTE = "7 дней файл лежит в корзине"
private const val ROOT_NAME = "корень памяти"

class Planner(
    private val layout: StorageLayout,
    private val keepPolicy: KeepCopyPolicy,
    private val duplicateFinder: DuplicateFinder,
    private val apkRule: ApkRule,
    private val screenshotRule: OldScreenshotRule,
    private val largeFileRule: LargeFileRule,
    private val messengerRule: MessengerMediaRule,
) {
    fun plan(entries: List<FileEntry>): Plan {
        val claimed = mutableSetOf<String>()
        val groups = mutableListOf(appCacheGroup())
        groups += duplicateGroups(duplicateFinder.find(entries), claimed)

        val apks = apkRule.classify(entries)
        groups += listOfNotNull(
            simpleGroup(
                "apk-installed", GroupKind.APK_INSTALLED, RiskLevel.EXACT,
                "Установщики установленных приложений",
                "сами приложения остаются установленными",
                "приложение уже установлено той же или более новой версии; установщик всегда можно скачать снова",
                apks.installed, claimed, selected = true,
            ),
            simpleGroup(
                "apk-unknown", GroupKind.APK_UNKNOWN, RiskLevel.PERSONAL,
                "Установщики неизвестных приложений",
                "всё, что не отметите",
                "приложение из этого файла не установлено — проверьте, нужен ли он; $TRASH_NOTE",
                apks.unknown, claimed, selected = false,
            ),
        )
        messengerRule.find(entries).forEach { (messenger, files) ->
            simpleGroup(
                "messenger:${messenger.name.lowercase()}", GroupKind.MESSENGER_MEDIA, RiskLevel.PERSONAL,
                "Медиа ${messenger.title} старше ${messengerRule.maxAgeDays} дней",
                "всё, что не отметите",
                "файлы, полученные в мессенджере; обычно их можно снова скачать из чата; $TRASH_NOTE",
                files, claimed, selected = false,
            )?.let { groups += it }
        }
        simpleGroup(
            "screenshots", GroupKind.OLD_SCREENSHOTS, RiskLevel.PERSONAL,
            "Скриншоты старше ${screenshotRule.maxAgeDays} дней",
            "всё, что не отметите",
            "старые скриншоты редко нужны; $TRASH_NOTE",
            screenshotRule.find(entries), claimed, selected = false,
        )?.let { groups += it }
        largeFileRule.find(entries).forEach { (kind, files) ->
            simpleGroup(
                "large:${kind.name.lowercase()}", GroupKind.LARGE_FILES, RiskLevel.PERSONAL,
                "${kind.title} (от ${formatBytes(largeFileRule.minSizeBytes)})",
                "всё, что не отметите",
                "крупные личные файлы — решайте поштучно; $TRASH_NOTE",
                files, claimed, selected = false,
            )?.let { groups += it }
        }

        return Plan(
            groups = groups.sortedWith(compareBy<Group> { it.level.number }.thenByDescending { it.totalBytes }),
            scannedFiles = entries.size,
            scannedBytes = entries.sumOf { it.size },
        )
    }

    private fun appCacheGroup() = Group(
        id = "app-cache",
        kind = GroupKind.APP_CACHE,
        level = RiskLevel.SAFE,
        title = "Кэш приложений",
        willRemove = "временные данные всех приложений — очищает сама система",
        willKeep = "приложения, их настройки и ваши данные",
        why = "кэш пересоздаётся автоматически; точный объём станет известен после очистки",
        items = emptyList(),
        selectedByDefault = true,
    )

    private class DupRemoval(val item: PlannedItem, val dir: String, val fromWorkSet: Boolean)

    private fun duplicateGroups(sets: List<DuplicateSet>, claimed: MutableSet<String>): List<Group> {
        val removals = sets.flatMap { set ->
            val keeper = keepPolicy.chooseKeeper(set.files.map { it.path })
            val workSet = set.files.all { layout.isWorkFolder(it.path) }
            set.files.filter { it.path != keeper }.map { f ->
                DupRemoval(
                    PlannedItem(f.path, f.size, f.modifiedAt, keptOriginalPath = keeper, sha256 = set.sha256),
                    layout.parentRelative(f.path),
                    workSet,
                )
            }
        }
        claimed += removals.map { it.item.path }
        return removals.groupBy { it.dir }.map { (dir, rs) ->
            val n = rs.size
            val keptDirs = rs.map { layout.parentRelative(it.item.keptOriginalPath!!).ifEmpty { ROOT_NAME } }.distinct()
            val keptText = keptDirs.take(2).joinToString(", ") + if (keptDirs.size > 2) " и др." else ""
            val anyWork = rs.any { it.fromWorkSet }
            Group(
                id = "dup:$dir",
                kind = GroupKind.DUPLICATES,
                level = RiskLevel.EXACT,
                title = "Дубли в папке ${dir.ifEmpty { ROOT_NAME }}",
                willRemove = "$n ${plural(n, "копия", "копии", "копий")} из этой папки",
                willKeep = "оригиналы в: $keptText",
                why = "содержимое совпадает побайтово (SHA-256); одна копия каждого файла всегда остаётся" +
                    if (anyWork) ". Папка похожа на рабочую — по умолчанию не отмечено" else "",
                items = rs.map { it.item }.sortedByDescending { it.size },
                selectedByDefault = !anyWork,
            )
        }
    }

    private fun simpleGroup(
        id: String,
        kind: GroupKind,
        level: RiskLevel,
        title: String,
        willKeep: String,
        why: String,
        files: List<FileEntry>,
        claimed: MutableSet<String>,
        selected: Boolean,
    ): Group? {
        val items = files.filter { it.path !in claimed }
            .sortedByDescending { it.size }
            .map { PlannedItem(it.path, it.size, it.modifiedAt) }
        if (items.isEmpty()) return null
        claimed += items.map { it.path }
        val n = items.size
        val count = "$n ${plural(n, "файл", "файла", "файлов")}"
        val willRemove = if (level == RiskLevel.PERSONAL) "$count — отмечаете поштучно" else count
        return Group(id, kind, level, title, willRemove, willKeep, why, items, selected)
    }
}
