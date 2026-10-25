package com.smartspacecleaner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import java.time.Instant

@Entity(tableName = "action_records")
data class ActionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val sessionId: String,
    val groupId: String,
    val groupTitle: String,
    val kind: String,
    val originalPath: String,
    val size: Long,
    val trashPath: String?,
    val keptOriginalPath: String?,
    val sha256: String?,
    val status: String,
    val reason: String?,
    val createdAtMillis: Long,
    val expiresAtMillis: Long?,
) {
    fun toDomain() = ActionRecord(
        id = id,
        sessionId = sessionId,
        groupId = groupId,
        groupTitle = groupTitle,
        kind = ActionKind.valueOf(kind),
        originalPath = originalPath,
        size = size,
        trashPath = trashPath,
        keptOriginalPath = keptOriginalPath,
        sha256 = sha256,
        status = ActionStatus.valueOf(status),
        reason = reason,
        createdAt = Instant.ofEpochMilli(createdAtMillis),
        expiresAt = expiresAtMillis?.let { Instant.ofEpochMilli(it) },
    )

    companion object {
        fun from(r: ActionRecord) = ActionRecordEntity(
            id = r.id,
            sessionId = r.sessionId,
            groupId = r.groupId,
            groupTitle = r.groupTitle,
            kind = r.kind.name,
            originalPath = r.originalPath,
            size = r.size,
            trashPath = r.trashPath,
            keptOriginalPath = r.keptOriginalPath,
            sha256 = r.sha256,
            status = r.status.name,
            reason = r.reason,
            createdAtMillis = r.createdAt.toEpochMilli(),
            expiresAtMillis = r.expiresAt?.toEpochMilli(),
        )
    }
}
