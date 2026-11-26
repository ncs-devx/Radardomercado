package com.radardemercado.app.worker

import android.content.Context
import androidx.work.*
import com.radardemercado.app.RadarApplication
import com.radardemercado.app.data.AlertHistory
import com.radardemercado.app.notifications.AlertNotifications
import java.util.concurrent.TimeUnit

class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as RadarApplication).repository
        return try {
            repository.activeAlerts().groupBy { it.asset.symbol to it.quote }.forEach { (_, alerts) ->
                val first = alerts.firstOrNull() ?: return@forEach
                val ticker = repository.ticker(first.asset, first.quote)
                alerts.forEach { alert ->
                    if (repository.evaluate(alert, ticker.price)) {
                        AlertNotifications.show(applicationContext, AlertHistory(
                            alertId = alert.id, asset = alert.asset, quote = alert.quote,
                            direction = alert.direction, targetPrice = alert.targetPrice,
                            observedPrice = ticker.price
                        ), repository.alertSoundEnabled(), repository.alertVibrationEnabled())
                    }
                }
            }
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }.also { runCatching { repository.recordBackgroundCheck() } }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "market-alert-check", ExistingPeriodicWorkPolicy.UPDATE, request
                )
            }
        }
    }
}
