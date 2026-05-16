package com.edm.fire

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import com.google.gson.JsonParser

class JoinedPlayersFragment : Fragment() {

    companion object {
        private const val TAG = "JoinedPlayersFragment"
        private const val ARG_TOURNAMENT_ID = "tournament_id"
        private const val ARG_TOURNAMENT_TYPE = "tournament_type"
        private const val ARG_DATABASE_URL = "database_url"

        fun newInstance(tournamentId: String, tournamentType: String, databaseUrl: String): JoinedPlayersFragment {
            return JoinedPlayersFragment().apply {
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
    private lateinit var playersAdapter: JoinedPlayersAdapter
    private val playersList = mutableListOf<Player>()

    private var tournamentId: String = ""
    private var tournamentType: String = ""
    private var databaseUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            tournamentId = it.getString(ARG_TOURNAMENT_ID, "")
            tournamentType = it.getString(ARG_TOURNAMENT_TYPE, "")
            databaseUrl = it.getString(ARG_DATABASE_URL, "")
        }
        Log.d(TAG, "onCreate - tournamentId: $tournamentId, databaseUrl: $databaseUrl")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_joined_players, container, false)

        initViews(view)
        setupRecyclerView()
        loadJoinedPlayers()

        return view
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.rvJoinedPlayers)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage)
    }

    private fun setupRecyclerView() {
        playersAdapter = JoinedPlayersAdapter(playersList)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = playersAdapter
            setHasFixedSize(true)
        }
    }

    private fun loadJoinedPlayers() {
        if (tournamentId.isEmpty() || databaseUrl.isEmpty()) {
            Log.e(TAG, "Cannot load players: tournamentId or databaseUrl empty")
            showEmptyState("Tournament ID or Database URL missing")
            return
        }

        showLoading(true)

        // 🔥 TournamentDetails node se JoinedPlayers fetch karo (REST API)
        val url = "$databaseUrl/Tournaments/TournamentDetails/$tournamentType/$tournamentId/JoinedPlayers.json"

        Log.d(TAG, "Fetching players from: $url")

        UniversalReader.readJson(
            url = url,
            onResult = { jsonString ->
                requireActivity().runOnUiThread {
                    Log.d(TAG, "Players response length: ${jsonString.length}")
                    parseAndDisplayPlayers(jsonString)
                    showLoading(false)
                }
            },
            onError = { error ->
                requireActivity().runOnUiThread {
                    Log.e(TAG, "Failed to load players: $error")
                    showEmptyState("Failed to load players: $error")
                    showLoading(false)
                }
            }
        )
    }

    private fun parseAndDisplayPlayers(jsonString: String) {
        playersList.clear()

        if (jsonString.isEmpty() || jsonString == "{}" || jsonString == "null") {
            Log.d(TAG, "Empty players response")
            showEmptyState("No players joined yet")
            return
        }

        try {
            val jsonElement = JsonParser.parseString(jsonString)

            // Firebase RTDB sparse array ko object me convert kar deta hai
            // isliye dono format handle karna padega
            if (jsonElement.isJsonArray) {
                // array format: [null, {...}, {...}]
                val jsonArray = jsonElement.asJsonArray
                Log.d(TAG, "JoinedPlayers is ARRAY format, total entries: ${jsonArray.size()}")

                for (element in jsonArray) {
                    if (element.isJsonNull) continue
                    val obj = element.asJsonObject

                    val player = Player(
                        InGameName = getString(obj, "InGameName"),
                        userId = getString(obj, "userId"),
                        InGameUID = getLong(obj, "InGameUID"),
                        InGameLevel = getInt(obj, "InGameLevel"),
                        PositionSeat = getInt(obj, "PositionSeat"),
                        Kills = getInt(obj, "Kills"),
                        Deaths = getInt(obj, "Deaths"),
                        Assists = getInt(obj, "Assists"),
                        Damage = getInt(obj, "Damage"),
                        CoinsEarned = getInt(obj, "CoinsEarned"),
                        Rank = getInt(obj, "Rank"),
                        JoinTime = getLong(obj, "JoinTime")
                    )
                    playersList.add(player)
                }

            } else if (jsonElement.isJsonObject) {
                // object format: {"1": {...}, "2": {...}, "16": {...}}
                val jsonObject = jsonElement.asJsonObject
                Log.d(TAG, "JoinedPlayers is OBJECT format, total entries: ${jsonObject.size()}")

                for (key in jsonObject.keySet()) {
                    val element = jsonObject.get(key)
                    if (element.isJsonNull) continue
                    val obj = element.asJsonObject

                    val player = Player(
                        InGameName = getString(obj, "InGameName"),
                        userId = getString(obj, "userId"),
                        InGameUID = getLong(obj, "InGameUID"),
                        InGameLevel = getInt(obj, "InGameLevel"),
                        PositionSeat = getInt(obj, "PositionSeat"),
                        Kills = getInt(obj, "Kills"),
                        Deaths = getInt(obj, "Deaths"),
                        Assists = getInt(obj, "Assists"),
                        Damage = getInt(obj, "Damage"),
                        CoinsEarned = getInt(obj, "CoinsEarned"),
                        Rank = getInt(obj, "Rank"),
                        JoinTime = getLong(obj, "JoinTime")
                    )
                    playersList.add(player)
                }

            } else {
                Log.e(TAG, "Unexpected JSON format: ${jsonString.substring(0, minOf(100, jsonString.length))}")
                showEmptyState("Unexpected data format")
                return
            }

            // Sort by Rank if available, otherwise by PositionSeat
            val allPlayersHaveRank = playersList.all { it.Rank != 0 }
            if (allPlayersHaveRank) {
                playersList.sortBy { it.Rank }
            } else {
                playersList.sortBy { it.PositionSeat }
            }

            Log.d(TAG, "Parsed ${playersList.size} players successfully")
            updateUI()

        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            showEmptyState("Error parsing players data")
        }
    }

    private fun getString(obj: com.google.gson.JsonObject, key: String): String {
        return if (obj.has(key) && !obj.get(key).isJsonNull) {
            obj.get(key).asString
        } else ""
    }

    private fun getInt(obj: com.google.gson.JsonObject, key: String, default: Int = 0): Int {
        return if (obj.has(key) && !obj.get(key).isJsonNull) {
            obj.get(key).asInt
        } else default
    }

    private fun getLong(obj: com.google.gson.JsonObject, key: String, default: Long = 0L): Long {
        return if (obj.has(key) && !obj.get(key).isJsonNull) {
            obj.get(key).asLong
        } else default
    }

    private fun updateUI() {
        if (playersList.isEmpty()) {
            showEmptyState("No players joined yet")
        } else {
            tvEmptyMessage.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            playersAdapter.updateList(playersList)
            Log.d(TAG, "UI updated with ${playersList.size} players")
        }
    }

    private fun showEmptyState(message: String) {
        tvEmptyMessage.text = message
        tvEmptyMessage.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            recyclerView.visibility = View.GONE
            tvEmptyMessage.visibility = View.GONE
        }
    }
}