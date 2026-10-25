package com.smartspacecleaner.core.port

import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.model.FileEntry
import java.io.InputStream
import java.time.Instant

interface FileSystem {
    /** Все файлы под [root]; пути из [excludedPrefixes] и всё под ними пропускаются. */
    fun walk(root: String, excludedPrefixes: Set<String>): Sequence<FileEntry>
    fun exists(path: String): Boolean
    fun openRead(path: String): InputStream
    /** Переименование; создаёт родительские папки; false, если цель уже существует или не получилось. */
    fun move(from: String, to: String): Boolean
    /** Копирование; false, если цель уже существует или не получилось. */
    fun copy(from: String, to: String): Boolean
    fun delete(path: String): Boolean
    fun mkdirs(path: String): Boolean
    fun createEmptyFile(path: String): Boolean
    fun freeBytes(): Long
}

interface Clock {
    fun now(): Instant
}

object WallClock : Clock {
    override fun now(): Instant = Instant.now()
}

interface ActionLogStore {
    /** Сохраняет запись и возвращает присвоенный id. */
    fun insert(record: ActionRecord): Long
    fun update(record: ActionRecord)
    fun get(id: Long): ActionRecord?
    fun byStatus(status: ActionStatus): List<ActionRecord>
    fun all(): List<ActionRecord>
    /** Стирает записи из журнала; сами файлы не трогает. */
    fun delete(ids: Collection<Long>)
}

enum class ApkStatus { INSTALLED_SAME_OR_NEWER, NOT_INSTALLED_OR_OLDER, UNREADABLE }

interface ApkInspector {
    fun inspect(path: String): ApkStatus
}
