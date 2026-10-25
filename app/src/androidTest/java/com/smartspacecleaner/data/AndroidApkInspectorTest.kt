package com.smartspacecleaner.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartspacecleaner.core.port.ApkStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidApkInspectorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val inspector = AndroidApkInspector(context.packageManager)

    @Test
    fun ownInstalledApkIsRecognised() {
        assertEquals(ApkStatus.INSTALLED_SAME_OR_NEWER, inspector.inspect(context.applicationInfo.sourceDir))
    }

    @Test
    fun garbageFileIsUnreadable() {
        val fake = File(context.cacheDir, "fake.apk").apply { writeText("not an apk") }
        try {
            assertEquals(ApkStatus.UNREADABLE, inspector.inspect(fake.path))
        } finally {
            fake.delete()
        }
    }
}
