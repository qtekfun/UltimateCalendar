// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.domain.timegrid

/** Where the zoom of the hour grid is kept between launches ([GridZoom]). */
interface GridZoomStore {
    /** The zoom last saved, within [GridZoom.MIN]..[GridZoom.MAX]. */
    fun gridZoom(): Float

    fun saveGridZoom(zoom: Float)
}
