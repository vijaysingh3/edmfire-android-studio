package com.edm.fire

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class LoneWolfViewPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val databaseUrl: String
) : FragmentStateAdapter(fragmentActivity) {

    companion object {
        const val ARG_DATABASE_URL = "database_url"
    }

    private val tabTitles = arrayOf("Upcoming", "Ongoing", "Past Matches")

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> LoneWolfUpcomingFragment.newInstance(databaseUrl)
            1 -> LoneWolfOngoingFragment.newInstance(databaseUrl)
            2 -> LoneWolfPastMatchesFragment.newInstance(databaseUrl)
            else -> LoneWolfUpcomingFragment.newInstance(databaseUrl)
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
