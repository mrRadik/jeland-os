package ru.jeland.os.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import androidx.core.content.ContextCompat
import ru.jeland.os.JelandApplication
import ru.jeland.os.MainActivity
import ru.jeland.os.R
import java.util.Locale

class TripRecordingService : Service() {
    private val dependencies get() = (application as JelandApplication).container
    private var started = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val journal = dependencies.trips
        if (intent?.action == STOP) {
            journal.finish(report = dependencies.car::recordIntegration)
            stopSelf()
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY
        if (dependencies.car.link.value.demo) {
            journal.failedToStart()
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            started = journal.start(automatic = intent?.action == AUTO_START)
            if (started) dependencies.automationTrip.setTripRecording(true) else stopSelf()
        } catch (error: Exception) {
            dependencies.car.recordIntegration("TRIP_RECORDER_START_FAILED ${error.javaClass.simpleName}")
            journal.failedToStart()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val language = AndroidSettingsStore(this).string("app.language", "ru")
        val strings = createConfigurationContext(Configuration(resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        })
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, strings.getString(R.string.trip_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, NOTIFICATION_ID, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this, NOTIFICATION_ID, Intent(this, TripRecordingService::class.java).setAction(STOP), flags
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_startup_notification)
            .setContentTitle(strings.getString(R.string.trip_recording_notification))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(null, strings.getString(R.string.trip_stop_notification), stop).build())
            .build()
    }

    override fun onDestroy() {
        if (started) {
            dependencies.trips.finish(interrupted = true, report = dependencies.car::recordIntegration)
            dependencies.automationTrip.setTripRecording(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val CHANNEL = "jeland_trip_recording"
        private const val NOTIFICATION_ID = 2702
        private const val STOP = "ru.jeland.os.trips.STOP"
        private const val AUTO_START = "ru.jeland.os.trips.AUTO_START"

        fun start(context: Context, automatic: Boolean = false) {
            try {
                val intent = Intent(context, TripRecordingService::class.java)
                if (automatic) intent.action = AUTO_START
                ContextCompat.startForegroundService(context, intent)
            } catch (error: Exception) {
                val dependencies = (context.applicationContext as JelandApplication).container
                dependencies.trips.failedToStart()
                dependencies.car.recordIntegration("TRIP_RECORDER_START_FAILED ${error.javaClass.simpleName}")
            }
        }

        fun finish(context: Context) {
            val dependencies = (context.applicationContext as JelandApplication).container
            dependencies.trips.finish(report = dependencies.car::recordIntegration)
            dependencies.automationTrip.setTripRecording(false)
            context.stopService(Intent(context, TripRecordingService::class.java))
        }
    }
}
