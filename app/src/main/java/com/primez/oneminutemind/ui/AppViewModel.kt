package com.primez.oneminutemind.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.primez.oneminutemind.ads.Ads
import com.primez.oneminutemind.billing.Billing
import com.primez.oneminutemind.notify.Reminder
import com.primez.oneminutemind.share.ShareCard
import com.primez.oneminutemind.online.Scoreboard
import com.primez.oneminutemind.online.ScoreboardException
import kotlinx.coroutines.launch

enum class Mode { PRACTICE, DAILY }

sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data object Stats : Screen
    data object Awards : Screen
    data object Settings : Screen
    data object Board : Screen
    data object Premium : Screen
    /** [key] makes every new game a fresh screen, even for the same game twice. */
    data class Play(val game: GameId, val mode: Mode, val seed: Long, val key: Long) : Screen
    data class Result(val info: ResultInfo) : Screen
    data class DailyDone(val total: Int, val streak: Int, val games: List<Pair<GameId, Int>>) : Screen
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

    private var gameCounter = 0L

    private var submitting = false

    /** Short message shown at the bottom of the screen (e.g. "+50 XP for sharing!"). */
    var toast by mutableStateOf<String?>(null)

    init {
        applySettings(progress.settings, reschedule = true)
        Ads.premium = progress.premium
        Billing.onPremium = { on -> if (on && !progress.premium) setPremium(true) }
        Billing.start(app)
        submitPendingScore()
        syncPremiumToScoreboard()
    }

    // ---------- premium & rewards ----------

    /** Turns Premium on/off on this phone (and on the scoreboard, for the crown). */
    fun setPremium(on: Boolean) {
        save(progress.copy(premium = on))
        Ads.premium = on
        if (on) toast = "👑 Welcome to Premium!"
        syncPremiumToScoreboard()
    }

    private fun syncPremiumToScoreboard() {
        val player = progress.player ?: return
        if (!Scoreboard.configured) return
        val on = progress.premium
        viewModelScope.launch {
            try { Scoreboard.setPremium(player, on) } catch (_: ScoreboardException) {}
        }
    }

    /** Daily-open reward. Returns (xp, gotShield) or null if already claimed today. */
    fun claimOpenReward(): Pair<Int, Boolean>? {
        if (!progress.openRewardAvailable) return null
        val (p, xp, shield) = Rules.claimOpenReward(progress)
        save(Rules.unlock(p, Achievements.check(p, null)))
        return xp to shield
    }

    /** Opens WhatsApp or the share menu with the Brain Score picture; gives the daily share reward. */
    fun share(context: android.content.Context, toWhatsApp: Boolean, headline: String? = null) {
        if (!ShareCard.share(context, progress, toWhatsApp, headline)) {
            toast = "Couldn't open sharing on this phone."
            return
        }
        val (p, xp) = Rules.claimShareReward(progress)
        if (xp > 0) {
            save(p)
            toast = "🎁 +$xp XP for sharing! Come back tomorrow for more."
        }
    }

    // ---------- scoreboard ----------

    /** Sends today's finished daily total if it hasn't been sent yet (retries on next launch if offline). */
    fun submitPendingScore() {
        val player = progress.player ?: return
        val total = progress.pendingSubmission ?: return
        if (submitting || !Scoreboard.configured) return
        submitting = true
        val day = Days.today()
        viewModelScope.launch {
            try {
                Scoreboard.submit(player, day, total)
                save(progress.copy(lastSubmittedDay = day, lastSubmittedScore = total))
            } catch (_: ScoreboardException) {
            } finally {
                submitting = false
            }
        }
    }

    /** Sign up with a username and state. [done] gets an error message, or null on success. */
    fun signUp(name: String, state: String, done: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val existing = progress.player
                val player = if (existing == null) {
                    Scoreboard.register(name, state)
                } else {
                    Scoreboard.update(existing, name, state)
                    existing.copy(name = name.trim(), state = state)
                }
                save(progress.copy(player = player))
                submitPendingScore()
                syncPremiumToScoreboard()
                done(null)
            } catch (e: ScoreboardException) {
                done(e.message)
            }
        }
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

    /** Games of the current try of today's challenge (retries use a different set). */
    val todaysGames: List<GameId> get() = Daily.gamesFor(Days.today(), progress.currentAttempt)

    /** Index of the next daily game to play today (0..2), or 3 when all are done. */
    val nextDailyIndex: Int get() = progress.todayDailyScores.size

    /** Starts (or continues) today's challenge, or a started Premium retry, at the next game. */
    fun startDaily() {
        val p = progress
        val i = nextDailyIndex
        val canPlay = (!p.playedDailyToday || p.retryInProgress) && i < Daily.GAMES
        if (!canPlay) return
        go(playScreen(todaysGames[i], Mode.DAILY, Daily.seedFor(Days.today(), i, p.currentAttempt)))
    }

    /** Premium: play today's challenge again with 3 different games. The best total counts. */
    fun startRetry() {
        if (!progress.premium) { go(Screen.Premium); return }
        val p = Rules.startRetry(progress)
        if (p == progress) return
        save(p)
        startDaily()
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
        val dailyIndex = before.todayDailyScores.size
        val xp = xpFor(result, mode == Mode.DAILY)
        var p = Rules.applyGame(before, result, xp)
        if (mode == Mode.DAILY) p = Rules.recordDailyGame(p, result.score)
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
        if (p.playedDailyToday) submitPendingScore()
    }

    fun nextDailyGame() {
        val i = nextDailyIndex
        if (i >= Daily.GAMES) {
            val scores = progress.todayDailyScores
            replace(Screen.DailyDone(scores.sum(), progress.streak, todaysGames.zip(scores)))
        } else {
            replace(playScreen(todaysGames[i], Mode.DAILY, Daily.seedFor(Days.today(), i)))
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
        save(Progress(onboarded = true, settings = keep, player = progress.player, premium = progress.premium))
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
