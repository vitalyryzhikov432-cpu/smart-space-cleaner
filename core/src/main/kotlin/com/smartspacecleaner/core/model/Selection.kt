package com.smartspacecleaner.core.model

enum class GroupCheck { ALL, SOME, NONE }

fun Group.checkState(selected: Set<String>): GroupCheck {
    val checked = items.count { it.path in selected }
    return when {
        checked == 0 -> GroupCheck.NONE
        checked == items.size -> GroupCheck.ALL
        else -> GroupCheck.SOME
    }
}

fun Set<String>.toggleGroup(group: Group): Set<String> {
    val paths = group.items.map { it.path }.toSet()
    return if (group.checkState(this) == GroupCheck.ALL) this - paths else this + paths
}

fun Set<String>.toggleItem(path: String): Set<String> = if (path in this) this - path else this + path
