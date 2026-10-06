// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Robust mode (RF-08): a foreground service whose only job is to keep the process alive on
 * phones that kill apps in the background (ColorOS, MIUI, OriginOS…), where the alarms of a
 * killed app are lost. Every [BEAT] it runs [ReminderBeat]; it never uses the network. Its
 * fixed notification says what it is for and turns the mode off.
 */
@AndroidEntryPoint
class KeepAliveService : Service() {
    @Inject
    lateinit var beat: ReminderBeat

    @Inject
    lateinit var settings: ReminderSettingsSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TURN_OFF) {
            // The controller stops the service when it sees the setting change.
            settings.setRobustMode(false)
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), foregroundType())
        if (loop?.isActive != true) {
            loop = scope.launch {
                while (isActive) {
                    delay(BEAT)
                    beat.beat()
                }
            }
        }
        mutableRunning.value = true
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        mutableRunning.value = false
        super.onDestroy()
    }

    private fun foregroundType(): Int = if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    ) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } else {
        0
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL,
            getString(R.string.keep_alive_channel),
            NotificationManager.IMPORTANCE_MIN
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val turnOff = PendingIntent.getService(
            this,
            0,
            Intent(this, KeepAliveService::class.java).setAction(ACTION_TURN_OFF),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = getString(R.string.keep_alive_text)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.keep_alive_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .addAction(0, getString(R.string.keep_alive_turn_off), turnOff)
            .build()
    }

    companion object {
        private const val CHANNEL = "keep_alive"
        private const val NOTIFICATION_ID = 0x5EED
        private const val ACTION_TURN_OFF = "com.qtekfun.ultimatecalendar.KEEP_ALIVE_OFF"
        private val BEAT = 30.minutes

        private val mutableRunning = MutableStateFlow(false)

        /** Whether the service is running in this process. */
        val running: StateFlow<Boolean> = mutableRunning.asStateFlow()

        /**
         * Starts it in the foreground. Android 12+ refuses that from the background except in a
         * few cases (boot, an exact alarm, the app on screen): then it starts next time.
         */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, KeepAliveService::class.java)
                )
            } catch (_: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: not allowed from the background now.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
