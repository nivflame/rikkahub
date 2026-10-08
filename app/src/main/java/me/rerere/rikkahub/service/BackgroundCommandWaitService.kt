package me.rerere.rikkahub.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R

class BackgroundCommandWaitService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.chat_live_update_title))
                .setContentText(getString(R.string.background_command_waiting))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .build()
        )
        // Stop must go through onStartCommand: calling stopService() before the
        // service finished starting still requires startForeground() and crashes
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 99998
        private const val ACTION_STOP = "me.rerere.rikkahub.service.BackgroundCommandWaitService.STOP"

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, BackgroundCommandWaitService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context, BackgroundCommandWaitService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
