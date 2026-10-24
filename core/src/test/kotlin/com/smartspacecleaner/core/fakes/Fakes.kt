package com.smartspacecleaner.core.fakes

import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.ApkInspector
import com.smartspacecleaner.core.port.ApkStatus
import com.smartspacecleaner.core.port.Clock
import com.smartspacecleaner.core.port.FileSystem
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant

const val ROOT = "/storage/emulated/0"
fun p(relative: String) = "$ROOT/$relative"
val T0: Instant = Instant.parse("2026-10-01T00:00:00Z")

/** Детерминированное содержимое: одинаковые (size, seed) дают одинаковые байты. */
fun bytes(size: Int, seed: Int): ByteArray = ByteArray(size) { ((it * 31 + seed) % 251).toByte() }

class FakeFileSystem(var free: Long = 10L * 1024 * 1024 * 1024) : FileSystem {
    private class FakeFile(val content: ByteArray, val modifiedAt: Instant)

    private val files = linkedMapOf<String, FakeFile>()
    /** move/copy/delete с этими путями (как источником) возвращают false. */
    val failingPaths = mutableSetOf<String>()
    /** openRead для этих путей бросает IOException. */
    val unreadablePaths = mutableSetOf<String>()

    fun add(path: String, content: ByteArray, modifiedAt: Instant = T0) {
        files[path] = FakeFile(content, modifiedAt)
    }

    fun write(path: String, content: ByteArray) {
        files[path] = FakeFile(content, files[path]?.modifiedAt ?: T0)
    }

    fun content(path: String): ByteArray = files.getValue(path).content

    override fun walk(root: String, excludedPrefixes: Set<String>): Sequence<FileEntry> =
        files.entries
            .filter { (path, _) -> path.startsWith("$root/") && excludedPrefixes.none { path == it || path.startsWith("$it/") } }
            .map { (path, f) -> FileEntry(path, f.content.size.toLong(), f.modifiedAt) }
            .asSequence()

    override fun exists(path: String) = path in files

    override fun openRead(path: String): InputStream {
        if (path in unreadablePaths) throw IOException("permission denied: $path")
        val f = files[path] ?: throw IOException("no such file: $path")
        return ByteArrayInputStream(f.content)
    }

    override fun move(from: String, to: String): Boolean {
        if (from in failingPaths || from !in files || to in files) return false
        files[to] = files.remove(from)!!
        return true
    }

    override fun copy(from: String, to: String): Boolean {
        if (from in failingPaths || from !in files || to in files) return false
        val f = files.getValue(from)
        files[to] = FakeFile(f.content.copyOf(), f.modifiedAt)
        return true
    }

    override fun delete(path: String): Boolean {
        if (path in failingPaths) return false
        return files.remove(path) != null
    }

    override fun mkdirs(path: String) = true

    override fun createEmptyFile(path: String): Boolean {
        if (path !in files) add(path, ByteArray(0))
        return true
    }

    override fun freeBytes() = free
}

class FakeClock(var current: Instant = T0) : Clock {
    override fun now(): Instant = current
}

class InMemoryActionLogStore : ActionLogStore {
    private val records = linkedMapOf<Long, ActionRecord>()
    private var nextId = 1L

    override fun insert(record: ActionRecord): Long {
        val id = nextId++
        records[id] = record.copy(id = id)
        return id
    }

    override fun update(record: ActionRecord) {
        require(record.id in records) { "unknown record ${record.id}" }
        records[record.id] = record
    }

    override fun get(id: Long) = records[id]
    override fun byStatus(status: ActionStatus) = records.values.filter { it.status == status }
    override fun all() = records.values.toList()
}

class FakeApkInspector(private val statuses: Map<String, ApkStatus> = emptyMap()) : ApkInspector {
    override fun inspect(path: String) = statuses[path] ?: ApkStatus.UNREADABLE
}
