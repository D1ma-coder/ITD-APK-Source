package com.itd.app.data.remote

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.itd.app.MainActivity
import com.itd.app.R
import com.itd.app.data.model.NotificationItem
import java.util.Collections

/** Notification ids that were already shown in-app or as a system notification. */
object ShownNotifications {
    val ids: MutableSet<String> = Collections.newSetFromMap(LinkedHashMap<String, Boolean>(101, 0.75f, true))

    fun contains(id: String): Boolean = synchronized(ids) { id in ids }

    fun markShown(id: String) {
        synchronized(ids) {
            ids.add(id)
            if (ids.size > 100) {
                val iterator = ids.iterator()
                iterator.next()
                iterator.remove()
            }
        }
    }
}

/** Time of the last session created from this device; "new_session" pushes right after it are suppressed. */
object SessionCreation {
    @Volatile
    var lastCreatedAt: Long = 0L
}

const val NOTIFICATION_CHANNEL_ID = "itd_notifications_v2"

fun showNotification(context: Context, item: NotificationItem, alert: Boolean) {
    val uri = item.actor.username?.let {
        when (item.type) {
            "follow" -> Uri.parse("itd://profile/${item.actor.username}")
            "like", "reply", "comment_like", "mention", "comment", "wall_post" -> item.targetId?.let { targetId ->
                val comment = if (item.type == "like") item.subjectId?.takeIf { item.subjectType == "comment" } else item.subjectId
                if (comment == null) Uri.parse("itd://post/$targetId") else Uri.parse("itd://post/$targetId?comment=$comment")
            }
            else -> null
        }
    }
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (uri != null) {
            action = Intent.ACTION_VIEW
            data = uri
        }
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        item.id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val action = when (item.type) {
        "follow" -> "подписался(-ась) на вас"
        "like" -> "оценил(а) запись"
        "reply" -> "ответил(а) на комментарий"
        "comment_like" -> "оценил(а) ваш комментарий"
        "mention" -> "упомянул(а) вас"
        "comment" -> "прокомментировал(а)"
        "wall_post" -> "оставил(а) запись на стене"
        else -> ""
    }
    val text = buildString {
        append(action)
        if (!item.preview.isNullOrBlank()) {
            append(": ")
            append(item.preview)
        }
    }
    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notifications)
        .setContentTitle(item.actor.displayName)
        .setContentText(text)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setFullScreenIntent(pendingIntent, true)
        .setSilent(!alert)
        .build()
    context.getSystemService(NotificationManager::class.java).notify(item.id.hashCode(), notification)
}
