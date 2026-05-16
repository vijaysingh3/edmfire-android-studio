package com.edm.fire

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
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

class AichatActivity : AppCompatActivity() {

    // UI
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var txtStatus: TextView
    private lateinit var typingIndicator: View
    private lateinit var loadingOverlay: View
    private lateinit var progressBar: ProgressBar
    private lateinit var txtLoadingMessage: TextView
    private lateinit var bannerUnregistered: LinearLayout

    // Adapter — MessageAdapter use kar raha hai (alag from ChatMessageAdapter)
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<Message>()

    // Network
    private var client: OkHttpClient? = null
    private lateinit var remoteConfigManager: RemoteConfigManager
    private lateinit var conversationStore: ConversationStore

    // Config
    private var coordinatorEndpoint: String = ""
    private var supabaseAnonKey: String = ""
    private var isConfigLoaded = false

    // ✅ User info — SignupActivity se aata hai, unregistered context
    private var userId: String = ""
    private var userScreen: String = ""   // "signup" / "login" / etc.
    private var userLevel: Int = 0
    private var userKycStatus: String = "pending"
    private var userTotalCoins: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aichat)

        remoteConfigManager = RemoteConfigManager.getInstance(this)
        conversationStore   = ConversationStore(this)

        getUserDataFromIntent()
        initViews()
        setupRecyclerView()
        loadConfigFromRemote()
    }

    // ─────────────────────────────────────────
    // Intent Data
    // ─────────────────────────────────────────

    private fun getUserDataFromIntent() {
        // ✅ Unregistered user ke liye sirf screen context bhejo
        userId      = intent.getStringExtra("USER_ID") ?: "unregistered_${System.currentTimeMillis()}"
        userScreen  = intent.getStringExtra("USER_SCREEN") ?: "signup"
        userLevel   = intent.getIntExtra("USER_LEVEL", 0)
        userKycStatus  = intent.getStringExtra("USER_KYC_STATUS") ?: "unregistered"
        userTotalCoins = intent.getIntExtra("USER_TOTAL_COINS", 0)
    }

    // ─────────────────────────────────────────
    // Views
    // ─────────────────────────────────────────

    private fun initViews() {
        rvMessages         = findViewById(R.id.rvMessages)
        etMessage          = findViewById(R.id.etMessage)
        btnSend            = findViewById(R.id.btnSend)
        btnBack            = findViewById(R.id.btnBack)
        txtStatus          = findViewById(R.id.txtStatus)
        typingIndicator    = findViewById(R.id.typingIndicator)
        loadingOverlay     = findViewById(R.id.loadingOverlay)
        progressBar        = findViewById(R.id.progressBar)
        txtLoadingMessage  = findViewById(R.id.txtLoadingMessage)
        bannerUnregistered = findViewById(R.id.bannerUnregistered)

        btnBack.setOnClickListener { finish() }
        btnSend.isEnabled   = false
        etMessage.isEnabled = false
        etMessage.hint      = "Loading AI Agent..."

        loadingOverlay.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        txtLoadingMessage.text = "Connecting to EDM Fire..."

        // ✅ Signup screen se aaya hai to banner dikhao
        if (userScreen == "signup" || userKycStatus == "unregistered") {
            bannerUnregistered.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(messageList)
        rvMessages.apply {
            layoutManager = LinearLayoutManager(this@AichatActivity).apply {
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

                    btnSend.isEnabled   = true
                    etMessage.isEnabled = true
                    etMessage.hint      = "Ask me anything..."

                    txtStatus.text = "● Online"
                    txtStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))

                    hideLoading()
                    setupMessageInput()
                    loadSavedConversation()

                }.onFailure { error ->
                    hideLoading()
                    txtStatus.text = "● Offline"
                    txtStatus.setTextColor(android.graphics.Color.parseColor("#FF6B6B"))
                    showSnackbar("Failed: ${error.message}")
                    etMessage.hint = "Configuration failed. Please restart."
                }

            } catch (e: Exception) {
                hideLoading()
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
        // AichatActivity ka apna alag store — "aichat_" prefix se
        val saved = conversationStore.getHistory()
        if (saved.isEmpty()) {
            showWelcomeMessage()
        } else {
            saved.forEach { chat ->
                messageList.add(Message(chat.content, chat.isUser, chat.timestamp))
            }
            messageAdapter.notifyDataSetChanged()
            rvMessages.scrollToPosition(messageList.size - 1)
        }

        // ✅ Draft restore
        val draft = conversationStore.getDraft()
        if (draft.isNotEmpty()) {
            etMessage.setText(draft)
            etMessage.setSelection(draft.length)
        }
    }

    // ─────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────

    private fun setupMessageInput() {
        btnSend.setOnClickListener { sendMessage() }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        // ✅ Realtime draft save
        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                conversationStore.saveDraft(s?.toString() ?: "")
            }
        })
    }

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

        val msg = Message(message, true, System.currentTimeMillis())
        messageList.add(msg)
        messageAdapter.notifyItemInserted(messageList.size - 1)
        rvMessages.smoothScrollToPosition(messageList.size - 1)

        // ✅ Device mein save
        conversationStore.saveMessage(
            ChatMessage(content = message, isUser = true,
                timestamp = msg.timestamp, userId = userId, userName = "")
        )

        etMessage.text.clear()
        conversationStore.clearDraft()

        showTypingIndicator(true)
        callAIAgent(message)
    }

    // ─────────────────────────────────────────
    // AI Agent Call
    // ─────────────────────────────────────────

    private fun callAIAgent(message: String) {
        if (coordinatorEndpoint.isBlank() || supabaseAnonKey.isBlank()) {
            showTypingIndicator(false)
            addMessage(Message("AI Agent error. Please restart app.", false, System.currentTimeMillis()))
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // ✅ Unregistered user context — AI ko clearly pata chalega
                val userContext = JSONObject().apply {
                    put("user_type",   "unregistered")   // ← AI ko pata chalega
                    put("screen",      userScreen)        // "signup" / "login"
                    put("kyc_status",  "unregistered")
                    put("level",       0)
                    put("total_coins", 0)
                    put("note", "User abhi SignUp screen par hai. Signup related help karo.")
                }

                // ✅ Conversation history bhi bhejo
                val historyArray = conversationStore.getHistoryAsJson()

                val body = JSONObject().apply {
                    put("user_id",              userId)
                    put("user_name",            "New User")
                    put("user_email",           "")
                    put("message",              message)
                    put("user_context",         userContext)
                    put("conversation_history", historyArray)
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

                            val botMsg = Message(reply, false, System.currentTimeMillis())
                            addMessage(botMsg)

                            // ✅ Bot reply bhi save karo
                            conversationStore.saveMessage(
                                ChatMessage(content = reply, isUser = false,
                                    timestamp = botMsg.timestamp, userId = "", userName = "")
                            )
                        } else {
                            addMessage(Message("Empty response.", false, System.currentTimeMillis()))
                        }
                    } else {
                        val errorMsg = when (response?.code) {
                            401  -> "Authentication failed."
                            404  -> "Service not available."
                            500  -> "Server error. Try again."
                            else -> "Network error (${response?.code})."
                        }
                        addMessage(Message(errorMsg, false, System.currentTimeMillis()))
                    }
                }

            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    showTypingIndicator(false)
                    addMessage(Message("Network issue. Check connection.", false, System.currentTimeMillis()))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showTypingIndicator(false)
                    addMessage(Message("Something went wrong. Try again.", false, System.currentTimeMillis()))
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

    private fun addMessage(message: Message) {
        messageList.add(message)
        messageAdapter.notifyItemInserted(messageList.size - 1)
        rvMessages.smoothScrollToPosition(messageList.size - 1)
    }

    private fun showTypingIndicator(show: Boolean) {
        typingIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showWelcomeMessage() {
        val welcome = Message(
            "🔥 Welcome to EDM Fire! 🔥\n\n" +
                    "Main aapko signup mein help kar sakta hun:\n\n" +
                    "• Account kaise banayein?\n" +
                    "• Password bhool gaye?\n" +
                    "• Email verification\n" +
                    "• Login nahi ho raha?\n\n" +
                    "Apna sawaal likhein! 😊",
            false,
            System.currentTimeMillis()
        )
        addMessage(welcome)
        conversationStore.saveMessage(
            ChatMessage(content = welcome.content, isUser = false,
                timestamp = welcome.timestamp, userId = "", userName = "")
        )
    }

    private fun hideLoading() {
        loadingOverlay.visibility   = View.GONE
        progressBar.isIndeterminate = false
        txtLoadingMessage.text      = ""
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        client?.dispatcher?.executorService?.shutdown()
    }
}