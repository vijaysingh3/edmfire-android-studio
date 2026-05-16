package com.edm.fire

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class TransactionRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val COLLECTION_USERS = "Users"
        private const val SUBCOLLECTION_TRANSACTION_HISTORY = "TransactionHistory"
        private const val PAGE_SIZE = 50L
    }

    suspend fun fetchAllTransactions(): List<TransactionModel> {
        val currentUser = auth.currentUser ?: throw Exception("User not authenticated")
        val userId = currentUser.uid

        try {
            val querySnapshot = db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_TRANSACTION_HISTORY)
                .orderBy("processedAt", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE)
                .get()
                .await()

            val transactions = querySnapshot.documents.mapNotNull { document ->
                identifyTransactionType(document)
            }

            return transactions
        } catch (e: Exception) {
            throw Exception("Failed to fetch transactions: ${e.message}")
        }
    }

    private fun identifyTransactionType(document: com.google.firebase.firestore.DocumentSnapshot): TransactionModel? {
        val data = document.data ?: return null

        val transactionType = (data["transactionType"] as? String) ?: return null

        return when (transactionType) {
            "Deposit" -> TransactionModel.mapDepositTransaction(document)
            "Tournament Joining" -> TransactionModel.mapTournamentJoiningTransaction(document)
            "Tournament Winnings" -> TransactionModel.mapTournamentWinningsTransaction(document)
            "Tournament Refund" -> TransactionModel.mapTournamentRefundTransaction(document)
            "Withdrawal Request" -> TransactionModel.mapWithdrawalTransaction(document)
            "Referral Bonus", "Signup Bonus" -> TransactionModel.mapBonusTransaction(document)
            else -> {
                // Legacy transaction type detection
                when {
                    data.containsKey("utr") || data.containsKey("amountInPaisa") -> TransactionModel.mapDepositTransaction(document)
                    data.containsKey("entryFee") -> TransactionModel.mapTournamentJoiningTransaction(document)
                    data.containsKey("rank") && !data.containsKey("refundPercent") -> TransactionModel.mapTournamentWinningsTransaction(document)
                    data.containsKey("refundPercent") -> TransactionModel.mapTournamentRefundTransaction(document)
                    data.containsKey("bankAddress") || data.containsKey("paymentMethod") -> TransactionModel.mapWithdrawalTransaction(document)
                    data.containsKey("coin") && (data["type"] == "Referral Bonus" || data["transactionType"] == "referral_bonus") -> TransactionModel.mapBonusTransaction(document)
                    else -> null
                }
            }
        }
    }
}