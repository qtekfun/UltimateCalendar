// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.widget

import com.qtekfun.ultimatecalendar.R

/** The views of one day cell of the Month widget; every cell has ids of its own (RemoteViews). */
internal class MonthCellIds(
    val root: Int,
    val today: Int,
    val day: Int,
    val markers: List<Int>,
    val more: Int
)

/** The ids of `widget_month.xml`, generated with it: 6 weeks of 7 cells. */
internal object MonthWidgetIds {
    val weekRows =
        listOf(
            R.id.week_row_0,
            R.id.week_row_1,
            R.id.week_row_2,
            R.id.week_row_3,
            R.id.week_row_4,
            R.id.week_row_5
        )

    val weekdays =
        listOf(
            R.id.month_weekday_0,
            R.id.month_weekday_1,
            R.id.month_weekday_2,
            R.id.month_weekday_3,
            R.id.month_weekday_4,
            R.id.month_weekday_5,
            R.id.month_weekday_6
        )

    val cells: List<List<MonthCellIds>> = listOf(
        listOf(
            MonthCellIds(
                R.id.cell_0_0,
                R.id.cell_0_0_today,
                R.id.cell_0_0_day,
                listOf(R.id.cell_0_0_m0, R.id.cell_0_0_m1, R.id.cell_0_0_m2),
                R.id.cell_0_0_more
            ),
            MonthCellIds(
                R.id.cell_0_1,
                R.id.cell_0_1_today,
                R.id.cell_0_1_day,
                listOf(R.id.cell_0_1_m0, R.id.cell_0_1_m1, R.id.cell_0_1_m2),
                R.id.cell_0_1_more
            ),
            MonthCellIds(
                R.id.cell_0_2,
                R.id.cell_0_2_today,
                R.id.cell_0_2_day,
                listOf(R.id.cell_0_2_m0, R.id.cell_0_2_m1, R.id.cell_0_2_m2),
                R.id.cell_0_2_more
            ),
            MonthCellIds(
                R.id.cell_0_3,
                R.id.cell_0_3_today,
                R.id.cell_0_3_day,
                listOf(R.id.cell_0_3_m0, R.id.cell_0_3_m1, R.id.cell_0_3_m2),
                R.id.cell_0_3_more
            ),
            MonthCellIds(
                R.id.cell_0_4,
                R.id.cell_0_4_today,
                R.id.cell_0_4_day,
                listOf(R.id.cell_0_4_m0, R.id.cell_0_4_m1, R.id.cell_0_4_m2),
                R.id.cell_0_4_more
            ),
            MonthCellIds(
                R.id.cell_0_5,
                R.id.cell_0_5_today,
                R.id.cell_0_5_day,
                listOf(R.id.cell_0_5_m0, R.id.cell_0_5_m1, R.id.cell_0_5_m2),
                R.id.cell_0_5_more
            ),
            MonthCellIds(
                R.id.cell_0_6,
                R.id.cell_0_6_today,
                R.id.cell_0_6_day,
                listOf(R.id.cell_0_6_m0, R.id.cell_0_6_m1, R.id.cell_0_6_m2),
                R.id.cell_0_6_more
            )
        ),
        listOf(
            MonthCellIds(
                R.id.cell_1_0,
                R.id.cell_1_0_today,
                R.id.cell_1_0_day,
                listOf(R.id.cell_1_0_m0, R.id.cell_1_0_m1, R.id.cell_1_0_m2),
                R.id.cell_1_0_more
            ),
            MonthCellIds(
                R.id.cell_1_1,
                R.id.cell_1_1_today,
                R.id.cell_1_1_day,
                listOf(R.id.cell_1_1_m0, R.id.cell_1_1_m1, R.id.cell_1_1_m2),
                R.id.cell_1_1_more
            ),
            MonthCellIds(
                R.id.cell_1_2,
                R.id.cell_1_2_today,
                R.id.cell_1_2_day,
                listOf(R.id.cell_1_2_m0, R.id.cell_1_2_m1, R.id.cell_1_2_m2),
                R.id.cell_1_2_more
            ),
            MonthCellIds(
                R.id.cell_1_3,
                R.id.cell_1_3_today,
                R.id.cell_1_3_day,
                listOf(R.id.cell_1_3_m0, R.id.cell_1_3_m1, R.id.cell_1_3_m2),
                R.id.cell_1_3_more
            ),
            MonthCellIds(
                R.id.cell_1_4,
                R.id.cell_1_4_today,
                R.id.cell_1_4_day,
                listOf(R.id.cell_1_4_m0, R.id.cell_1_4_m1, R.id.cell_1_4_m2),
                R.id.cell_1_4_more
            ),
            MonthCellIds(
                R.id.cell_1_5,
                R.id.cell_1_5_today,
                R.id.cell_1_5_day,
                listOf(R.id.cell_1_5_m0, R.id.cell_1_5_m1, R.id.cell_1_5_m2),
                R.id.cell_1_5_more
            ),
            MonthCellIds(
                R.id.cell_1_6,
                R.id.cell_1_6_today,
                R.id.cell_1_6_day,
                listOf(R.id.cell_1_6_m0, R.id.cell_1_6_m1, R.id.cell_1_6_m2),
                R.id.cell_1_6_more
            )
        ),
        listOf(
            MonthCellIds(
                R.id.cell_2_0,
                R.id.cell_2_0_today,
                R.id.cell_2_0_day,
                listOf(R.id.cell_2_0_m0, R.id.cell_2_0_m1, R.id.cell_2_0_m2),
                R.id.cell_2_0_more
            ),
            MonthCellIds(
                R.id.cell_2_1,
                R.id.cell_2_1_today,
                R.id.cell_2_1_day,
                listOf(R.id.cell_2_1_m0, R.id.cell_2_1_m1, R.id.cell_2_1_m2),
                R.id.cell_2_1_more
            ),
            MonthCellIds(
                R.id.cell_2_2,
                R.id.cell_2_2_today,
                R.id.cell_2_2_day,
                listOf(R.id.cell_2_2_m0, R.id.cell_2_2_m1, R.id.cell_2_2_m2),
                R.id.cell_2_2_more
            ),
            MonthCellIds(
                R.id.cell_2_3,
                R.id.cell_2_3_today,
                R.id.cell_2_3_day,
                listOf(R.id.cell_2_3_m0, R.id.cell_2_3_m1, R.id.cell_2_3_m2),
                R.id.cell_2_3_more
            ),
            MonthCellIds(
                R.id.cell_2_4,
                R.id.cell_2_4_today,
                R.id.cell_2_4_day,
                listOf(R.id.cell_2_4_m0, R.id.cell_2_4_m1, R.id.cell_2_4_m2),
                R.id.cell_2_4_more
            ),
            MonthCellIds(
                R.id.cell_2_5,
                R.id.cell_2_5_today,
                R.id.cell_2_5_day,
                listOf(R.id.cell_2_5_m0, R.id.cell_2_5_m1, R.id.cell_2_5_m2),
                R.id.cell_2_5_more
            ),
            MonthCellIds(
                R.id.cell_2_6,
                R.id.cell_2_6_today,
                R.id.cell_2_6_day,
                listOf(R.id.cell_2_6_m0, R.id.cell_2_6_m1, R.id.cell_2_6_m2),
                R.id.cell_2_6_more
            )
        ),
        listOf(
            MonthCellIds(
                R.id.cell_3_0,
                R.id.cell_3_0_today,
                R.id.cell_3_0_day,
                listOf(R.id.cell_3_0_m0, R.id.cell_3_0_m1, R.id.cell_3_0_m2),
                R.id.cell_3_0_more
            ),
            MonthCellIds(
                R.id.cell_3_1,
                R.id.cell_3_1_today,
                R.id.cell_3_1_day,
                listOf(R.id.cell_3_1_m0, R.id.cell_3_1_m1, R.id.cell_3_1_m2),
                R.id.cell_3_1_more
            ),
            MonthCellIds(
                R.id.cell_3_2,
                R.id.cell_3_2_today,
                R.id.cell_3_2_day,
                listOf(R.id.cell_3_2_m0, R.id.cell_3_2_m1, R.id.cell_3_2_m2),
                R.id.cell_3_2_more
            ),
            MonthCellIds(
                R.id.cell_3_3,
                R.id.cell_3_3_today,
                R.id.cell_3_3_day,
                listOf(R.id.cell_3_3_m0, R.id.cell_3_3_m1, R.id.cell_3_3_m2),
                R.id.cell_3_3_more
            ),
            MonthCellIds(
                R.id.cell_3_4,
                R.id.cell_3_4_today,
                R.id.cell_3_4_day,
                listOf(R.id.cell_3_4_m0, R.id.cell_3_4_m1, R.id.cell_3_4_m2),
                R.id.cell_3_4_more
            ),
            MonthCellIds(
                R.id.cell_3_5,
                R.id.cell_3_5_today,
                R.id.cell_3_5_day,
                listOf(R.id.cell_3_5_m0, R.id.cell_3_5_m1, R.id.cell_3_5_m2),
                R.id.cell_3_5_more
            ),
            MonthCellIds(
                R.id.cell_3_6,
                R.id.cell_3_6_today,
                R.id.cell_3_6_day,
                listOf(R.id.cell_3_6_m0, R.id.cell_3_6_m1, R.id.cell_3_6_m2),
                R.id.cell_3_6_more
            )
        ),
        listOf(
            MonthCellIds(
                R.id.cell_4_0,
                R.id.cell_4_0_today,
                R.id.cell_4_0_day,
                listOf(R.id.cell_4_0_m0, R.id.cell_4_0_m1, R.id.cell_4_0_m2),
                R.id.cell_4_0_more
            ),
            MonthCellIds(
                R.id.cell_4_1,
                R.id.cell_4_1_today,
                R.id.cell_4_1_day,
                listOf(R.id.cell_4_1_m0, R.id.cell_4_1_m1, R.id.cell_4_1_m2),
                R.id.cell_4_1_more
            ),
            MonthCellIds(
                R.id.cell_4_2,
                R.id.cell_4_2_today,
                R.id.cell_4_2_day,
                listOf(R.id.cell_4_2_m0, R.id.cell_4_2_m1, R.id.cell_4_2_m2),
                R.id.cell_4_2_more
            ),
            MonthCellIds(
                R.id.cell_4_3,
                R.id.cell_4_3_today,
                R.id.cell_4_3_day,
                listOf(R.id.cell_4_3_m0, R.id.cell_4_3_m1, R.id.cell_4_3_m2),
                R.id.cell_4_3_more
            ),
            MonthCellIds(
                R.id.cell_4_4,
                R.id.cell_4_4_today,
                R.id.cell_4_4_day,
                listOf(R.id.cell_4_4_m0, R.id.cell_4_4_m1, R.id.cell_4_4_m2),
                R.id.cell_4_4_more
            ),
            MonthCellIds(
                R.id.cell_4_5,
                R.id.cell_4_5_today,
                R.id.cell_4_5_day,
                listOf(R.id.cell_4_5_m0, R.id.cell_4_5_m1, R.id.cell_4_5_m2),
                R.id.cell_4_5_more
            ),
            MonthCellIds(
                R.id.cell_4_6,
                R.id.cell_4_6_today,
                R.id.cell_4_6_day,
                listOf(R.id.cell_4_6_m0, R.id.cell_4_6_m1, R.id.cell_4_6_m2),
                R.id.cell_4_6_more
            )
        ),
        listOf(
            MonthCellIds(
                R.id.cell_5_0,
                R.id.cell_5_0_today,
                R.id.cell_5_0_day,
                listOf(R.id.cell_5_0_m0, R.id.cell_5_0_m1, R.id.cell_5_0_m2),
                R.id.cell_5_0_more
            ),
            MonthCellIds(
                R.id.cell_5_1,
                R.id.cell_5_1_today,
                R.id.cell_5_1_day,
                listOf(R.id.cell_5_1_m0, R.id.cell_5_1_m1, R.id.cell_5_1_m2),
                R.id.cell_5_1_more
            ),
            MonthCellIds(
                R.id.cell_5_2,
                R.id.cell_5_2_today,
                R.id.cell_5_2_day,
                listOf(R.id.cell_5_2_m0, R.id.cell_5_2_m1, R.id.cell_5_2_m2),
                R.id.cell_5_2_more
            ),
            MonthCellIds(
                R.id.cell_5_3,
                R.id.cell_5_3_today,
                R.id.cell_5_3_day,
                listOf(R.id.cell_5_3_m0, R.id.cell_5_3_m1, R.id.cell_5_3_m2),
                R.id.cell_5_3_more
            ),
            MonthCellIds(
                R.id.cell_5_4,
                R.id.cell_5_4_today,
                R.id.cell_5_4_day,
                listOf(R.id.cell_5_4_m0, R.id.cell_5_4_m1, R.id.cell_5_4_m2),
                R.id.cell_5_4_more
            ),
            MonthCellIds(
                R.id.cell_5_5,
                R.id.cell_5_5_today,
                R.id.cell_5_5_day,
                listOf(R.id.cell_5_5_m0, R.id.cell_5_5_m1, R.id.cell_5_5_m2),
                R.id.cell_5_5_more
            ),
            MonthCellIds(
                R.id.cell_5_6,
                R.id.cell_5_6_today,
                R.id.cell_5_6_day,
                listOf(R.id.cell_5_6_m0, R.id.cell_5_6_m1, R.id.cell_5_6_m2),
                R.id.cell_5_6_more
            )
        )
    )
}
