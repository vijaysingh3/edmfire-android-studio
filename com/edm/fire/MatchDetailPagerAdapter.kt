package com.edm.fire

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MatchDetailPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val tournamentId: String,
    private val tournamentType: String,
    private val databaseUrl: String,
    private val metaTitle: String,
    private val metaMap: String,
    private val metaMode: String,
    private val metaType: String,
    private val metaDateTime: String,
    private val metaPrizePool: Int,
    private val metaJoiningFee: Int,
    private val metaPerKill: Int,
    private val metaSlotNumbers: Int,
    private val metaJoinedCount: Int,
    private val metaBannerUrl: String,
    private val metaStatus: String
) : FragmentStateAdapter(fragmentActivity) {

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> MatchDetailFragment.newInstance(
                tournamentId, tournamentType, databaseUrl,
                metaTitle, metaMap, metaMode, metaType, metaDateTime,
                metaPrizePool, metaJoiningFee, metaPerKill,
                metaSlotNumbers, metaJoinedCount, metaBannerUrl, metaStatus
            )
            1 -> JoinedPlayersFragment.newInstance(tournamentId, tournamentType, databaseUrl)
            2 -> ChattingFragment.newInstance(tournamentId, tournamentType, databaseUrl)
            else -> MatchDetailFragment.newInstance(
                tournamentId, tournamentType, databaseUrl,
                metaTitle, metaMap, metaMode, metaType, metaDateTime,
                metaPrizePool, metaJoiningFee, metaPerKill,
                metaSlotNumbers, metaJoinedCount, metaBannerUrl, metaStatus
            )
        }
    }

    override fun getItemCount(): Int = 3
}