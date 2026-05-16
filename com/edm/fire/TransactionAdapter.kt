package com.edm.fire

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class TransactionAdapter : ListAdapter<TransactionModel, TransactionAdapter.TransactionViewHolder>(
    TransactionDiffCallback()
) {

    interface OnTransactionClickListener {
        fun onTransactionClick(transaction: TransactionModel)
        fun onTransactionLongClick(transaction: TransactionModel): Boolean
    }

    private var clickListener: OnTransactionClickListener? = null

    fun setOnTransactionClickListener(listener: OnTransactionClickListener) {
        this.clickListener = listener
    }

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Header Views
        private val ivTransactionIcon: ImageView = itemView.findViewById(R.id.ivTransactionIcon)
        private val tvTransactionType: TextView = itemView.findViewById(R.id.tvTransactionType)
        private val tvTransactionStatus: TextView = itemView.findViewById(R.id.tvTransactionStatus)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)

        // Content Section Views
        private val contentSection: LinearLayout = itemView.findViewById(R.id.contentSection)
        private val row1: LinearLayout = itemView.findViewById(R.id.row1)
        private val row2: LinearLayout = itemView.findViewById(R.id.row2)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val rowTransactionId: LinearLayout = itemView.findViewById(R.id.rowTransactionId)
        private val tvNotes: TextView = itemView.findViewById(R.id.tvNotes)

        // Dynamic content views
        private val tvUtr: TextView = itemView.findViewById(R.id.tvUtr)
        private val tvPayerInfo: TextView = itemView.findViewById(R.id.tvPayerInfo)
        private val tvTransactionId: TextView = itemView.findViewById(R.id.tvTransactionId)

        // Footer Views
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvAdditionalInfo: TextView = itemView.findViewById(R.id.tvAdditionalInfo)

        fun bind(transaction: TransactionModel, clickListener: OnTransactionClickListener?) {
            bindCommonData(transaction)

            when (transaction.transactionType) {
                "Deposit" -> bindDepositView(transaction)
                "Withdrawal Request" -> bindWithdrawalView(transaction)
                "Tournament Joining" -> bindTournamentJoiningView(transaction)
                "Tournament Winnings" -> bindTournamentWinningsView(transaction)
                "Tournament Refund" -> bindTournamentRefundView(transaction)
                "Referral Bonus", "Signup Bonus" -> bindBonusView(transaction)
                else -> hideAllContentViews()
            }

            itemView.setOnClickListener {
                clickListener?.onTransactionClick(transaction)
            }

            itemView.setOnLongClickListener {
                clickListener?.onTransactionLongClick(transaction) ?: false
            }
        }

        private fun bindCommonData(transaction: TransactionModel) {
            ivTransactionIcon.setImageResource(transaction.typeIcon)
            ivTransactionIcon.setColorFilter(
                ContextCompat.getColor(itemView.context, transaction.typeColor)
            )

            tvTransactionType.text = transaction.displayType
            tvTransactionType.setTextColor(
                ContextCompat.getColor(itemView.context, transaction.typeColor)
            )

            tvTransactionStatus.text = transaction.displayStatus
            tvTransactionStatus.setBackgroundColor(
                ContextCompat.getColor(itemView.context, transaction.statusColor)
            )

            tvAmount.text = transaction.displayAmount
            tvAmount.setTextColor(
                ContextCompat.getColor(itemView.context, transaction.amountColor)
            )

            tvTimestamp.text = transaction.timestamp
        }

        private fun bindDepositView(transaction: TransactionModel) {
            contentSection.visibility = View.VISIBLE

            if (transaction.utr.isNotEmpty()) {
                row1.visibility = View.VISIBLE
                tvUtr.text = transaction.utr
            } else {
                row1.visibility = View.GONE
            }

            if (transaction.payerName.isNotEmpty() || transaction.payerHandle.isNotEmpty()) {
                row2.visibility = View.VISIBLE
                val payerInfo = buildString {
                    if (transaction.payerName.isNotEmpty()) append(transaction.payerName)
                    if (transaction.payerHandle.isNotEmpty()) {
                        if (isNotEmpty()) append(" ")
                        append("(${transaction.payerHandle})")
                    }
                }
                tvPayerInfo.text = payerInfo
            } else {
                row2.visibility = View.GONE
            }

            // Show bonus info if any
            if (transaction.bonusCoins > 0) {
                tvAdditionalInfo.visibility = View.VISIBLE
                val bonusRupees = transaction.bonusCoins / 100.0
                val bonusText = if (bonusRupees % 1 == 0.0) "${bonusRupees.toInt()}" else "$bonusRupees"
                tvAdditionalInfo.text = "Bonus: +$bonusText Coins"
                tvAdditionalInfo.setTextColor(ContextCompat.getColor(itemView.context, R.color.type_referral))
            } else {
                tvAdditionalInfo.visibility = View.GONE
            }

            tvDescription.visibility = View.GONE
            rowTransactionId.visibility = View.GONE
            tvNotes.visibility = View.GONE
        }

        private fun bindWithdrawalView(transaction: TransactionModel) {
            contentSection.visibility = View.VISIBLE

            if (transaction.transactionId.isNotEmpty()) {
                rowTransactionId.visibility = View.VISIBLE
                tvTransactionId.text = transaction.transactionId
            } else {
                rowTransactionId.visibility = View.GONE
            }

            if (transaction.paymentMethod.isNotEmpty()) {
                row1.visibility = View.VISIBLE
                tvUtr.text = "Via: ${transaction.paymentMethod}"
            } else {
                row1.visibility = View.GONE
            }

            if (transaction.bankAddress.isNotEmpty()) {
                row2.visibility = View.VISIBLE
                tvPayerInfo.text = transaction.bankAddress
            } else {
                row2.visibility = View.GONE
            }

            if (transaction.notes.isNotEmpty()) {
                tvNotes.visibility = View.VISIBLE
                tvNotes.text = transaction.notes
                val noteColor = when (transaction.paymentStatus.lowercase()) {
                    "pending" -> R.color.status_pending
                    "rejected", "refunded" -> R.color.status_failed
                    else -> R.color.status_default
                }
                tvNotes.setTextColor(ContextCompat.getColor(itemView.context, noteColor))
            } else {
                tvNotes.visibility = View.GONE
            }

            tvDescription.visibility = View.GONE
            tvAdditionalInfo.visibility = View.GONE
        }

        private fun bindTournamentJoiningView(transaction: TransactionModel) {
            contentSection.visibility = View.VISIBLE

            tvDescription.visibility = View.VISIBLE
            tvDescription.text = transaction.description

            if (transaction.referralBonusUsed > 0) {
                tvAdditionalInfo.visibility = View.VISIBLE
                tvAdditionalInfo.text = "Referral Used: ${transaction.referralBonusUsed} Coins"
                tvAdditionalInfo.setTextColor(ContextCompat.getColor(itemView.context, R.color.type_referral))
            } else {
                tvAdditionalInfo.visibility = View.GONE
            }

            row1.visibility = View.GONE
            row2.visibility = View.GONE
            rowTransactionId.visibility = View.GONE
            tvNotes.visibility = View.GONE
        }

        private fun bindTournamentWinningsView(transaction: TransactionModel) {
            contentSection.visibility = View.VISIBLE

            tvDescription.visibility = View.VISIBLE
            tvDescription.text = transaction.description

            if (transaction.rank > 0) {
                tvAdditionalInfo.visibility = View.VISIBLE
                tvAdditionalInfo.text = "Rank: ${transaction.rank}"
                tvAdditionalInfo.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_success))
            } else {
                tvAdditionalInfo.visibility = View.GONE
            }

            row1.visibility = View.GONE
            row2.visibility = View.GONE
            rowTransactionId.visibility = View.GONE
            tvNotes.visibility = View.GONE
        }

        private fun bindTournamentRefundView(transaction: TransactionModel) {
            contentSection.visibility = View.VISIBLE

            tvDescription.visibility = View.VISIBLE
            tvDescription.text = transaction.description

            if (transaction.refundPercent > 0) {
                tvAdditionalInfo.visibility = View.VISIBLE
                tvAdditionalInfo.text = "Refund: ${transaction.refundPercent}%"
                tvAdditionalInfo.setTextColor(ContextCompat.getColor(itemView.context, R.color.warning_orange))
            } else {
                tvAdditionalInfo.visibility = View.GONE
            }

            row1.visibility = View.GONE
            row2.visibility = View.GONE
            rowTransactionId.visibility = View.GONE
            tvNotes.visibility = View.GONE
        }

        private fun bindBonusView(transaction: TransactionModel) {
            contentSection.visibility = View.VISIBLE

            if (transaction.description.isNotEmpty()) {
                tvDescription.visibility = View.VISIBLE
                tvDescription.text = transaction.description
            } else {
                tvDescription.visibility = View.GONE
            }

            row1.visibility = View.GONE
            row2.visibility = View.GONE
            rowTransactionId.visibility = View.GONE
            tvNotes.visibility = View.GONE
            tvAdditionalInfo.visibility = View.GONE
        }

        private fun hideAllContentViews() {
            contentSection.visibility = View.GONE
            row1.visibility = View.GONE
            row2.visibility = View.GONE
            tvDescription.visibility = View.GONE
            rowTransactionId.visibility = View.GONE
            tvNotes.visibility = View.GONE
            tvAdditionalInfo.visibility = View.GONE
        }
    }

    class TransactionDiffCallback : DiffUtil.ItemCallback<TransactionModel>() {
        override fun areItemsTheSame(oldItem: TransactionModel, newItem: TransactionModel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TransactionModel, newItem: TransactionModel): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = getItem(position)
        holder.bind(transaction, clickListener)
    }
}