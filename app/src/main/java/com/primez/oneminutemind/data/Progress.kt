package com.primez.oneminutemind.data

import com.primez.oneminutemind.game.GameId
import com.primez.oneminutemind.game.GameResult
import com.primez.oneminutemind.game.Skill
import java.util.Calendar

/** Days are stored as yyyymmdd ints, e.g. 20260928. */
object Days {
    fun key(cal: Calendar): Int =
        cal.get(Calendar.YEAR) * 10_000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)

    fun today(): Int = key(Calendar.getInstance())

    fun offset(day: Int, delta: Int): Int {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(day / 10_000, (day / 100) % 100 - 1, day % 100)
        cal.add(Calendar.DAY_OF_MONTH, delta)
        return key(cal)
    }

    /** Days since 1 Jan 1970 for a yyyymmdd day (same number on every phone). */
    fun epochDay(day: Int): Long {
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(day / 10_000, (day / 100) % 100 - 1, day % 100)
        return cal.timeInMillis / 86_400_000L
    }
}

/** The three looks the player can pick in Settings. */
enum class AppTheme(val label: String, val emoji: String) {
    DAYLIGHT("Daylight", "☀️"),
    MIDNIGHT("Midnight", "🌙"),
    OCEAN("Ocean", "🌊"),
    ;

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: DAYLIGHT
    }
}

/** Online scoreboard identity. [secret] proves it's you when sending scores. */
data class Player(val id: String, val secret: String, val name: String, val state: String)

data class Settings(
    val theme: AppTheme = AppTheme.DAYLIGHT,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val reminder: Boolean = true,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 30,
)

