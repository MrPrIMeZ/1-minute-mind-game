package com.primez.oneminutemind.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import com.primez.oneminutemind.data.Rewards
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primez.oneminutemind.ads.Ads
import com.primez.oneminutemind.ads.BannerAd
import com.primez.oneminutemind.ads.findActivity
import com.primez.oneminutemind.data.Daily
import com.primez.oneminutemind.data.Days
import com.primez.oneminutemind.game.GameId
import com.primez.oneminutemind.game.Skill

@Composable
fun HomeScreen(vm: AppViewModel) {
    val p = vm.progress
    val context = LocalContext.current
    var showReward by remember { mutableStateOf(p.openRewardAvailable) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "🧠 1 MINUTE MIND" + if (p.premium) " 👑" else "",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                        )
                        Text(
                            "Level ${p.level.level} · ${p.level.title}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.go(Screen.Awards) }) {
                        Icon(Icons.Filled.Star, "Achievements", tint = Brand.gold)
                    }
                    IconButton(onClick = { vm.go(Screen.Settings) }) {
                        Icon(Icons.Filled.Settings, "Settings")
                    }
                }
            }
            item(span = { GridItemSpan(2) }) {
                Column {
                    Bar(p.level.fraction, Brand.violet)
                    Text(
                        "${p.level.intoLevel} / ${p.level.needed} XP to level ${p.level.level + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item(span = { GridItemSpan(2) }) { DailyCard(vm, onSaveStreak = {
                Ads.showRewarded(context.findActivity()) { vm.saveStreak() }
            }) }
            item(span = { GridItemSpan(2) }) { BoardCard(vm) }
            item(span = { GridItemSpan(2) }) { BrainCard(vm) }
            if (!p.premium) item(span = { GridItemSpan(2) }) { PremiumCard(vm) }
            item(span = { GridItemSpan(2) }) { SectionTitle("Practice · pick any game") }
            items(GameId.entries) { g -> GameTile(g, p.bestScores[g] ?: 0) { vm.startPractice(g) } }
        }
        ToastHost(vm)
        BannerAd()
    }
    if (showReward && p.openRewardAvailable) DailyRewardDialog(vm) { showReward = false }
}

