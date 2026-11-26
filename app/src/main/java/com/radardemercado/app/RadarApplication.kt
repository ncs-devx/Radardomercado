package com.radardemercado.app

import android.app.Application
import com.radardemercado.app.data.AlertStore
import com.radardemercado.app.data.MarketRepository
import com.radardemercado.app.notifications.AlertNotifications
import com.radardemercado.app.worker.AlertWorker

class RadarApplication : Application() {
    val store by lazy { AlertStore(this) }
    val repository by lazy { MarketRepository(store) }

    override fun onCreate() {
        super.onCreate()
        runCatching { AlertNotifications.createChannel(this) }
        runCatching { AlertWorker.schedule(this) }
    }
}
