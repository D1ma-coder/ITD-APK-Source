package com.itd.app.data.remote

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.itd.app.MainActivity
import com.itd.app.R
import com.itd.app.data.model.NotificationActor
import com.itd.app.data.model.NotificationItem
import com.itd.app.util.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PushNotificationService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"]
        val link = data["link"]?.takeIf { value ->
            val uri = Uri.parse(value)
            uri.host == null && uri.pathSegments.firstOrNull() == "event"
        }
        val eventUri = if (link != null || type == "event") {
            Uri.Builder().scheme("itd").authority("event").apply {
                if (link != null) appendQueryParameter("link", link)
            }.build()
        } else {
            null
        }
        if (eventUri != null) {
            val id = data["notificationId"] ?: data["broadcastId"] ?: return
            val title = message.notification?.title ?: data["title"]
            if (title.isNullOrBlank()) {
                LogTag.PUSH.tree().w("event push without title id=%s", id)
                return
            }
            if (ShownNotifications.contains(id)) return
            val body = message.notification?.body ?: data["body"] ?: ""
            val pendingIntent = activityIntent(id, eventUri)
            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            getSystemService(NotificationManager::class.java).notify(id.hashCode(), notification)
            return
        }

        val id = data["notificationId"] ?: return
        LogTag.PUSH.tree().d("push received id=%s type=%s", id, data["type"])
        if (data["type"] != "new_session") {
            if (ShownNotifications.contains(id)) {
                LogTag.PUSH.tree().d("push already shown id=%s", id)
                return
            }
            val notificationType = data["type"] ?: return
            val actorId = data["actorId"] ?: return
            showNotification(
                this,
                NotificationItem(
                    id = id,
                    type = notificationType,
                    targetType = data["targetType"],
                    targetId = data["targetId"],
                    subjectType = data["subjectType"],
                    subjectId = data["subjectId"],
                    preview = data["preview"],
                    createdAt = "",
                    actor = NotificationActor(
                        id = actorId,
                        displayName = data["actorName"] ?: "",
                        username = data["actorUsername"],
                        avatar = data["actorAvatar"],
                    ),
                ),
                alert = true,
            )
            return
        }

        val createdAt = SessionCreation.lastCreatedAt
        if (createdAt != 0L && System.currentTimeMillis() - createdAt < 20_000) {
            LogTag.PUSH.tree().d("skip new_session push for own sign-in")
            return
        }
        val title = message.notification?.title
        if (title.isNullOrBlank()) {
            LogTag.PUSH.tree().w("new_session push without title")
            return
        }
        val body = message.notification?.body ?: ""
        val pendingIntent = activityIntent(id, Uri.parse("itd://sessions"))
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(pendingIntent, true)
            .build()
        getSystemService(NotificationManager::class.java).notify(id.hashCode(), notification)
    }

    override fun onNewToken(token: String) {
        LogTag.PUSH.tree().d("FCM token refreshed token=%s", if (token.isEmpty()) "tok(nil)" else "tok(len=${token.length})")
        getSharedPreferences("fcm_prefs", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
    }

    private fun activityIntent(id: String, uri: Uri): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Intent.ACTION_VIEW
            data = uri
        }
        return PendingIntent.getActivity(this, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
