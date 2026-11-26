package com.radardemercado.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.radardemercado.app.MainActivity
import br.com.nathan.radardemercado.R
import com.radardemercado.app.data.AlertHistory
import com.radardemercado.app.ui.formatPrice
import java.text.SimpleDateFormat
import java.util.*

object AlertNotifications {
    private const val CHANNEL_PREFIX = "price_alerts_v3"

    fun createChannel(context: Context) {
        createChannel(context, sound = true, vibration = true)
    }

    private fun createChannel(context: Context, sound: Boolean, vibration: Boolean): String {
        val channelId = "${CHANNEL_PREFIX}_${if (sound) 1 else 0}_${if (vibration) 1 else 0}"
        val channel = NotificationChannel(channelId, "Alertas de preço", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisos quando um ativo atingir o preço definido"
            enableVibration(vibration)
            vibrationPattern = if (vibration) longArrayOf(0, 250, 150, 350) else null
            setShowBadge(true)
            if (sound) {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build()
                )
            } else setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return channelId
    }

    fun show(context: Context, item: AlertHistory, sound: Boolean = true, vibration: Boolean = true): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return runCatching {
            val channelId = createChannel(context, sound, vibration)
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.triggeredAt))
            val title = "${item.asset.symbol}/${item.quote.name} atingiu seu alerta"
            val simulation = if (item.simulated) " • TESTE" else ""
            val body = "Atual ${formatPrice(item.observedPrice, item.quote)} • Alvo ${formatPrice(item.targetPrice, item.quote)} • $time$simulation"
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSilent(!sound)
                .setVibrate(if (vibration) longArrayOf(0, 250, 150, 350) else null)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            NotificationManagerCompat.from(context).notify(item.alertId.toInt(), notification)
            true
        }.getOrDefault(false)
    }

    fun enabled(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()
}