data class Progress(
    val xp: Int = 0,
    val gamesPlayed: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastDailyDay: Int = 0,
    val dailyScores: Map<Int, Int> = emptyMap(),
    val bestScores: Map<GameId, Int> = emptyMap(),
    val playsPerGame: Map<GameId, Int> = emptyMap(),
    /** Last few 0–100 ratings per game, newest last. */
    val recentRatings: Map<GameId, List<Int>> = emptyMap(),
    val achievements: Set<String> = emptySet(),
    val bestCombo: Int = 0,
    val bestMemory: Int = 0,
    val totalCorrect: Int = 0,
    val onboarded: Boolean = false,
    val settings: Settings = Settings(),
    /** Daily games finished so far on [dailyProgressDay], in order. */
    val dailyProgressDay: Int = 0,
    val dailyProgressScores: List<Int> = emptyList(),
    val player: Player? = null,
    /** Last day whose daily total was sent to the scoreboard, and the total sent. */
    val lastSubmittedDay: Int = 0,
    val lastSubmittedScore: Int = 0,
    /** Which try of today's challenge [dailyProgressScores] belongs to (0 = first, 1 = the Premium retry). */
    val dailyAttempt: Int = 0,
    val premium: Boolean = false,
    /** Streak Shields: each one saves the streak for one missed day. */
    val shields: Int = 0,
    val lastOpenRewardDay: Int = 0,
    /** Position (0–6) in the 7-day reward calendar of the last claimed reward. */
    val openRewardIndex: Int = -1,
    val lastShareRewardDay: Int = 0,
    /** Today's ranked result that counts (the better try for Premium): games and their scores. */
    val dailyBestDay: Int = 0,
    val dailyBestGames: List<GameId> = emptyList(),
    val dailyBestScores: List<Int> = emptyList(),
) {
    /** Today's three ranked games with their 0–100 ratings, or empty if today's challenge isn't finished. */
    val todayBrain: List<Pair<GameId, Int>>
        get() {
            val today = Days.today()
            val (games, scores) = when {
                dailyBestDay == today -> dailyBestGames to dailyBestScores
                // finished today on an older app version that didn't save the best try yet
                lastDailyDay == today && dailyProgressDay == today && dailyAttempt == 0 &&
                    dailyProgressScores.size == Daily.GAMES -> Daily.gamesFor(today) to dailyProgressScores
                else -> return emptyList()
            }
            if (games.size != scores.size) return emptyList()
            return games.zip(scores).map { (g, s) -> g to g.ratingFor(s) }
        }

    val hasBrainToday: Boolean get() = todayBrain.isNotEmpty()

    /** Which try of today's challenge is current (0 if none started today). */
    val currentAttempt: Int
        get() = if (dailyProgressDay == Days.today()) dailyAttempt else 0

    /** A Premium retry that has been started but not finished. */
    val retryInProgress: Boolean
        get() = playedDailyToday && currentAttempt > 0 && todayDailyScores.size < Daily.GAMES

    val retriesLeft: Int get() = (Daily.MAX_RETRIES - currentAttempt).coerceAtLeast(0)

    val canStartRetry: Boolean get() = playedDailyToday && !retryInProgress && retriesLeft > 0

    val openRewardAvailable: Boolean get() = lastOpenRewardDay != Days.today()

    /** Calendar position of the reward that can be claimed today. */
    val nextOpenRewardIndex: Int
        get() = if (lastOpenRewardDay == Days.offset(Days.today(), -1)) (openRewardIndex + 1) % 7 else 0

    val shareRewardAvailable: Boolean get() = lastShareRewardDay != Days.today()

    /** Scores of today's daily games already played (0 to 3 entries). */
    val todayDailyScores: List<Int>
        get() = if (dailyProgressDay == Days.today()) dailyProgressScores else emptyList()

    /** Today's best daily total if the scoreboard doesn't have it yet. */
    val pendingSubmission: Int?
        get() {
            val today = Days.today()
            if (lastDailyDay != today) return null
            val best = dailyScores[today] ?: return null
            return if (lastSubmittedDay != today || best > lastSubmittedScore) best else null
        }

    val level: LevelInfo get() = Levels.of(xp)
    val playedDailyToday: Boolean get() = lastDailyDay == Days.today()

    /** Streak that still counts today (it breaks if yesterday was missed). */
    val liveStreak: Int
        get() {
            val today = Days.today()
            return when {
                lastDailyDay == today || lastDailyDay == Days.offset(today, -1) -> streak
                lastDailyDay == Days.offset(today, -2) && shields > 0 -> streak // a Shield will save it
                else -> 0
            }
        }

    /** True when exactly one day was missed and no Shield is left, so a rewarded ad can save the streak. */
    val canSaveStreak: Boolean
        get() {
            val today = Days.today()
            return streak >= 2 && shields == 0 && lastDailyDay == Days.offset(today, -2)
        }

    fun skillRating(skill: Skill): Int {
        val ratings = GameId.entries.filter { it.skill == skill }
            .mapNotNull { g -> recentRatings[g]?.takeIf { it.isNotEmpty() }?.let { it.average() } }
        return if (ratings.isEmpty()) 0 else ratings.average().toInt()
    }

    /**
     * 0–100 Brain Score: how well today's three ranked daily games went (average of their ratings).
     * It changes every day with the daily challenge. 0 until today's challenge is finished.
     */
    val brainScore: Int get() = todayBrain.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.second } / l.size } ?: 0

    /** Whether any game of this skill has been played yet (for the all-games skill profile). */
    fun skillPlayed(skill: Skill): Boolean = GameId.entries.any { it.skill == skill && !recentRatings[it].isNullOrEmpty() }

    fun bestDaily(): Int = dailyScores.values.maxOrNull() ?: 0
}

data class LevelInfo(val level: Int, val intoLevel: Int, val needed: Int, val title: String) {
    val fraction: Float get() = if (needed == 0) 0f else intoLevel.toFloat() / needed
}

object Levels {
    private val titles = listOf(
        "Curious Mind", "Quick Thinker", "Sharp Wit", "Brain Athlete",
        "Mind Master", "Genius", "Mastermind", "Legend",
    )

    fun neededFor(level: Int) = 100 + (level - 1) * 60

    fun titleFor(level: Int) = titles[((level - 1) / 5).coerceIn(0, titles.lastIndex)]

    fun of(xp: Int): LevelInfo {
        var level = 1
        var left = xp
        while (left >= neededFor(level)) {
            left -= neededFor(level); level++
        }
        return LevelInfo(level, left, neededFor(level), titleFor(level))
    }
}

/** XP for a result. Daily games give 50% more. */
fun xpFor(result: GameResult, daily: Boolean): Int {
    val base = maxOf(5, result.score / 4 + result.correct)
    return if (daily) base * 3 / 2 else base
}

data class Achievement(val id: String, val emoji: String, val title: String, val description: String)

