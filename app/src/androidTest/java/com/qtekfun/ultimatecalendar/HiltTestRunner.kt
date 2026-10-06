// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.qtekfun.ultimatecalendar.sync.InvitationWorkerFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.components.SingletonComponent

/**
 * Base of the test app. Its screens show over the lock screen and wake it, so the tests also run
 * on a locked phone.
 */
open class TestAppBase :
    Application(),
    Configuration.Provider {
    // WorkManager starts on demand, like in the real app (the manifest removes its initializer).
    // The factory looks the app's own one up when a worker is made, in the graph of the test that
    // is running, because WorkManager outlives the graph of the test that first used it.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(TestWorkerFactory(this)).build()

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(OverLockScreen)
    }
}

/** The app's worker factory, as the running test's graph has it. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TestWorkers {
    fun factory(): InvitationWorkerFactory
}

private class TestWorkerFactory(private val app: Application) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = EntryPointAccessors.fromApplication(app, TestWorkers::class.java)
        .factory()
        .createWorker(appContext, workerClassName, workerParameters)
}

private object OverLockScreen : Application.ActivityLifecycleCallbacks {
    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}

/** The Hilt app used by UI tests, generated from [TestAppBase]. */
@CustomTestApplication(TestAppBase::class)
interface UiTestApplication

/** Runs UI tests on the Hilt test app, so modules can be replaced with test ones. */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, UiTestApplication_Application::class.java.name, context)
}
