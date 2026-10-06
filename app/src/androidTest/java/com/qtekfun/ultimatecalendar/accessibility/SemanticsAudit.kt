// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.accessibility

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density

/**
 * A small accessibility audit over the merged semantics tree, with no library beyond Compose's own
 * test API (Google's accessibility-test-framework would add 28 artifacts to the verified
 * dependencies). It looks for what TalkBack and switch users trip on:
 *
 * - an actionable node that says nothing (no text, no description, no state);
 * - an actionable node smaller than [MIN_DP] when [allowSmall] is false;
 * - a node that announces the same words twice;
 */
internal object SemanticsAudit {
    const val MIN_DP = 48f

    fun violations(root: SemanticsNode, density: Density, allowSmall: Boolean): List<String> =
        buildList { visit(root, density, allowSmall, this) }

    private fun visit(
        node: SemanticsNode,
        density: Density,
        allowSmall: Boolean,
        out: MutableList<String>
    ) {
        val config = node.config
        if (config.getOrNull(SemanticsProperties.HideFromAccessibility) == null) {
            val actionable = config.contains(SemanticsActions.OnClick) ||
                config.contains(SemanticsProperties.ToggleableState)
            val words = words(node)
            val label = describe(node, words)
            if (actionable && words.none { it.isNotBlank() }) {
                out += "silent actionable node $label"
            }
            if (actionable && !allowSmall) {
                val width = node.size.width / density.density
                val height = node.size.height / density.density
                if (width < MIN_DP || height < MIN_DP) {
                    out += "small actionable node $label: $width x $height dp"
                }
            }
            val spoken = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            if (spoken.size != spoken.toSet().size) {
                out += "node says the same twice $label: $spoken"
            }
        }
        node.children.forEach { visit(it, density, allowSmall, out) }
    }

    private fun words(node: SemanticsNode): List<String> {
        val config = node.config
        return config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            listOfNotNull(config.getOrNull(SemanticsProperties.StateDescription))
    }

    private fun describe(node: SemanticsNode, words: List<String>) =
        "#${node.id} \"${words.joinToString(", ")}\" at ${node.boundsInRoot}"
}
