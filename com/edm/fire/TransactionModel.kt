package com.edm.fire

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.parcelize.Parcelize
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@Parcelize
data class TransactionModel(
    // ==================== COMMON FIELDS ====================
    val id: String = "",
    val transactionType: String = "",        // "Deposit", "Tournament Joining", "Withdrawal Request", "Tournament Refund", "Tournament Winnings", "Referral Bonus", "Signup Bonus"
    val displayType: String = "",
    val amount: Long = 0,                    // PAISA (Integer)
    val paymentStatus: String = "",          // "completed", "pending", "refunded", "rejected"
    val timestamp: String = "",              // "09 May 2026, 10:30 AM" (IST format)
    val processedAt: Timestamp? = null,      // Firestore server timestamp
    val iconRes: Int = R.drawable.ic_transaction_default,
    val colorRes: Int = R.color.transaction_default,

    // ==================== DEPOSIT SPECIFIC FIELDS ====================
    val utr: String = "",
    val bonusCoins: Long = 0,                // PAISA
    val totalCoins: Long = 0,                // PAISA
    val payerName: String = "",
    val payerHandle: String = "",

    // ==================== TOURNAMENT JOINING SPECIFIC FIELDS ====================
    val entryFee: Long = 0,                  // PAISA
    val tournamentType: String = "",
    val tournamentId: String = "",
    val slotNumber: Int = 0,
    val referralBonusUsed: Int = 0,
    val description: String = "",

    // ==================== TOURNAMENT WINNINGS SPECIFIC FIELDS ====================
    val rank: Int = 0,
    val result: String = "",

    // ==================== TOURNAMENT REFUND SPECIFIC FIELDS ====================
    val refundPercent: Int = 0,

    // ==================== WITHDRAWAL SPECIFIC FIELDS ====================
    val paymentMethod: String = "",
    val bankAddress: String = "",
    val notes: String = "",

    // ==================== LEGACY / BACKWARD COMPATIBILITY ====================
    val createdAt: Timestamp? = null,
    val transactionId: String = "",
    val documentId: String = ""
) : Parcelable {

    private val decimalFormat = DecimalFormat("#.##")

    // Convert PAISA to RUPEES (Bank Method)
    private fun paisaToRupees(paisa: Long): Double = paisa / 100.0

    // ==================== DISPLAY AMOUNT ====================

    val displayAmount: String
        get() = when (transactionType) {
            "Deposit" -> {
                val rupees = paisaToRupees(amount)
                val formatted = if (rupees % 1 == 0.0) "₹${rupees.toInt()}" else "₹${decimalFormat.format(rupees)}"
                "+$formatted"
            }
            "Withdrawal Request" -> {
                val rupees = paisaToRupees(amount)
                val formatted = if (rupees % 1 == 0.0) "₹${rupees.toInt()}" else "₹${decimalFormat.format(rupees)}"
                "-$formatted"
            }
            "Tournament Joining" -> {
                val rupees = paisaToRupees(entryFee)
                val formatted = if (rupees % 1 == 0.0) rupees.toInt().toString() else decimalFormat.format(rupees)
                "-$formatted Coins"
            }
            "Tournament Winnings" -> {
                val rupees = paisaToRupees(amount)
                val formatted = if (rupees % 1 == 0.0) rupees.toInt().toString() else decimalFormat.format(rupees)
                "+$formatted Coins"
            }
            "Tournament Refund" -> {
                val rupees = paisaToRupees(amount)
                val formatted = if (rupees % 1 == 0.0) rupees.toInt().toString() else decimalFormat.format(rupees)
                "+$formatted Coins"
            }
            "Referral Bonus", "Signup Bonus" -> {
                val rupees = paisaToRupees(amount)
                val formatted = if (rupees % 1 == 0.0) rupees.toInt().toString() else decimalFormat.format(rupees)
                "+$formatted Coins"
            }
            else -> ""
        }

    val amountColor: Int
        get() = when (transactionType) {
            "Deposit", "Tournament Winnings", "Tournament Refund", "Referral Bonus", "Signup Bonus" -> R.color.amount_positive
            "Withdrawal Request", "Tournament Joining" -> R.color.amount_negative
            else -> R.color.transaction_default
        }

    val statusColor: Int
        get() = when (paymentStatus.lowercase()) {
            "completed" -> R.color.status_success
            "pending" -> R.color.status_pending
            "refunded", "rejected" -> R.color.status_failed
            else -> R.color.status_default
        }

    val typeIcon: Int
        get() = when (transactionType) {
            "Deposit" -> R.drawable.ic_deposit
            "Withdrawal Request" -> R.drawable.ic_withdrawal
            "Tournament Joining", "Tournament Winnings", "Tournament Refund" -> R.drawable.ic_tournament
            "Referral Bonus", "Signup Bonus" -> R.drawable.ic_referral
            else -> R.drawable.ic_transaction_default
        }

    val typeColor: Int
        get() = when (transactionType) {
            "Deposit" -> R.color.type_deposit
            "Withdrawal Request" -> R.color.type_withdrawal
            "Tournament Joining", "Tournament Winnings", "Tournament Refund" -> R.color.type_tournament
            "Referral Bonus", "Signup Bonus" -> R.color.type_referral
            else -> R.color.type_default
        }

    val displayStatus: String
        get() = paymentStatus.uppercase()

    val shortTimestamp: String
        get() = timestamp

    companion object {

        // Map Deposit Transaction (NEW STRUCTURE)
        fun mapDepositTransaction(doc: DocumentSnapshot): TransactionModel {
            val data = doc.data ?: return TransactionModel()

            return TransactionModel(
                id = doc.id,
                transactionType = "Deposit",
                displayType = "Deposit",
                amount = (data["amount"] as? Long) ?: (data["amountInPaisa"] as? Long) ?: 0,
                paymentStatus = (data["paymentStatus"] as? String) ?: (data["status"] as? String) ?: "completed",
                timestamp = (data["timestamp"] as? String) ?: "",
                processedAt = data["processedAt"] as? Timestamp,
                utr = (data["utr"] as? String) ?: "",
                bonusCoins = (data["bonusCoins"] as? Long) ?: 0,
                totalCoins = (data["totalCoins"] as? Long) ?: 0,
                payerName = (data["payerName"] as? String) ?: "",
                payerHandle = (data["payerHandle"] as? String) ?: "",
                transactionId = (data["transactionId"] as? String) ?: doc.id
            )
        }

        // Map Tournament Joining Transaction
        fun mapTournamentJoiningTransaction(doc: DocumentSnapshot): TransactionModel {
            val data = doc.data ?: return TransactionModel()

            return TransactionModel(
                id = doc.id,
                transactionType = "Tournament Joining",
                displayType = "Tournament Joining",
                entryFee = (data["entryFee"] as? Long) ?: (data["coin"] as? Long) ?: 0,
                paymentStatus = (data["paymentStatus"] as? String) ?: (data["status"] as? String) ?: "completed",
                timestamp = (data["timestamp"] as? String) ?: "",
                processedAt = data["processedAt"] as? Timestamp,
                tournamentType = (data["tournamentType"] as? String) ?: "",
                tournamentId = (data["tournamentId"] as? String) ?: "",
                slotNumber = (data["slotNumber"] as? Int) ?: 0,
                referralBonusUsed = (data["referralBonusUsed"] as? Int) ?: 0,
                description = (data["description"] as? String) ?: "",
                transactionId = (data["transactionId"] as? String) ?: doc.id
            )
        }

        // Map Tournament Winnings Transaction
        fun mapTournamentWinningsTransaction(doc: DocumentSnapshot): TransactionModel {
            val data = doc.data ?: return TransactionModel()

            // Try new field "amount" first, then legacy "coin"
            val amountValue = (data["amount"] as? Long) ?: (data["coin"] as? Long) ?: 0

            return TransactionModel(
                id = doc.id,
                transactionType = "Tournament Winnings",
                displayType = "Tournament Winnings",
                amount = amountValue,
                paymentStatus = (data["paymentStatus"] as? String) ?: (data["status"] as? String) ?: "completed",
                timestamp = (data["timestamp"] as? String) ?: "",
                processedAt = data["processedAt"] as? Timestamp,
                description = (data["description"] as? String) ?: "",
                tournamentType = (data["tournamentType"] as? String) ?: "",
                tournamentId = (data["tournamentId"] as? String) ?: "",
                rank = (data["rank"] as? Int) ?: 0,
                result = (data["result"] as? String) ?: "",
                transactionId = (data["transactionId"] as? String) ?: doc.id
            )
        }

        // Map Tournament Refund Transaction
        fun mapTournamentRefundTransaction(doc: DocumentSnapshot): TransactionModel {
            val data = doc.data ?: return TransactionModel()

            return TransactionModel(
                id = doc.id,
                transactionType = "Tournament Refund",
                displayType = "Tournament Refund",
                amount = (data["amount"] as? Long) ?: 0,
                paymentStatus = (data["paymentStatus"] as? String) ?: "completed",
                timestamp = (data["timestamp"] as? String) ?: "",
                processedAt = data["processedAt"] as? Timestamp,
                description = (data["description"] as? String) ?: "",
                tournamentId = (data["tournamentId"] as? String) ?: "",
                refundPercent = (data["refundPercent"] as? Int) ?: 0,
                transactionId = (data["transactionId"] as? String) ?: doc.id
            )
        }

        // Map Withdrawal Transaction
        fun mapWithdrawalTransaction(doc: DocumentSnapshot): TransactionModel {
            val data = doc.data ?: return TransactionModel()

            return TransactionModel(
                id = doc.id,
                transactionType = "Withdrawal Request",
                displayType = "Withdrawal Request",
                amount = (data["amount"] as? Long) ?: 0,
                paymentStatus = (data["paymentStatus"] as? String) ?: (data["status"] as? String) ?: "pending",
                timestamp = (data["timestamp"] as? String) ?: "",
                processedAt = data["processedAt"] as? Timestamp,
                paymentMethod = (data["paymentMethod"] as? String) ?: "",
                bankAddress = (data["bankAddress"] as? String) ?: "",
                notes = (data["notes"] as? String) ?: "",
                transactionId = (data["transactionId"] as? String) ?: doc.id,
                createdAt = data["createdAt"] as? Timestamp
            )
        }

        // Map Referral/Signup Bonus Transaction
        fun mapBonusTransaction(doc: DocumentSnapshot): TransactionModel {
            val data = doc.data ?: return TransactionModel()

            val transactionType = (data["transactionType"] as? String) ?: "Referral Bonus"
            val amountValue = (data["amount"] as? Long) ?: (data["coin"] as? Long) ?: 0

            return TransactionModel(
                id = doc.id,
                transactionType = transactionType,
                displayType = if (transactionType == "Signup Bonus") "Signup Bonus" else "Referral Bonus",
                amount = amountValue,
                paymentStatus = (data["paymentStatus"] as? String) ?: (data["status"] as? String) ?: "completed",
                timestamp = (data["timestamp"] as? String) ?: "",
                processedAt = data["processedAt"] as? Timestamp,
                description = (data["description"] as? String) ?: "",
                transactionId = (data["transactionId"] as? String) ?: doc.id,
                createdAt = data["createdAt"] as? Timestamp
            )
        }

        // Sort transactions by timestamp (newest first)
        fun sortTransactionsByDate(transactions: List<TransactionModel>): List<TransactionModel> {
            return transactions.sortedByDescending { it.timestamp }
        }
    }
}