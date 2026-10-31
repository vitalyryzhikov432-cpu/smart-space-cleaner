package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.port.FileSystem

/** Свободный путь рядом с [path]: «x.jpg» → «x (метка).jpg»; [label] получает номер попытки, начиная с 1. Ничего не перезаписываем. */
fun freeTarget(fs: FileSystem, path: String, label: (Int) -> String): String {
    if (!fs.exists(path)) return path
    val dir = path.substringBeforeLast('/')
    val name = path.substringAfterLast('/')
    val base = name.substringBeforeLast('.')
    val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
    var i = 1
    while (true) {
        val candidate = "$dir/$base (${label(i)})$ext"
        if (!fs.exists(candidate)) return candidate
        i++
    }
}
