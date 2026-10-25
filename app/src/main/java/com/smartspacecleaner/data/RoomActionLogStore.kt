package com.smartspacecleaner.data

import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.port.ActionLogStore

class RoomActionLogStore(private val dao: ActionRecordDao) : ActionLogStore {
    override fun insert(record: ActionRecord): Long = dao.insert(ActionRecordEntity.from(record.copy(id = 0)))
    override fun update(record: ActionRecord) = dao.update(ActionRecordEntity.from(record))
    override fun get(id: Long): ActionRecord? = dao.get(id)?.toDomain()
    override fun byStatus(status: ActionStatus): List<ActionRecord> = dao.byStatus(status.name).map { it.toDomain() }
    override fun all(): List<ActionRecord> = dao.all().map { it.toDomain() }
    override fun delete(ids: Collection<Long>) = ids.chunked(500).forEach { dao.delete(it) }
}
