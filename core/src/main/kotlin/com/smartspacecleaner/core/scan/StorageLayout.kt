package com.smartspacecleaner.core.scan

enum class Messenger(val title: String, val dirs: List<String>) {
    TELEGRAM("Telegram", listOf("Android/media/org.telegram.messenger", "Telegram")),
    WHATSAPP("WhatsApp", listOf("Android/media/com.whatsapp")),
}

class StorageLayout(val root: String) {
    val trashRoot: String get() = "$root/.SmartSpaceCleaner/trash"
    val excludedPrefixes: Set<String> get() = setOf("$root/Android/data", "$root/Android/obb", "$root/.SmartSpaceCleaner")

    fun relative(path: String): String = path.removePrefix("$root/")

    fun isUnder(path: String, dir: String): Boolean {
        val rel = relative(path)
        return rel.equals(dir, ignoreCase = true) || rel.startsWith("$dir/", ignoreCase = true)
    }

    fun isPrimaryMedia(path: String) = isUnder(path, "DCIM/Camera") || isUnder(path, "Pictures")
    fun isDownload(path: String) = isUnder(path, "Download")
    fun messengerOf(path: String): Messenger? = Messenger.entries.firstOrNull { m -> m.dirs.any { isUnder(path, it) } }

    /** Папка, где копии могут лежать намеренно: всё, кроме камеры/картинок, загрузок и мессенджеров. */
    fun isWorkFolder(path: String) =
        !isUnder(path, "DCIM") && !isUnder(path, "Pictures") && !isDownload(path) && messengerOf(path) == null

    fun parentRelative(path: String): String = relative(path).substringBeforeLast('/', "")
}
