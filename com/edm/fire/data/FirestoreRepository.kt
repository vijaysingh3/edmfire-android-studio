package com.edm.fire.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreRepository(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val cacheManager = SimpleCacheManager(context)

    // ============ User Data Fetch with Cache ============
    suspend fun fetchUserData(forceRefresh: Boolean = false): SimpleCacheManager.UserCacheData? = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext null

        // Check cache first (if not force refresh)
        if (!forceRefresh) {
            val cachedData = cacheManager.getUserData(userId)
            if (cachedData != null) {
                return@withContext cachedData
            }
        }

        // Fetch from Firestore
        return@withContext try {
            val document = db.collection("Users")
                .document(userId)
                .get()
                .await()

            if (document.exists()) {
                val referralBonus = document.getLong("MyReferralBonus") ?: 0L
                val winningCoins = document.getLong("WinningCoins") ?: 0L
                val topUpCoins = document.getLong("TopUpCoins") ?: 0L
                val totalCoins = referralBonus + winningCoins + topUpCoins

                val userData = SimpleCacheManager.UserCacheData(
                    userId = userId,
                    totalCoins = totalCoins,
                    referralBonus = referralBonus,
                    winningCoins = winningCoins,
                    topUpCoins = topUpCoins,
                    lastUpdated = System.currentTimeMillis()
                )

                // Save to cache
                cacheManager.saveUserData(userData)
                userData
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ============ Notifications Fetch ============
    suspend fun fetchUnreadNotificationCount(forceRefresh: Boolean = false): Int = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext 0

        // Check cache first
        if (!forceRefresh) {
            val cachedCount = cacheManager.getUnreadNotificationCount(userId)
            if (cachedCount >= 0) {
                return@withContext cachedCount
            }
        }

        // Fetch from Firestore
        return@withContext try {
            val querySnapshot = db.collection("Users")
                .document(userId)
                .collection("Notifications")
                .whereEqualTo("read", false)
                .get()
                .await()

            val count = querySnapshot.size()
            cacheManager.saveUnreadNotificationCount(userId, count)
            count
        } catch (e: Exception) {
            0
        }
    }

    // Mark notifications as read
    suspend fun markNotificationsAsRead(): Boolean = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext false
        cacheManager.markNotificationsAsRead(userId)
        true
    }
}