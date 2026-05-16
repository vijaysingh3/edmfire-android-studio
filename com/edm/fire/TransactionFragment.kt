package com.edm.fire

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class TransactionFragment : Fragment(), TransactionAdapter.OnTransactionClickListener {

    // UI Components
    private lateinit var recyclerViewTransactions: RecyclerView
    private lateinit var btnStartDate: LinearLayout
    private lateinit var btnEndDate: LinearLayout
    private lateinit var tvStartDate: TextView
    private lateinit var tvEndDate: TextView
    private lateinit var btnApplyFilter: Button
    private lateinit var btnClearFilter: Button
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var chipGroup: com.google.android.material.chip.ChipGroup

    // State Views
    private lateinit var loadingState: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var errorState: LinearLayout
    private lateinit var btnRetry: Button
    private lateinit var btnErrorRetry: Button
    private lateinit var tvError: TextView
    private lateinit var tvEmptyMessage: TextView

    // Adapter & Repository
    private lateinit var adapter: TransactionAdapter
    private lateinit var repository: TransactionRepository

    // Data
    private var allTransactions: List<TransactionModel> = emptyList()
    private var filteredTransactions: List<TransactionModel> = emptyList()
    private var currentChipFilter = "all"
    private var startDate: Date? = null
    private var endDate: Date? = null

    // Date Format for display
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val compareDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // Fetch Guard
    private var isFetching = false

    companion object {
        private const val TAG = "TransactionFragment"

        @JvmStatic
        fun newInstance() = TransactionFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_transaction, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()
        setupChipListeners()
        setupSwipeRefresh()

        loadTransactions()
    }

    private fun initializeViews(view: View) {
        recyclerViewTransactions = view.findViewById(R.id.recyclerViewTransactions)
        btnStartDate = view.findViewById(R.id.btnStartDate)
        btnEndDate = view.findViewById(R.id.btnEndDate)
        tvStartDate = view.findViewById(R.id.tvStartDate)
        tvEndDate = view.findViewById(R.id.tvEndDate)
        btnApplyFilter = view.findViewById(R.id.btnApplyFilter)
        btnClearFilter = view.findViewById(R.id.btnClearFilter)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        chipGroup = view.findViewById(R.id.chipGroup)
        loadingState = view.findViewById(R.id.loadingState)
        emptyState = view.findViewById(R.id.emptyState)
        errorState = view.findViewById(R.id.errorState)
        btnRetry = view.findViewById(R.id.btnRetry)
        btnErrorRetry = view.findViewById(R.id.btnErrorRetry)
        tvError = view.findViewById(R.id.tvError)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage)

        repository = TransactionRepository()

        // Set default dates (last 30 days to current date)
        val calendar = Calendar.getInstance()
        endDate = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        startDate = calendar.time

        updateDateDisplay()
    }

    private fun updateDateDisplay() {
        startDate?.let { tvStartDate.text = displayDateFormat.format(it) }
        endDate?.let { tvEndDate.text = displayDateFormat.format(it) }
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter()
        adapter.setOnTransactionClickListener(this)
        recyclerViewTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TransactionFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeColors(
            requireContext().getColor(android.R.color.holo_blue_bright),
            requireContext().getColor(android.R.color.holo_green_light),
            requireContext().getColor(android.R.color.holo_orange_light),
            requireContext().getColor(android.R.color.holo_red_light)
        )
        swipeRefreshLayout.setOnRefreshListener {
            loadTransactions()
        }
    }

    private fun setupClickListeners() {
        btnStartDate.setOnClickListener { showDatePicker(true) }
        btnEndDate.setOnClickListener { showDatePicker(false) }
        btnApplyFilter.setOnClickListener { applyFilters() }
        btnClearFilter.setOnClickListener { clearFilters() }
        btnRetry.setOnClickListener { loadTransactions() }
        btnErrorRetry.setOnClickListener { loadTransactions() }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (isStartDate) "Select Start Date" else "Select End Date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selection
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            if (isStartDate) {
                startDate = calendar.time
                if (endDate != null && startDate!!.after(endDate)) {
                    Toast.makeText(requireContext(), "Start date cannot be after end date", Toast.LENGTH_SHORT).show()
                    startDate = endDate
                }
            } else {
                endDate = calendar.time
                if (startDate != null && endDate!!.before(startDate)) {
                    Toast.makeText(requireContext(), "End date cannot be before start date", Toast.LENGTH_SHORT).show()
                    endDate = startDate
                }
            }
            updateDateDisplay()
        }

        datePicker.show(parentFragmentManager, "date_picker")
    }

    private fun clearFilters() {
        // Reset dates to last 30 days
        val calendar = Calendar.getInstance()
        endDate = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        startDate = calendar.time
        updateDateDisplay()

        // Reset chip filter to "All"
        currentChipFilter = "all"
        val allChip = view?.findViewById<Chip>(R.id.chipAll)
        allChip?.isChecked = true

        applyFilters()
    }

    private fun applyFilters() {
        var filtered = allTransactions

        // Apply date filter
        if (startDate != null && endDate != null) {
            filtered = filtered.filter { transaction ->
                val transactionDate = parseTransactionDate(transaction.timestamp)
                transactionDate != null &&
                        transactionDate >= startDate!! &&
                        transactionDate <= endDate!!
            }
        }

        // Apply chip type filter
        if (currentChipFilter != "all") {
            filtered = filtered.filter { it.transactionType == currentChipFilter }
        }

        filteredTransactions = filtered.sortedByDescending { it.timestamp }
        adapter.submitList(filteredTransactions)

        if (filteredTransactions.isEmpty()) {
            showEmptyState(
                if (allTransactions.isEmpty()) "No transactions found"
                else "No transactions match your filter"
            )
        } else {
            showSuccessState()
        }
    }

    private fun parseTransactionDate(timestamp: String): Date? {
        return try {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            sdf.parse(timestamp)
        } catch (e: Exception) {
            try {
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                sdf.parse(timestamp)
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun loadTransactions() {
        if (isFetching) return

        isFetching = true
        showLoadingState()

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.fetchAllTransactions()
                }

                allTransactions = result
                applyFilters() // This will apply current date and chip filters
                showSuccessState()

            } catch (e: Exception) {
                showErrorState("Failed to load: ${e.message}")
            } finally {
                isFetching = false
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadTransactions()
    }

    private fun setupChipListeners() {
        val chips = mapOf(
            R.id.chipAll to "all",
            R.id.chipDeposit to "Deposit",
            R.id.chipWithdrawal to "Withdrawal Request",
            R.id.chipTournamentJoining to "Tournament Joining",
            R.id.chipTournamentWinnings to "Tournament Winnings",
            R.id.chipTournamentRefund to "Tournament Refund",
            R.id.chipReferral to "Referral Bonus"
        )

        chips.forEach { (chipId, filterType) ->
            view?.findViewById<Chip>(chipId)?.setOnClickListener {
                currentChipFilter = filterType
                applyFilters()
            }
        }
    }

    private fun showLoadingState() {
        loadingState.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        errorState.visibility = View.GONE
        recyclerViewTransactions.visibility = View.GONE
    }

    private fun showSuccessState() {
        loadingState.visibility = View.GONE
        emptyState.visibility = View.GONE
        errorState.visibility = View.GONE
        recyclerViewTransactions.visibility = View.VISIBLE
    }

    private fun showEmptyState(message: String) {
        loadingState.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        errorState.visibility = View.GONE
        recyclerViewTransactions.visibility = View.GONE
        tvEmptyMessage.text = message
    }

    private fun showErrorState(errorMessage: String) {
        loadingState.visibility = View.GONE
        emptyState.visibility = View.GONE
        errorState.visibility = View.VISIBLE
        recyclerViewTransactions.visibility = View.GONE
        tvError.text = errorMessage
    }

    override fun onTransactionClick(transaction: TransactionModel) {
        showTransactionDetailsDialog(transaction)
    }

    override fun onTransactionLongClick(transaction: TransactionModel): Boolean {
        showTransactionOptions(transaction)
        return true
    }

    private fun showTransactionDetailsDialog(transaction: TransactionModel) {
        try {
            val detailsDialog = TransactionDetailsDialog.newInstance(transaction)
            detailsDialog.show(parentFragmentManager, "TransactionDetailsDialog")
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error showing details", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTransactionOptions(transaction: TransactionModel) {
        val options = arrayOf("View Details", "Copy Transaction ID", "Share")
        AlertDialog.Builder(requireContext())
            .setTitle("Transaction Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTransactionDetailsDialog(transaction)
                    1 -> copyToClipboard(transaction.id, "Transaction ID copied")
                    2 -> shareTransaction(transaction)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copyToClipboard(text: String, message: String) {
        val clipboard = requireContext()
            .getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Transaction", text))
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun shareTransaction(transaction: TransactionModel) {
        val shareText = buildString {
            append("Transaction Details:\n")
            append("Type: ${transaction.displayType}\n")
            append("Amount: ${transaction.displayAmount}\n")
            append("Status: ${transaction.displayStatus}\n")
            append("Date: ${transaction.timestamp}\n")
            if (transaction.transactionId.isNotEmpty()) append("Transaction ID: ${transaction.transactionId}\n")
            if (transaction.description.isNotEmpty()) append("Description: ${transaction.description}\n")
        }
        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(android.content.Intent.createChooser(shareIntent, "Share Transaction"))
    }
}