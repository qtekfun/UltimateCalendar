// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

/**
 * Writes iCalendar text. Nodes that came from the server are written exactly as they were read;
 * new or changed ones are written in canonical form: CRLF line breaks and lines folded at 75
 * octets without splitting a character (RFC 5545 §3.1).
 */
object IcsWriter {
    private const val MAX_OCTETS = 75
    private const val CRLF = "\r\n"

    fun write(components: List<IcsComponent>): String = buildString {
        components.forEach { appendNode(it) }
    }

    fun write(component: IcsComponent): String = write(listOf(component))

    private fun StringBuilder.appendNode(node: IcsNode) {
        when (node) {
            is IcsComponent -> {
                append(node.beginRaw ?: fold("BEGIN:${node.name}"))
                node.children.forEach { appendNode(it) }
                append(node.endRaw ?: fold("END:${node.name}"))
            }

            is IcsProperty -> append(node.raw ?: fold(contentLine(node)))
        }
    }

    internal fun contentLine(property: IcsProperty): String = buildString {
        append(property.name)
        property.parameters.forEach { parameter ->
            append(';').append(parameter.name).append('=')
            append(parameter.values.joinToString(",") { quoteIfNeeded(it) })
        }
        append(':').append(property.value)
    }

    private fun quoteIfNeeded(value: String) =
        if (value.any { it == ':' || it == ';' || it == ',' }) "\"$value\"" else value

    /** Splits [line] into lines of at most 75 octets, each continuation starting with a space. */
    internal fun fold(line: String): String = buildString {
        var octets = 0
        var index = 0
        while (index < line.length) {
            val codePoint = line.codePointAt(index)
            val chars = Character.charCount(codePoint)
            val size = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (octets + size > MAX_OCTETS) {
                append(CRLF).append(' ')
                octets = 1
            }
            appendCodePoint(codePoint)
            octets += size
            index += chars
        }
        append(CRLF)
    }
}
