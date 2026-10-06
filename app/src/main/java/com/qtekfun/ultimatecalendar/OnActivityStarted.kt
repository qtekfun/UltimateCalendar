// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Runs [onStarted] each time an activity of the app comes to the foreground. */
class OnActivityStarted(private val onStarted: () -> Unit) :
    Application.ActivityLifecycleCallbacks {
    override fun onActivityStarted(activity: Activity) = onStarted()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
