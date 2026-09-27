package com.primez.oneminutemind.ui

import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.primez.oneminutemind.data.AppTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primez.oneminutemind.BuildConfig
import com.primez.oneminutemind.ads.Ads
import com.primez.oneminutemind.ads.BannerAd
import com.primez.oneminutemind.ads.findActivity
import com.primez.oneminutemind.data.Achievements
import com.primez.oneminutemind.data.Days
import com.primez.oneminutemind.game.GameId
import com.primez.oneminutemind.game.Skill
import com.primez.oneminutemind.notify.Reminder

// ---------------- Onboarding ----------------

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppLogo(120.dp)
        Gap(8.dp)
        Gap(12.dp)
        Text("1 MINUTE MIND", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, letterSpacing = 1.sp)
        Gap(8.dp)
        Text(
            "Train your brain in one minute a day.",
            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(32.dp)
        listOf(
            "🎮" to "10 fast games for math, memory, focus, speed and logic",
            "📅" to "A new daily challenge every day: build your streak",
            "📈" to "Level up, unlock achievements and grow your Brain Score",
        ).forEach { (e, t) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e, fontSize = 30.sp); HGap(14.dp)
                Text(t, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Gap(28.dp)
        Text("Pick your look", style = MaterialTheme.typography.titleMedium)
        Gap(10.dp)
        val s = vm.progress.settings
        ThemePicker(s.theme) { vm.updateSettings(s.copy(theme = it)) }
        Gap(28.dp)
        BigButton("Let's go!", onClick = vm::finishOnboarding)
    }
}

// ---------------- Stats ----------------

