package com.edm.fire

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class HelpFragment : Fragment() {

    // UI
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnClearHistory: ImageButton
    private lateinit var txtStatus: TextView
    private lateinit var typingIndicator: View

    // Adapter
    private lateinit var messageAdapter: ChatMessageAdapter
    private val messageList = mutableListOf<ChatMessage>()

    // Network
    private var client: OkHttpClient? = null
    private lateinit var remoteConfigManager: RemoteConfigManager
    private lateinit var userDataManager: UserDataManager
    private lateinit var conversationStore: ConversationStore

    // Config
    private var coordinatorEndpoint: String = ""
    private var supabaseAnonKey: String = ""
    private var isConfigLoaded = false

    // User Data
    private var userId: String = ""
    private var userName: String = ""
    private var userEmail: String = ""
    private var userLevel: Int = 0
    private var userKycStatus: String = "pending"
    private var userTotalCoins: Int = 0
    private var userWinningCoins: Int = 0
    private var userTopUpCoins: Int = 0
    private var userWithdrawalCount: Int = 0
    private var userReferralBonus: Int = 0
    private var userFreeFireVerified: Boolean = false
    private var userInGameUID: Long = 0
    private var userPhone: String = ""
    private var userTotalPlayed: Int = 0
    private var userTotalWon: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_help, container, false)

        remoteConfigManager = RemoteConfigManager.getInstance(requireContext())
        userDataManager = UserDataManager(requireContext())
        conversationStore = ConversationStore(requireContext())

        loadUserData()
        initViews(view)
        setupRecyclerView()
        loadConfigFromRemote()

        return view
    }

    // ─────────────────────────────────────────
    // User Data
    // ─────────────────────────────────────────

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    userId = "guest_${System.currentTimeMillis()}"
                    return@launch
                }

                val realData = userDataManager.fetchRealUserData()
                if (realData != null) {
                    userId              = realData.userId
                    userName            = realData.userName
                    userEmail           = realData.email
                    userLevel           = realData.level
                    userKycStatus       = realData.kycStatus
                    userTotalCoins      = realData.totalCoins
                    userWinningCoins    = realData.winningCoins
                    userTopUpCoins      = realData.topUpCoins
                    userWithdrawalCount = realData.withdrawalCount
                    userReferralBonus   = realData.referralBonus
                    userFreeFireVerified = realData.freeFireVerified
                    userInGameUID       = realData.inGameUID
                    userPhone           = realData.phone
                    userTotalPlayed     = realData.totalPlayed
                    userTotalWon        = realData.totalWon
                    userDataManager.updateCachedUserData(realData)
                } else {
                    userId    = currentUser.uid
                    userEmail = currentUser.email ?: ""
                }
            } catch (e: Exception) {
                val cu = FirebaseAuth.getInstance().currentUser
                userId    = cu?.uid ?: "guest_${System.currentTimeMillis()}"
                userEmail = cu?.email ?: ""
            }
        }
    }

    // ─────────────────────────────────────────
    // Views
    // ─────────────────────────────────────────

    private fun initViews(view: View) {
        rvMessages       = view.findViewById(R.id.rvMessages)
        etMessage        = view.findViewById(R.id.etMessage)
        btnSend          = view.findViewById(R.id.btnSend)
        btnClearHistory  = view.findViewById(R.id.btnClearHistory)
        txtStatus        = view.findViewById(R.id.txtStatus)
        typingIndicator  = view.findViewById(R.id.typingIndicator)

        etMessage.isEnabled = false
        etMessage.hint      = "Loading AI Agent..."
        btnSend.isEnabled   = false

        btnClearHistory.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Chat History")
                .setMessage("Kya aap poori chat history delete karna chahte hain?")
                .setPositiveButton("Haan, Delete Karo") { _, _ ->
                    conversationStore.clearHistory()
                    messageList.clear()
                    messageAdapter.notifyDataSetChanged()
                    showWelcomeMessage()
                    Toast.makeText(requireContext(), "Chat history cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = ChatMessageAdapter(
            messages     = messageList,
            onCopyClick  = { text ->
                val cb   = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("EDM Agent Reply", text)
                cb.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Copied!", Toast.LENGTH_SHORT).show()
            }
        )
        rvMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }
    }

    // ─────────────────────────────────────────
    // Remote Config
    // ─────────────────────────────────────────

    private fun loadConfigFromRemote() {
        lifecycleScope.launch {
            try {
                val result = remoteConfigManager.fetchConfig()

                result.onSuccess { config ->
                    coordinatorEndpoint = config.coordinatorEndpoint
                    supabaseAnonKey     = config.supabaseAnonKey

                    if (coordinatorEndpoint.isBlank() || supabaseAnonKey.isBlank()) {
                        throw Exception("Invalid configuration")
                    }

                    isConfigLoaded      = true
                    initOkHttp()

                    etMessage.isEnabled = true
                    etMessage.hint      = "Ask me anything..."
                    btnSend.isEnabled   = true

                    txtStatus.text = "● Online"
                    txtStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))

                    setupMessageInput()
                    loadSavedConversation()

                }.onFailure { error ->
                    txtStatus.text = "● Offline"
                    txtStatus.setTextColor(android.graphics.Color.parseColor("#FF6B6B"))
                    showSnackbar("Failed: ${error.message}")
                    etMessage.hint = "Configuration failed. Please restart."
                }

            } catch (e: Exception) {
                txtStatus.text = "● Error"
                txtStatus.setTextColor(android.graphics.Color.parseColor("#FF6B6B"))
                showSnackbar("Error: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────
    // Conversation — Load + Draft
    // ─────────────────────────────────────────

    private fun loadSavedConversation() {
        val saved = conversationStore.getHistory()
        if (saved.isEmpty()) {
            showWelcomeMessage()
        } else {
            messageList.addAll(saved)
            messageAdapter.notifyDataSetChanged()
            rvMessages.scrollToPosition(messageList.size - 1)
        }

        // ✅ Draft restore — jahan chhoda tha wahan se
        val draft = conversationStore.getDraft()
        if (draft.isNotEmpty()) {
            etMessage.setText(draft)
            etMessage.setSelection(draft.length)
        }
    }

    // ─────────────────────────────────────────
    // Input Setup
    // ─────────────────────────────────────────

    private fun setupMessageInput() {
        btnSend.setOnClickListener { sendMessage() }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        // ✅ Realtime draft — har character pe save hota hai (phone note ki tarah)
        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                conversationStore.saveDraft(s?.toString() ?: "")
            }
        })
    }

    // ─────────────────────────────────────────
    // Send Message
    // ─────────────────────────────────────────

    private fun sendMessage() {
        val message = etMessage.text.toString().trim()
        if (message.isEmpty()) {
            etMessage.error = "Type a message"
            return
        }
        if (!isConfigLoaded) {
            showSnackbar("AI Agent not ready. Please wait.")
            return
        }

        val userMsg = ChatMessage(
            content   = message,
            isUser    = true,
            timestamp = System.currentTimeMillis(),
            userId    = userId,
            userName  = userName
        )
        messageList.add(userMsg)
        messageAdapter.notifyItemInserted(messageList.size - 1)
        rvMessages.smoothScrollToPosition(messageList.size - 1)

        conversationStore.saveMessage(userMsg)

        etMessage.text.clear()
        conversationStore.clearDraft()   // ✅ Send ke baad draft clean

        showTypingIndicator(true)
        callAIAgent(message)
    }

    // ─────────────────────────────────────────
    // AI Agent Call
    // ─────────────────────────────────────────

    private fun callAIAgent(message: String) {
        if (coordinatorEndpoint.isBlank() || supabaseAnonKey.isBlank()) {
            showTypingIndicator(false)
            addErrorMessage("AI Agent error. Please restart app.")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userContext = JSONObject().apply {
                    put("level",             userLevel)
                    put("kyc_status",        userKycStatus)
                    put("total_coins",       userTotalCoins)
                    put("winning_coins",     userWinningCoins)
                    put("topup_coins",       userTopUpCoins)
                    put("withdrawal_count",  userWithdrawalCount)
                    put("referral_bonus",    userReferralBonus)
                    put("freefire_verified", userFreeFireVerified)
                    put("in_game_uid",       userInGameUID)
                    put("total_played",      userTotalPlayed)
                    put("total_won",         userTotalWon)
                }

                // ✅ Poori 24hr conversation history AI ko bhejo
                val historyArray = conversationStore.getHistoryAsJson()

                val body = JSONObject().apply {
                    put("user_id",               userId)
                    put("user_name",             userName)
                    put("user_email",            userEmail)
                    put("message",               message)
                    put("user_context",          userContext)
                    put("conversation_history",  historyArray)
                }

                val request = Request.Builder()
                    .url(coordinatorEndpoint)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer $supabaseAnonKey")
                    .build()

                val response = client?.newCall(request)?.execute()

                withContext(Dispatchers.Main) {
                    showTypingIndicator(false)

                    if (response?.isSuccessful == true) {
                        val responseBody = response.body?.string()
                        if (!responseBody.isNullOrEmpty()) {
                            val json  = JSONObject(responseBody)
                            val reply = json.optString("reply", "I couldn't understand.")

                            val botMsg = ChatMessage(
                                content   = reply,
                                isUser    = false,
                                timestamp = System.currentTimeMillis(),
                                userId    = "",
                                userName  = ""
                            )
                            messageList.add(botMsg)
                            messageAdapter.notifyItemInserted(messageList.size - 1)
                            rvMessages.smoothScrollToPosition(messageList.size - 1)

                            conversationStore.saveMessage(botMsg) // ✅ Bot reply bhi save
                        } else {
                            addErrorMessage("Empty response.")
                        }
                    } else {
                        addErrorMessage("Network error. Please try again.")
                    }
                }

            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    showTypingIndicator(false)
                    addErrorMessage("Network issue. Check connection.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showTypingIndicator(false)
                    addErrorMessage("Something went wrong. Try again.")
                }
            }
        }
    }

    // ─────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────

    private fun initOkHttp() {
        client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun addErrorMessage(msg: String) {
        val errMsg = ChatMessage(
            content   = msg,
            isUser    = false,
            timestamp = System.currentTimeMillis(),
            userId    = "",
            userName  = ""
        )
        messageList.add(errMsg)
        messageAdapter.notifyItemInserted(messageList.size - 1)
        rvMessages.smoothScrollToPosition(messageList.size - 1)
    }

    private fun showTypingIndicator(show: Boolean) {
        typingIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showWelcomeMessage() {
        val welcome = ChatMessage(
            content = "🔥 Welcome! I'm your EDM Fire AI Assistant. 🔥\n\n" +
                    "I can help you with:\n" +
                    "• Signup & Account\n" +
                    "• Withdrawal\n" +
                    "• KYC Verification\n" +
                    "• Gameplay & Tournaments\n" +
                    "• Password Changes\n\n" +
                    "What can I help you with? 😊",
            isUser    = false,
            timestamp = System.currentTimeMillis(),
            userId    = "",
            userName  = ""
        )
        messageList.add(welcome)
        messageAdapter.notifyItemInserted(messageList.size - 1)
        conversationStore.saveMessage(welcome)
    }

    private fun showSnackbar(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        client?.dispatcher?.executorService?.shutdown()
    }
}