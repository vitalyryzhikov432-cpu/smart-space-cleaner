package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.model.FileEntry
import java.io.IOException

data class DuplicateSet(val sha256: String, val size: Long, val files: List<FileEntry>)

class DuplicateFinder(private val hasher: Hasher, private val minSize: Long = 1024) {
    fun find(entries: List<FileEntry>): List<DuplicateSet> {
        val result = mutableListOf<DuplicateSet>()
        entries.filter { it.size >= minSize }
            .groupBy { it.size }.values
            .filter { it.size > 1 }
            .forEach { sameSize ->
                sameSize.groupBy { orNull { hasher.partial(it) } }
                    .filterKeys { it != null }.values
                    .filter { it.size > 1 }
                    .forEach { samePartial ->
                        samePartial.groupBy { orNull { hasher.full(it.path) } }
                            .forEach { (hash, files) ->
                                if (hash != null && files.size > 1) {
                                    result += DuplicateSet(hash, files.first().size, files.sortedBy { it.path })
                                }
                            }
                    }
            }
        return result.sortedByDescending { it.size * (it.files.size - 1) }
    }

    private fun orNull(block: () -> String): String? =
        try { block() } catch (e: IOException) { null } catch (e: SecurityException) { null }
}
