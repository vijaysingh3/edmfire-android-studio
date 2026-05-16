package com.edm.fire

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException

class SelectSlotActivity : AppCompatActivity() {

    companion object {
        // 3 minute expiry — agar user ne 3 min me confirm nahi kiya to slot selection auto-expire
        private const val SELECTION_EXPIRY_MS = 3 * 60 * 1000L
        private const val TAG = "SelectSlot_Debug" // ⚡ Debugging Tag
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var slotsAdapter: SlotsAdapter
    private lateinit var btnJoinNow: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var progressOverlay: View

    private var tournamentId: String = ""
    private var tournamentType: String = ""
    private var tournamentMode: String = ""
    private var selectedSlot: Slot? = null
    private var currentUserId: String = ""
    private var isSelectingInProgress = false
    private var selectionTimer: CountDownTimer? = null
    private var currentSelectionSlotNumber: Int = -1

    private lateinit var remoteConfig: FirebaseRemoteConfig
    private val auth = FirebaseAuth.getInstance()
    private val client = OkHttpClient()

    private var baseDatabaseUrl: String = ""

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_slot)

        Log.d(TAG, "Step 1: Activity onCreate started")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currentUserId = auth.currentUser?.uid ?: ""
        if (currentUserId.isEmpty()) {
            Log.e(TAG, "Error: User not logged in. Closing activity.")
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeRemoteConfig()
    }

