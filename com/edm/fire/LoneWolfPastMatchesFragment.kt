package com.edm.fire

import android.content.Intent
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
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoneWolfPastMatchesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyMessage: TextView
    private lateinit var tournamentAdapter: TournamentAdapter
    private val tournamentList = mutableListOf<Tournament>()
    private var currentUserId: String = ""
    private var currentDatabaseUrl: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_free_upcoming, container, false)

        recyclerView = view.findViewById(R.id.rvUpcomingTournaments)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage)

        currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        currentDatabaseUrl = arguments?.getString("database_url") ?: ""

        setupRecyclerView()
        loadPastTournaments()

        return view
    }

    private fun setupRecyclerView() {
        tournamentAdapter = TournamentAdapter(
            currentUserId = currentUserId,
            fragmentType = "past",
            onItemClick = { tournament, tournamentType ->
                val intent = Intent(requireContext(), MatchDetailActivity::class.java).apply {
                    putExtra("TOURNAMENT_ID", tournament.tournamentId)
                    putExtra("TOURNAMENT_TYPE", tournamentType)
                    putExtra("TOURNAMENT_TITLE", tournament.Title)
                    putExtra("TOURNAMENT_MAP", tournament.Map)
                    putExtra("TOURNAMENT_MODE", tournament.Mode)
                    putExtra("TOURNAMENT_TYPE_NAME", tournament.Type)
                    putExtra("TOURNAMENT_DATETIME", tournament.DateTime)
                    putExtra("TOURNAMENT_PRIZEPOOL", tournament.PricePool)
                    putExtra("TOURNAMENT_JOININGFEE", tournament.JoiningFee)
                    putExtra("TOURNAMENT_PERKILL", tournament.PerKill)
                    putExtra("TOURNAMENT_SLOTNUMBERS", tournament.SlotNumbers)
                    putExtra("TOURNAMENT_JOINEDCOUNT", tournament.JoinedPlayersCount)
                    putExtra("TOURNAMENT_BANNERURL", tournament.BannerUrl)
                    putExtra("TOURNAMENT_STATUS", tournament.Status)
                }
                startActivity(intent)
            }
        )
        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = tournamentAdapter
            setHasFixedSize(true)
        }
    }

    private fun loadPastTournaments() {
        if (currentDatabaseUrl.isEmpty()) {
            showError("Database URL not configured")
            return
        }

        showLoading(true)

        val url = "$currentDatabaseUrl/Tournaments/TournamentMeta/LoneWolf.json?orderBy=\"Status\"&equalTo=\"Completed\"&limitToLast=2"

        UniversalReader.readJson(
            url = url,
            onResult = { jsonString ->
                activity?.runOnUiThread {
                    if (!isAdded || isDetached) return@runOnUiThread
                    parseAndDisplayTournaments(jsonString)
                    showLoading(false)
                }
            },
            onError = { error ->
                activity?.runOnUiThread {
                    if (!isAdded || isDetached) return@runOnUiThread
                    showError("Failed to load: $error")
                    showLoading(false)
                }
            }
        )
    }

    private fun parseAndDisplayTournaments(jsonString: String) {
        tournamentList.clear()

        if (jsonString.isEmpty() || jsonString == "{}" || jsonString == "null") {
            updateUI()
            return
        }

        try {
            val jsonObject = JsonParser.parseString(jsonString).asJsonObject

            for ((key, value) in jsonObject.entrySet()) {
                val obj = value.asJsonObject

                val tournament = Tournament(
                    tournamentId = key,
                    Title = getString(obj, "Title"),
                    Map = getString(obj, "Map"),
                    Mode = getString(obj, "Mode"),
                    PerKill = getInt(obj, "PerKill"),
                    PricePool = getInt(obj, "PricePool"),
                    Type = getString(obj, "Type"),
                    DateTime = getString(obj, "DateTime"),
                    Description = "",
                    JoiningFee = getInt(obj, "JoiningFee"),
                    JoinedPlayersCount = getInt(obj, "JoinedPlayersCount"),
                    SlotNumbers = getInt(obj, "SlotNumbers", 4),
                    BannerUrl = getString(obj, "BannerUrl"),
                    Status = getString(obj, "Status"),
                    JoinedPlayers = emptyMap()
                )
                tournamentList.add(tournament)
            }

            tournamentList.sortByDescending { parseDate(it.DateTime) }
            updateUI()

        } catch (e: Exception) {
            updateUI()
        }
    }

    private fun getString(obj: JsonObject, key: String): String = if (obj.has(key) && !obj.get(key).isJsonNull) obj.get(key).asString else ""
    private fun getInt(obj: JsonObject, key: String, default: Int = 0): Int = if (obj.has(key) && !obj.get(key).isJsonNull) {
        try { obj.get(key).asInt } catch (e: Exception) { default }
    } else default

    private fun parseDate(dateTimeString: String): Date {
        return try {
            val format = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            format.parse(dateTimeString) ?: Date()
        } catch (e: Exception) {
            try {
                val format2 = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                format2.parse(dateTimeString) ?: Date()
            } catch (e2: Exception) { Date() }
        }
    }

    private fun updateUI() {
        if (tournamentList.isEmpty()) {
            tvEmptyMessage.text = "No past Lone Wolf tournaments available"
            tvEmptyMessage.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmptyMessage.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            tournamentAdapter.updateList(tournamentList)
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) { recyclerView.visibility = View.GONE; tvEmptyMessage.visibility = View.GONE }
    }

    private fun showError(message: String) {
        showLoading(false)
        tvEmptyMessage.text = message
        tvEmptyMessage.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    companion object {
        fun newInstance(databaseUrl: String): LoneWolfPastMatchesFragment {
            return LoneWolfPastMatchesFragment().apply {
                arguments = Bundle().apply { putString("database_url", databaseUrl) }
            }
        }
    }
}