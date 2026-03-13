package com.merlottv.kotlin.domain.repository

import com.merlottv.kotlin.domain.model.*

interface EspnRepository {
    /** Live scores / scoreboard for a league */
    suspend fun getScoreboard(league: SportsLeague): List<SportScore>

    /** Conference/division standings */
    suspend fun getStandings(league: SportsLeague): List<SportConference>

    /** All teams in a league */
    suspend fun getTeams(league: SportsLeague): List<SportTeamInfo>

    /** Team detail with roster and schedule */
    suspend fun getTeamRoster(league: SportsLeague, teamId: String): List<SportPlayer>
    suspend fun getTeamSchedule(league: SportsLeague, teamId: String): List<SportScheduleEvent>

    /** Game summary — box score, play-by-play, team stats */
    suspend fun getGameSummary(league: SportsLeague, eventId: String): SportGameSummary?
}