@Composable
private fun DailyCard(vm: AppViewModel, onSaveStreak: () -> Unit) {
    val p = vm.progress
    val games = vm.todaysGames
    val done = p.playedDailyToday
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(1f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
    Card(
        brush = Brush.linearGradient(listOf(Color(0xFF6D4AFF), Color(0xFFB144FF), Color(0xFFFF6B8A))),
        onClick = if (done && !p.retryInProgress) null else vm::startDaily,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DAILY CHALLENGE", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    Text(
                        when {
                            p.retryInProgress -> "Retry · ${p.todayDailyScores.size} of 3 done"
                            done -> "Completed today ✓"
                            p.todayDailyScores.isNotEmpty() -> "${p.todayDailyScores.size} of 3 done"
                            else -> "3 games · 3 minutes"
                        },
                        color = Color.White, style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔥", fontSize = 30.sp, modifier = Modifier.scale(if (p.liveStreak > 0 && !done) scale else 1f))
                    Text("${p.liveStreak} day${if (p.liveStreak == 1) "" else "s"}", color = Color.White, fontWeight = FontWeight.Bold)
                    if (p.shields > 0) Text("🛡️ ${p.shields}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Gap()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val played = p.todayDailyScores
                games.forEachIndexed { i, g ->
                    val score = played.getOrNull(i)
                    Box(Modifier.weight(1f)) {
                        Card(color = Color.White.copy(alpha = if (score != null) 0.30f else 0.16f), padding = 10.dp) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (score != null) "✅" else g.emoji, fontSize = 24.sp)
                                Text(g.title, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                Text(
                                    if (score != null) "$score pts" else "Game ${i + 1}",
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (score != null) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
            Gap()
            if (p.retryInProgress) {
                BigButton(
                    "Continue retry: game ${p.todayDailyScores.size + 1} of 3",
                    onClick = vm::startDaily, color = Color(0xFF1B1446),
                )
            } else if (done) {
                val score = p.dailyScores[Days.today()] ?: 0
                Text(
                    "Today's best: $score" + if (p.canStartRetry) "" else " · come back tomorrow for new games!",
                    color = Color.White, fontWeight = FontWeight.SemiBold,
                )
                if (p.canStartRetry) {
                    Gap(8.dp)
                    GhostOnColor(
                        if (p.premium) "👑 Use your 1 retry: 3 new games · better total counts"
                        else "🔒 Retry with new games is for Premium members",
                        enabled = true, onClick = vm::startRetry,
                    )
                }
            } else {
                if (p.canSaveStreak) {
                    GhostOnColor(Ads.rewardLabel("save your ${p.streak}-day streak"), enabled = Ads.canReward, onClick = onSaveStreak)
                    Gap(8.dp)
                }
                val next = p.todayDailyScores.size
                BigButton(
                    if (next == 0) "Play today's challenge" else "Continue: game ${next + 1} of 3",
                    onClick = vm::startDaily, color = Color(0xFF1B1446),
                )
            }
        }
    }
}

@Composable
private fun GhostOnColor(text: String, enabled: Boolean, onClick: () -> Unit) {
    Card(color = Color.White.copy(alpha = if (enabled) 0.22f else 0.1f), onClick = if (enabled) onClick else null, padding = 12.dp) {
        Text(
            if (enabled) text else "$text · loading…",
            color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BrainCard(vm: AppViewModel) {
    Card(onClick = { vm.go(Screen.Stats) }) {
        Column {
            TodayBrain(vm, big = false)
            Gap(12.dp)
            ShareButtons(vm)
        }
    }
}

/**
 * Today's Brain Score (0–100) with one bar per skill of today's ranked games.
 * [big] is the centred version used at the top of the Stats page.
 */
@Composable
fun TodayBrain(vm: AppViewModel, big: Boolean) {
    val p = vm.progress
    val today = p.todayBrain
    // Skills of today's ranked games (they rotate every day); "–" until today's challenge is done.
    val rows: List<Pair<GameId, Int?>> =
        if (today.isNotEmpty()) today else Daily.gamesFor(Days.today()).map { it to null }
    val score = if (today.isEmpty()) "–" else "${p.brainScore}"

    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (big) Alignment.CenterHorizontally else Alignment.Start) {
        if (big) {
            Text("Brain Score", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(score, fontSize = 64.sp, fontWeight = FontWeight.Black, color = Brand.violet)
            Text("out of 100", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Gap()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BRAIN SCORE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(score, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                }
                Text("See stats ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Gap(8.dp)
        }
        rows.forEach { (g, rating) ->
            val s = g.skill
            Row(Modifier.padding(vertical = if (big) 4.dp else 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${s.emoji} ${s.label}", Modifier.weight(if (big) 0.3f else 0.34f), style = MaterialTheme.typography.bodyMedium)
                Bar((rating ?: 0) / 100f, Brand.skill(s), Modifier.weight(if (big) 0.58f else 0.56f), height = if (big) 10.dp else 8.dp)
                Text(rating?.toString() ?: "–", Modifier.weight(if (big) 0.12f else 0.1f).padding(start = 8.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** WhatsApp + general share of the Brain Score picture, with the daily share reward. */
@Composable
fun ShareButtons(vm: AppViewModel, headline: String? = null) {
    val context = LocalContext.current
    val p = vm.progress
    // Before today's challenge is finished there is no Brain Score to show yet, so the buttons
    // explain that instead of sharing an empty picture.
    fun share(toWhatsApp: Boolean) {
        if (!p.hasBrainToday) {
            vm.toast = "🧠 Finish today's challenge first, then share your Brain Score!"
            return
        }
        vm.share(context, toWhatsApp = toWhatsApp, headline = headline)
    }
    Column {
        Text(
            when {
                !p.hasBrainToday -> "📣 Share your Brain Score after today's challenge"
                p.shareRewardAvailable -> "📣 Share your Brain Score · +${Rewards.SHARE_XP * (if (p.premium) 2 else 1)} XP today"
                else -> "📣 Share your Brain Score (today's reward claimed)"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                BigButton("WhatsApp", onClick = { share(true) }, color = Color(0xFF25D366))
            }
            Box(Modifier.weight(1f)) {
                BigButton("More apps", onClick = { share(false) })
            }
        }
    }
}

@Composable
private fun GameTile(g: GameId, best: Int, onClick: () -> Unit) {
    Card(onClick = onClick, padding = 14.dp) {
        Column {
            EmojiBadge(g.emoji, Brand.skill(g.skill), 44.dp)
            Gap(10.dp)
            Text(g.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(g.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, minLines = 2)
            Gap(6.dp)
            Text(
                if (best > 0) "Best $best" else "New",
                style = MaterialTheme.typography.labelMedium,
                color = if (best > 0) Brand.skill(g.skill) else Brand.coral,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BoardCard(vm: AppViewModel) {
    val player = vm.progress.player
    Card(onClick = { vm.go(Screen.Board) }, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏆", fontSize = 30.sp)
            HGap()
            Column(Modifier.weight(1f)) {
                Text("Scoreboard", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (player == null) "Sign up and see your rank in India and your state"
                    else "${player.name} · ${player.state}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PremiumCard(vm: AppViewModel) {
    Card(
        brush = Brush.linearGradient(listOf(Color(0xFF2B1650), Color(0xFF6B3F0A))),
        onClick = { vm.go(Screen.Premium) },
        padding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👑", fontSize = 30.sp)
            HGap()
            Column(Modifier.weight(1f)) {
                Text("Go Premium", color = Brand.gold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "No ads · daily retries · gold share card & crown",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("›", fontSize = 26.sp, color = Brand.gold, fontWeight = FontWeight.Bold)
        }
    }
}

/** Shows [AppViewModel.toast] for 3 seconds. */
@Composable
fun ToastHost(vm: AppViewModel) {
    vm.toast?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(3000); vm.toast = null }
        Toast(msg)
    }
}

@Composable
fun Toast(msg: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Card(color = MaterialTheme.colorScheme.inverseSurface, padding = 14.dp) {
            Text(msg, color = MaterialTheme.colorScheme.inverseOnSurface, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 7-day calendar of daily-open rewards. Day 7 also gives a Streak Shield. */
@Composable
private fun DailyRewardDialog(vm: AppViewModel, onClose: () -> Unit) {
    val p = vm.progress
    val today = p.nextOpenRewardIndex
    var claimed by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    AlertDialog(
        onDismissRequest = { if (claimed != null) onClose() },
        title = { Text(if (claimed == null) "🎁 Daily reward" else "🎉 Reward claimed!") },
        text = {
            Column {
                Text(
                    if (claimed == null) "Open the app every day for bigger rewards. Day 7 gives a 🛡️ Streak Shield!"
                    else "+${claimed!!.first} XP" + if (claimed!!.second) " and a 🛡️ Streak Shield (it saves your streak if you miss a day)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Gap()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Rewards.openXp.forEachIndexed { i, xp ->
                        val isToday = i == today
                        val past = i < today
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isToday -> Brand.gold
                                        past -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                )
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("D${i + 1}", style = MaterialTheme.typography.labelSmall, color = if (isToday) Color(0xFF2A1A00) else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                when {
                                    past -> "✓"
                                    Rewards.givesShield(i) -> "🛡️"
                                    else -> "🎁"
                                },
                                fontSize = 16.sp,
                            )
                            Text("${xp * (if (p.premium) 2 else 1)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = if (isToday) Color(0xFF2A1A00) else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                if (p.premium) { Gap(8.dp); Text("👑 Premium: double rewards", color = Brand.gold, style = MaterialTheme.typography.labelMedium) }
            }
        },
        confirmButton = {
            if (claimed == null) TextButton(onClick = { claimed = vm.claimOpenReward() ?: (0 to false) }) { Text("Claim") }
            else TextButton(onClick = onClose) { Text("Nice!") }
        },
    )
}
