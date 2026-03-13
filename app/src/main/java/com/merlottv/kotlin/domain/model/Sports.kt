package com.merlottv.kotlin.domain.model

/**
 * Supported ESPN leagues — each entry maps to the ESPN Site API URL path segments.
 */
enum class SportsLeague(
    val sport: String,
    val league: String,
    val displayName: String
) {
    NFL("football", "nfl", "NFL"),
    NCAAF("football", "college-football", "NCAAF"),
    NBA("basketball", "nba", "NBA"),
    NCAAM("basketball", "mens-college-basketball", "NCAAM"),
    MLB("baseball", "mlb", "MLB"),
    UFC("mma", "ufc", "UFC"),
    CFL("football", "cfl", "CFL"),
    XFL("football", "xfl", "XFL"),
    UFL("football", "ufl", "UFL");

    /** Whether this league has traditional standings (UFC uses rankings instead) */
    val hasStandings: Boolean get() = this != UFC
}

// ─── Scoreboard / Live Scores ─────────────────────────────────────────

data class SportScore(
    val eventId: String,
    val league: SportsLeague,
    val name: String = "",
    val shortName: String = "",
    val homeTeam: SportTeamRef = SportTeamRef(),
    val awayTeam: SportTeamRef = SportTeamRef(),
    val homeScore: String = "0",
    val awayScore: String = "0",
    val statusText: String = "",        // "Final", "1st Quarter", "3:42 - 2nd"
    val statusState: String = "",       // "pre", "in", "post"
    val clock: String = "",
    val period: Int = 0,
    val isLive: Boolean = false,
    val isComplete: Boolean = false,
    val gameDate: String = "",          // ISO date string
    val broadcasts: List<String> = emptyList(),  // ["ESPN", "ABC"]
    val venue: String = "",
    val venueCity: String = ""
)

data class SportTeamRef(
    val id: String = "",
    val name: String = "",
    val abbreviation: String = "",
    val displayName: String = "",
    val logo: String = "",
    val color: String = "",
    val record: String = "",            // "10-3"
    val isWinner: Boolean = false
)

// ─── Standings ────────────────────────────────────────────────────────

data class SportConference(
    val id: String = "",
    val name: String = "",
    val abbreviation: String = "",
    val divisions: List<SportDivision> = emptyList()
)

data class SportDivision(
    val name: String = "",
    val entries: List<SportStandingsEntry> = emptyList()
)

data class SportStandingsEntry(
    val team: SportTeamRef = SportTeamRef(),
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
    val winPct: String = ".000",
    val gamesBack: String = "-",
    val streak: String = "",
    val pointsFor: Int = 0,
    val pointsAgainst: Int = 0,
    val stats: Map<String, String> = emptyMap()  // any extra stats
)

// ─── Teams ────────────────────────────────────────────────────────────

data class SportTeamInfo(
    val id: String = "",
    val name: String = "",
    val location: String = "",
    val abbreviation: String = "",
    val displayName: String = "",
    val logo: String = "",
    val color: String = "",
    val record: String = "",
    val standingSummary: String = "",    // "1st in AFC East"
    val venue: SportVenue = SportVenue()
)

data class SportVenue(
    val name: String = "",
    val city: String = "",
    val state: String = "",
    val capacity: Int = 0,
    val imageUrl: String = ""
)

// ─── Roster ───────────────────────────────────────────────────────────

data class SportPlayer(
    val id: String = "",
    val name: String = "",
    val jersey: String = "",
    val position: String = "",
    val age: Int = 0,
    val height: String = "",
    val weight: String = "",
    val headshot: String = "",
    val experience: Int = 0
)

// ─── Schedule ─────────────────────────────────────────────────────────

data class SportScheduleEvent(
    val eventId: String = "",
    val date: String = "",
    val opponent: SportTeamRef = SportTeamRef(),
    val homeAway: String = "",          // "home" or "away"
    val score: String = "",             // "W 24-17" or "L 10-20" or ""
    val isComplete: Boolean = false,
    val broadcasts: List<String> = emptyList(),
    val venue: String = ""
)

// ─── Game Summary (Detail) ────────────────────────────────────────────

data class SportGameSummary(
    val eventId: String = "",
    val homeTeam: SportTeamRef = SportTeamRef(),
    val awayTeam: SportTeamRef = SportTeamRef(),
    val homeScore: String = "0",
    val awayScore: String = "0",
    val statusText: String = "",
    val venue: String = "",
    val boxScore: List<SportBoxScoreSection> = emptyList(),
    val plays: List<SportPlay> = emptyList(),
    val teamStats: List<SportStatComparison> = emptyList()
)

data class SportBoxScoreSection(
    val title: String = "",             // "Passing", "Rushing", "Receiving"
    val headers: List<String> = emptyList(),  // ["Player", "C/ATT", "YDS", "TD", "INT"]
    val homeRows: List<List<String>> = emptyList(),
    val awayRows: List<List<String>> = emptyList()
)

data class SportPlay(
    val id: String = "",
    val period: Int = 0,
    val periodText: String = "",        // "1st Quarter", "Top 3rd"
    val clock: String = "",
    val description: String = "",
    val teamLogo: String = "",
    val isScoring: Boolean = false
)

data class SportStatComparison(
    val label: String = "",             // "Total Yards", "Turnovers"
    val homeValue: String = "",
    val awayValue: String = ""
)
