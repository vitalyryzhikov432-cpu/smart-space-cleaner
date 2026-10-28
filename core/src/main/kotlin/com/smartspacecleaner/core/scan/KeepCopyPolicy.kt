package com.smartspacecleaner.core.scan

class KeepCopyPolicy(private val layout: StorageLayout) {
    fun chooseKeeper(paths: List<String>): String = paths.minWith(
        compareBy<String>({ folderRank(it) }, { if (hasCopySuffix(it)) 1 else 0 }, { it.length }, { it }),
    )

    /** Мессенджеры и скрытые корзины (.trashed-*, .trashBin) сами чистят свои файлы — там оригинал не оставляем. */
    private fun folderRank(path: String): Int = when {
        layout.isHidden(path) -> 4
        layout.messengerOf(path) != null -> 3
        layout.isDownload(path) -> 2
        layout.isPrimaryMedia(path) -> 0
        else -> 1
    }

    companion object {
        private val COPY_SUFFIX = Regex("""(\s?\(\d+\)|[\s_-]copy|\s-\sкопия)$""", RegexOption.IGNORE_CASE)

        fun hasCopySuffix(path: String): Boolean {
            val base = path.substringAfterLast('/').substringBeforeLast('.')
            return COPY_SUFFIX.containsMatchIn(base)
        }
    }
}
