package com.edm.fire.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// TTL values in milliseconds
const val TTL_USER_DATA = 5 * 60 * 1000L      // 5 minutes
const val TTL_NOTIFICATIONS = 2 * 60 * 1000L  // 2 minutes
const val TTL_TOURNAMENT_COUNTS = 3 * 60 * 1000L  // 3 minutes
const val TTL_BANNERS = 10 * 60 * 1000L       // 10 minutes
const val TTL_ANNOUNCEMENTS = 5 * 60 * 1000L  // 5 minutes

class SimpleCacheManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("edm_fire_cache", Context.MODE_PRIVATE)

    // ============ User Data Cache ============
    data class UserCacheData(
        val userId: String,
        val totalCoins: Long,
        val referralBonus: Long,
        val winningCoins: Long,
        val topUpCoins: Long,
        val lastUpdated: Long
    )

    suspend fun getUserData(userId: String): UserCacheData? = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString("user_data_$userId", null) ?: return@withContext null
        try {
            val json = JSONObject(jsonString)
            val lastUpdated = json.optLong("lastUpdated", 0)

            // Check if data is expired
            if (System.currentTimeMillis() - lastUpdated > TTL_USER_DATA) {
                return@withContext null
            }

            UserCacheData(
                userId = json.getString("userId"),
                totalCoins = json.optLong("totalCoins", 0),
                referralBonus = json.optLong("referralBonus", 0),
                winningCoins = json.optLong("winningCoins", 0),
                topUpCoins = json.optLong("topUpCoins", 0),
                lastUpdated = lastUpdated
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveUserData(data: UserCacheData) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("userId", data.userId)
                put("totalCoins", data.totalCoins)
                put("referralBonus", data.referralBonus)
                put("winningCoins", data.winningCoins)
                put("topUpCoins", data.topUpCoins)
                put("lastUpdated", System.currentTimeMillis())
            }
            prefs.edit().putString("user_data_${data.userId}", json.toString()).apply()
        } catch (e: Exception) { }
    }

    // ============ Notifications Cache ============
    suspend fun getUnreadNotificationCount(userId: String): Int = withContext(Dispatchers.IO) {
        val lastFetchTime = prefs.getLong("notifications_fetch_time_$userId", 0)
        val currentTime = System.currentTimeMillis()

        // Agar TTL expire ho gaya hai toh cache invalid hai
        if (currentTime - lastFetchTime > TTL_NOTIFICATIONS) {
            return@withContext -1 // -1 means expired, need fresh fetch
        }

        prefs.getInt("unread_count_$userId", 0)
    }

    suspend fun saveUnreadNotificationCount(userId: String, count: Int) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            putInt("unread_count_$userId", count)
            putLong("notifications_fetch_time_$userId", System.currentTimeMillis())
        }.apply()
    }

    suspend fun markNotificationsAsRead(userId: String) = withContext(Dispatchers.IO) {
        prefs.edit().putInt("unread_count_$userId", 0).apply()
    }

    // ============ Tournament Counts Cache ============
    data class TournamentCountCache(
        val freeCount: Int,
        val battleCount: Int,
        val loneCount: Int,
        val clashCount: Int,
        val lastUpdated: Long
    )

    suspend fun getTournamentCounts(): TournamentCountCache? = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString("tournament_counts", null) ?: return@withContext null
        try {
            val json = JSONObject(jsonString)
            val lastUpdated = json.optLong("lastUpdated", 0)

            if (System.currentTimeMillis() - lastUpdated > TTL_TOURNAMENT_COUNTS) {
                return@withContext null
            }

            TournamentCountCache(
                freeCount = json.optInt("freeCount", 0),
                battleCount = json.optInt("battleCount", 0),
                loneCount = json.optInt("loneCount", 0),
                clashCount = json.optInt("clashCount", 0),
                lastUpdated = lastUpdated
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveTournamentCounts(counts: TournamentCountCache) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("freeCount", counts.freeCount)
                put("battleCount", counts.battleCount)
                put("loneCount", counts.loneCount)
                put("clashCount", counts.clashCount)
                put("lastUpdated", System.currentTimeMillis())
            }
            prefs.edit().putString("tournament_counts", json.toString()).apply()
        } catch (e: Exception) { }
    }

    // ============ Banners Cache ============
    data class BannerCache(
        val bannerId: String,
        val imageUrl: String,
        val navigationUrl: String
    )

    suspend fun getBanners(): List<BannerCache>? = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString("banners", null) ?: return@withContext null
        val lastUpdated = prefs.getLong("banners_last_updated", 0)

        if (System.currentTimeMillis() - lastUpdated > TTL_BANNERS) {
            return@withContext null
        }

        try {
            val jsonArray = JSONArray(jsonString)
            val banners = mutableListOf<BannerCache>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                banners.add(
                    BannerCache(
                        bannerId = json.getString("bannerId"),
                        imageUrl = json.optString("imageUrl", ""),
                        navigationUrl = json.optString("navigationUrl", "")
                    )
                )
            }
            banners
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveBanners(banners: List<BannerCache>) = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            banners.forEach { banner ->
                val json = JSONObject().apply {
                    put("bannerId", banner.bannerId)
                    put("imageUrl", banner.imageUrl)
                    put("navigationUrl", banner.navigationUrl)
                }
                jsonArray.put(json)
            }
            prefs.edit().apply {
                putString("banners", jsonArray.toString())
                putLong("banners_last_updated", System.currentTimeMillis())
            }.apply()
        } catch (e: Exception) { }
    }

    // ============ Announcements Cache ============
    data class AnnouncementCache(
        val announcementId: String,
        val message: String
    )

    suspend fun getAnnouncements(): List<AnnouncementCache>? = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString("announcements", null) ?: return@withContext null
        val lastUpdated = prefs.getLong("announcements_last_updated", 0)

        if (System.currentTimeMillis() - lastUpdated > TTL_ANNOUNCEMENTS) {
            return@withContext null
        }

        try {
            val jsonArray = JSONArray(jsonString)
            val announcements = mutableListOf<AnnouncementCache>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                announcements.add(
                    AnnouncementCache(
                        announcementId = json.getString("announcementId"),
                        message = json.optString("message", "")
                    )
                )
            }
            announcements
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveAnnouncements(announcements: List<AnnouncementCache>) = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            announcements.forEach { announcement ->
                val json = JSONObject().apply {
                    put("announcementId", announcement.announcementId)
                    put("message", announcement.message)
                }
                jsonArray.put(json)
            }
            prefs.edit().apply {
                putString("announcements", jsonArray.toString())
                putLong("announcements_last_updated", System.currentTimeMillis())
            }.apply()
        } catch (e: Exception) { }
    }

    // Clear all cache (for logout)
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }
}