package com.primez.oneminutemind.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primez.oneminutemind.ads.Ads
import com.primez.oneminutemind.ads.findActivity
import com.primez.oneminutemind.data.Daily
import com.primez.oneminutemind.game.ChoiceRound
import com.primez.oneminutemind.game.GameSession
import com.primez.oneminutemind.game.MemoryRound
import com.primez.oneminutemind.game.OddRound
import com.primez.oneminutemind.game.OptionLayout
import com.primez.oneminutemind.game.Outcome
import com.primez.oneminutemind.game.ROUND_MS
import com.primez.oneminutemind.game.RushRound
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Phase { INTRO, COUNTDOWN, PLAYING, TIME_UP, DONE }

private const val CONTINUE_MS = 15_000L

@Composable
fun GameScreen(vm: AppViewModel, play: Screen.Play) {
    val game = play.game
    val session = remember(play.key) { GameSession(game, play.seed) }
    val activity = LocalContext.current.findActivity()
    val fb = vm.feedback

    var phase by remember(play.key) { mutableStateOf(Phase.INTRO) }
    var remaining by remember(play.key) { mutableLongStateOf(ROUND_MS) }
    var paused by remember(play.key) { mutableStateOf(false) }
    var roundIndex by remember(play.key) { mutableIntStateOf(0) }
    var countdown by remember(play.key) { mutableIntStateOf(3) }
    var usedContinue by remember(play.key) { mutableStateOf(false) }
    var flashKey by remember(play.key) { mutableIntStateOf(0) }
    var flashGood by remember(play.key) { mutableStateOf(true) }
    var popText by remember(play.key) { mutableStateOf("") }

    fun finish() {
        if (phase == Phase.DONE) return
        phase = Phase.DONE
        val result = session.result()
        if (play.mode == Mode.PRACTICE) Ads.maybeShowInterstitial(activity) { vm.finishGame(result, play.mode) }
        else vm.finishGame(result, play.mode)
    }

    fun applyOutcome(o: Outcome) {
        when {
            o.correct && o.comboUp -> fb.comboUp()
            o.correct -> fb.correct()
            else -> fb.wrong()
        }
        if (o.penaltyMs > 0) remaining = (remaining - o.penaltyMs).coerceAtLeast(0)
        flashGood = o.correct
        popText = if (o.correct) (if (o.points > 0) "+${o.points}" else "") else "−${o.penaltyMs / 1000}s"
        flashKey++
    }

    fun answer(idx: Int, correct: Boolean) {
        // Ignore taps on a round that is already sliding away.
        if (phase != Phase.PLAYING || idx != roundIndex) return
        applyOutcome(session.answer(correct))
        roundIndex++
    }

    // Leaving a daily game after it has started ends it with the score so far
    // (so nobody can quit and retry a bad run for the scoreboard).
    fun quit() {
        if (play.mode == Mode.DAILY && (phase == Phase.PLAYING || phase == Phase.TIME_UP)) finish()
        else if (phase != Phase.DONE) vm.back()
    }

    BackHandler { quit() }

    // 3-2-1 countdown
    LaunchedEffect(phase) {
        if (phase == Phase.COUNTDOWN) {
            for (i in 3 downTo 1) { countdown = i; fb.tap(); delay(650) }
            phase = Phase.PLAYING
        }
    }

    // The clock. Pauses while Memory Grid shows its tiles.
    LaunchedEffect(phase, paused) {
        if (phase != Phase.PLAYING || paused) return@LaunchedEffect
        var last = SystemClock.uptimeMillis()
        while (remaining > 0) {
            delay(50)
            val now = SystemClock.uptimeMillis()
            remaining = (remaining - (now - last)).coerceAtLeast(0)
            last = now
        }
        fb.finish()
        phase = Phase.TIME_UP
    }

    // Time up: practice players may watch an ad for +15 s once, otherwise go to results.
    LaunchedEffect(phase) {
        if (phase == Phase.TIME_UP) {
            val canContinue = play.mode == Mode.PRACTICE && !usedContinue && Ads.canReward
            if (!canContinue) { delay(900); finish() }
        }
    }

    val flash = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(flashKey) {
        if (flashKey == 0) return@LaunchedEffect
        flash.snapTo(1f); pop.snapTo(0f)
        coroutineScope {
            launch { flash.animateTo(0f, tween(350)) }
            launch { pop.animateTo(1f, tween(650)) }
        }
    }

    val skillColor = Brand.skill(game.skill)

    Box(Modifier.fillMaxSize()) {
        // coloured flash behind everything on each answer
        Box(
            Modifier.fillMaxSize().alpha(flash.value * 0.22f)
                .background(if (flashGood) Brand.good else Brand.bad),
        )
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            // ---------- HUD ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::quit) { Icon(Icons.Filled.Close, "Quit") }
                Text(game.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (play.mode == Mode.DAILY) Pill("Daily ${vmDailyNumber(vm, play)}/${Daily.GAMES}", Brand.coral)
            }
            if (phase == Phase.PLAYING || phase == Phase.TIME_UP || phase == Phase.DONE) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val secs = (remaining + 999) / 1000
                    Text(
                        "${secs / 60}:${(secs % 60).toString().padStart(2, '0')}",
                        style = MonoNumbers.copy(fontSize = 26.sp),
                        color = if (secs <= 10) Brand.coral else MaterialTheme.colorScheme.onBackground,
                    )
                    Box(Modifier.weight(1f))
                    if (session.multiplier > 1) {
                        Pill("×${session.multiplier}  🔥${session.combo}", Brand.gold)
                        HGap(10.dp)
                    }
                    Text("${session.score}", style = MonoNumbers.copy(fontSize = 26.sp))
                }
                Gap(6.dp)
                Bar(remaining / ROUND_MS.toFloat(), if (remaining <= 10_000) Brand.coral else skillColor, height = 8.dp)
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (phase) {
                    Phase.INTRO -> Intro(vm, play) { phase = Phase.COUNTDOWN }
                    Phase.COUNTDOWN -> AnimatedContent(
                        countdown, transitionSpec = { scaleIn() + fadeIn() togetherWith fadeOut() }, label = "cd",
                    ) { n -> Text("$n", fontSize = 110.sp, fontWeight = FontWeight.Black, color = skillColor) }
                    Phase.PLAYING -> AnimatedContent(
                        targetState = roundIndex to session.round,
                        contentKey = { it.first },
                        transitionSpec = {
                            (slideInHorizontally { it / 3 } + fadeIn(tween(160))) togetherWith
                                (slideOutHorizontally { -it / 3 } + fadeOut(tween(120)))
                        },
                        label = "round",
                    ) { (idx, r) ->
                        key(idx) {
                            when (r) {
                                is ChoiceRound -> ChoiceView(r) { answer(idx, it == r.correct) }
                                is MemoryRound -> MemoryView(r, skillColor, onShowing = { paused = it }, onTap = fb::tap) { answer(idx, it) }
                                is OddRound -> OddView(r) { answer(idx, it) }
                                is RushRound -> RushView(r, skillColor, onTap = fb::tap, onMistake = {
                                    if (phase == Phase.PLAYING && idx == roundIndex) applyOutcome(session.rushMistake())
                                }) { answer(idx, true) }
                            }
                        }
                    }
                    Phase.TIME_UP, Phase.DONE -> TimeUp(
                        score = session.score,
                        offerContinue = phase == Phase.TIME_UP && play.mode == Mode.PRACTICE && !usedContinue && Ads.canReward,
                        onContinue = {
                            Ads.showRewarded(activity, onClosed = {
                                if (phase == Phase.TIME_UP && !usedContinue) finish()
                            }) {
                                usedContinue = true
                                remaining = CONTINUE_MS
                                phase = Phase.COUNTDOWN
                            }
                        },
                        onResults = ::finish,
                    )
                }
                // floating "+20" / "−2s"
                if (popText.isNotEmpty() && phase == Phase.PLAYING && pop.value < 1f) {
                    Text(
                        popText,
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = (40 - 60 * pop.value).dp).alpha(1f - pop.value),
                        color = if (flashGood) Brand.good else Brand.bad,
                        fontSize = 28.sp, fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

private fun vmDailyNumber(vm: AppViewModel, play: Screen.Play): Int =
    (vm.todaysGames.indexOf(play.game) + 1).coerceAtLeast(1)

@Composable
private fun Intro(vm: AppViewModel, play: Screen.Play, onStart: () -> Unit) {
    val g = play.game
    val best = vm.progress.bestScores[g] ?: 0
    Column(Modifier.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EmojiBadge(g.emoji, Brand.skill(g.skill), 96.dp)
        Gap(16.dp)
        Text(g.title, style = MaterialTheme.typography.headlineLarge)
        Gap(6.dp)
        Pill("${g.skill.emoji} ${g.skill.label}", Brand.skill(g.skill))
        Gap(18.dp)
        Text(g.howTo, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        Gap(10.dp)
        Text(
            "60 seconds · streaks of 5 and 10 multiply your points · mistakes cost time",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (best > 0) { Gap(10.dp); Text("Your best: $best", fontWeight = FontWeight.Bold, color = Brand.gold) }
        if (play.mode == Mode.DAILY) {
            Gap(10.dp)
            Pill(if (vm.progress.premium) "Ranked · your best try counts" else "One try only · counts for the scoreboard", Brand.coral)
        }
        Gap(28.dp)
        BigButton("Start", onClick = onStart, color = Brand.skill(g.skill))
    }
}

@Composable
private fun TimeUp(score: Int, offerContinue: Boolean, onContinue: () -> Unit, onResults: () -> Unit) {
    Column(Modifier.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⏰", fontSize = 64.sp)
        Text("Time's up!", style = MaterialTheme.typography.headlineLarge)
        Gap(4.dp)
        Text("Score $score", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (offerContinue) {
            Gap(28.dp)
            BigButton(Ads.rewardLabel("+15 seconds"), onClick = onContinue, color = Brand.coral)
            Gap(10.dp)
            GhostButton("See results", onClick = onResults)
        }
    }
}

// ---------------- round views ----------------

@Composable
private fun ChoiceView(r: ChoiceRound, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        r.caption?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            Gap(10.dp)
        }
        val promptSize = when {
            !r.bigPrompt -> 30.sp
            r.prompt.length > 12 -> 40.sp
            else -> 60.sp
        }
        Text(
            r.prompt,
            fontSize = promptSize,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = r.promptInk?.let { Brand.ink[it] } ?: MaterialTheme.colorScheme.onBackground,
            lineHeight = promptSize * 1.1f,
        )
        Gap(36.dp)
        val longest = r.options.maxOf { it.length }
        val optSize = if (longest > 8) 22.sp else if (longest > 4) 26.sp else 32.sp
        if (r.layout == OptionLayout.ROW || r.options.size <= 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                r.options.forEachIndexed { i, o -> OptionButton(o, optSize, Modifier.weight(1f).height(110.dp)) { onPick(i) } }
            }
        } else {
            r.options.chunked(2).forEachIndexed { row, pair ->
                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { j, o ->
                        OptionButton(o, optSize, Modifier.weight(1f).height(88.dp)) { onPick(row * 2 + j) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionButton(text: String, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = size, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Square grid of [side]×[side] cells. */
@Composable
private fun SquareGrid(side: Int, cell: @Composable (index: Int, modifier: Modifier) -> Unit) {
    Column(
        Modifier.fillMaxWidth().widthIn(max = 460.dp).aspectRatio(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (row in 0 until side) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until side) cell(row * side + col, Modifier.weight(1f).fillMaxSize())
            }
        }
    }
}

@Composable
private fun MemoryView(
    r: MemoryRound,
    color: Color,
    onShowing: (Boolean) -> Unit,
    onTap: () -> Unit,
    onDone: (Boolean) -> Unit,
) {
    var showing by remember { mutableStateOf(true) }
    var found by remember { mutableStateOf(setOf<Int>()) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        onShowing(true); delay(r.showMs); showing = false; onShowing(false)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (showing) "Remember ${r.lit.size} tiles…" else "Tap the ${r.lit.size} tiles (${found.size}/${r.lit.size})",
            style = MaterialTheme.typography.titleMedium,
        )
        Gap(16.dp)
        SquareGrid(r.side) { i, m ->
            val lit = (showing && i in r.lit) || i in found
            Box(
                m.clip(RoundedCornerShape(14.dp))
                    .background(if (lit) color else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = !showing && !finished && i !in found) {
                        if (i in r.lit) {
                            onTap()
                            found = found + i
                            if (found.size == r.lit.size) { finished = true; onDone(true) }
                        } else {
                            finished = true; onDone(false)
                        }
                    },
            )
        }
    }
}

@Composable
private fun OddView(r: OddRound, onDone: (Boolean) -> Unit) {
    val base = Color.hsv(r.hue, r.saturation, r.value)
    val odd = Color.hsv(r.hue, r.saturation, r.oddValue)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Tap the different shade", style = MaterialTheme.typography.titleMedium)
        Gap(16.dp)
        SquareGrid(r.side) { i, m ->
            Box(
                m.clip(RoundedCornerShape(12.dp))
                    .background(if (i == r.oddIndex) odd else base)
                    .clickable { onDone(i == r.oddIndex) },
            )
        }
    }
}

@Composable
private fun RushView(r: RushRound, color: Color, onTap: () -> Unit, onMistake: () -> Unit, onCleared: () -> Unit) {
    var next by remember { mutableIntStateOf(1) }
    val total = r.numbers.size
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (next <= total) "Tap $next" else "Cleared!", style = MaterialTheme.typography.titleLarge)
        Gap(16.dp)
        SquareGrid(r.side) { i, m ->
            val n = r.numbers[i]
            val done = n < next
            Box(
                m.clip(RoundedCornerShape(14.dp))
                    .background(if (done) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface)
                    .border(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .clickable(enabled = !done && next <= total) {
                        if (n == next) {
                            onTap(); next++
                            if (next > total) onCleared()
                        } else onMistake()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$n",
                    fontSize = if (r.side >= 5) 22.sp else 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
