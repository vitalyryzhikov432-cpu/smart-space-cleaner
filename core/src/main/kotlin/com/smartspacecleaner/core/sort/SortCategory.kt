package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.scan.StorageLayout

/** Категории раскладки. Название — это же имя папки внутри `Pictures/Сортировка`. */
enum class SortCategory(val title: String) {
    DOCUMENTS("Документы"),
    SCREENSHOTS("Скриншоты"),
    PEOPLE("Люди"),
    FOOD("Еда"),
    NATURE("Природа"),
    ANIMALS("Животные"),
    MESSENGERS("Мессенджеры"),
    OTHER("Разное"),
    VIDEO("Видео"),
    ;

    val relativeFolder: String get() = "${StorageLayout.SORT_DIR}/$title"

    fun folder(layout: StorageLayout): String = "${layout.sortRoot}/$title"
}
