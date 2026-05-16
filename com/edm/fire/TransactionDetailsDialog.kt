package com.edm.fire

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.card.MaterialCardView

class TransactionDetailsDialog : DialogFragment() {

    private lateinit var transaction: TransactionModel
    private lateinit var dialogView: View

    companion object {
        private const val ARG_TRANSACTION = "transaction"

        fun newInstance(transaction: TransactionModel): TransactionDetailsDialog {
            val args = Bundle()
            args.putParcelable(ARG_TRANSACTION, transaction)
            val fragment = TransactionDetailsDialog()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        transaction = arguments?.getParcelable(ARG_TRANSACTION)
            ?: throw IllegalStateException("Transaction must be provided")

        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        dialogView = inflater.inflate(R.layout.dialog_transaction_details, null)

        builder.setView(dialogView)
        val dialog = builder.create()

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            requestFeature(Window.FEATURE_NO_TITLE)
        }

        return dialog
    }

    override fun onStart() {
        super.onStart()

        initializeViews()
        bindTransactionData()

        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )
    }

    private fun initializeViews() {
        dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dismiss()
        }
    }

    private fun bindTransactionData() {
        bindCommonData()

        when (transaction.transactionType) {
            "Deposit" -> bindDepositData()
            "Tournament Joining" -> bindTournamentJoiningData()
            "Tournament Winnings" -> bindTournamentWinningsData()
            "Tournament Refund" -> bindTournamentRefundData()
            "Withdrawal Request" -> bindWithdrawalData()
            "Referral Bonus", "Signup Bonus" -> bindBonusData()
            else -> {
                // Legacy support
                when {
                    transaction.utr.isNotEmpty() -> bindDepositData()
                    transaction.entryFee > 0 -> bindTournamentJoiningData()
                    transaction.rank > 0 && transaction.refundPercent == 0 -> bindTournamentWinningsData()
                    transaction.refundPercent > 0 -> bindTournamentRefundData()
                    transaction.bankAddress.isNotEmpty() -> bindWithdrawalData()
                    else -> bindBonusData()
                }
            }
        }

        hideAllTypeCards()
        showRelevantTypeCard()
    }

    private fun bindCommonData() {
        try {
            // Header Section
            dialogView.findViewById<ImageView>(R.id.ivDetailIcon).apply {
                setImageResource(transaction.typeIcon)
                setColorFilter(ContextCompat.getColor(requireContext(), transaction.typeColor))
            }

            dialogView.findViewById<TextView>(R.id.tvDetailType).apply {
                text = transaction.displayType
                setTextColor(ContextCompat.getColor(requireContext(), transaction.typeColor))
            }

            dialogView.findViewById<TextView>(R.id.tvDetailStatus).apply {
                text = transaction.displayStatus
                setBackgroundColor(ContextCompat.getColor(requireContext(), transaction.statusColor))
            }

            dialogView.findViewById<TextView>(R.id.tvDetailAmount).apply {
                text = transaction.displayAmount
                setTextColor(ContextCompat.getColor(requireContext(), transaction.amountColor))
            }

            // Basic Information Section
            val transactionIdText = when {
                transaction.transactionId.isNotEmpty() -> transaction.transactionId
                transaction.id.isNotEmpty() -> transaction.id
                else -> "N/A"
            }
            dialogView.findViewById<TextView>(R.id.tvDetailTransactionId).text = transactionIdText

            dialogView.findViewById<TextView>(R.id.tvDetailTimestamp).text =
                if (transaction.timestamp.isNotEmpty()) transaction.timestamp else "N/A"

            // Processed At (new field)
            val rowProcessedAt = dialogView.findViewById<LinearLayout>(R.id.rowProcessedAt)
            val tvProcessedAt = dialogView.findViewById<TextView>(R.id.tvProcessedAt)
            if (transaction.processedAt != null) {
                rowProcessedAt.visibility = View.VISIBLE
                val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                tvProcessedAt.text = sdf.format(transaction.processedAt!!.toDate())
            } else {
                rowProcessedAt.visibility = View.GONE
            }

            // Document ID (legacy)
            val rowDocumentId = dialogView.findViewById<LinearLayout>(R.id.rowDocumentId)
            val tvDetailDocumentId = dialogView.findViewById<TextView>(R.id.tvDetailDocumentId)
            if (transaction.documentId.isNotEmpty()) {
                rowDocumentId.visibility = View.VISIBLE
                tvDetailDocumentId.text = transaction.documentId
            } else {
                rowDocumentId.visibility = View.GONE
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== DEPOSIT DETAILS ====================

    private fun bindDepositData() {
        try {
            val cardDeposit = dialogView.findViewById<MaterialCardView>(R.id.cardDepositDetails)
            cardDeposit.visibility = View.VISIBLE

            // UTR Number
            dialogView.findViewById<TextView>(R.id.tvDetailUtr).text =
                if (transaction.utr.isNotEmpty()) transaction.utr else "N/A"

            // Amount in PAISA to RUPEES
            val amountInRupees = transaction.amount / 100.0
            val amountText = if (amountInRupees % 1 == 0.0)
                "₹${amountInRupees.toInt()}"
            else
                "₹$amountInRupees"
            dialogView.findViewById<TextView>(R.id.tvDetailDepositAmount).text = amountText

            // Bonus Coins (NEW)
            val rowBonusCoins = dialogView.findViewById<LinearLayout>(R.id.rowBonusCoins)
            val tvBonusCoins = dialogView.findViewById<TextView>(R.id.tvBonusCoins)
            if (transaction.bonusCoins > 0) {
                rowBonusCoins.visibility = View.VISIBLE
                val bonusInRupees = transaction.bonusCoins / 100.0
                val bonusText = if (bonusInRupees % 1 == 0.0)
                    "${bonusInRupees.toInt()} Coins"
                else
                    "$bonusInRupees Coins"
                tvBonusCoins.text = bonusText
            } else {
                rowBonusCoins.visibility = View.GONE
            }

            // Total Coins (NEW)
            val rowTotalCoins = dialogView.findViewById<LinearLayout>(R.id.rowTotalCoins)
            val tvTotalCoins = dialogView.findViewById<TextView>(R.id.tvTotalCoins)
            if (transaction.totalCoins > 0) {
                rowTotalCoins.visibility = View.VISIBLE
                val totalInRupees = transaction.totalCoins / 100.0
                val totalText = if (totalInRupees % 1 == 0.0)
                    "${totalInRupees.toInt()} Coins"
                else
                    "$totalInRupees Coins"
                tvTotalCoins.text = totalText
            } else {
                rowTotalCoins.visibility = View.GONE
            }

            // Payer Information
            val payerInfo = buildString {
                if (transaction.payerName.isNotEmpty()) append(transaction.payerName)
                if (transaction.payerHandle.isNotEmpty()) {
                    if (isNotEmpty()) append(" ")
                    append("(${transaction.payerHandle})")
                }
                if (isEmpty()) append("N/A")
            }
            dialogView.findViewById<TextView>(R.id.tvDetailPayerInfo).text = payerInfo

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== TOURNAMENT JOINING DETAILS ====================

    private fun bindTournamentJoiningData() {
        try {
            val cardTournament = dialogView.findViewById<MaterialCardView>(R.id.cardTournamentDetails)
            cardTournament.visibility = View.VISIBLE

            // Change title for joining
            dialogView.findViewById<TextView>(R.id.tvTournamentSectionTitle).text = "Tournament Joining Details"

            // Tournament Info
            val tournamentInfo = buildString {
                if (transaction.tournamentType.isNotEmpty()) append("Type: ${transaction.tournamentType}")
                if (transaction.tournamentId.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("ID: ${transaction.tournamentId}")
                }
                if (transaction.slotNumber > 0) {
                    if (isNotEmpty()) append("\n")
                    append("Slot: ${transaction.slotNumber}")
                }
                if (isEmpty()) append("N/A")
            }
            dialogView.findViewById<TextView>(R.id.tvDetailTournamentInfo).text = tournamentInfo

            // Entry Fee
            val entryFeeInRupees = transaction.entryFee / 100.0
            val entryFeeText = if (entryFeeInRupees % 1 == 0.0)
                "${entryFeeInRupees.toInt()} Coins"
            else
                "$entryFeeInRupees Coins"
            dialogView.findViewById<TextView>(R.id.tvDetailEntryFee).text = entryFeeText

            // Description
            dialogView.findViewById<TextView>(R.id.tvDetailDescription).text =
                if (transaction.description.isNotEmpty()) transaction.description else "N/A"

            // Referral Bonus Used
            val referralText = if (transaction.referralBonusUsed > 0)
                "${transaction.referralBonusUsed} Coins"
            else
                "0 Coins"
            dialogView.findViewById<TextView>(R.id.tvDetailReferralBonus).text = referralText

            // Hide winnings-specific rows
            dialogView.findViewById<LinearLayout>(R.id.rowRank).visibility = View.GONE
            dialogView.findViewById<LinearLayout>(R.id.rowResult).visibility = View.GONE
            dialogView.findViewById<LinearLayout>(R.id.rowRefundPercent).visibility = View.GONE

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== TOURNAMENT WINNINGS DETAILS ====================

    private fun bindTournamentWinningsData() {
        try {
            val cardTournament = dialogView.findViewById<MaterialCardView>(R.id.cardTournamentDetails)
            cardTournament.visibility = View.VISIBLE

            // Change title for winnings
            dialogView.findViewById<TextView>(R.id.tvTournamentSectionTitle).text = "Tournament Winnings Details"

            // Tournament Info
            val tournamentInfo = buildString {
                if (transaction.tournamentType.isNotEmpty()) append("Type: ${transaction.tournamentType}")
                if (transaction.tournamentId.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("ID: ${transaction.tournamentId}")
                }
                if (isEmpty()) append("N/A")
            }
            dialogView.findViewById<TextView>(R.id.tvDetailTournamentInfo).text = tournamentInfo

            // Winning Amount
            val winningInRupees = transaction.amount / 100.0
            val winningText = if (winningInRupees % 1 == 0.0)
                "${winningInRupees.toInt()} Coins"
            else
                "$winningInRupees Coins"
            dialogView.findViewById<TextView>(R.id.tvDetailWinningAmount).text = winningText

            // Description
            dialogView.findViewById<TextView>(R.id.tvDetailDescription).text =
                if (transaction.description.isNotEmpty()) transaction.description else "N/A"

            // Rank
            val rowRank = dialogView.findViewById<LinearLayout>(R.id.rowRank)
            val tvDetailRank = dialogView.findViewById<TextView>(R.id.tvDetailRank)
            if (transaction.rank > 0) {
                rowRank.visibility = View.VISIBLE
                tvDetailRank.text = "${transaction.rank}"
            } else {
                rowRank.visibility = View.GONE
            }

            // Result with dynamic color
            val rowResult = dialogView.findViewById<LinearLayout>(R.id.rowResult)
            val tvDetailResult = dialogView.findViewById<TextView>(R.id.tvDetailResult)
            if (transaction.result.isNotEmpty()) {
                rowResult.visibility = View.VISIBLE
                tvDetailResult.text = transaction.result
                val resultColor = when (transaction.result.lowercase()) {
                    "winner" -> R.color.status_success
                    "loss" -> R.color.status_failed
                    else -> R.color.status_default
                }
                tvDetailResult.setTextColor(ContextCompat.getColor(requireContext(), resultColor))
            } else {
                rowResult.visibility = View.GONE
            }

            // Hide joining-specific rows
            dialogView.findViewById<LinearLayout>(R.id.rowEntryFee).visibility = View.GONE
            dialogView.findViewById<LinearLayout>(R.id.rowReferralBonus).visibility = View.GONE
            dialogView.findViewById<LinearLayout>(R.id.rowRefundPercent).visibility = View.GONE

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== TOURNAMENT REFUND DETAILS ====================

    private fun bindTournamentRefundData() {
        try {
            val cardTournament = dialogView.findViewById<MaterialCardView>(R.id.cardTournamentDetails)
            cardTournament.visibility = View.VISIBLE

            // Change title for refund
            dialogView.findViewById<TextView>(R.id.tvTournamentSectionTitle).text = "Tournament Refund Details"

            // Tournament ID
            val tournamentInfo = buildString {
                if (transaction.tournamentId.isNotEmpty()) append("Tournament ID: ${transaction.tournamentId}")
                if (isEmpty()) append("N/A")
            }
            dialogView.findViewById<TextView>(R.id.tvDetailTournamentInfo).text = tournamentInfo

            // Refund Amount
            val refundInRupees = transaction.amount / 100.0
            val refundText = if (refundInRupees % 1 == 0.0)
                "${refundInRupees.toInt()} Coins"
            else
                "$refundInRupees Coins"
            dialogView.findViewById<TextView>(R.id.tvDetailRefundAmount).text = refundText

            // Refund Percentage
            val rowRefundPercent = dialogView.findViewById<LinearLayout>(R.id.rowRefundPercent)
            val tvRefundPercent = dialogView.findViewById<TextView>(R.id.tvDetailRefundPercent)
            if (transaction.refundPercent > 0) {
                rowRefundPercent.visibility = View.VISIBLE
                tvRefundPercent.text = "${transaction.refundPercent}%"
            } else {
                rowRefundPercent.visibility = View.GONE
            }

            // Description
            dialogView.findViewById<TextView>(R.id.tvDetailDescription).text =
                if (transaction.description.isNotEmpty()) transaction.description else "N/A"

            // Hide other rows
            dialogView.findViewById<LinearLayout>(R.id.rowEntryFee).visibility = View.GONE
            dialogView.findViewById<LinearLayout>(R.id.rowWinningAmount).visibility = View.GONE
            dialogView.findViewById<LinearLayout>(R.id.rowRank).visibility = View.GONE
            dialogView.findViewById<LinearLayout>(R.id.rowResult).visibility = View.GONE
            dialogView.findViewById<LinearLayout>(R.id.rowReferralBonus).visibility = View.GONE

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== WITHDRAWAL DETAILS ====================

    private fun bindWithdrawalData() {
        try {
            val cardWithdrawal = dialogView.findViewById<MaterialCardView>(R.id.cardWithdrawalDetails)
            cardWithdrawal.visibility = View.VISIBLE

            // Transaction ID
            dialogView.findViewById<TextView>(R.id.tvDetailWithdrawalId).text =
                if (transaction.transactionId.isNotEmpty()) transaction.transactionId else "N/A"

            // Payment Method
            dialogView.findViewById<TextView>(R.id.tvDetailPaymentMethod).text =
                if (transaction.paymentMethod.isNotEmpty()) transaction.paymentMethod else "N/A"

            // Amount
            val amountInRupees = transaction.amount / 100.0
            val amountText = if (amountInRupees % 1 == 0.0)
                "₹${amountInRupees.toInt()}"
            else
                "₹$amountInRupees"
            dialogView.findViewById<TextView>(R.id.tvDetailWithdrawalAmount).apply {
                text = amountText
                setTextColor(ContextCompat.getColor(requireContext(), transaction.amountColor))
            }

            // Bank Address / UPI
            dialogView.findViewById<TextView>(R.id.tvDetailWithdrawalDetails).text =
                if (transaction.bankAddress.isNotEmpty()) transaction.bankAddress else "N/A"

            // Notes
            val tvNotes = dialogView.findViewById<TextView>(R.id.tvDetailNotes)
            if (transaction.notes.isNotEmpty()) {
                tvNotes.visibility = View.VISIBLE
                tvNotes.text = transaction.notes
                val noteColor = when (transaction.paymentStatus.lowercase()) {
                    "pending" -> R.color.status_pending
                    "rejected", "refunded" -> R.color.status_failed
                    else -> R.color.status_default
                }
                tvNotes.setTextColor(ContextCompat.getColor(requireContext(), noteColor))
            } else {
                tvNotes.visibility = View.GONE
            }

            // Created At (legacy)
            val createdAtText = transaction.createdAt?.let { timestamp ->
                java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                    .format(timestamp.toDate())
            } ?: "N/A"
            dialogView.findViewById<TextView>(R.id.tvDetailCreatedAt).text = createdAtText

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== BONUS DETAILS (Referral/Signup) ====================

    private fun bindBonusData() {
        try {
            val cardReferral = dialogView.findViewById<MaterialCardView>(R.id.cardReferralDetails)
            cardReferral.visibility = View.VISIBLE

            // Bonus Type
            val bonusType = when (transaction.transactionType) {
                "Signup Bonus" -> "Signup Bonus"
                else -> "Referral Bonus"
            }
            dialogView.findViewById<TextView>(R.id.tvDetailReferralType).text = bonusType

            // Description
            dialogView.findViewById<TextView>(R.id.tvDetailReferralDescription).text =
                if (transaction.description.isNotEmpty()) transaction.description else "N/A"

            // Bonus Amount
            val bonusInRupees = transaction.amount / 100.0
            val bonusText = if (bonusInRupees % 1 == 0.0)
                "${bonusInRupees.toInt()} Coins"
            else
                "$bonusInRupees Coins"
            dialogView.findViewById<TextView>(R.id.tvDetailReferralCoins).apply {
                text = bonusText
                setTextColor(ContextCompat.getColor(requireContext(), transaction.amountColor))
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideAllTypeCards() {
        try {
            dialogView.findViewById<MaterialCardView>(R.id.cardDepositDetails).visibility = View.GONE
            dialogView.findViewById<MaterialCardView>(R.id.cardTournamentDetails).visibility = View.GONE
            dialogView.findViewById<MaterialCardView>(R.id.cardWithdrawalDetails).visibility = View.GONE
            dialogView.findViewById<MaterialCardView>(R.id.cardReferralDetails).visibility = View.GONE
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showRelevantTypeCard() {
        try {
            when (transaction.transactionType) {
                "Deposit" -> dialogView.findViewById<MaterialCardView>(R.id.cardDepositDetails).visibility = View.VISIBLE
                "Tournament Joining", "Tournament Winnings", "Tournament Refund" ->
                    dialogView.findViewById<MaterialCardView>(R.id.cardTournamentDetails).visibility = View.VISIBLE
                "Withdrawal Request" -> dialogView.findViewById<MaterialCardView>(R.id.cardWithdrawalDetails).visibility = View.VISIBLE
                "Referral Bonus", "Signup Bonus" ->
                    dialogView.findViewById<MaterialCardView>(R.id.cardReferralDetails).visibility = View.VISIBLE
                else -> {
                    // Legacy fallback
                    when {
                        transaction.utr.isNotEmpty() -> dialogView.findViewById<MaterialCardView>(R.id.cardDepositDetails).visibility = View.VISIBLE
                        transaction.entryFee > 0 || transaction.rank > 0 || transaction.refundPercent > 0 ->
                            dialogView.findViewById<MaterialCardView>(R.id.cardTournamentDetails).visibility = View.VISIBLE
                        transaction.bankAddress.isNotEmpty() -> dialogView.findViewById<MaterialCardView>(R.id.cardWithdrawalDetails).visibility = View.VISIBLE
                        else -> dialogView.findViewById<MaterialCardView>(R.id.cardReferralDetails).visibility = View.VISIBLE
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}