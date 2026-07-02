package me.rerere.rikkahub.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.utils.sendNotification

class GenerationKeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val senderName = intent?.getStringExtra(EXTRA_SENDER_NAME)
            ?: getString(R.string.chat_live_update_title)

        sendNotification(
            channelId = CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
            notificationId = NOTIFICATION_ID
        ) {
            title = senderName
            content = getString(R.string.chat_live_update_generating)
            ongoing = true
            onlyAlertOnce = true
            category = NotificationCompat.CATEGORY_PROGRESS
            requestPromotedOngoing = true
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_SENDER_NAME = "sender_name"
        const val NOTIFICATION_ID = 99999
    }
}
