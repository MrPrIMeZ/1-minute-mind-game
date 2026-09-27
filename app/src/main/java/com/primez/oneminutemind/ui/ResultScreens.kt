package com.primez.oneminutemind.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primez.oneminutemind.ads.Ads
import com.primez.oneminutemind.ads.BannerAd
import com.primez.oneminutemind.ads.findActivity
import com.primez.oneminutemind.data.Achievements
import com.primez.oneminutemind.data.Daily
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ResultScreen(vm: AppViewModel, info: ResultInfo) {
    val r = info.result
    val activity = LocalContext.current.findActivity()
    val p = vm.progress
    val color = Brand.skill(r.game.skill)
    val lastDaily = info.mode == Mode.DAILY && info.dailyIndex >= Daily.GAMES - 1

    BackHandler { if (info.mode == Mode.DAILY) vm.nextDailyGame() else vm.back() }

    val shown = remember(info.result) { Animatable(0f) }
    LaunchedEffect(info.result) { shown.animateTo(r.score.toFloat(), tween(1100, easing = FastOutSlowInEasing)) }

    Box(Modifier.fillMaxSize()) {
        if (info.newBest || info.levelUp != null) Confetti()
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).statusBarsPadding().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (info.mode == Mode.DAILY) "Daily game ${info.dailyIndex + 1} of ${Daily.GAMES}" else r.game.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(8.dp)
                Text("${shown.value.toInt()}", fontSize = 84.sp, fontWeight = FontWeight.Black, color = color)
                Text("points", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(8.dp)
                when {
                    info.newBest -> Pill("🏆 New best! (was ${info.previousBest})", Brand.gold)
                    info.previousBest == 0 -> Pill("First score set!", Brand.teal)
                    else -> Text("Best: ${maxOf(info.previousBest, r.score)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Gap(20.dp)
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat("${r.correct}", "correct", color = Brand.good)
                        Stat("${r.accuracy}%", "accuracy")
                        Stat("${r.maxCombo}", "best combo", color = Brand.gold)
                    }
                }
                Gap()
                Card {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "+${if (info.doubled) info.xp * 2 else info.xp} XP" + if (info.doubled) "  (doubled!)" else "",
                                style = MaterialTheme.typography.titleLarge, color = Brand.violet, modifier = Modifier.weight(1f),
                            )
                            Text("Level ${p.level.level}", fontWeight = FontWeight.Bold)
                        }
                        Gap(8.dp)
                        Bar(p.level.fraction, Brand.violet)
                        if (info.levelUp != null) {
                            Gap(10.dp)
                            Text(
                                "⭐ Level up! You are now level ${info.levelUp} · ${p.level.title}",
                                fontWeight = FontWeight.Bold, color = Brand.gold,
                            )
                        }
                    }
                }
                if (info.unlocked.isNotEmpty()) {
                    Gap()
                    Card {
                        Column {
                            Text("Achievement unlocked!", style = MaterialTheme.typography.titleMedium)
                            info.unlocked.mapNotNull { Achievements.byId(it) }.forEach { a ->
                                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(a.emoji, fontSize = 28.sp); HGap()
                                    Column {
                                        Text(a.title, fontWeight = FontWeight.Bold)
                                        Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                Gap(20.dp)
                if (!info.doubled && Ads.rewardedReady) {
                    BigButton("🎬  Watch an ad: double XP", onClick = {
                        Ads.showRewarded(activity) { vm.doubleXp(info) }
                    }, color = Brand.coral)
                    Gap(10.dp)
                }
                if (info.mode == Mode.DAILY) {
                    BigButton(if (lastDaily) "Finish daily challenge" else "Next game ›", onClick = vm::nextDailyGame, color = color)
                } else {
                    BigButton("Play again", onClick = { vm.playAgain(r.game) }, color = color)
                    Gap(10.dp)
                    GhostButton("Home", onClick = vm::home)
                }
            }
            BannerAd()
        }
    }
}

@Composable
fun DailyDoneScreen(vm: AppViewModel, s: Screen.DailyDone) {
    val context = LocalContext.current
    BackHandler { vm.home() }
    Box(Modifier.fillMaxSize()) {
        Confetti()
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).statusBarsPadding().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Gap(20.dp)
                Text("🔥", fontSize = 72.sp)
                Text("${s.streak}-day streak!", style = MaterialTheme.typography.headlineLarge)
                Text("Daily challenge complete", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(24.dp)
                Card {
                    Column {
                        Text("Total", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${s.total}", fontSize = 56.sp, fontWeight = FontWeight.Black, color = Brand.violet)
                        s.games.forEach { (game, score) ->
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${game.emoji}  ${game.title}", Modifier.weight(1f))
                                Text("$score", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Gap(24.dp)
                BigButton("Share my score", onClick = {
                    val text = "🧠 I scored ${s.total} in today's 1 Minute Mind Game challenge " +
                        "and I'm on a ${s.streak}-day streak! 🔥 Can you beat me?\n" +
                        "https://play.google.com/store/apps/details?id=${context.packageName}"
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, "Share your score"))
                })
                Gap(10.dp)
                GhostButton("🏆  See my rank", onClick = { vm.home(); vm.go(Screen.Board) })
                Gap(10.dp)
                GhostButton("Home", onClick = vm::home)
                Gap(10.dp)
                Text(
                    "New games tomorrow. Keep practising to raise your Brain Score!",
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BannerAd()
        }
    }
}

/** Simple confetti burst drawn on a Canvas, no extra libraries. */
@Composable
fun Confetti() {
    val pieces = remember {
        val rnd = Random(System.nanoTime())
        List(70) {
            Triple(rnd.nextFloat() * 360f, 0.5f + rnd.nextFloat(), Brand.ink[rnd.nextInt(Brand.ink.size)])
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(1800)) }
    if (t.value >= 1f) return
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - t.value * t.value }) {
        val origin = Offset(size.width / 2, size.height * 0.28f)
        pieces.forEach { (angle, speed, c) ->
            val rad = Math.toRadians(angle.toDouble())
            val dist = size.width * 0.55f * speed * t.value
            val x = origin.x + (cos(rad) * dist).toFloat()
            val y = origin.y + (sin(rad) * dist).toFloat() + size.height * 0.35f * t.value * t.value
            drawCircle(c, radius = 7f + 5f * speed, center = Offset(x, y))
        }
    }
}