object Achievements {
    val all = listOf(
        Achievement("first", "🎉", "First Steps", "Finish your first game"),
        Achievement("games10", "🔟", "Warming Up", "Play 10 games"),
        Achievement("games50", "🏃", "Regular", "Play 50 games"),
        Achievement("games200", "🏋️", "Brain Gym Member", "Play 200 games"),
        Achievement("daily1", "📅", "Daily Habit", "Complete a daily challenge"),
        Achievement("streak3", "🔥", "On Fire", "Reach a 3-day streak"),
        Achievement("streak7", "🌟", "One Week Strong", "Reach a 7-day streak"),
        Achievement("streak30", "👑", "Unstoppable", "Reach a 30-day streak"),
        Achievement("combo10", "⚡", "Combo Starter", "Get 10 right in a row"),
        Achievement("combo25", "💥", "Combo King", "Get 25 right in a row"),
        Achievement("perfect", "💯", "Flawless", "Get 15+ right with no mistakes"),
        Achievement("score300", "🎯", "Sharpshooter", "Score 300 in one game"),
        Achievement("score600", "🚀", "High Flyer", "Score 600 in one game"),
        Achievement("memory8", "🧠", "Elephant Memory", "Remember 8 tiles in Memory Grid"),
        Achievement("allgames", "🗺️", "Explorer", "Play every game at least once"),
        Achievement("level5", "⭐", "Rising Star", "Reach level 5"),
        Achievement("level15", "🌠", "Big Brain", "Reach level 15"),
        Achievement("brain50", "🏆", "Top Mind", "Reach a Brain Score of 50"),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }

    /** Returns ids newly unlocked by the given progress (after a result has been applied). */
    fun check(p: Progress, last: GameResult?): Set<String> {
        val got = mutableSetOf<String>()
        fun unlock(id: String, cond: Boolean) { if (cond && id !in p.achievements) got += id }
        unlock("first", p.gamesPlayed >= 1)
        unlock("games10", p.gamesPlayed >= 10)
        unlock("games50", p.gamesPlayed >= 50)
        unlock("games200", p.gamesPlayed >= 200)
        unlock("daily1", p.lastDailyDay != 0)
        unlock("streak3", p.bestStreak >= 3)
        unlock("streak7", p.bestStreak >= 7)
        unlock("streak30", p.bestStreak >= 30)
        unlock("combo10", p.bestCombo >= 10)
        unlock("combo25", p.bestCombo >= 25)
        unlock("perfect", last != null && last.correct >= 15 && last.wrong == 0)
        unlock("score300", p.bestScores.values.any { it >= 300 })
        unlock("score600", p.bestScores.values.any { it >= 600 })
        unlock("memory8", p.bestMemory >= 8)
        unlock("allgames", GameId.entries.all { (p.playsPerGame[it] ?: 0) > 0 })
        unlock("level5", p.level.level >= 5)
        unlock("level15", p.level.level >= 15)
        unlock("brain50", p.brainScore >= 50)
        return got
    }
}

object Daily {
    const val GAMES = 3
    /** Premium only: one extra try per day. */
    const val MAX_RETRIES = 1

    /**
     * Fixed order where any 3 games in a row train 3 different skills. Each day takes the next 3,
     * so no game repeats from yesterday and every game comes back within 4 days.
     */
    private val ORDER = listOf(
        GameId.COLOUR_CLASH, GameId.NUMBER_RUSH, GameId.QUICK_MATH, GameId.ODD_ONE_OUT, GameId.MEMORY_GRID,
        GameId.TRUE_FALSE, GameId.ARROW_FOCUS, GameId.BIGGER, GameId.MATCH_BACK, GameId.NEXT_NUMBER,
    )

    /** The retry starts further along the order, so it never shares a game with the first try. */
    private val ATTEMPT_OFFSET = listOf(0, 4, 7)

    /** Same games for everyone on the same day. [attempt] 1 is the Premium retry. */
    fun gamesFor(day: Int, attempt: Int = 0): List<GameId> {
        val start = ((Days.epochDay(day) * GAMES + ATTEMPT_OFFSET[attempt.coerceIn(0, 2)]) % ORDER.size).toInt()
        return List(GAMES) { ORDER[(start + it) % ORDER.size] }
    }

    fun seedFor(day: Int, index: Int, attempt: Int = 0): Long = day.toLong() * 100 + attempt * 10 + index
}

/** Daily-open calendar and share rewards. Premium players get double XP. */
object Rewards {
    val openXp = listOf(20, 30, 40, 50, 60, 80, 100)
    const val SHARE_XP = 50

    /** The last day of the 7-day calendar also gives a Streak Shield. */
    fun givesShield(index: Int) = index == 6
}