@Composable
fun StatsScreen(vm: AppViewModel) {
    val p = vm.progress
    BackHandler { vm.back() }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopBar("Your stats", onBack = { vm.back() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Card { TodayBrain(vm, big = true) } }
            item { Card { ShareButtons(vm) } }
            item {
                Card {
                    Column {
                        Text("Skill profile · all games", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Your level in each skill from all your recent games, including practice.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Gap(8.dp)
                        Skill.entries.forEach { s ->
                            val played = p.skillPlayed(s)
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${s.emoji} ${s.label}", Modifier.weight(0.3f))
                                if (played) {
                                    Bar(p.skillRating(s) / 100f, Brand.skill(s), Modifier.weight(0.58f))
                                    Text("${p.skillRating(s)}", Modifier.weight(0.12f).padding(start = 8.dp), fontWeight = FontWeight.Bold)
                                } else {
                                    val games = GameId.entries.filter { it.skill == s }.joinToString(" or ") { it.title }
                                    Text(
                                        "Not played yet · try $games",
                                        Modifier.weight(0.7f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Stat("${p.gamesPlayed}", "games")
                            Stat("${p.totalCorrect}", "correct")
                            Stat("${p.bestStreak}", "best streak", color = Brand.coral)
                            Stat("${p.bestCombo}", "best combo", color = Brand.gold)
                        }
                    }
                }
            }
            item { DailyChart(p.dailyScores) }
            item { SectionTitle("Best scores") }
            items(GameId.entries) { g ->
                Card(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiBadge(g.emoji, Brand.skill(g.skill), 40.dp); HGap()
                        Column(Modifier.weight(1f)) {
                            Text(g.title, fontWeight = FontWeight.Bold)
                            Text("Played ${p.playsPerGame[g] ?: 0}×", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${p.bestScores[g] ?: 0}", style = MaterialTheme.typography.titleLarge, color = Brand.skill(g.skill))
                    }
                }
            }
        }
        ToastHost(vm)
        BannerAd()
    }
}

@Composable
private fun DailyChart(scores: Map<Int, Int>) {
    val days = (13 downTo 0).map { Days.offset(Days.today(), -it) }
    val values = days.map { scores[it] ?: 0 }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val barColor = Brand.violet
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Card {
        Column {
            Text("Daily challenge · last 14 days", style = MaterialTheme.typography.titleMedium)
            Gap()
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val gap = 6.dp.toPx()
                val w = (size.width - gap * (values.size - 1)) / values.size
                values.forEachIndexed { i, v ->
                    val h = if (v == 0) 6.dp.toPx() else (size.height * v / max).coerceAtLeast(8.dp.toPx())
                    drawRoundRect(
                        color = if (v == 0) empty else barColor,
                        topLeft = Offset(i * (w + gap), size.height - h),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                }
            }
            Gap(6.dp)
            Text(
                "Played ${values.count { it > 0 }} of the last 14 days",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------- Achievements ----------------

@Composable
fun AwardsScreen(vm: AppViewModel) {
    val got = vm.progress.achievements
    BackHandler { vm.back() }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopBar("Achievements  ${got.size}/${Achievements.all.size}", onBack = { vm.back() })
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(Achievements.all) { a ->
                val unlocked = a.id in got
                Card(Modifier.alpha(if (unlocked) 1f else 0.45f), padding = 14.dp) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (unlocked) a.emoji else "🔒", fontSize = 36.sp)
                        Gap(6.dp)
                        Text(a.title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(
                            a.description, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2,
                        )
                    }
                }
            }
        }
        BannerAd()
    }
}

// ---------------- Settings ----------------

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val s = vm.progress.settings
    var confirmReset by remember { mutableStateOf(false) }
    BackHandler { vm.back() }

    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopBar("Settings", onBack = { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionTitle("Premium")
            Card(padding = 4.dp) {
                Column {
                    LinkRow(if (vm.progress.premium) "👑  Premium is active" else "👑  Get Premium", "") { vm.go(Screen.Premium) }
                    if (BuildConfig.DEBUG) {
                        // Test builds only: try Premium features before the Play Store product exists.
                        ToggleRow("🧪  Test mode: Premium on", vm.progress.premium) { vm.setPremium(it) }
                    }
                }
            }
            SectionTitle("Theme")
            ThemePicker(s.theme) { vm.updateSettings(s.copy(theme = it)) }
            SectionTitle("Game")
            Card(padding = 4.dp) {
                Column {
                    ToggleRow("🔊  Sounds", s.sound) { vm.updateSettings(s.copy(sound = it)) }
                    ToggleRow("📳  Vibration", s.haptics) { vm.updateSettings(s.copy(haptics = it)) }
                }
            }
            SectionTitle("Daily reminder")
            Card(padding = 4.dp) {
                Column {
                    ToggleRow("🔔  Remind me to play", s.reminder) {
                        vm.updateSettings(s.copy(reminder = it))
                        if (it && !Reminder.canNotify(context)) vm.askNotificationPermission = true
                    }
                    val time = "%d:%02d %s".format(
                        if (s.reminderHour % 12 == 0) 12 else s.reminderHour % 12,
                        s.reminderMinute, if (s.reminderHour < 12) "AM" else "PM",
                    )
                    LinkRow("⏰  Time", time, enabled = s.reminder) {
                        TimePickerDialog(context, { _, h, m ->
                            vm.updateSettings(s.copy(reminderHour = h, reminderMinute = m))
                        }, s.reminderHour, s.reminderMinute, false).show()
                    }
                }
            }
            SectionTitle("About")
            Card(padding = 4.dp) {
                Column {
                    LinkRow("⭐  Rate the app", "") {
                        val id = context.packageName
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
                        } catch (_: ActivityNotFoundException) {
                            open("https://play.google.com/store/apps/details?id=$id")
                        }
                    }
                    LinkRow("📤  Share with friends", "") {
                        val text = "Train your brain in 1 minute a day 🧠 https://play.google.com/store/apps/details?id=${context.packageName}"
                        context.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share"),
                        )
                    }
                    LinkRow("🔒  Privacy policy", "") { open(BuildConfig.PRIVACY_POLICY_URL) }
                    if (Ads.privacyOptionsRequired) {
                        LinkRow("🛡️  Ad privacy choices", "") { context.findActivity()?.let { Ads.showPrivacyOptions(it) } }
                    }
                    LinkRow("🗑️  Reset progress", "") { confirmReset = true }
                }
            }
            Gap(20.dp)
            Text(
                "Version ${BuildConfig.VERSION_NAME}" + if (BuildConfig.ADS_TESTING) " · test ads" else "",
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all progress?") },
            text = { Text("Your XP, levels, streak, scores and achievements will be deleted. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetProgress() }) { Text("Reset", color = Brand.bad) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun LinkRow(label: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f)) {
        TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(value.ifEmpty { "›" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
    }
}


/** Three theme cards with a small preview of each look. */
@Composable
fun ThemePicker(selected: AppTheme, onPick: (AppTheme) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppTheme.entries.forEach { t ->
            val (bg, card, accent) = when (t) {
                AppTheme.DAYLIGHT -> Triple(Color(0xFFF5F4FF), Color.White, Color(0xFF5B3DF5))
                AppTheme.MIDNIGHT -> Triple(Color(0xFF0E0F22), Color(0xFF23244A), Color(0xFF7C5CFF))
                AppTheme.OCEAN -> Triple(Color(0xFF051A26), Color(0xFF123B52), Color(0xFF22B8CF))
            }
            val isOn = t == selected
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .border(
                        if (isOn) 3.dp else 1.dp,
                        if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(18.dp),
                    )
                    .clickable { onPick(t) }
                    .background(bg)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(accent))
                Gap(6.dp)
                Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(7.dp)).background(card))
                Gap(8.dp)
                Text(
                    "${t.emoji} ${t.label}",
                    color = if (t == AppTheme.DAYLIGHT) Color(0xFF15152E) else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}
