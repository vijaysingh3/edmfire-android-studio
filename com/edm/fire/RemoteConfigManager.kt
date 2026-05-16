package com.edm.fire

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.tasks.await

class RemoteConfigManager private constructor(private val context: Context) {

    companion object {
        private const val PREF_NAME     = "edmfire_secure_config"
        private const val CACHE_EXPIRY  = 24 * 60 * 60 * 1000L

        const val KEY_SUPABASE_URL          = "Supa_Url"
        const val KEY_SUPABASE_ANON_KEY     = "supa_anonkey"
        const val KEY_COORDINATOR_ENDPOINT  = "edmfire_agent"

        @Volatile
        private var instance: RemoteConfigManager? = null

        fun getInstance(context: Context): RemoteConfigManager {
            return instance ?: synchronized(this) {
                instance ?: RemoteConfigManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    data class SecureConfig(
        val supabaseUrl: String,
        val supabaseAnonKey: String,
        val coordinatorEndpoint: String,
        val lastFetched: Long
    ) {
        fun isExpired(): Boolean =
            System.currentTimeMillis() - lastFetched > CACHE_EXPIRY
    }

    init {
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600
                fetchTimeoutInSeconds         = 30
            }
        )
        remoteConfig.setDefaultsAsync(emptyMap())
    }

    suspend fun fetchConfig(): Result<SecureConfig> {
        return try {
            remoteConfig.fetchAndActivate().await()

            val supabaseUrl         = remoteConfig.getString(KEY_SUPABASE_URL)
            val supabaseAnonKey     = remoteConfig.getString(KEY_SUPABASE_ANON_KEY)
            val coordinatorEndpoint = remoteConfig.getString(KEY_COORDINATOR_ENDPOINT)

            if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank() || coordinatorEndpoint.isBlank()) {
                return Result.failure(Exception("Remote Config values are empty"))
            }

            val config = SecureConfig(
                supabaseUrl         = supabaseUrl,
                supabaseAnonKey     = supabaseAnonKey,
                coordinatorEndpoint = coordinatorEndpoint,
                lastFetched         = System.currentTimeMillis()
            )
            saveToCache(config)
            Result.success(config)

        } catch (e: Exception) {
            val cached = getCachedConfig()
            if (cached != null) Result.success(cached)
            else Result.failure(Exception("No config available. Check internet."))
        }
    }

    fun getCachedConfig(): SecureConfig? {
        val url      = prefs.getString(KEY_SUPABASE_URL, null)
        val anon     = prefs.getString(KEY_SUPABASE_ANON_KEY, null)
        val endpoint = prefs.getString(KEY_COORDINATOR_ENDPOINT, null)
        val fetched  = prefs.getLong("last_fetched", 0)

        return if (!url.isNullOrBlank() && !anon.isNullOrBlank() &&
            !endpoint.isNullOrBlank() && fetched > 0)
            SecureConfig(url, anon, endpoint, fetched)
        else null
    }

    private fun saveToCache(config: SecureConfig) {
        prefs.edit()
            .putString(KEY_SUPABASE_URL, config.supabaseUrl)
            .putString(KEY_SUPABASE_ANON_KEY, config.supabaseAnonKey)
            .putString(KEY_COORDINATOR_ENDPOINT, config.coordinatorEndpoint)
            .putLong("last_fetched", config.lastFetched)
            .apply()
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }
}