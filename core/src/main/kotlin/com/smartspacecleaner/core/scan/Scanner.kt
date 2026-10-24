package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.port.FileSystem

class Scanner(
    private val fs: FileSystem,
    private val layout: StorageLayout,
    private val planner: Planner,
) {
    /** Только чтение: обходит память и строит план. */
    fun scan(onProgress: (filesSeen: Int) -> Unit = {}): Plan {
        val entries = ArrayList<FileEntry>()
        fs.walk(layout.root, layout.excludedPrefixes).forEach {
            entries += it
            if (entries.size % 500 == 0) onProgress(entries.size)
        }
        onProgress(entries.size)
        return planner.plan(entries)
    }
}
