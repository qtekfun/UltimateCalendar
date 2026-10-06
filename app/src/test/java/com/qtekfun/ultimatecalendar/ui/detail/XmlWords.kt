// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.detail

import com.qtekfun.ultimatecalendar.R
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * The app's real strings, read from `res/values` and `res/values-es` so a unit test can check the
 * words in each language without Android. Plurals follow CLDR for English and Spanish.
 */
class XmlWords private constructor(private val language: String) : WordSource {
    private val strings = HashMap<String, String>()
    private val plurals = HashMap<String, Map<String, String>>()

    private val locale = Locale.forLanguageTag(language)

    init {
        val folder = if (language == DEFAULT) "values" else "values-$language"
        File("src/main/res/$folder").listFiles { file -> file.extension == "xml" }.orEmpty()
            .forEach(::read)
    }

    private fun read(file: File) {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            .documentElement
        for (index in 0 until root.childNodes.length) {
            val node = root.childNodes.item(index) as? Element ?: continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> strings[name] = node.textContent
                "plurals" -> plurals[name] = items(node)
            }
        }
    }

    private fun items(plural: Element): Map<String, String> =
        (0 until plural.childNodes.length).mapNotNull { plural.childNodes.item(it) as? Element }
            .associate { it.getAttribute("quantity") to it.textContent }

    val stringNames: Set<String> get() = strings.keys

    val pluralNames: Set<String> get() = plurals.keys

    fun raw(name: String): String = strings.getValue(name)

    fun rawPlural(name: String): Map<String, String> = plurals.getValue(name)

    override fun text(id: Int, vararg args: Any): String =
        String.format(locale, strings.getValue(STRING_NAMES.getValue(id)), *args)

    override fun plural(id: Int, quantity: Int, vararg args: Any): String {
        val name = PLURAL_NAMES.getValue(id)
        val items = plurals.getValue(name)
        return String.format(locale, items.getValue(category(quantity)), *args)
    }

    private fun category(quantity: Int): String = when {
        quantity == 1 -> "one"
        language == "es" && quantity != 0 && quantity % MILLION == 0 -> "many"
        else -> "other"
    }

    companion object {
        const val DEFAULT = "en"
        private const val MILLION = 1_000_000

        private val STRING_NAMES: Map<Int, String> =
            R.string::class.java.fields.associate { it.getInt(null) to it.name }
        private val PLURAL_NAMES: Map<Int, String> =
            R.plurals::class.java.fields.associate { it.getInt(null) to it.name }

        fun of(language: String) = XmlWords(language)
    }
}
