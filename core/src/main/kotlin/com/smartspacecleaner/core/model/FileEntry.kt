package com.smartspacecleaner.core.model

import java.time.Instant

data class FileEntry(val path: String, val size: Long, val modifiedAt: Instant) {
    val name: String get() = path.substringAfterLast('/')
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}
