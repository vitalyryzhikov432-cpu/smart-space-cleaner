package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.port.FileSystem
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

private const val CHUNK = 64 * 1024

class Hasher(private val fs: FileSystem) {
    /** SHA-256 первых и последних 64 КБ (или всего файла, если он не больше 128 КБ). */
    fun partial(entry: FileEntry): String {
        val md = MessageDigest.getInstance("SHA-256")
        fs.openRead(entry.path).use { input ->
            if (entry.size <= 2L * CHUNK) {
                md.update(input.readBytes())
            } else {
                md.update(input.readExactly(CHUNK))
                input.skipExactly(entry.size - 2L * CHUNK)
                md.update(input.readExactly(CHUNK))
            }
        }
        return md.digest().toHex()
    }

    fun full(path: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        fs.openRead(path).use { input ->
            val buf = ByteArray(CHUNK)
            while (true) {
                val read = input.read(buf)
                if (read < 0) break
                md.update(buf, 0, read)
            }
        }
        return md.digest().toHex()
    }
}

// InputStream.readNBytes/skipNBytes появились в Android только в API 33, поэтому свои версии.
private fun InputStream.readExactly(n: Int): ByteArray {
    val buf = ByteArray(n)
    var offset = 0
    while (offset < n) {
        val read = read(buf, offset, n - offset)
        if (read < 0) throw IOException("unexpected end of file")
        offset += read
    }
    return buf
}

private fun InputStream.skipExactly(n: Long) {
    var left = n
    while (left > 0) {
        val skipped = skip(left)
        if (skipped > 0) {
            left -= skipped
        } else {
            if (read() < 0) throw IOException("unexpected end of file")
            left--
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
