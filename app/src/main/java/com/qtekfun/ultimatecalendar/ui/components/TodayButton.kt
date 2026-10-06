// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.ui.theme.Dimens
import java.time.LocalDate

private val IconCorner = 4.dp
private val IconStroke = 2.dp
private val IconBand = 6.dp
private val NumberSize = 11.dp

/**
 * "Today" as Google Calendar draws it: a small calendar page with today's day number in it. The
 * glyph is a fixed 24 dp (it does not grow with the font size, like any icon); the button around
 * it is the usual 48 dp.
 */
@Composable
fun TodayButton(today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cal_today_button, today.dayOfMonth)
    IconButton(onClick = onClick, modifier = modifier) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        val shape = RoundedCornerShape(IconCorner)
        Box(
            Modifier
                .size(Dimens.todayIcon)
                .clip(shape)
                .border(IconStroke, color, shape)
                .clearAndSetSemantics { contentDescription = description }
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(IconBand)
                    .background(color)
                    .align(Alignment.TopCenter)
            )
            val number = with(LocalDensity.current) { NumberSize.toSp() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.todayIcon - IconBand - IconStroke)
                    .align(Alignment.BottomCenter),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    today.dayOfMonth.toString(),
                    color = color,
                    fontSize = number,
                    lineHeight = number,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@ComponentPreviews
@Composable
internal fun TodayButtonPreview() {
    PreviewSurface { TodayButton(PreviewToday, onClick = {}) }
}
