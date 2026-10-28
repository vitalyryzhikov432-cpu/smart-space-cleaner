package com.smartspacecleaner.data

import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.port.FileSystem
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.time.Instant

class AndroidFileSystem(private val storageRoot: File) : FileSystem {
    override fun walk(root: String, excludedPrefixes: Set<String>): Sequence<FileEntry> =
        File(root).walkTopDown()
            .onEnter { dir -> !isExcluded(dir.path, excludedPrefixes) && !Files.isSymbolicLink(dir.toPath()) }
            .filter { it.isFile && !isExcluded(it.path, excludedPrefixes) }
            .map { FileEntry(it.path, it.length(), Instant.ofEpochMilli(it.lastModified())) }

    private fun isExcluded(path: String, prefixes: Set<String>) =
        prefixes.any { path == it || path.startsWith("$it/") }

    override fun exists(path: String) = File(path).exists()

    override fun openRead(path: String): InputStream = FileInputStream(path)

    override fun move(from: String, to: String): Boolean {
        val target = File(to)
        if (target.exists()) return false
        target.parentFile?.mkdirs()
        return File(from).renameTo(target)
    }

    override fun copy(from: String, to: String): Boolean {
        val target = File(to)
        if (target.exists()) return false
        return try {
            File(from).copyTo(target)
            true
        } catch (e: IOException) {
            target.delete()
            false
        }
    }

    override fun delete(path: String) = File(path).delete()

    override fun mkdirs(path: String): Boolean {
        val dir = File(path)
        return dir.isDirectory || dir.mkdirs()
    }

    override fun createEmptyFile(path: String): Boolean {
        val file = File(path)
        return try { file.exists() || file.createNewFile() } catch (e: IOException) { false }
    }

    override fun freeBytes(): Long = storageRoot.usableSpace
}
