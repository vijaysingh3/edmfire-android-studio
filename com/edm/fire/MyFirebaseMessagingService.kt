package com.edm.fire

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data  = remoteMessage.data
        val title = data["title"] ?: remoteMessage.notification?.title
        val body  = data["body"]  ?: remoteMessage.notification?.body

        if (!title.isNullOrEmpty() && !body.isNullOrEmpty()) {
            // ✅ Screen off/background mein bhi kaam karega — service context se
            showNotification(title, body, data)
            triggerVibration()   // ✅ Vibration alag se — notification ke saath
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NOTIFICATION — notification.wav sound + screen off support
    // ══════════════════════════════════════════════════════════════════════════
    private fun showNotification(title: String, body: String, data: Map<String, String>?) {
        val channelId      = getChannelId(data?.get("type"))
        val notificationId = System.currentTimeMillis().toInt()

        // ✅ notification.wav — res/raw/notification.wav file use hogi
        val soundUri = Uri.parse("android.resource://${packageName}/${R.raw.notification}")

        // ✅ Vibration pattern — short long short (tournament feel)
        var vibrationPattern = longArrayOf(0, 300, 150, 600)

        // ── Channel create karo (Android 8+) ─────────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "EDMFire Alerts",
                NotificationManager.IMPORTANCE_HIGH  // ✅ HIGH = screen off pe bhi show
            ).apply {
                description = "EDM Fire tournament and payment alerts"

                // ✅ Custom sound — notification.wav
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)

                // ✅ Vibration channel level pe bhi set karo
                enableVibration(true)
                vibrationPattern = this@MyFirebaseMessagingService.run {
                    longArrayOf(0, 300, 150, 600)
                }

                // ✅ Screen off pe bhi notification dikhao
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC

                // ✅ Heads-up notification (screen off se bhi wake karta hai feel)
                enableLights(true)
                lightColor = android.graphics.Color.YELLOW
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // ── Intent — notification tap pe HomeActivity open ────────────────────
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            data?.forEach { (key, value) -> putExtra(key, value) }
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent, pendingIntentFlags
        )

        // ── Notification build ────────────────────────────────────────────────
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.applogo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)       // ✅ HIGH priority
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)                                  // ✅ notification.wav
            .setVibrate(vibrationPattern)                        // ✅ Vibration pattern
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // ✅ Lock screen pe bhi
            .setLights(android.graphics.Color.YELLOW, 500, 500) // ✅ LED blink
            // ✅ Wakelock style — screen off pe bhi notification pop hogi
            .setFullScreenIntent(pendingIntent, false)

        val notificationManager = NotificationManagerCompat.from(this)
        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(notificationId, builder.build())
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VIBRATION — Android version safe handling
    // ✅ Screen off pe bhi kaam karega — system level vibration hai
    // ══════════════════════════════════════════════════════════════════════════
    private fun triggerVibration() {
        try {
            // Pattern: wait=0ms, vibrate=300ms, pause=150ms, vibrate=600ms
            val pattern = longArrayOf(0, 300, 150, 600)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ — VibratorManager use karo
                val vibratorManager =
                    getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, -1) // -1 = repeat nahi
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8-11 — VibrationEffect use karo
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, -1)
                )
            } else {
                // Android 7 aur neeche — purana API
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            // Vibration fail hone pe koi crash nahi — silently ignore
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CHANNEL ID — notification type ke hisaab se
    // ══════════════════════════════════════════════════════════════════════════
    private fun getChannelId(type: String?): String {
        return when (type) {
            "deposit_success",
            "withdrawal_success" -> "payment_channel"
            "tournament_join",
            "tournament_joined"  -> "tournament_channel"
            else                 -> "general_channel"
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NEW TOKEN — future use ke liye ready
    // ══════════════════════════════════════════════════════════════════════════
    override fun onNewToken(token: String) {
        // Token refresh hone par Firestore update karo
        // HomeActivity ka updateFcmTokenSilently() pattern use kar sakte ho
    }
}