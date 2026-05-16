package com.edm.fire

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class HomeFragment : Fragment() {

    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var databaseUrl = ""

    // Tournament count views
    private lateinit var tvFreeCount: TextView
    private lateinit var tvBattleCount: TextView
    private lateinit var tvLoneCount: TextView
    private lateinit var tvClashCount: TextView

    // Tournament status indicators
    private lateinit var ivFreeStatus: ImageView
    private lateinit var ivBattleStatus: ImageView
    private lateinit var ivLoneStatus: ImageView
    private lateinit var ivClashStatus: ImageView

    // Tournament images
    private lateinit var ivFreeTournamentImage: ImageView
    private lateinit var ivBattleTournamentImage: ImageView
    private lateinit var ivClashTournamentImage: ImageView
    private lateinit var ivLoneTournamentImage: ImageView

    // Refer Banner Image
    private lateinit var imgShareBtn: ImageView

    // main progressbar
    private lateinit var mainProgressBar: ProgressBar

    // Banner
    private lateinit var bannerViewPager: ViewPager2
    private lateinit var bannerIndicator: TabLayout
    private lateinit var bannerProgress: View
    private lateinit var bannerAdapter: BannerAdapter
    private var currentPage = 0
    private val handler = Handler(Looper.getMainLooper())
    private var isBannerAutoScrollEnabled = true

    private val BANNER_SLIDE_DELAY_MS = 4000L
    private val BANNER_RESTART_DELAY_MS = 3000L

    private val runnable = object : Runnable {
        override fun run() {
            if (isBannerAutoScrollEnabled && bannerAdapter.itemCount > 1) {
                currentPage = (currentPage + 1) % bannerAdapter.itemCount
                bannerViewPager.currentItem = currentPage
                handler.postDelayed(this, BANNER_SLIDE_DELAY_MS)
            }
        }
    }

    // Announcement
    private lateinit var announcementTextView: TextView
    private lateinit var announcements: MutableList<AnnouncementModel>
    private var currentAnnouncementIndex = 0
    private val announcementHandler = Handler(Looper.getMainLooper())
    private var currentScrollAnimator: ObjectAnimator? = null
    private val ANNOUNCEMENT_SCROLL_SPEED_PX_PER_SEC = 160f

    private val announcementColors = listOf(
        0xFFFF006E, 0xFFFFBE0B, 0xFF00D9FF, 0xFF08FF00,
        0xFFFF5400, 0xFFFF006E, 0xFF00FFB3, 0xFFFFD60A
    )
    private var currentColorIndex = 0

    private val announcementRunnable = Runnable {
        if (announcements.isNotEmpty()) {
            currentAnnouncementIndex = (currentAnnouncementIndex + 1) % announcements.size
            if (isAdded && context != null) {
                val fadeIn = AnimationUtils.loadAnimation(requireContext(), android.R.anim.fade_in)
                announcementTextView.startAnimation(fadeIn)
                announcementTextView.text = announcements[currentAnnouncementIndex].messages
                currentColorIndex = (currentColorIndex + 1) % announcementColors.size
                announcementTextView.setTextColor(announcementColors[currentColorIndex].toInt())
                startAnnouncementScrollAnimation()
            }
        }
    }

    private val blinkAnimation = AlphaAnimation(1.0f, 0.0f).apply {
        duration = 500
        repeatMode = Animation.REVERSE
        repeatCount = Animation.INFINITE
    }

    private fun baseUrl(): String {
        return databaseUrl.trimEnd('/')
    }

    private var dataLoadCount = 0
    private val totalDataLoads = 3
    private var isDataLoaded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        initializeUIComponents(view)
        setupClickListeners(view)
        setupBannerTapListener()
        if (!isDataLoaded) {
            getDatabaseUrlAndLoadData()
        }
        return view
    }

    private fun startAnnouncementScrollAnimation() {
        currentScrollAnimator?.cancel()
        announcementTextView.viewTreeObserver.addOnGlobalLayoutListener(object :
            android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                announcementTextView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val textViewWidth = announcementTextView.width.toFloat()
                val parentWidth = (announcementTextView.parent as? View)?.width?.toFloat() ?: textViewWidth
                val totalDistance = parentWidth + textViewWidth
                val durationMs = ((totalDistance / ANNOUNCEMENT_SCROLL_SPEED_PX_PER_SEC) * 1000).toLong()
                announcementTextView.translationX = parentWidth
                val animator = ObjectAnimator.ofFloat(
                    announcementTextView,
                    "translationX",
                    parentWidth,
                    -textViewWidth
                )
                animator.duration = durationMs
                animator.interpolator = LinearInterpolator()
                animator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (isAdded && announcements.isNotEmpty()) {
                            announcementHandler.removeCallbacks(announcementRunnable)
                            announcementHandler.postDelayed(announcementRunnable, 300L)
                        }
                    }
                })
                currentScrollAnimator = animator
                animator.start()
            }
        })
    }

    private fun setupBannerTapListener() {
        bannerViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPage = position
            }
        })
        bannerViewPager.post {
            val innerRecyclerView = bannerViewPager.getChildAt(0) as? RecyclerView
            innerRecyclerView?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        isBannerAutoScrollEnabled = false
                        handler.removeCallbacks(runnable)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.postDelayed({
                            if (isAdded) {
                                isBannerAutoScrollEnabled = true
                                handler.removeCallbacks(runnable)
                                if (bannerAdapter.itemCount > 1) {
                                    handler.postDelayed(runnable, BANNER_SLIDE_DELAY_MS)
                                }
                            }
                        }, BANNER_RESTART_DELAY_MS)
                    }
                }
                false
            }
        }
    }

    private fun getDatabaseUrlAndLoadData() {
        showMainProgress()
        remoteConfig = FirebaseRemoteConfig.getInstance()
        databaseUrl = remoteConfig.getString("FirebaseDatabase_url")
        if (databaseUrl.isNotEmpty()) {
            Log.d("HOME", "RemoteConfig cached URL mil gaya, REST API se data load karte hain")
            loadAllData()
        } else {
            Log.d("HOME", "RemoteConfig se fresh fetch kar rahe hain (cache=0)...")
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0)
                .build()
            remoteConfig.setConfigSettingsAsync(settings)
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener { task ->
                    databaseUrl = if (task.isSuccessful) {
                        remoteConfig.getString("FirebaseDatabase_url")
                    } else ""
                    Log.d("HOME", "RemoteConfig fetch done, URL: $databaseUrl")
                    if (databaseUrl.isNotEmpty()) {
                        loadAllData()
                    } else {
                        Log.e("HOME", "Database URL nahi mila! Data load nahi ho payega.")
                        hideMainProgress()
                    }
                }
                .addOnFailureListener {
                    Log.e("HOME", "RemoteConfig fetch failed: ${it.message}")
                    hideMainProgress()
                }
        }
    }

    private fun loadAllData() {
        dataLoadCount = 0
        showMainProgress()
        fetchTournamentCounts()
        fetchBanners()
        fetchAnnouncements()
    }

    private fun onDataFetchComplete() {
        dataLoadCount++
        Log.d("HOME", "Data load progress: $dataLoadCount/$totalDataLoads")
        if (dataLoadCount >= totalDataLoads) {
            hideMainProgress()
            isDataLoaded = true
            Log.d("HOME", "Sab data load ho gaya!")
        }
    }

    private fun fetchTournamentCounts() {
        if (databaseUrl.isEmpty()) {
            if (isAdded) {
                requireActivity().runOnUiThread {
                    showZeroCounts()
                    onDataFetchComplete()
                }
            }
            return
        }
        val url = "${baseUrl()}/Tournaments/TournamentsCount.json"
        Log.d("HOME_COUNT", "REST API call: $url")
        UniversalReader.readJson(
            url = url,
            onResult = { jsonString ->
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        try {
                            Log.d("HOME_COUNT", "Response: $jsonString")
                            if (jsonString.isEmpty() || jsonString == "null" || jsonString == "{}") {
                                showZeroCounts()
                            } else {
                                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                                val freeCount   = getInt(jsonObject, "FreeTournaments")
                                val battleCount = getInt(jsonObject, "BattleRoyal")
                                val loneCount   = getInt(jsonObject, "LoneWolf")
                                val clashCount  = getInt(jsonObject, "ClashSquad")
                                updateTournamentCountUI("FreeTournaments", freeCount,  ivFreeStatus)
                                updateTournamentCountUI("BattleRoyal",    battleCount, ivBattleStatus)
                                updateTournamentCountUI("LoneWolf",       loneCount,   ivLoneStatus)
                                updateTournamentCountUI("ClashSquad",     clashCount,  ivClashStatus)
                                Log.d("HOME_COUNT", "Counts updated: Free=$freeCount, Battle=$battleCount, Lone=$loneCount, Clash=$clashCount")
                            }
                        } catch (e: Exception) {
                            Log.e("HOME_COUNT", "JSON parse error: ${e.message}")
                            showZeroCounts()
                        }
                        onDataFetchComplete()
                    }
                }
            },
            onError = { error ->
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        Log.e("HOME_COUNT", "REST API fail: $error")
                        showZeroCounts()
                        onDataFetchComplete()
                    }
                }
            }
        )
    }

    private fun showZeroCounts() {
        updateTournamentCountUI("FreeTournaments", 0, ivFreeStatus)
        updateTournamentCountUI("BattleRoyal",    0, ivBattleStatus)
        updateTournamentCountUI("LoneWolf",       0, ivLoneStatus)
        updateTournamentCountUI("ClashSquad",     0, ivClashStatus)
    }

    private fun updateTournamentCountUI(
        tournamentType: String,
        count: Int,
        statusIndicator: ImageView
    ) {
        val formattedCount = if (count < 10) "0$count" else count.toString()
        when (tournamentType) {
            "FreeTournaments" -> tvFreeCount.text  = formattedCount
            "BattleRoyal"     -> tvBattleCount.text = formattedCount
            "LoneWolf"        -> tvLoneCount.text   = formattedCount
            "ClashSquad"      -> tvClashCount.text  = formattedCount
        }
        if (count > 0) {
            statusIndicator.visibility = View.VISIBLE
            statusIndicator.setImageResource(android.R.drawable.presence_online)
            statusIndicator.startAnimation(blinkAnimation)
        } else {
            statusIndicator.visibility = View.GONE
            statusIndicator.clearAnimation()
        }
    }

    private fun fetchBanners() {
        if (databaseUrl.isEmpty()) {
            if (isAdded) {
                requireActivity().runOnUiThread {
                    showDefaultBanners()
                    onDataFetchComplete()
                }
            }
            return
        }
        val url = "${baseUrl()}/AppBanner.json"
        Log.d("HOME_BANNER", "REST API call: $url")
        UniversalReader.readJson(
            url = url,
            onResult = { jsonString ->
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        try {
                            Log.d("HOME_BANNER", "Response length: ${jsonString.length}")
                            if (jsonString.isEmpty() || jsonString == "null" || jsonString == "{}") {
                                showDefaultBanners()
                            } else {
                                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                                val bannersMap = mutableMapOf<String, BannerModel>()
                                for ((key, value) in jsonObject.entrySet()) {
                                    val obj = value.asJsonObject
                                    bannersMap[key] = BannerModel(
                                        imageurl             = getString(obj, "imageurl"),
                                        image_navigation_url = getString(obj, "image_navigation_url")
                                    )
                                }
                                if (bannersMap.isEmpty()) {
                                    showDefaultBanners()
                                } else {
                                    bannerAdapter.setBannerList(bannersMap)
                                    setupBannerIndicator()
                                    startAutoSlide()
                                    bannerProgress.visibility  = View.GONE
                                    bannerViewPager.visibility = View.VISIBLE
                                    bannerIndicator.visibility = View.VISIBLE
                                    Log.d("HOME_BANNER", "${bannersMap.size} banners display ho rahe hain")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("HOME_BANNER", "JSON parse error: ${e.message}")
                            showDefaultBanners()
                        }
                        onDataFetchComplete()
                    }
                }
            },
            onError = { error ->
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        Log.e("HOME_BANNER", "REST API fail: $error")
                        showDefaultBanners()
                        onDataFetchComplete()
                    }
                }
            }
        )
    }

    private fun fetchAnnouncements() {
        if (databaseUrl.isEmpty()) {
            if (isAdded) {
                requireActivity().runOnUiThread {
                    announcementTextView.text = "Welcome to EDM Fire Tournament App!"
                    onDataFetchComplete()
                }
            }
            return
        }
        val url = "${baseUrl()}/AppAnnouncement.json"
        Log.d("HOME_ANNOUNCE", "REST API call: $url")
        UniversalReader.readJson(
            url = url,
            onResult = { jsonString ->
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        try {
                            Log.d("HOME_ANNOUNCE", "Response length: ${jsonString.length}")
                            if (jsonString.isEmpty() || jsonString == "null" || jsonString == "{}") {
                                announcementTextView.text = "Welcome to EDM Fire Tournament App!"
                            } else {
                                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                                announcements.clear()
                                for ((_, value) in jsonObject.entrySet()) {
                                    val obj = value.asJsonObject
                                    val msg = getString(obj, "messages")
                                    if (msg.isNotEmpty()) {
                                        announcements.add(AnnouncementModel(messages = msg))
                                    }
                                }
                                if (announcements.isNotEmpty()) {
                                    currentColorIndex = 0
                                    announcementTextView.text = announcements[0].messages
                                    announcementTextView.setTextColor(announcementColors[0].toInt())
                                    startAnnouncementScrollAnimation()
                                    Log.d("HOME_ANNOUNCE", "${announcements.size} announcements display ho rahe hain")
                                } else {
                                    announcementTextView.text = "Welcome to EDM Fire Tournament App!"
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("HOME_ANNOUNCE", "JSON parse error: ${e.message}")
                            announcementTextView.text = "Welcome to EDM Fire Tournament App!"
                        }
                        onDataFetchComplete()
                    }
                }
            },
            onError = { error ->
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        Log.e("HOME_ANNOUNCE", "REST API fail: $error")
                        announcementTextView.text = "Welcome to EDM Fire Tournament App!"
                        onDataFetchComplete()
                    }
                }
            }
        )
    }

    private fun getString(obj: JsonObject, key: String): String {
        return if (obj.has(key) && !obj.get(key).isJsonNull) {
            obj.get(key).asString
        } else ""
    }

    private fun getInt(obj: JsonObject, key: String, default: Int = 0): Int {
        return if (obj.has(key) && !obj.get(key).isJsonNull) {
            obj.get(key).asInt
        } else default
    }

    private fun initializeUIComponents(view: View) {
        tvFreeCount   = view.findViewById(R.id.tv_free_count)
        tvBattleCount = view.findViewById(R.id.tv_battle_count)
        tvLoneCount   = view.findViewById(R.id.tv_lone_count)
        tvClashCount  = view.findViewById(R.id.tv_clash_count)

        ivFreeStatus   = view.findViewById(R.id.iv_free_status)
        ivBattleStatus = view.findViewById(R.id.iv_battle_status)
        ivLoneStatus   = view.findViewById(R.id.iv_lone_status)
        ivClashStatus  = view.findViewById(R.id.iv_clash_status)

        ivFreeTournamentImage  = view.findViewById(R.id.free_tournament_image)
        ivBattleTournamentImage = view.findViewById(R.id.battle_tournament_image)
        ivClashTournamentImage = view.findViewById(R.id.clash_tournament_image)
        ivLoneTournamentImage  = view.findViewById(R.id.lone_tournament_image)

        imgShareBtn     = view.findViewById(R.id.img_share_btn)
        mainProgressBar = view.findViewById(R.id.pb_home_main)

        bannerViewPager = view.findViewById(R.id.banner_viewpager)
        bannerIndicator = view.findViewById(R.id.banner_indicator)
        bannerProgress  = view.findViewById(R.id.banner_progress)
        bannerAdapter   = BannerAdapter(requireContext())
        bannerViewPager.adapter = bannerAdapter

        announcementTextView = view.findViewById(R.id.tv_announcement)
        announcements        = mutableListOf()

        // Refer banner load
        Glide.with(this)
            .load(R.drawable.share_banner)
            .placeholder(R.drawable.share_banner)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .into(imgShareBtn)

        // Tournament images load with programmatic size handling
        loadTournamentImagesProgrammatically()
    }

    // ══════════════════════════════════════════════════════════════
    // PROGRAMMATIC SIZE HANDLING - Screen ke according size set hoga
    // ══════════════════════════════════════════════════════════════
    private fun loadTournamentImagesProgrammatically() {
        // Screen width calculate karo
        val screenWidth = resources.displayMetrics.widthPixels

        // Card width = (screenWidth - padding - margins) / 2
        // Padding: 12dp left + 12dp right = 24dp
        // Margin: 5dp left + 5dp right = 10dp per card, total 20dp for 2 cards
        val totalPaddingDp = 24 + 20  // 44dp
        val totalPaddingPx = (totalPaddingDp * resources.displayMetrics.density).toInt()
        val cardWidth = (screenWidth - totalPaddingPx) / 2

        // Image height = cardWidth * 0.56 (16:9 aspect ratio ke liye - 9/16 = 0.5625)
        // Thoda chhota rakhne ke liye 0.5 use kiya (width ka half)
        val imageHeight = (cardWidth * 0.5).toInt()

        Log.d("HOME_IMAGES", "ScreenWidth: $screenWidth, CardWidth: $cardWidth, ImageHeight: $imageHeight")

        // Free Tournament Image
        Glide.with(this)
            .load(R.drawable.battel_royal)
            .override(cardWidth, imageHeight)
            .fitCenter()
            .placeholder(R.drawable.battel_royal)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .into(ivFreeTournamentImage)

        // Battle Royal Image
        Glide.with(this)
            .load(R.drawable.battel_royal)
            .override(cardWidth, imageHeight)
            .fitCenter()
            .placeholder(R.drawable.battel_royal)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .into(ivBattleTournamentImage)

        // Clash Squad Image
        Glide.with(this)
            .load(R.drawable.clash_squad_img1)
            .override(cardWidth, imageHeight)
            .fitCenter()
            .placeholder(R.drawable.clash_squad_img1)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .into(ivClashTournamentImage)

        // Lone Wolf Image
        Glide.with(this)
            .load(R.drawable.lone_wolf_img1)
            .override(cardWidth, imageHeight)
            .fitCenter()
            .placeholder(R.drawable.lone_wolf_img1)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .into(ivLoneTournamentImage)
    }

    private fun showMainProgress() {
        if (::mainProgressBar.isInitialized) {
            mainProgressBar.visibility = View.VISIBLE
        }
    }

    private fun hideMainProgress() {
        if (::mainProgressBar.isInitialized) {
            mainProgressBar.visibility = View.GONE
        }
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<CardView>(R.id.btn_upcoming).setOnClickListener {
            startActivity(Intent(requireContext(), UpcomingActivity::class.java))
        }
        view.findViewById<CardView>(R.id.btn_ongoing).setOnClickListener {
            startActivity(Intent(requireContext(), OnGoingActivity::class.java))
        }
        view.findViewById<CardView>(R.id.btn_completed).setOnClickListener {
            startActivity(Intent(requireContext(), CompletedMatches::class.java))
        }
        view.findViewById<CardView>(R.id.card_free_tournament).setOnClickListener {
            startActivity(Intent(requireContext(), FreeTournamentActivity::class.java))
        }
        view.findViewById<CardView>(R.id.card_battle_royal).setOnClickListener {
            startActivity(Intent(requireContext(), BattleRoyalActivity::class.java))
        }
        view.findViewById<CardView>(R.id.card_lone_wolf).setOnClickListener {
            startActivity(Intent(requireContext(), LoneWolfActivity::class.java))
        }
        view.findViewById<CardView>(R.id.card_clash_squad).setOnClickListener {
            startActivity(Intent(requireContext(), ClashSquadActivity::class.java))
        }
        view.findViewById<View>(R.id.btn_close_announcement).setOnClickListener {
            view.findViewById<View>(R.id.announcement_container).visibility = View.GONE
            announcementHandler.removeCallbacks(announcementRunnable)
            currentScrollAnimator?.cancel()
        }
        imgShareBtn.setOnClickListener {
            startActivity(Intent(requireContext(), ReferActivity::class.java))
        }
        view.findViewById<TextView>(R.id.txtbtn_leaderboard).setOnClickListener {
            startActivity(Intent(requireContext(), Activity_LeaderBoard::class.java))
        }
    }

    fun refreshAllData() {
        isDataLoaded = false
        isBannerAutoScrollEnabled = true
        loadAllData()
    }

    private fun showDefaultBanners() {
        val defaultBanners = mutableMapOf<String, BannerModel>()
        defaultBanners["banner1"] = BannerModel(imageurl = "", image_navigation_url = "")
        defaultBanners["banner2"] = BannerModel(imageurl = "", image_navigation_url = "")
        bannerAdapter.setBannerList(defaultBanners)
        setupBannerIndicator()
        startAutoSlide()
        bannerProgress.visibility  = View.GONE
        bannerViewPager.visibility = View.VISIBLE
        bannerIndicator.visibility = View.VISIBLE
    }

    private fun setupBannerIndicator() {
        bannerIndicator.removeAllTabs()
        for (i in 0 until bannerAdapter.itemCount) {
            bannerIndicator.addTab(bannerIndicator.newTab())
        }
        TabLayoutMediator(bannerIndicator, bannerViewPager) { _, _ -> }.attach()
        bannerIndicator.tabGravity = TabLayout.GRAVITY_CENTER
        bannerIndicator.tabMode    = TabLayout.MODE_FIXED
        for (i in 0 until bannerIndicator.tabCount) {
            val tab = bannerIndicator.getTabAt(i)
            tab?.view?.let { tabView ->
                val lp = tabView.layoutParams
                lp.width  = 16
                lp.height = 16
                tabView.layoutParams = lp
                (tabView.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd = 8
            }
        }
    }

    private fun startAutoSlide() {
        handler.removeCallbacks(runnable)
        isBannerAutoScrollEnabled = true
        if (bannerAdapter.itemCount > 1) {
            handler.postDelayed(runnable, BANNER_SLIDE_DELAY_MS)
        }
    }

    override fun onResume() {
        super.onResume()
        isBannerAutoScrollEnabled = true
        startAutoSlide()
        if (announcements.isNotEmpty()) {
            announcementTextView.text = announcements[currentAnnouncementIndex].messages
            announcementTextView.setTextColor(announcementColors[currentColorIndex].toInt())
            startAnnouncementScrollAnimation()
        }
        if (isDataLoaded && databaseUrl.isNotEmpty()) {
            loadAllData()
            Log.d("HOME", "onResume — fresh data load (no cache)")
        }
        // Screen rotation ya resize pe images dobara load hongi
        loadTournamentImagesProgrammatically()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(runnable)
        announcementHandler.removeCallbacks(announcementRunnable)
        currentScrollAnimator?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(runnable)
        announcementHandler.removeCallbacks(announcementRunnable)
        currentScrollAnimator?.cancel()
        isDataLoaded = false
    }
}