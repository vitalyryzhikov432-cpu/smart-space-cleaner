package com.smartspacecleaner.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomActionLogStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var store: RoomActionLogStore

    private val sample = ActionRecord(
        sessionId = "s1", groupId = "dup:redmi6", groupTitle = "Дубли в папке redmi6", kind = ActionKind.TRASH,
        originalPath = "/storage/emulated/0/redmi6/a.jpg", size = 1234, trashPath = "/storage/emulated/0/.SmartSpaceCleaner/trash/t1",
        keptOriginalPath = "/storage/emulated/0/DCIM/Camera/a.jpg", sha256 = "ab", status = ActionStatus.PENDING,
        createdAt = Instant.ofEpochMilli(1_700_000_000_123), expiresAt = Instant.ofEpochMilli(1_700_604_800_123),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomActionLogStore(db.actionRecords())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertAssignsIdAndRoundTripsAllFields() {
        val id = store.insert(sample)
        assertEquals(sample.copy(id = id), store.get(id))
    }

    @Test
    fun nullableFieldsRoundTrip() {
        val id = store.insert(sample.copy(trashPath = null, keptOriginalPath = null, sha256 = null, expiresAt = null, kind = ActionKind.DELETE_APK))
        val loaded = store.get(id)!!
        assertNull(loaded.trashPath)
        assertNull(loaded.expiresAt)
        assertEquals(ActionKind.DELETE_APK, loaded.kind)
    }

    @Test
    fun updateAndQueryByStatus() {
        val a = store.insert(sample)
        val b = store.insert(sample.copy(originalPath = "/b"))
        store.update(store.get(a)!!.copy(status = ActionStatus.DONE, reason = "ok"))
        assertEquals(listOf(a), store.byStatus(ActionStatus.DONE).map { it.id })
        assertEquals(listOf(b), store.byStatus(ActionStatus.PENDING).map { it.id })
        assertEquals(listOf(a, b), store.all().map { it.id })
        assertEquals("ok", store.get(a)!!.reason)
    }

    @Test
    fun missingRecordIsNull() {
        assertNull(store.get(42))
    }
}