/** Pure state changes, so the rules can be unit-tested without Android. */
object Rules {
    fun applyGame(p: Progress, r: GameResult, xp: Int): Progress {
        val ratings = ((p.recentRatings[r.game] ?: emptyList()) + r.rating).takeLast(10)
        return p.copy(
            xp = p.xp + xp,
            gamesPlayed = p.gamesPlayed + 1,
            bestScores = p.bestScores + (r.game to maxOf(p.bestScores[r.game] ?: 0, r.score)),
            playsPerGame = p.playsPerGame + (r.game to (p.playsPerGame[r.game] ?: 0) + 1),
            recentRatings = p.recentRatings + (r.game to ratings),
            bestCombo = maxOf(p.bestCombo, r.maxCombo),
            bestMemory = maxOf(p.bestMemory, r.bestMemory),
            totalCorrect = p.totalCorrect + r.correct,
        )
    }

    /**
     * Saves one finished daily game straight away, so leaving half-way never loses it.
     * After the last game the day is marked done and the streak goes up.
     */
    fun recordDailyGame(p: Progress, score: Int, today: Int = Days.today()): Progress {
        val attempt = if (p.dailyProgressDay == today) p.dailyAttempt else 0
        if (attempt == 0 && p.lastDailyDay == today) return p
        val done = (if (p.dailyProgressDay == today) p.dailyProgressScores else emptyList()) + score
        if (done.size > Daily.GAMES) return p
        var next = p.copy(dailyProgressDay = today, dailyProgressScores = done, dailyAttempt = attempt)
        if (done.size < Daily.GAMES) return next
        val games = Daily.gamesFor(today, attempt)
        val beatsBest = next.dailyBestDay != today || done.sum() > next.dailyBestScores.sum()
        next = if (beatsBest) next.copy(dailyBestDay = today, dailyBestGames = games, dailyBestScores = done) else next
        return if (attempt == 0) {
            completeDaily(next, done.sum(), today)
        } else {
            // Premium retry: the best total of the day counts.
            val best = maxOf(next.dailyScores[today] ?: 0, done.sum())
            next.copy(dailyScores = next.dailyScores + (today to best))
        }
    }

    /** Premium: start another try of today's challenge with different games. */
    fun startRetry(p: Progress, today: Int = Days.today()): Progress =
        if (!p.premium || !p.canStartRetry) p
        else p.copy(dailyProgressDay = today, dailyAttempt = p.currentAttempt + 1, dailyProgressScores = emptyList())

    /** Daily-open reward. Returns the new progress, the XP given and whether a Shield was given. */
    fun claimOpenReward(p: Progress, today: Int = Days.today()): Triple<Progress, Int, Boolean> {
        if (!p.openRewardAvailable) return Triple(p, 0, false)
        val i = p.nextOpenRewardIndex
        val xp = Rewards.openXp[i] * (if (p.premium) 2 else 1)
        val shield = Rewards.givesShield(i)
        val next = p.copy(
            xp = p.xp + xp, lastOpenRewardDay = today, openRewardIndex = i,
            shields = p.shields + if (shield) 1 else 0,
        )
        return Triple(next, xp, shield)
    }

    /** Once a day for sharing. Returns the new progress and the XP given (0 if already claimed). */
    fun claimShareReward(p: Progress, today: Int = Days.today()): Pair<Progress, Int> {
        if (!p.shareRewardAvailable) return p to 0
        val xp = Rewards.SHARE_XP * (if (p.premium) 2 else 1)
        return p.copy(xp = p.xp + xp, lastShareRewardDay = today) to xp
    }

    fun completeDaily(p: Progress, total: Int, today: Int = Days.today()): Progress {
        if (p.lastDailyDay == today) return p
        val keptByShield = p.lastDailyDay == Days.offset(today, -2) && p.shields > 0 && p.streak > 0
        val newStreak = if (p.lastDailyDay == Days.offset(today, -1) || keptByShield) p.streak + 1 else 1
        val scores = (p.dailyScores + (today to total)).entries
            .sortedByDescending { it.key }.take(60).associate { it.key to it.value }
        return p.copy(
            shields = if (keptByShield) p.shields - 1 else p.shields,
            streak = newStreak,
            bestStreak = maxOf(p.bestStreak, newStreak),
            lastDailyDay = today,
            dailyScores = scores,
        )
    }

    /** Rewarded ad: pretend yesterday was played so the streak continues today. */
    fun saveStreak(p: Progress, today: Int = Days.today()): Progress =
        if (p.canSaveStreak) p.copy(lastDailyDay = Days.offset(today, -1)) else p

    fun unlock(p: Progress, ids: Set<String>) = p.copy(achievements = p.achievements + ids)
}
