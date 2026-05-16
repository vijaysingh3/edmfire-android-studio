package com.edm.fire

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

// databaseUrl Activity se milega — fragments ko pass karega
class BattleRoyalViewPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val databaseUrl: String
) : FragmentStateAdapter(fragmentActivity) {

    private val tabTitles = arrayOf("Upcoming", "Ongoing", "Past Matches")

    override fun createFragment(position: Int): Fragment {
        // har fragment ko databaseUrl pass karo — woh Remote Config fetch nahi karenge
        return when (position) {
            0 -> BattleRoyalUpcomingFragment.newInstance(databaseUrl)
            1 -> BattleRoyalOngoingFragment.newInstance(databaseUrl)
            2 -> BattleRoyalPastMatchesFragment.newInstance(databaseUrl)
            else -> BattleRoyalUpcomingFragment.newInstance(databaseUrl)
        }
    }

    override fun getItemCount(): Int = tabTitles.size

    fun getTabTitle(position: Int): String {
        return if (position in tabTitles.indices) {
            tabTitles[position]
        } else {
            "Tab $position"
        }
    }
}