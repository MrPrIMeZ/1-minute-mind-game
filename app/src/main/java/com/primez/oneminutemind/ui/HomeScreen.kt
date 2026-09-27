package com.primez.oneminutemind.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import com.primez.oneminutemind.data.Days
import com.primez.oneminutemind.game.GameId
import com.primez.oneminutemind.game.Skill

@Composable
fun HomeScreen(vm: AppViewModel) {
    val p = vm.progress
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("1 Minute Mind", style = MaterialTheme.typography.headlineMedium)
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
            item(span = { GridItemSpan(2) }) { SectionTitle("Practice · pick any game") }
            items(GameId.entries) { g -> GameTile(g, p.bestScores[g] ?: 0) { vm.startPractice(g) } }
        }
        BannerAd()
    }
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
        onClick = if (done) null else vm::startDaily,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DAILY CHALLENGE", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    Text(
                        when {
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
            if (done) {
                val score = p.dailyScores[Days.today()] ?: 0
                Text("Today's score: $score · come back tomorrow for new games!", color = Color.White)
            } else {
                if (p.canSaveStreak) {
                    GhostOnColor("🎬 Save your ${p.streak}-day streak (watch an ad)", enabled = Ads.rewardedReady, onClick = onSaveStreak)
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
    val p = vm.progress
    Card(onClick = { vm.go(Screen.Stats) }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BRAIN SCORE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${p.brainScore}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                }
                Text("See stats ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Gap(8.dp)
            Skill.entries.forEach { s ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.emoji} ${s.label}", Modifier.weight(0.34f), style = MaterialTheme.typography.bodyMedium)
                    Bar(p.skillRating(s) / 100f, Brand.skill(s), Modifier.weight(0.56f), height = 8.dp)
                    Text("${p.skillRating(s)}", Modifier.weight(0.1f).padding(start = 8.dp), fontWeight = FontWeight.Bold)
                }
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
