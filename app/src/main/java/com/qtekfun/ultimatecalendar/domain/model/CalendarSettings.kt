// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.model

/**
 * The local overrides of one calendar (RF-02). Null fields keep the source's own value; the
 * source is never written to.
 */
data class CalendarSettings(
    val displayName: String? = null,
    val color: Int? = null,
    val visible: Boolean? = null
) {
    /** Nothing is overridden, so there is nothing to store. */
    val isEmpty: Boolean get() = displayName == null && color == null && visible == null

    /** [calendar] as the user sees it: the source's data with these overrides on top. */
    fun applyTo(calendar: CalendarInfo): CalendarInfo = calendar.copy(
        displayName = displayName ?: calendar.displayName,
        color = color ?: calendar.color,
        visible = visible ?: calendar.visible
    )

    /**
     * The overrides after the user edited the look of a calendar in the drawer (RF-02). A name
     * or color left as it was shown keeps what was stored (the source's own when nothing was);
     * a blank name or a null [pickedColor] goes back to the source's.
     */
    fun withLook(
        shownName: String,
        typedName: String,
        shownColor: Int,
        pickedColor: Int?
    ): CalendarSettings = copy(
        displayName = when {
            typedName.isBlank() -> null
            typedName.trim() == shownName -> displayName
            else -> typedName.trim()
        },
        color = when (pickedColor) {
            null -> null
            shownColor -> color
            else -> pickedColor
        }
    )
}
