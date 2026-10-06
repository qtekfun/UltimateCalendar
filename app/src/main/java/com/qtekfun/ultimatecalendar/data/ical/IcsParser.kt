// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.ical

/**
 * Reads iCalendar text (RFC 5545) without losing anything: every content line keeps its exact
 * original text, line breaks (CRLF or LF) and folding included. It is lenient with what servers
 * and clients really send: lines it cannot understand are kept as they are, a missing `END` is
 * closed at the end of the file and a stray `END` is kept as an ordinary line.
 */
object IcsParser {
    /** The top-level components of [text], normally a single `VCALENDAR`. */
    fun parse(text: String): List<IcsComponent> {
        val root = Builder(name = "")
        val stack = ArrayDeque<Builder>().apply { addLast(root) }
        for (line in logicalLines(text)) {
            val property = parseLine(line.unfolded, line.raw)
            val current = stack.last()
            when {
                property.name.equals("BEGIN", ignoreCase = true) -> {
                    stack.addLast(Builder(property.value, beginRaw = line.raw))
                }

                property.name.equals("END", ignoreCase = true) && stack.size > 1 &&
                    property.value.equals(current.name, ignoreCase = true) -> {
                    stack.removeLast()
                    stack.last().children.add(current.build(endRaw = line.raw))
                }

                else -> current.children.add(property)
            }
        }
        while (stack.size > 1) {
            val open = stack.removeLast()
            stack.last().children.add(open.build(endRaw = null))
        }
        return root.children.filterIsInstance<IcsComponent>()
    }

    /** Parses one unfolded content line; [raw] is kept as its original text. */
    internal fun parseLine(line: String, raw: String? = null): IcsProperty {
        val cursor = Cursor(line)
        val name = cursor.readUntil(';', ':')
        val parameters = mutableListOf<IcsParameter>()
        while (cursor.peek() == ';') {
            cursor.skip()
            val parameterName = cursor.readUntil('=', ';', ':')
            val values = mutableListOf<String>()
            if (cursor.peek() == '=') {
                do {
                    cursor.skip()
                    values.add(cursor.readParameterValue())
                } while (cursor.peek() == ',')
            }
            parameters.add(IcsParameter(parameterName, values))
        }
        if (cursor.peek() != ':') {
            // Not a valid content line: keep it whole, so it is written back untouched.
            return IcsProperty(name = "", value = line, raw = raw)
        }
        cursor.skip()
        return IcsProperty(name, parameters, cursor.rest(), raw)
    }

    /** Joins folded lines (a line break followed by a space or tab), keeping the raw text. */
    private fun logicalLines(text: String): List<LogicalLine> {
        val physical = Regex("[^\\r\\n]*(?:\\r\\n|\\n|\\r)|[^\\r\\n]+$").findAll(text).map {
            it.value
        }
        val lines = mutableListOf<LogicalLine>()
        for (part in physical) {
            val content = part.trimEnd('\r', '\n')
            val folded = content.isNotEmpty() && (content[0] == ' ' || content[0] == '\t')
            if (folded && lines.isNotEmpty()) {
                val last = lines.removeAt(lines.lastIndex)
                lines.add(LogicalLine(last.unfolded + content.substring(1), last.raw + part))
            } else if (content.isNotEmpty() || part.isNotEmpty()) {
                lines.add(LogicalLine(content, part))
            }
        }
        return lines
    }

    private data class LogicalLine(val unfolded: String, val raw: String)

    private class Builder(val name: String, val beginRaw: String? = null) {
        val children = mutableListOf<IcsNode>()

        fun build(endRaw: String?) = IcsComponent(name, children.toList(), beginRaw, endRaw)
    }

    private class Cursor(private val text: String) {
        private var position = 0

        fun peek(): Char? = text.getOrNull(position)

        fun skip() {
            position++
        }

        fun rest(): String = text.substring(position).also { position = text.length }

        fun readUntil(vararg stops: Char): String {
            val start = position
            while (position < text.length && text[position] !in stops) position++
            return text.substring(start, position)
        }

        /** A parameter value, quoted (may contain `;`, `:` and `,`) or not. */
        fun readParameterValue(): String {
            if (peek() != '"') return readUntil(',', ';', ':')
            skip()
            val value = readUntil('"')
            if (peek() == '"') skip()
            return value
        }
    }
}
