package com.itd.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.os.Bundle
import android.provider.Settings
import com.itd.app.data.remote.NOTIFICATION_CHANNEL_ID
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arkivanov.decompose.defaultComponentContext
import com.itd.app.ui.navigation.ItdApp
import com.itd.app.ui.theme.ItdTheme

class MainActivity : ComponentActivity() {
    private var pendingLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()
        val container = (application as ItdApplication).container
        val componentContext = defaultComponentContext()
        pendingLink = savedInstanceState?.getString("pending_link") ?: appLink(intent)
        setContent {
            ItdTheme { ItdApp(container, componentContext, pendingLink) { pendingLink = null } }
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel("itd_notifications")
        val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Уведомления", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Уведомления о действиях"
            enableVibration(true)
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes)
        }
        manager.createNotificationChannel(channel)
        manager.deleteNotificationChannel("itd_messages_v1")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLink = appLink(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingLink?.let { outState.putString("pending_link", it) }
        super.onSaveInstanceState(outState)
    }

    private fun appLink(intent: Intent): String? {
        intent.dataString?.let { return it }
        return when (intent.getStringExtra("type")) {
            "follow" -> intent.getStringExtra("actorUsername")?.let { "itd://profile/${Uri.encode(it)}" }
            "like", "reply", "repost", "mention", "comment", "wall_post" -> intent.getStringExtra("targetId")?.let { id ->
                Uri.Builder().scheme("itd").authority("post").appendPath(id).apply {
                    intent.getStringExtra("subjectId")?.let { appendQueryParameter("comment", it) }
                }.build().toString()
            }
            "new_session" -> "itd://sessions"
            else -> null
        }
    }
}
