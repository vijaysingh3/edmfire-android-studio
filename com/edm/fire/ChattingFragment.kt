package com.edm.fire

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException

class ChattingFragment : Fragment() {

    companion object {
        private const val TAG = "ChattingFragment"
        private const val ARG_TOURNAMENT_ID = "tournament_id"
        private const val ARG_TOURNAMENT_TYPE = "tournament_type"
        private const val ARG_DATABASE_URL = "database_url"
        private const val MAX_MESSAGE_LENGTH = 600
        private const val CHAT_POLL_INTERVAL = 10000L // 10 seconds (Long for postDelayed)

        fun newInstance(tournamentId: String, tournamentType: String, databaseUrl: String): ChattingFragment {
            return ChattingFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TOURNAMENT_ID, tournamentId)
                    putString(ARG_TOURNAMENT_TYPE, tournamentType)
                    putString(ARG_DATABASE_URL, databaseUrl)
                }
            }
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyMessage: TextView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var tvChatInfo: TextView
    private lateinit var tvCharCount: TextView
    private lateinit var chatAdapter: ChatAdapter
    private val chatList = mutableListOf<ChatMessage>()

    private var tournamentId: String = ""
    private var tournamentType: String = ""
    private var databaseUrl: String = ""
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var isUserJoined: Boolean = false
    private var isUserHost: Boolean = false

    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null
    private var currentCall: Call? = null
    @Volatile
    private var isFragmentActive = false
    private var isPollingStarted = false

    private var cachedUserName: String? = null
    private var isUserNameFetched = false

    // host aur joined dono check hone ke baad hi polling start karo
    private var hostCheckDone = false
    private var joinedCheckDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            tournamentId = it.getString(ARG_TOURNAMENT_ID, "")
            tournamentType = it.getString(ARG_TOURNAMENT_TYPE, "")
            databaseUrl = it.getString(ARG_DATABASE_URL, "")
        }
        Log.d(TAG, "onCreate - tournamentId: $tournamentId, tournamentType: $tournamentType")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chatting, container, false)
        initViews(view)
        setupRecyclerView()
        setupCharCountListener()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""

        isFragmentActive = true

        checkUserStatusAndLoadChat()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentCall?.cancel()
        currentCall = null
        isFragmentActive = false
        stopPolling()
        Log.d(TAG, "onDestroyView - Cleanup done")
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.rvChatMessages)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage)
        etMessage = view.findViewById(R.id.etMessage)
        btnSend = view.findViewById(R.id.btnSend)
        tvChatInfo = view.findViewById(R.id.tvChatInfo)
        tvCharCount = view.findViewById(R.id.tvCharCount)

        etMessage.visibility = View.VISIBLE
        btnSend.visibility = View.VISIBLE
        etMessage.isEnabled = false
        btnSend.isEnabled = false
        etMessage.hint = "Initializing chat..."

        if (tvCharCount != null) {
            tvCharCount.text = "0/$MAX_MESSAGE_LENGTH"
            tvCharCount.visibility = View.VISIBLE
        }
    }

    private fun setupCharCountListener() {
        etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                if (tvCharCount != null) {
                    tvCharCount.text = "$length/$MAX_MESSAGE_LENGTH"
                    if (length > MAX_MESSAGE_LENGTH) {
                        tvCharCount.setTextColor(android.graphics.Color.RED)
                    } else {
                        tvCharCount.setTextColor(android.graphics.Color.GRAY)
                    }
                }
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(chatList, currentUserId)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
            setHasFixedSize(true)
        }
    }

    private fun checkUserStatusAndLoadChat() {
        if (tournamentId.isEmpty() || databaseUrl.isEmpty()) {
            showEmptyState("Tournament not found")
            return
        }

        showLoading(true)
        setupSendButton()

        // reset flags
        hostCheckDone = false
        joinedCheckDone = false
        isPollingStarted = false

        checkHostStatus()
        checkJoinedStatus()
    }

    private fun checkHostStatus() {
        if (currentUserId.isEmpty()) {
            isUserHost = false
            hostCheckDone = true
            onBothChecksComplete()
            return
        }

        val url = "$databaseUrl/Tournaments/TournamentDetails/$tournamentType/$tournamentId/HostUID.json"

        UniversalReader.readJson(
            url = url,
            onResult = { hostUid ->
                if (!isFragmentActive) return@readJson
                activity?.runOnUiThread {
                    isUserHost = hostUid.trim('"') == currentUserId
                    hostCheckDone = true
                    Log.d(TAG, "✅ Host check done: isHost=$isUserHost")
                    onBothChecksComplete()
                }
            },
            onError = { error ->
                Log.e(TAG, "Failed to check host: $error")
                if (!isFragmentActive) return@readJson
                activity?.runOnUiThread {
                    isUserHost = false
                    hostCheckDone = true
                    onBothChecksComplete()
                }
            }
        )
    }

    // Activity ka Firestore result reuse karo
    private fun checkJoinedStatus() {
        if (currentUserId.isEmpty()) {
            isUserJoined = false
            joinedCheckDone = true
            onBothChecksComplete()
            return
        }

        val activity = activity as? MatchDetailActivity
        if (activity != null) {
            Log.d(TAG, "🔄 Getting joined status from Activity (shared Firestore read)")
            activity.addJoinedStatusListener { joined ->
                if (!isFragmentActive) return@addJoinedStatusListener
                isUserJoined = joined
                joinedCheckDone = true
                Log.d(TAG, "✅ Joined status from Activity: joined=$joined")
                fetchUserNameFromFirestore()
            }
        } else {
            Log.d(TAG, "⚠️ Activity not available, doing direct Firestore check")
            firestore.collection("Users")
                .document(currentUserId)
                .collection("JoinedMatches")
                .document(tournamentId)
                .get()
                .addOnSuccessListener { doc ->
                    if (!isFragmentActive) return@addOnSuccessListener
                    isUserJoined = doc.exists()
                    joinedCheckDone = true
                    fetchUserNameFromFirestore()
                }
                .addOnFailureListener {
                    if (!isFragmentActive) return@addOnFailureListener
                    isUserJoined = false
                    joinedCheckDone = true
                    fetchUserNameFromFirestore()
                }
        }
    }

    // host aur joined dono check complete hone ke baad decide karo
    private fun onBothChecksComplete() {
        if (!hostCheckDone || !joinedCheckDone || !isUserNameFetched) return

        if (isUserJoined || isUserHost) {
            updateChatAccess()
            showLoading(false)
            if (!isPollingStarted) {
                startPolling()
            }
        } else {
            updateChatAccess()
            showLoading(false)
            showEmptyState("Join this tournament to see chat messages")
            Log.d(TAG, "⏸️ User not joined/host — chat polling NOT started (RTDB saved)")
        }
    }

    // Firestore se userName fetch
    private fun fetchUserNameFromFirestore() {
        if (isUserNameFetched) {
            onBothChecksComplete()
            return
        }

        Log.d(TAG, "Fetching UserName from Firestore for: $currentUserId")

        firestore.collection("Users").document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (!isFragmentActive) return@addOnSuccessListener

                var userName = ""
                if (document.exists()) {
                    userName = document.getString("UserName") ?: ""
                    if (userName.isNotEmpty()) {
                        Log.d(TAG, "✅ UserName from Firestore: $userName")
                    } else {
                        Log.d(TAG, "⚠️ UserName field empty in Firestore")
                    }
                } else {
                    Log.d(TAG, "⚠️ User document not found in Firestore")
                }

                if (userName.isEmpty()) {
                    userName = auth.currentUser?.displayName ?: ""
                    if (userName.isNotEmpty()) {
                        Log.d(TAG, "✅ Using Auth displayName: $userName")
                    }
                }

                if (userName.isEmpty()) {
                    userName = "Player"
                    Log.d(TAG, "⚠️ Using fallback name: Player")
                }

                currentUserName = userName
                cachedUserName = userName
                isUserNameFetched = true

                onBothChecksComplete()
            }
            .addOnFailureListener { e ->
                if (!isFragmentActive) return@addOnFailureListener
                Log.e(TAG, "❌ Firestore error: ${e.message}")

                var userName = auth.currentUser?.displayName ?: ""
                if (userName.isEmpty()) {
                    userName = "Player"
                }

                currentUserName = userName
                cachedUserName = userName
                isUserNameFetched = true

                onBothChecksComplete()
            }
    }

    private fun updateChatAccess() {
        if (!isFragmentActive) return

        if (currentUserId.isEmpty()) {
            etMessage.isEnabled = false
            btnSend.isEnabled = false
            etMessage.hint = "Login to send messages"
            tvChatInfo.text = "Please login to participate in chat"
            tvChatInfo.visibility = View.VISIBLE
        } else if (isUserHost) {
            etMessage.isEnabled = true
            btnSend.isEnabled = true
            etMessage.hint = "Type your message... (Host)"
            tvChatInfo.text = "👑 HOST - You have full chat access"
            tvChatInfo.visibility = View.VISIBLE
        } else if (isUserJoined) {
            etMessage.isEnabled = true
            btnSend.isEnabled = true
            etMessage.hint = "Type your message..."
            tvChatInfo.text = "Chat as: $currentUserName"
            tvChatInfo.visibility = View.VISIBLE
        } else {
            etMessage.isEnabled = false
            btnSend.isEnabled = false
            etMessage.hint = "Join tournament to send messages"
            tvChatInfo.text = "Join this tournament to participate in chat"
            tvChatInfo.visibility = View.VISIBLE
        }
    }

    private fun getChatPath(): String {
        return "$databaseUrl/Conversation/$tournamentType/$tournamentId/messages.json"
    }

    private fun startPolling() {
        if (isPollingStarted) return
        isPollingStarted = true

        val url = getChatPath()
        Log.d(TAG, "📡 Starting chat polling (interval: ${CHAT_POLL_INTERVAL}ms)")

        pollingRunnable = createPollingRunnable(url)
        handler.postDelayed(pollingRunnable!!, 1000L)
    }

    private fun stopPolling() {
        pollingRunnable?.let { handler.removeCallbacks(it) }
        pollingRunnable = null
        isPollingStarted = false
    }

    private fun createPollingRunnable(url: String): Runnable {
        return object : Runnable {
            override fun run() {
                if (!isFragmentActive || !isAdded) {
                    return
                }

                val request = Request.Builder().url(url).get().build()
                currentCall = client.newCall(request)

                currentCall?.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (call.isCanceled()) {
                            return
                        }
                        Log.e(TAG, "Polling error: ${e.message}")
                        if (isFragmentActive && isAdded) {
                            handler.postDelayed(createPollingRunnable(url), CHAT_POLL_INTERVAL)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (currentCall == call) {
                            currentCall = null
                        }

                        if (!isFragmentActive || !isAdded) {
                            return
                        }

                        val jsonString = response.body?.string() ?: "null"
                        activity?.runOnUiThread {
                            parseAndDisplayChat(jsonString)
                        }

                        if (isFragmentActive && isAdded) {
                            handler.postDelayed(createPollingRunnable(url), CHAT_POLL_INTERVAL)
                        }
                    }
                })
            }
        }
    }

    private fun parseAndDisplayChat(jsonString: String) {
        if (!isFragmentActive) return

        if (jsonString.isEmpty() || jsonString == "{}" || jsonString == "null") {
            if (chatList.isEmpty()) {
                showEmptyState("No messages yet")
            }
            return
        }

        try {
            val jsonObject = JsonParser.parseString(jsonString).asJsonObject
            val newMessages = mutableListOf<ChatMessage>()

            for ((key, value) in jsonObject.entrySet()) {
                val obj = value.asJsonObject
                val message = ChatMessage(
                    content = getString(obj, "message"),
                    isUser = getString(obj, "userId") == currentUserId,
                    timestamp = getLong(obj, "timestamp"),
                    userId = getString(obj, "userId"),
                    userName = getString(obj, "userName")
                )
                newMessages.add(message)
            }

            newMessages.sortBy { it.timestamp }

            if (chatList.size != newMessages.size) {
                chatList.clear()
                chatList.addAll(newMessages)
                chatAdapter.updateList(chatList)

                if (chatList.isEmpty()) {
                    showEmptyState("No messages yet")
                } else {
                    tvEmptyMessage.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    recyclerView.post {
                        recyclerView.smoothScrollToPosition(chatList.size - 1)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse chat error: ${e.message}")
        }
    }

    private fun getString(obj: com.google.gson.JsonObject, key: String): String {
        return if (obj.has(key) && !obj.get(key).isJsonNull) {
            obj.get(key).asString
        } else ""
    }

    private fun getLong(obj: com.google.gson.JsonObject, key: String, default: Long = 0L): Long {
        return if (obj.has(key) && !obj.get(key).isJsonNull) {
            obj.get(key).asLong
        } else default
    }

    private fun setupSendButton() {
        btnSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun sendMessage() {
        val messageText = etMessage.text.toString().trim()

        if (messageText.isEmpty()) {
            Toast.makeText(requireContext(), "Cannot send empty message", Toast.LENGTH_SHORT).show()
            return
        }

        if (messageText.length > MAX_MESSAGE_LENGTH) {
            Toast.makeText(
                requireContext(),
                "Message is too long! Maximum $MAX_MESSAGE_LENGTH characters allowed.\nCurrent: ${messageText.length} chars",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (currentUserId.isEmpty()) {
            Toast.makeText(requireContext(), "Please login to send messages", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isUserJoined && !isUserHost) {
            Toast.makeText(requireContext(), "You need to join this tournament to send messages", Toast.LENGTH_SHORT).show()
            return
        }

        val finalUserName = if (currentUserName.isNotEmpty()) currentUserName else "Player"
        sendMessageWithName(messageText, finalUserName)
    }

    private fun sendMessageWithName(messageText: String, userName: String) {
        val messageUrl = "$databaseUrl/Conversation/$tournamentType/$tournamentId/messages.json"
        val messageId = System.currentTimeMillis().toString()
        val finalUrl = "$messageUrl?name=$messageId"

        val messageData = """
            {
                "userId": "$currentUserId",
                "userName": "$userName",
                "message": "$messageText",
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(finalUrl)
            .post(RequestBody.create(mediaType, messageData))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        etMessage.text.clear()
                        if (tvCharCount != null) {
                            tvCharCount.text = "0/$MAX_MESSAGE_LENGTH"
                        }
                        Toast.makeText(requireContext(), "Message sent", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to send", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun showEmptyState(message: String) {
        if (!isFragmentActive) return
        tvEmptyMessage.text = message
        tvEmptyMessage.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvChatInfo.visibility = View.VISIBLE
    }

    private fun showLoading(show: Boolean) {
        if (!isFragmentActive) return
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            recyclerView.visibility = View.GONE
            tvEmptyMessage.visibility = View.GONE
            tvChatInfo.visibility = View.GONE
        }
    }
}