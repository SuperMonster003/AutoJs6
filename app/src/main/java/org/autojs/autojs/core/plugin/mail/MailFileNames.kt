package org.autojs.autojs.core.plugin.mail

import java.io.File

/**
 * Attachment file name hygiene (roadmap P1.2): the name a server sends is untrusted, so path
 * separators, control characters and leading dots are removed, over-long names are shortened
 * while keeping their extension, and clashes get a numeric suffix unless the caller overwrites.
 */
object MailFileNames {

    const val DEFAULT_NAME = "attachment"
    const val MAX_LENGTH = 120
    const val MAX_EXTENSION_LENGTH = 16
    const val MAX_UNIQUE_ATTEMPTS = 10_000

    fun sanitize(name: String?, maxLength: Int = MAX_LENGTH): String {
        require(maxLength >= 8) { "maxLength must allow a base name and an extension" }
        val cleaned = buildString {
            name.orEmpty().forEach { char ->
                append(if (char in FORBIDDEN || char < ' ' || char == '') '_' else char)
            }
        }.trim().trimStart('.', ' ', '_').trimEnd('.', ' ')
        if (cleaned.isEmpty()) return DEFAULT_NAME
        if (cleaned.length <= maxLength) return cleaned
        val dot = cleaned.lastIndexOf('.')
        val extension = if (dot > 0 && cleaned.length - dot - 1 in 1..MAX_EXTENSION_LENGTH) cleaned.substring(dot) else ""
        val base = if (extension.isEmpty()) cleaned else cleaned.substring(0, dot)
        val keep = (maxLength - extension.length).coerceAtLeast(1)
        return base.take(keep).trimEnd('.', ' ').ifEmpty { DEFAULT_NAME } + extension
    }

    /** Returns [directory]/[name] or, when it exists, the first free `name (n).ext` variant. */
    fun unique(directory: File, name: String): File {
        val candidate = File(directory, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val (base, extension) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        for (index in 1..MAX_UNIQUE_ATTEMPTS) {
            val next = File(directory, "$base ($index)$extension")
            if (!next.exists()) return next
        }
        throw IllegalStateException("no free file name for $name in $directory")
    }

    private val FORBIDDEN = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
}
