package com.edm.fire

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserDataManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    data class UserData(
        val userId: String,
        val userName: String,
        val email: String,
        val level: Int,
        val kycStatus: String,
        val totalCoins: Int,
        val winningCoins: Int,
        val topUpCoins: Int,
        val accountStatus: String,
        val withdrawalCount: Int,
        val referralBonus: Int,
        val freeFireVerified: Boolean,
        val inGameUID: Long,
        val phone: String,
        val joiningDate: String,
        val joiningTime: String,
        val totalPlayed: Int,
        val totalWon: Int
    )

    fun getCurrentUserId(): String {
        return auth.currentUser?.uid
            ?: prefs.getString("user_uid", "guest_${System.currentTimeMillis()}")
            ?: "guest_${System.currentTimeMillis()}"
    }

    suspend fun fetchRealUserData(): UserData? {
        return try {
            val currentUser = auth.currentUser ?: return null
            val userId = currentUser.uid
            val doc = db.collection("Users").document(userId).get().await()
            if (!doc.exists()) return null

            val data = doc.data ?: return null
            val winningCoins = (data["WinningCoins"] as? Long)?.toInt() ?: 0
            val topUpCoins = (data["TopUpCoins"] as? Long)?.toInt() ?: 0

            UserData(
                userId = userId,
                userName = data["UserName"] as? String ?: "KYC Required",
                email = data["email"] as? String ?: currentUser.email ?: "Not Set",
                level = (data["Level"] as? Long)?.toInt() ?: 0,
                kycStatus = data["KYCStatus"] as? String ?: "Incomplete",
                totalCoins = winningCoins + topUpCoins,
                winningCoins = winningCoins,
                topUpCoins = topUpCoins,
                accountStatus = data["AccountStatus"] as? String ?: "Active",
                withdrawalCount = (data["WithdrawalCount"] as? Long)?.toInt() ?: 0,
                referralBonus = (data["MyReferralBonus"] as? Long)?.toInt() ?: 0,
                freeFireVerified = data["freeFireVerified"] as? Boolean ?: false,
                inGameUID = data["InGameUID"] as? Long ?: 0L,
                phone = data["PhoneNumber"] as? String ?: "Not Available",
                joiningDate = data["JoiningDate"] as? String ?: "",
                joiningTime = data["JoiningTime"] as? String ?: "",
                totalPlayed = (data["TotalPlayed"] as? Long)?.toInt() ?: 0,
                totalWon = (data["TotalWon"] as? Long)?.toInt() ?: 0
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getCachedUserData(): UserData? {
        val userId = getCurrentUserId()
        return UserData(
            userId = userId,
            userName = prefs.getString("user_name", "") ?: "",
            email = prefs.getString("user_email", "") ?: "",
            level = prefs.getInt("user_level", 0),
            kycStatus = prefs.getString("user_kyc_status", "pending") ?: "pending",
            totalCoins = prefs.getInt("user_total_coins", 0),
            winningCoins = prefs.getInt("user_winning_coins", 0),
            topUpCoins = prefs.getInt("user_topup_coins", 0),
            accountStatus = prefs.getString("user_account_status", "Active") ?: "Active",
            withdrawalCount = prefs.getInt("user_withdrawal_count", 0),
            referralBonus = prefs.getInt("user_referral_bonus", 0),
            freeFireVerified = prefs.getBoolean("user_freefire_verified", false),
            inGameUID = prefs.getLong("user_ingame_uid", 0),
            phone = prefs.getString("user_phone", "") ?: "",
            joiningDate = prefs.getString("user_joining_date", "") ?: "",
            joiningTime = prefs.getString("user_joining_time", "") ?: "",
            totalPlayed = prefs.getInt("user_total_played", 0),
            totalWon = prefs.getInt("user_total_won", 0)
        )
    }

    fun updateCachedUserData(data: UserData) {
        prefs.edit()
            .putString("user_name", data.userName)
            .putString("user_email", data.email)
            .putInt("user_level", data.level)
            .putString("user_kyc_status", data.kycStatus)
            .putInt("user_total_coins", data.totalCoins)
            .putInt("user_winning_coins", data.winningCoins)
            .putInt("user_topup_coins", data.topUpCoins)
            .putString("user_account_status", data.accountStatus)
            .putInt("user_withdrawal_count", data.withdrawalCount)
            .putInt("user_referral_bonus", data.referralBonus)
            .putBoolean("user_freefire_verified", data.freeFireVerified)
            .putLong("user_ingame_uid", data.inGameUID)
            .putString("user_phone", data.phone)
            .putString("user_joining_date", data.joiningDate)
            .putString("user_joining_time", data.joiningTime)
            .putInt("user_total_played", data.totalPlayed)
            .putInt("user_total_won", data.totalWon)
            .apply()
    }
}