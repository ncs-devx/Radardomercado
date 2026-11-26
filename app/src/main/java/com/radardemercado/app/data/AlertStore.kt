package com.radardemercado.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Armazenamento privado no sandbox do app, sem conta ou servidor externo. */
class AlertStore(context: Context) {
    private val preferences = context.getSharedPreferences("radar_local_data", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val alertType = object : TypeToken<List<PriceAlert>>() {}.type
    private val historyType = object : TypeToken<List<AlertHistory>>() {}.type
    private val tickerType = object : TypeToken<List<Ticker>>() {}.type
    private val newsType = object : TypeToken<List<NewsItem>>() {}.type
    private val alertState = MutableStateFlow(readAlerts())
    private val historyState = MutableStateFlow(readHistory())
    private val favoriteState = MutableStateFlow(readFavorites())
    private val newsEnabledState = MutableStateFlow(preferences.getBoolean("news_enabled", true))
    private val alertSoundState = MutableStateFlow(preferences.getBoolean("alert_sound", true))
    private val alertVibrationState = MutableStateFlow(preferences.getBoolean("alert_vibration", true))
    private val lastBackgroundCheckState = MutableStateFlow(preferences.getLong("last_background_check", 0L))

    fun observeAlerts() = alertState.asStateFlow()
    fun observeHistory() = historyState.asStateFlow()
    fun observeFavorites() = favoriteState.asStateFlow()
    fun observeNewsEnabled() = newsEnabledState.asStateFlow()
    fun observeAlertSound() = alertSoundState.asStateFlow()
    fun observeAlertVibration() = alertVibrationState.asStateFlow()
    fun observeLastBackgroundCheck() = lastBackgroundCheckState.asStateFlow()
    fun isNewsEnabled() = newsEnabledState.value
    fun setNewsEnabled(enabled: Boolean) { newsEnabledState.value = enabled; preferences.edit().putBoolean("news_enabled", enabled).apply() }
    fun alertSoundEnabled() = alertSoundState.value
    fun alertVibrationEnabled() = alertVibrationState.value
    fun setAlertSound(enabled: Boolean) { alertSoundState.value = enabled; preferences.edit().putBoolean("alert_sound", enabled).apply() }
    fun setAlertVibration(enabled: Boolean) { alertVibrationState.value = enabled; preferences.edit().putBoolean("alert_vibration", enabled).apply() }
    fun recordBackgroundCheck() { val now = System.currentTimeMillis(); lastBackgroundCheckState.value = now; preferences.edit().putLong("last_background_check", now).apply() }

    fun twelveDataKey(): String {
        val encrypted = preferences.getString("twelve_data_key_encrypted", null)
        if (!encrypted.isNullOrBlank()) return decryptSecret(encrypted)
        val legacy = preferences.getString("twelve_data_key", "").orEmpty()
        if (legacy.isNotBlank()) saveTwelveDataKey(legacy)
        return legacy
    }

    fun saveTwelveDataKey(value: String): Boolean = runCatching {
        val trimmed = value.trim()
        val protectedValue = if (trimmed.isBlank()) "" else encryptSecret(trimmed)
        preferences.edit().remove("twelve_data_key").putString("twelve_data_key_encrypted", protectedValue).apply()
    }.isSuccess

    fun cachedTickers(): List<Ticker> = runCatching {
        gson.fromJson<List<Ticker>>(preferences.getString("cached_tickers", "[]"), tickerType)
    }.getOrDefault(emptyList())

    fun saveCachedTickers(values: List<Ticker>) {
        preferences.edit().putString("cached_tickers", gson.toJson(values)).apply()
    }

    fun cachedNews(): List<NewsItem> = runCatching {
        gson.fromJson<List<NewsItem>>(preferences.getString("cached_news", "[]"), newsType)
    }.getOrDefault(emptyList())

    fun saveCachedNews(values: List<NewsItem>) {
        preferences.edit().putString("cached_news", gson.toJson(values)).apply()
    }
    suspend fun activeAlerts() = alertState.value.filter { it.enabled && !it.triggered }
    suspend fun findAlert(id: Long) = alertState.value.firstOrNull { it.id == id }

    suspend fun saveAlert(alert: PriceAlert): Long {
        val id = if (alert.id == 0L) (alertState.value.maxOfOrNull { it.id } ?: 0L) + 1 else alert.id
        val value = alert.copy(id = id)
        alertState.value = alertState.value.filterNot { it.id == id } + value
        persistAlerts()
        return id
    }

    suspend fun deleteAlert(alert: PriceAlert) {
        alertState.value = alertState.value.filterNot { it.id == alert.id }
        persistAlerts()
    }

    suspend fun recordTrigger(alert: PriceAlert, price: Double, simulated: Boolean) {
        val item = AlertHistory(
            id = (historyState.value.maxOfOrNull { it.id } ?: 0L) + 1,
            alertId = alert.id,
            asset = alert.asset,
            quote = alert.quote,
            direction = alert.direction,
            targetPrice = alert.targetPrice,
            observedPrice = price,
            simulated = simulated
        )
        historyState.value = listOf(item) + historyState.value
        saveAlert(alert.copy(enabled = true, triggered = false, waitingForReset = true))
        val previous = preferences.getString("history", "[]")
        preferences.edit().putString("history_backup", previous).putString("history", gson.toJson(historyState.value)).apply()
    }

    suspend fun toggleFavorite(asset: Asset) {
        favoriteState.value = if (favoriteState.value.contains(asset)) {
            favoriteState.value.filterNot { it == asset }
        } else favoriteState.value + asset
        preferences.edit().putStringSet("favorites", favoriteState.value.map { it.symbol }.toSet()).apply()
    }

    private fun readAlerts(): List<PriceAlert> {
        val raw = preferences.getString("alerts", "[]") ?: "[]"
        return runCatching { gson.fromJson<List<PriceAlert>>(normalizeLegacyAssets(raw), alertType) }
            .onFailure { preserveCorrupt("alerts", raw) }
            .getOrDefault(emptyList())
    }

    private fun readHistory(): List<AlertHistory> {
        val raw = preferences.getString("history", "[]") ?: "[]"
        return runCatching { gson.fromJson<List<AlertHistory>>(normalizeLegacyAssets(raw), historyType) }
            .onFailure { preserveCorrupt("history", raw) }
            .getOrDefault(emptyList())
    }

    private fun readFavorites(): List<Asset> {
        val saved = preferences.getStringSet("favorites", null) ?: return Asset.DEFAULTS
        return saved.map(::Asset).sortedBy { it.symbol }
    }

    private fun persistAlerts() {
        val previous = preferences.getString("alerts", "[]")
        preferences.edit().putString("alerts_backup", previous).putString("alerts", gson.toJson(alertState.value)).apply()
    }

    private fun preserveCorrupt(name: String, raw: String) {
        preferences.edit().putString("${name}_corrupt_${System.currentTimeMillis()}", raw).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private fun encryptSecret(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decryptSecret(value: String): String = runCatching {
        val parts = value.split(":", limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrDefault("")

    /** Migra silenciosamente o formato antigo: "asset":"BTC" -> "asset":{"symbol":"BTC"}. */
    private fun normalizeLegacyAssets(json: String): String {
        val array = JsonParser.parseString(json).asJsonArray
        array.forEach { element ->
            val item = element.asJsonObject
            val oldAsset = item.get("asset")
            if (oldAsset?.isJsonPrimitive == true) {
                item.add("asset", JsonObject().apply { addProperty("symbol", oldAsset.asString) })
            }
            if (!item.has("waitingForReset")) item.addProperty("waitingForReset", false)
        }
        return array.toString()
    }

    private companion object { const val KEY_ALIAS = "radar_twelve_data_key" }
}
