package com.primez.oneminutemind.data

import com.primez.oneminutemind.game.GameId
import com.primez.oneminutemind.game.GameResult
import com.primez.oneminutemind.game.Skill
import java.util.Calendar
import kotlin.random.Random

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
    /** Last day whose daily total was sent to the scoreboard. */
    val lastSubmittedDay: Int = 0,
) {
    /** Scores of today's daily games already played (0 to 3 entries). */
    val todayDailyScores: List<Int>
        get() = if (dailyProgressDay == Days.today()) dailyProgressScores else emptyList()

    /** Today's finished daily total that still has to be sent to the scoreboard, if any. */
    val pendingSubmission: Int?
        get() {
            val today = Days.today()
            return if (lastDailyDay == today && lastSubmittedDay != today) dailyScores[today] else null
        }

    val level: LevelInfo get() = Levels.of(xp)
    val playedDailyToday: Boolean get() = lastDailyDay == Days.today()

    /** Streak that still counts today (it breaks if yesterday was missed). */
    val liveStreak: Int
        get() {
            val today = Days.today()
            return if (lastDailyDay == today || lastDailyDay == Days.offset(today, -1)) streak else 0
        }

    /** True when exactly one day was missed, so a rewarded ad can save the streak. */
    val canSaveStreak: Boolean
        get() {
            val today = Days.today()
            return streak >= 2 && lastDailyDay == Days.offset(today, -2)
        }

    fun skillRating(skill: Skill): Int {
        val ratings = GameId.entries.filter { it.skill == skill }
            .mapNotNull { g -> recentRatings[g]?.takeIf { it.isNotEmpty() }?.let { it.average() } }
        return if (ratings.isEmpty()) 0 else ratings.average().toInt()
    }

    val brainScore: Int get() = Skill.entries.sumOf { skillRating(it) } * 2

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
        Achievement("brain500", "🏆", "Top Mind", "Reach a Brain Score of 500"),
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
        unlock("brain500", p.brainScore >= 500)
        return got
    }
}

object Daily {
    const val GAMES = 3

    /** Same three games for everyone on the same day. */
    fun gamesFor(day: Int): List<GameId> =
        GameId.entries.shuffled(Random(day.toLong() * 7919L)).take(GAMES)

    fun seedFor(day: Int, index: Int): Long = day.toLong() * 100 + index
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
        if (p.lastDailyDay == today) return p
        val done = (if (p.dailyProgressDay == today) p.dailyProgressScores else emptyList()) + score
        val next = p.copy(dailyProgressDay = today, dailyProgressScores = done)
        return if (done.size >= Daily.GAMES) completeDaily(next, done.sum(), today) else next
    }

    fun completeDaily(p: Progress, total: Int, today: Int = Days.today()): Progress {
        if (p.lastDailyDay == today) return p
        val newStreak = if (p.lastDailyDay == Days.offset(today, -1)) p.streak + 1 else 1
        val scores = (p.dailyScores + (today to total)).entries
            .sortedByDescending { it.key }.take(60).associate { it.key to it.value }
        return p.copy(
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
