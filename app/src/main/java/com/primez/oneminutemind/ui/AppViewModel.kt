package com.primez.oneminutemind.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.primez.oneminutemind.data.Achievements
import com.primez.oneminutemind.data.Daily
import com.primez.oneminutemind.data.Days
import com.primez.oneminutemind.data.Progress
import com.primez.oneminutemind.data.Rules
import com.primez.oneminutemind.data.Settings
import com.primez.oneminutemind.data.Store
import com.primez.oneminutemind.data.xpFor
import com.primez.oneminutemind.game.GameId
import com.primez.oneminutemind.game.GameResult
import com.primez.oneminutemind.notify.Reminder

enum class Mode { PRACTICE, DAILY }

sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data object Stats : Screen
    data object Awards : Screen
    data object Settings : Screen
    /** [key] makes every new game a fresh screen, even for the same game twice. */
    data class Play(val game: GameId, val mode: Mode, val seed: Long, val key: Long) : Screen
    data class Result(val info: ResultInfo) : Screen
    data class DailyDone(val total: Int, val streak: Int, val games: List<GameResult>) : Screen
}

data class ResultInfo(
    val result: GameResult,
    val mode: Mode,
    val xp: Int,
    val newBest: Boolean,
    val previousBest: Int,
    val levelUp: Int?,
    val unlocked: List<String>,
    val doubled: Boolean = false,
    /** Daily only: which of the 3 games this was (0-based). */
    val dailyIndex: Int = 0,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val feedback = Feedback(app)

    var progress by mutableStateOf(store.load())
        private set

    private val stack = mutableStateListOf<Screen>(if (progress.onboarded) Screen.Home else Screen.Onboarding)
    val screen: Screen get() = stack.last()

    /** Set after the first game so the app can ask for notification permission once. */
    var askNotificationPermission by mutableStateOf(false)

    private var dailyResults = mutableListOf<GameResult>()
    private var dailyIndex = 0
    private var gameCounter = 0L

    init {
        applySettings(progress.settings, reschedule = true)
    }

    // ---------- navigation ----------

    fun go(s: Screen) { stack.add(s) }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun home() {
        stack.clear(); stack.add(Screen.Home)
    }

    private fun replace(s: Screen) {
        stack.removeAt(stack.lastIndex); stack.add(s)
    }

    // ---------- playing ----------

    val todaysGames: List<GameId> get() = Daily.gamesFor(Days.today())

    fun startDaily() {
        if (progress.playedDailyToday) return
        dailyResults = mutableListOf()
        dailyIndex = 0
        go(playScreen(todaysGames[0], Mode.DAILY, Daily.seedFor(Days.today(), 0)))
    }

    fun startPractice(game: GameId) {
        go(playScreen(game, Mode.PRACTICE, System.nanoTime()))
    }

    fun playAgain(game: GameId) {
        replace(playScreen(game, Mode.PRACTICE, System.nanoTime()))
    }

    private fun playScreen(game: GameId, mode: Mode, seed: Long) =
        Screen.Play(game, mode, seed, ++gameCounter)

    fun finishGame(result: GameResult, mode: Mode) {
        val before = progress
        val xp = xpFor(result, mode == Mode.DAILY)
        var p = Rules.applyGame(before, result, xp)
        if (mode == Mode.DAILY) dailyResults.add(result)
        val finishingDaily = mode == Mode.DAILY && dailyResults.size >= Daily.GAMES
        if (finishingDaily) p = Rules.completeDaily(p, dailyResults.sumOf { it.score })
        val unlocked = Achievements.check(p, result)
        p = Rules.unlock(p, unlocked)
        save(p)
        if (before.gamesPlayed == 0) askNotificationPermission = true

        val prevBest = before.bestScores[result.game] ?: 0
        val info = ResultInfo(
            result = result, mode = mode, xp = xp,
            newBest = result.score > prevBest && prevBest > 0,
            previousBest = prevBest,
            levelUp = p.level.level.takeIf { it > before.level.level },
            unlocked = unlocked.toList(),
            dailyIndex = dailyIndex,
        )
        replace(Screen.Result(info))
    }

    fun nextDailyGame() {
        dailyIndex++
        if (dailyIndex >= Daily.GAMES) {
            replace(Screen.DailyDone(dailyResults.sumOf { it.score }, progress.streak, dailyResults.toList()))
        } else {
            replace(playScreen(todaysGames[dailyIndex], Mode.DAILY, Daily.seedFor(Days.today(), dailyIndex)))
        }
    }

    /** Rewarded ad on the result screen: add the same XP again. */
    fun doubleXp(info: ResultInfo) {
        if (info.doubled) return
        var p = progress.copy(xp = progress.xp + info.xp)
        val unlocked = Achievements.check(p, null)
        p = Rules.unlock(p, unlocked)
        save(p)
        replace(Screen.Result(info.copy(doubled = true, unlocked = info.unlocked + unlocked)))
    }

    fun saveStreak() = save(Rules.saveStreak(progress))

    // ---------- settings ----------

    fun finishOnboarding() {
        save(progress.copy(onboarded = true))
        home()
    }

    fun updateSettings(s: Settings) {
        val old = progress.settings
        save(progress.copy(settings = s))
        applySettings(s, reschedule = old.reminder != s.reminder ||
            old.reminderHour != s.reminderHour || old.reminderMinute != s.reminderMinute)
    }

    private fun applySettings(s: Settings, reschedule: Boolean) {
        feedback.sound = s.sound
        feedback.haptics = s.haptics
        if (reschedule) Reminder.schedule(getApplication(), s.reminder, s.reminderHour, s.reminderMinute)
    }

    fun resetProgress() {
        val keep = progress.settings
        store.clear()
        save(Progress(onboarded = true, settings = keep))
        home()
    }

    private fun save(p: Progress) {
        progress = p
        store.save(p)
    }

    override fun onCleared() {
        feedback.release()
    }
}