    override fun onBackPressed() {
        Log.d(TAG, "User pressed back. Removing selection if any.")
        removeUserSelection()
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity onDestroy. Cancelling timers.")
        selectionTimer?.cancel()
        selectionTimer = null
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    private fun initializeRemoteConfig() {
        Log.d(TAG, "Step 2: Initializing RemoteConfig")
        remoteConfig = FirebaseRemoteConfig.getInstance()

        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 0
            fetchTimeoutInSeconds = 30
        }

        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task ->
                baseDatabaseUrl = if (task.isSuccessful) {
                    remoteConfig.getString("FirebaseDatabase_url")
                } else {
                    ""
                }
                Log.d(TAG, "Step 3: RemoteConfig fetched. Database URL: $baseDatabaseUrl")
                initializeActivity()
            }
    }

    private fun initializeActivity() {
        getIntentData()
        setupToolbar()
        initViews()
        setupRecyclerView()
        loadSlotsData()
        setupJoinButton()
    }

    private fun getIntentData() {
        tournamentType = intent.getStringExtra("TOURNAMENT_TYPE") ?: ""
        tournamentId = intent.getStringExtra("TOURNAMENT_ID") ?: ""

        Log.d(TAG, "Step 4: Intent Data received -> Type: $tournamentType, ID: $tournamentId")

        if (tournamentType.isEmpty() || tournamentId.isEmpty()) {
            Log.e(TAG, "Error: Invalid tournament data in Intent.")
            Toast.makeText(this, "Invalid tournament data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tournamentMode = when (tournamentType) {
            "BattleRoyal" -> "Battle Royal"
            "ClashSquad" -> "Clash Squad"
            "LoneWolf" -> "Lone Wolf"
            "FreeTournaments" -> "Free Tournament"
            else -> "Battle Royal"
        }
    }

    private fun setupToolbar() {
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.materialToolbar2).apply {
            setNavigationOnClickListener {
                Log.d(TAG, "Toolbar back button clicked.")
                removeUserSelection()
                setResult(RESULT_CANCELED)
                finish()
            }
            title = "Select Slot - $tournamentType"
        }
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.rvSlots)
        btnJoinNow = findViewById(R.id.btnJoinNow)
        progressBar = findViewById(R.id.progressBar)
        progressOverlay = findViewById(R.id.progressOverlay)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        slotsAdapter = SlotsAdapter(emptyList()) { slot ->
            Log.d(TAG, "User clicked on slot number: ${slot.slotNumber}")
            onSlotSelected(slot)
        }
        recyclerView.adapter = slotsAdapter
    }

    // ========================================================================
    // PROGRESS BAR
    // ========================================================================

    private fun showProgress() {
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
            progressOverlay.visibility = View.VISIBLE
        }
    }

    private fun hideProgress() {
        runOnUiThread {
            progressBar.visibility = View.GONE
            progressOverlay.visibility = View.GONE
        }
    }

    // ========================================================================
    // LOAD SLOTS — ✅ BUG FIXED: Exactly matching DB value, 0 means 0
    // ========================================================================

    private fun loadSlotsData() {
        showProgress()

        val url = "$baseDatabaseUrl/Tournaments/TournamentMeta/$tournamentType/$tournamentId/SlotNumbers.json"
        Log.d(TAG, "Step 5: Fetching exact SlotNumbers from DB -> URL: $url")

        UniversalReader.readJson(
            url = url,
            onResult = { responseString ->
                runOnUiThread {
                    Log.d(TAG, "Step 6: SlotNumbers Raw Response: $responseString")

                    // Firebase API can return "null" (as string) if node doesn't exist
                    val cleanString = responseString.replace("\"", "").trim()

                    val parsedSlotCount = if (cleanString == "null" || cleanString.isEmpty()) {
                        null
                    } else {
                        cleanString.toIntOrNull()
                    }

                    // Strict matching: If DB has a number (even 0), use it.
                    // Only fallback to default if DB value is missing or entirely invalid.
                    val totalSlots = if (parsedSlotCount != null) {
                        Log.d(TAG, "Step 7: DB contains specific slot count. Creating exact slots: $parsedSlotCount")
                        parsedSlotCount
                    } else {
                        val defaultCount = getDefaultSlotCount()
                        Log.w(TAG, "Step 7: SlotNumbers missing or invalid in DB. Using default: $defaultCount")
                        defaultCount
                    }

                    createSlotsBasedOnCount(totalSlots)
                }
            },
            onError = { error ->
                runOnUiThread {
                    val defaultCount = getDefaultSlotCount()
                    Log.e(TAG, "Step 6 (Error): Failed to fetch SlotNumbers. Error: $error. Using default: $defaultCount")
                    createSlotsBasedOnCount(defaultCount)
                }
            }
        )
    }

    private fun getDefaultSlotCount(): Int {
        return when (tournamentMode.lowercase()) {
            "clash squad" -> 8
            "lone wolf" -> 4
            else -> 48
        }
    }

    private fun createSlotsBasedOnCount(totalSlots: Int) {
        Log.d(TAG, "Step 8: Generating list for $totalSlots slots.")

        val slotNumbersList = if (totalSlots > 0) {
            (1..totalSlots).toList()
        } else {
            emptyList() // If 0 slots, create empty list
        }

        loadJoinedPlayersData(slotNumbersList)
    }

    private fun loadJoinedPlayersData(slotNumbersList: List<Int>) {
        val url = "$baseDatabaseUrl/Tournaments/TournamentDetails/$tournamentType/$tournamentId/JoinedPlayers.json"
        Log.d(TAG, "Step 9: Fetching JoinedPlayers data -> URL: $url")

        UniversalReader.readJson(
            url = url,
            onResult = { jsonString ->
                Log.d(TAG, "Step 10: Received JoinedPlayers data. Parsing...")
                parseJoinedPlayers(jsonString, slotNumbersList)

                // ⚡ Step 1 of smart approach: Initial realtime check
                Log.d(TAG, "Step 11: Triggering fetchAndApplyRealtimeSelections()")
                fetchAndApplyRealtimeSelections()
            },
            onError = { error ->
                runOnUiThread {
                    Log.e(TAG, "Step 10 (Error): Failed to fetch JoinedPlayers: $error. Marking all as available.")
                    val slots = slotNumbersList.map { slotNumber ->
                        Slot(slotNumber = slotNumber, slotStatus = "Available")
                    }
                    slotsAdapter.updateSlots(slots)

                    // ⚡ Step 1 of smart approach: Initial realtime check
                    fetchAndApplyRealtimeSelections()
                }
            }
        )
    }

    private fun parseJoinedPlayers(jsonString: String, slotNumbersList: List<Int>) {
        runOnUiThread {
            val slots = mutableListOf<Slot>()
            val occupiedSlots = HashSet<Int>()

            if (jsonString.isNotEmpty() && jsonString != "{}" && jsonString != "null") {
                try {
                    if (jsonString.startsWith("[")) {
                        val jsonArray = JsonParser.parseString(jsonString).asJsonArray
                        for (element in jsonArray) {
                            if (element.isJsonNull) continue
                            val obj = element.asJsonObject
                            val slotNumber = if (obj.has("PositionSeat") && !obj.get("PositionSeat").isJsonNull) {
                                obj.get("PositionSeat").asInt
                            } else continue

                            if (slotNumber >= 1) {
                                occupiedSlots.add(slotNumber)
                                slots.add(Slot(
                                    slotNumber = slotNumber,
                                    slotStatus = "Occupied",
                                    playerName = if (obj.has("InGameName")) obj.get("InGameName").asString else "Player",
                                    playerUID = if (obj.has("InGameUID")) obj.get("InGameUID").asString else "",
                                    userId = if (obj.has("userId")) obj.get("userId").asString else ""
                                ))
                            }
                        }
                    } else if (jsonString.startsWith("{")) {
                        val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                        for ((key, value) in jsonObject.entrySet()) {
                            val slotNumber = key.toIntOrNull() ?: continue
                            val obj = value.asJsonObject
                            occupiedSlots.add(slotNumber)
                            slots.add(Slot(
                                slotNumber = slotNumber,
                                slotStatus = "Occupied",
                                playerName = if (obj.has("InGameName")) obj.get("InGameName").asString else "Player",
                                playerUID = if (obj.has("InGameUID")) obj.get("InGameUID").asString else "",
                                userId = if (obj.has("userId")) obj.get("userId").asString else ""
                            ))
                        }
                    }
                    Log.d(TAG, "Parse JoinedPlayers: Found ${occupiedSlots.size} occupied slots.")
                } catch (e: Exception) {
                    Log.e(TAG, "Parse JoinedPlayers Exception: ${e.message}")
                }
            } else {
                Log.d(TAG, "Parse JoinedPlayers: No players found. All empty.")
            }

            // Check if current user already joined
            var userAlreadyJoined = false
            var userJoinedSlot = -1
            for (slot in slots) {
                if (slot.userId == currentUserId) {
                    userAlreadyJoined = true
                    userJoinedSlot = slot.slotNumber
                    Log.d(TAG, "Parse JoinedPlayers: Current user already joined at slot $userJoinedSlot")
                    break
                }
            }

            // Mark remaining slots as Available
            for (slotNumber in slotNumbersList) {
                if (!occupiedSlots.contains(slotNumber) && slots.none { it.slotNumber == slotNumber }) {
                    slots.add(Slot(slotNumber = slotNumber, slotStatus = "Available"))
                }
            }

            slotsAdapter.updateSlots(slots.sortedBy { it.slotNumber })

            if (userAlreadyJoined) {
                handleUserAlreadyJoined(userJoinedSlot)
            }
        }
    }

    // ========================================================================
    // SMART NO-POLLING APPROACH
    // ========================================================================

    private fun fetchAndApplyRealtimeSelections() {
        val url = "$baseDatabaseUrl/RealtimeSelection/$tournamentType/$tournamentId.json"
        Log.d(TAG, "RealtimeCheck 1: Fetching active selections from: $url")

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "RealtimeCheck 1 Error: Failed to fetch selections: ${e.message}")
                hideProgress()
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonString = response.body?.string() ?: "{}"
                response.close()
                Log.d(TAG, "RealtimeCheck 1 Success: Data received. Processing...")

                processRealtimeDataAndMarkBlocked(jsonString)
                hideProgress()
            }
        })
    }

    private fun processRealtimeDataAndMarkBlocked(jsonString: String) {
        val currentlySelectedSlots = HashSet<Int>()
        val currentTime = System.currentTimeMillis()
        val expiredSlotKeys = mutableListOf<String>()

        if (jsonString.isNotEmpty() && jsonString != "{}" && jsonString != "null") {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject

                for ((key, value) in jsonObject.entrySet()) {
                    val slotNumber = key.toIntOrNull() ?: continue
                    val obj = value.asJsonObject
                    val userId = if (obj.has("userId")) obj.get("userId").asString else ""
                    val selectionTime = if (obj.has("selectionTime") && !obj.get("selectionTime").isJsonNull) {
                        obj.get("selectionTime").asLong
                    } else 0L

                    // Expired selection check (3 min)
                    if (selectionTime > 0 && (currentTime - selectionTime) > SELECTION_EXPIRY_MS) {
                        Log.d(TAG, "Realtime Process: Slot $slotNumber selection expired. Marking for cleanup.")
                        expiredSlotKeys.add(key)
                    } else if (userId.isNotEmpty() && userId != currentUserId) {
                        currentlySelectedSlots.add(slotNumber)
                    }
                }

                if (expiredSlotKeys.isNotEmpty()) {
                    cleanupExpiredSelections(expiredSlotKeys)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Realtime Process Exception: ${e.message}")
            }
        }

        Log.d(TAG, "Realtime Process: Found ${currentlySelectedSlots.size} slots being selected by others.")

        // Update UI
        runOnUiThread {
            val currentSlots = slotsAdapter.getSlotsList()
            var needsUpdate = false

            for (slot in currentSlots) {
                val shouldBeBlocked = currentlySelectedSlots.contains(slot.slotNumber) && slot.slotStatus != "Occupied"
                if (slot.isBeingSelectedByOthers != shouldBeBlocked) {
                    needsUpdate = true
                    break
                }
            }

            if (needsUpdate) {
                val updatedSlots = currentSlots.map { slot ->
                    val shouldBeBlocked = currentlySelectedSlots.contains(slot.slotNumber) && slot.slotStatus != "Occupied"
                    if (slot.isBeingSelectedByOthers != shouldBeBlocked) {
                        slot.copy(isBeingSelectedByOthers = shouldBeBlocked)
                    } else {
                        slot
                    }
                }
                slotsAdapter.updateSlots(updatedSlots)
            }
        }
    }

    // ========================================================================
    // SLOT SELECTION
    // ========================================================================

    private fun onSlotSelected(slot: Slot) {
        if (isSelectingInProgress) {
            Log.w(TAG, "Slot Click: Blocked - Selection already in progress.")
            Toast.makeText(this, "Please wait, selecting...", Toast.LENGTH_SHORT).show()
            return
        }

        if (isUserAlreadyJoined()) {
            Log.w(TAG, "Slot Click: Blocked - User already joined.")
            Toast.makeText(this, "Already joined", Toast.LENGTH_SHORT).show()
            return
        }

        if (slot.slotStatus == "Occupied") {
            Log.w(TAG, "Slot Click: Blocked - Slot is already occupied.")
            Toast.makeText(this, "Slot occupied", Toast.LENGTH_SHORT).show()
            return
        }

        if (slot.isBeingSelectedByOthers) {
            Log.w(TAG, "Slot Click: Blocked - Slot being selected by another user.")
            Toast.makeText(this, "Slot being selected by another user!", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Slot Click: Validation passed. Proceeding to freshVerifyAndSelect for slot ${slot.slotNumber}.")
        freshVerifyAndSelect(slot)
    }

    private fun isUserAlreadyJoined(): Boolean {
        return slotsAdapter.getSlotsList().any { it.userId == currentUserId }
    }

    private fun freshVerifyAndSelect(slot: Slot) {
        isSelectingInProgress = true
        showProgress()
        updateJoinButtonState()

        val previousSlotNumber = currentSelectionSlotNumber
        if (previousSlotNumber > 0 && previousSlotNumber != slot.slotNumber) {
            Log.d(TAG, "FreshVerify: Deleting previous selection at slot $previousSlotNumber")
            deleteSelectionAtSlot(previousSlotNumber)
        }
        selectedSlot?.let { prev ->
            if (prev.slotNumber != slot.slotNumber) {
                deleteSelectionAtSlot(prev.slotNumber)
                selectedSlot = null
            }
        }

        currentSelectionSlotNumber = slot.slotNumber

        val url = "$baseDatabaseUrl/RealtimeSelection/$tournamentType/$tournamentId.json"
        Log.d(TAG, "FreshVerify: Fetching latest realtime data to ensure slot ${slot.slotNumber} is free.")

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Log.e(TAG, "FreshVerify Error: Failed to fetch data: ${e.message}")
                    isSelectingInProgress = false
                    currentSelectionSlotNumber = -1
                    updateJoinButtonState()
                    hideProgress()
                    Toast.makeText(this@SelectSlotActivity, "Network error, try again", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonString = response.body?.string() ?: "{}"
                response.close()

                val isFree = processFreshDataAndCheckSlot(jsonString, slot.slotNumber)

                // ⚡ FreeTournaments optimization log
                if (tournamentType != "FreeTournaments") {
                    Log.d(TAG, "FreshVerify: Triggering refreshJoinedPlayersQuiet (Standard Tournament)")
                    refreshJoinedPlayersQuiet()
                } else {
                    Log.d(TAG, "FreshVerify: Skipped refreshJoinedPlayersQuiet (FreeTournaments optimization)")
                }

                runOnUiThread {
                    if (isFree) {
                        Log.d(TAG, "FreshVerify: Slot ${slot.slotNumber} is free. Storing selection...")
                        storeRealtimeSelection(slot)
                    } else {
                        Log.w(TAG, "FreshVerify: Slot ${slot.slotNumber} was taken by someone else just now.")
                        isSelectingInProgress = false
                        currentSelectionSlotNumber = -1
                        updateJoinButtonState()
                        hideProgress()
                        Toast.makeText(this@SelectSlotActivity, "Slot ${slot.slotNumber} already taken!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun processFreshDataAndCheckSlot(jsonString: String, targetSlotNumber: Int): Boolean {
        val currentlySelectedSlots = HashSet<Int>()
        val currentTime = System.currentTimeMillis()
        val expiredSlotKeys = mutableListOf<String>()

        if (jsonString.isNotEmpty() && jsonString != "{}" && jsonString != "null") {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject

                for ((key, value) in jsonObject.entrySet()) {
                    val slotNumber = key.toIntOrNull() ?: continue
                    val obj = value.asJsonObject
                    val userId = if (obj.has("userId")) obj.get("userId").asString else ""
                    val selectionTime = if (obj.has("selectionTime") && !obj.get("selectionTime").isJsonNull) {
                        obj.get("selectionTime").asLong
                    } else 0L

                    if (selectionTime > 0 && (currentTime - selectionTime) > SELECTION_EXPIRY_MS) {
                        expiredSlotKeys.add(key)
                    } else if (userId.isNotEmpty() && userId != currentUserId) {
                        currentlySelectedSlots.add(slotNumber)
                    }
                }

                if (expiredSlotKeys.isNotEmpty()) {
                    cleanupExpiredSelections(expiredSlotKeys)
                }
            } catch (_: Exception) { }
        }

        runOnUiThread {
            val currentSlots = slotsAdapter.getSlotsList()
            val updatedSlots = currentSlots.map { slot ->
                val shouldBeBlocked = currentlySelectedSlots.contains(slot.slotNumber) && slot.slotStatus != "Occupied"
                slot.copy(isBeingSelectedByOthers = shouldBeBlocked, isSelected = false)
            }
            slotsAdapter.updateSlots(updatedSlots)
        }

        return !currentlySelectedSlots.contains(targetSlotNumber)
    }

    private fun storeRealtimeSelection(slot: Slot) {
        val url = "$baseDatabaseUrl/RealtimeSelection/$tournamentType/$tournamentId/${slot.slotNumber}.json"
        Log.d(TAG, "StoreSelection: PUT request to lock slot ${slot.slotNumber}")

        val selectionData = """
            {
                "userId": "$currentUserId",
                "slotNumber": ${slot.slotNumber},
                "selectionTime": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .put(RequestBody.create(mediaType, selectionData))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Log.e(TAG, "StoreSelection Error: PUT failed: ${e.message}")
                    if (currentSelectionSlotNumber == slot.slotNumber) {
                        isSelectingInProgress = false
                        currentSelectionSlotNumber = -1
                        updateJoinButtonState()
                        hideProgress()
                        Toast.makeText(this@SelectSlotActivity, "Network error, try again", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()

                if (!response.isSuccessful) {
                    runOnUiThread {
                        Log.e(TAG, "StoreSelection Error: Unsuccessful response ${response.code}")
                        if (currentSelectionSlotNumber == slot.slotNumber) {
                            isSelectingInProgress = false
                            currentSelectionSlotNumber = -1
                            updateJoinButtonState()
                            hideProgress()
                            Toast.makeText(this@SelectSlotActivity, "Slot ${slot.slotNumber} already taken!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return
                }

                if (currentSelectionSlotNumber == slot.slotNumber) {
                    runOnUiThread {
                        Log.d(TAG, "StoreSelection Success: Slot ${slot.slotNumber} locked. Starting 3s confirmation timer.")
                        selectionTimer?.cancel()
                        selectionTimer = object : CountDownTimer(3000, 1000) {
                            override fun onTick(millisUntilFinished: Long) { }

                            override fun onFinish() {
                                if (currentSelectionSlotNumber == slot.slotNumber && isSelectingInProgress) {
                                    confirmSlotSelection(slot)
                                }
                            }
                        }.start()
                    }
                }
            }
        })
    }

    private fun confirmSlotSelection(slot: Slot) {
        if (currentSelectionSlotNumber != slot.slotNumber) {
            isSelectingInProgress = false
            return
        }

        val url = "$baseDatabaseUrl/RealtimeSelection/$tournamentType/$tournamentId/${slot.slotNumber}.json"
        Log.d(TAG, "ConfirmSelection: Final verification for slot ${slot.slotNumber}")

        UniversalReader.readJson(
            url = url,
            onResult = { jsonString ->
                runOnUiThread {
                    isSelectingInProgress = false
                    currentSelectionSlotNumber = -1
                    updateJoinButtonState()
                    hideProgress()

                    if (jsonString.isNotEmpty() && jsonString != "null") {
                        try {
                            val obj = JsonParser.parseString(jsonString).asJsonObject
                            val userId = if (obj.has("userId")) obj.get("userId").asString else ""
                            val selectionTime = if (obj.has("selectionTime") && !obj.get("selectionTime").isJsonNull) {
                                obj.get("selectionTime").asLong
                            } else 0L

                            if (userId == currentUserId && selectionTime > 0 && (System.currentTimeMillis() - selectionTime) <= SELECTION_EXPIRY_MS) {
                                Log.d(TAG, "ConfirmSelection Success: Slot confirmed as yours.")
                                selectedSlot = slot
                                updateSlotSelectionUI(slot.slotNumber)
                                updateJoinButtonState()
                                Toast.makeText(this@SelectSlotActivity, "Slot ${slot.slotNumber} selected!", Toast.LENGTH_SHORT).show()
                            } else {
                                Log.w(TAG, "ConfirmSelection Failed: Slot belongs to someone else or expired.")
                                Toast.makeText(this@SelectSlotActivity, "Slot ${slot.slotNumber} taken by someone else!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {
                            Toast.makeText(this@SelectSlotActivity, "Selection failed", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.w(TAG, "ConfirmSelection Failed: Data is null. Selection lost.")
                        Toast.makeText(this@SelectSlotActivity, "Slot selection lost", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    Log.e(TAG, "ConfirmSelection Error: Network failure: $error")
                    isSelectingInProgress = false
                    currentSelectionSlotNumber = -1
                    updateJoinButtonState()
                    hideProgress()
                    Toast.makeText(this@SelectSlotActivity, "Network error, try again", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun updateSlotSelectionUI(selectedSlotNumber: Int) {
        val currentSlots = slotsAdapter.getSlotsList()
        val updatedSlots = currentSlots.map { slot ->
            if (slot.slotNumber == selectedSlotNumber) {
                slot.copy(isSelected = true)
            } else {
                slot.copy(isSelected = false)
            }
        }
        slotsAdapter.updateSlots(updatedSlots)
    }

    // ========================================================================
    // JOIN BUTTON
    // ========================================================================

    private fun setupJoinButton() {
        btnJoinNow.setOnClickListener {
            Log.d(TAG, "Join Button Clicked.")
            selectedSlot?.let { slot ->
                if (slot.slotStatus == "Occupied") {
                    Log.w(TAG, "Join Check: Slot is occupied. Cancelling join.")
                    Toast.makeText(this, "This slot is now occupied!", Toast.LENGTH_SHORT).show()
                    selectedSlot = null
                    updateJoinButtonState()

                    val currentSlots = slotsAdapter.getSlotsList()
                    val totalSlotsCount = if (currentSlots.isNotEmpty()) currentSlots.maxOf { it.slotNumber } else getDefaultSlotCount()
                    loadJoinedPlayersData((1..totalSlotsCount).toList())
                    return@setOnClickListener
                }

                verifySlotBeforeJoin(slot)
            } ?: Toast.makeText(this, "Select a slot first", Toast.LENGTH_SHORT).show()
        }
        updateJoinButtonState()
    }

    private fun updateJoinButtonState() {
        btnJoinNow.isEnabled = selectedSlot != null && !isSelectingInProgress
        btnJoinNow.alpha = if (selectedSlot != null && !isSelectingInProgress) 1.0f else 0.5f
    }

    private fun verifySlotBeforeJoin(slot: Slot) {
        showProgress()
        btnJoinNow.isEnabled = false
        Log.d(TAG, "VerifyBeforeJoin: Checking if slot ${slot.slotNumber} is still yours before navigating...")

        val url = "$baseDatabaseUrl/RealtimeSelection/$tournamentType/$tournamentId/${slot.slotNumber}.json"

        UniversalReader.readJson(
            url = url,
            onResult = { jsonString ->
                runOnUiThread {
                    hideProgress()

                    if (jsonString.isNotEmpty() && jsonString != "null") {
                        try {
                            val obj = JsonParser.parseString(jsonString).asJsonObject
                            val userId = if (obj.has("userId")) obj.get("userId").asString else ""
                            val selectionTime = if (obj.has("selectionTime") && !obj.get("selectionTime").isJsonNull) {
                                obj.get("selectionTime").asLong
                            } else 0L

                            if (userId == currentUserId && selectionTime > 0 && (System.currentTimeMillis() - selectionTime) <= SELECTION_EXPIRY_MS) {
                                Log.d(TAG, "VerifyBeforeJoin Success: Navigating to TournamentJoiningActivity.")
                                navigateToTournamentJoining(slot)
                            } else {
                                Log.w(TAG, "VerifyBeforeJoin Failed: Slot lost.")
                                selectedSlot = null
                                updateJoinButtonState()
                                updateSlotSelectionUI(-1)
                                Toast.makeText(this@SelectSlotActivity, "Slot ${slot.slotNumber} is no longer yours! Select again.", Toast.LENGTH_LONG).show()

                                val currentSlots = slotsAdapter.getSlotsList()
                                val totalSlotsCount = if (currentSlots.isNotEmpty()) currentSlots.maxOf { it.slotNumber } else getDefaultSlotCount()
                                loadJoinedPlayersData((1..totalSlotsCount).toList())
                            }
                        } catch (_: Exception) {
                            Log.e(TAG, "VerifyBeforeJoin Error: Exception parsing response.")
                            Toast.makeText(this@SelectSlotActivity, "Verification failed, try again", Toast.LENGTH_SHORT).show()
                            updateJoinButtonState()
                        }
                    } else {
                        Log.w(TAG, "VerifyBeforeJoin Failed: Data null. Slot lost.")
                        selectedSlot = null
                        updateJoinButtonState()
                        updateSlotSelectionUI(-1)
                        Toast.makeText(this@SelectSlotActivity, "Slot selection lost! Select again.", Toast.LENGTH_LONG).show()

                        val currentSlots = slotsAdapter.getSlotsList()
                        val totalSlotsCount = if (currentSlots.isNotEmpty()) currentSlots.maxOf { it.slotNumber } else getDefaultSlotCount()
                        loadJoinedPlayersData((1..totalSlotsCount).toList())
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    Log.e(TAG, "VerifyBeforeJoin Error: Network issue $error")
                    hideProgress()
                    updateJoinButtonState()
                    Toast.makeText(this@SelectSlotActivity, "Network error, try again", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun navigateToTournamentJoining(slot: Slot) {
        val intent = Intent(this, TournamentJoiningActivity::class.java).apply {
            putExtra("TOURNAMENT_TYPE", tournamentType)
            putExtra("TOURNAMENT_ID", tournamentId)
            putExtra("SELECTED_SLOT_NUMBER", slot.slotNumber)
        }
        startActivity(intent)
        finish()
    }

    private fun handleUserAlreadyJoined(joinedSlot: Int) {
        btnJoinNow.text = "ALREADY JOINED - Slot $joinedSlot"
        btnJoinNow.isEnabled = false
        btnJoinNow.alpha = 0.5f
        updateSlotSelectionUI(joinedSlot)
        Toast.makeText(this, "Already joined Slot $joinedSlot", Toast.LENGTH_LONG).show()
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    private fun removeUserSelection() {
        if (currentSelectionSlotNumber > 0) {
            Log.d(TAG, "Cleanup: Removing in-progress selection for slot $currentSelectionSlotNumber")
            deleteSelectionAtSlot(currentSelectionSlotNumber)
            currentSelectionSlotNumber = -1
            return
        }

        selectedSlot?.let { slot ->
            Log.d(TAG, "Cleanup: Removing confirmed selection for slot ${slot.slotNumber}")
            deleteSelectionAtSlot(slot.slotNumber)
            return
        }

        Log.d(TAG, "Cleanup: Performing fallback scan to remove any stale selections.")
        removeUserSelectionFallback()
    }

    private fun deleteSelectionAtSlot(slotNumber: Int) {
        val url = "$baseDatabaseUrl/RealtimeSelection/$tournamentType/$tournamentId/$slotNumber.json"
        val request = Request.Builder().url(url).delete().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    private fun removeUserSelectionFallback() {
        val url = "$baseDatabaseUrl/RealtimeSelection/$tournamentType/$tournamentId.json"

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { }

            override fun onResponse(call: Call, response: Response) {
                val jsonString = response.body?.string() ?: "{}"
                response.close()

                if (jsonString == "null" || jsonString.isEmpty() || jsonString == "{}") return

                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    for ((key, value) in jsonObject.entrySet()) {
                        val obj = value.asJsonObject
                        val userId = if (obj.has("userId")) obj.get("userId").asString else ""
                        if (userId == currentUserId) {
                            deleteSelectionAtSlot(key.toInt())
                        }
                    }
                } catch (_: Exception) { }
            }
        })
    }

    private fun refreshJoinedPlayersQuiet() {
        val url = "$baseDatabaseUrl/Tournaments/TournamentDetails/$tournamentType/$tournamentId/JoinedPlayers.json"
        UniversalReader.readJson(
            url = url,
            onResult = { jsonString ->
                runOnUiThread { mergeJoinedPlayersData(jsonString) }
            },
            onError = { }
        )
    }

    private fun mergeJoinedPlayersData(jsonString: String) {
        val occupiedMap = HashMap<Int, Triple<String, String, String>>()

        if (jsonString.isNotEmpty() && jsonString != "{}" && jsonString != "null") {
            try {
                if (jsonString.startsWith("[")) {
                    val jsonArray = JsonParser.parseString(jsonString).asJsonArray
                    for (element in jsonArray) {
                        if (element.isJsonNull) continue
                        val obj = element.asJsonObject
                        val slotNumber = if (obj.has("PositionSeat") && !obj.get("PositionSeat").isJsonNull) {
                            obj.get("PositionSeat").asInt
                        } else continue
                        if (slotNumber >= 1) {
                            occupiedMap[slotNumber] = Triple(
                                if (obj.has("InGameName")) obj.get("InGameName").asString else "Player",
                                if (obj.has("InGameUID")) obj.get("InGameUID").asString else "",
                                if (obj.has("userId")) obj.get("userId").asString else ""
                            )
                        }
                    }
                } else if (jsonString.startsWith("{")) {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    for ((key, value) in jsonObject.entrySet()) {
                        val slotNumber = key.toIntOrNull() ?: continue
                        val obj = value.asJsonObject
                        occupiedMap[slotNumber] = Triple(
                            if (obj.has("InGameName")) obj.get("InGameName").asString else "Player",
                            if (obj.has("InGameUID")) obj.get("InGameUID").asString else "",
                            if (obj.has("userId")) obj.get("userId").asString else ""
                        )
                    }
                }
            } catch (_: Exception) { }
        }

        val currentSlots = slotsAdapter.getSlotsList()
        val updatedSlots = currentSlots.map { slot ->
            occupiedMap[slot.slotNumber]?.let { (name, uid, userId) ->
                slot.copy(
                    slotStatus = "Occupied",
                    playerName = name,
                    playerUID = uid,
                    userId = userId,
                    isSelected = false,
                    isBeingSelectedByOthers = false
                )
            } ?: slot
        }
        slotsAdapter.updateSlots(updatedSlots)
    }

    private fun cleanupExpiredSelections(expiredKeys: List<String>) {
        for (slotKey in expiredKeys) {
            val deleteUrl = "$baseDatabaseUrl/RealtimeSelection/$tournamentType/$tournamentId/$slotKey.json"
            val deleteRequest = Request.Builder().url(deleteUrl).delete().build()
            client.newCall(deleteRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { }
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
        }
    }
}