package com.smartspacecleaner.data

import android.content.pm.PackageManager
import com.smartspacecleaner.core.port.ApkInspector
import com.smartspacecleaner.core.port.ApkStatus

class AndroidApkInspector(private val pm: PackageManager) : ApkInspector {
    override fun inspect(path: String): ApkStatus {
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(path, 0) ?: return ApkStatus.UNREADABLE
        val installed = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(archive.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return ApkStatus.NOT_INSTALLED_OR_OLDER
        }
        return if (installed.longVersionCode >= archive.longVersionCode) {
            ApkStatus.INSTALLED_SAME_OR_NEWER
        } else {
            ApkStatus.NOT_INSTALLED_OR_OLDER
        }
    }
}
