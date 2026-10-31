package com.smartspacecleaner.core.model

import java.time.Instant

enum class ActionKind { TRASH, DELETE_DUPLICATE, DELETE_APK, SORT_MOVE }

enum class ActionStatus { PENDING, DONE, FAILED, RESTORED, PURGED }

data class ActionRecord(
    val id: Long = 0,
    val sessionId: String,
    val groupId: String,
    val groupTitle: String,
    val kind: ActionKind,
    val originalPath: String,
    val size: Long,
    val trashPath: String? = null,
    val keptOriginalPath: String? = null,
    val sha256: String? = null,
    val status: ActionStatus,
    val reason: String? = null,
    val createdAt: Instant,
    val expiresAt: Instant? = null,
    /** Куда перенесён файл при сортировке по папкам. */
    val movedToPath: String? = null,
)
