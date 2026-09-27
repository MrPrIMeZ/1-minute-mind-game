package com.primez.oneminutemind.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primez.oneminutemind.ads.BannerAd
import com.primez.oneminutemind.data.Days
import com.primez.oneminutemind.online.BoardRow
import com.primez.oneminutemind.online.IndianStates
import com.primez.oneminutemind.online.Period
import com.primez.oneminutemind.online.Scoreboard
import com.primez.oneminutemind.online.ScoreboardException

private enum class Scope { INDIA, STATE }

@Composable
fun BoardScreen(vm: AppViewModel) {
    val p = vm.progress
    val player = p.player
    BackHandler { vm.back() }

    var scope by remember { mutableStateOf(Scope.INDIA) }
    var period by remember { mutableStateOf(Period.TODAY) }
    var viewState by remember { mutableStateOf(player?.state) }
    var rows by remember { mutableStateOf<List<BoardRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var showSignUp by remember { mutableStateOf(false) }
    var pickState by remember { mutableStateOf(false) }

    // When the player signs up or changes state, show their state on the State tab.
    LaunchedEffect(player?.state) { if (player != null) viewState = player.state }

    LaunchedEffect(scope, period, viewState, refresh, player?.id, p.lastSubmittedDay) {
        if (!Scoreboard.configured) return@LaunchedEffect
        if (scope == Scope.STATE && viewState == null) { rows = emptyList(); return@LaunchedEffect }
        loading = true; error = null
        try {
            rows = Scoreboard.leaderboard(period, if (scope == Scope.STATE) viewState else null, Days.today(), player?.id)
        } catch (e: ScoreboardException) {
            error = e.message
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopBar("Scoreboard", onBack = { vm.back() }) {
            IconButton(onClick = { refresh++ }) { Icon(Icons.Filled.Refresh, "Refresh") }
        }
        if (!Scoreboard.configured) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("🏆\nThe scoreboard is coming soon!", textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
            }
            return@Column
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            ProfileCard(vm, onSignUp = { showSignUp = true })
            Gap()
            Segmented(
                listOf("🇮🇳  All India", "📍  ${viewState ?: "My state"}"),
                if (scope == Scope.INDIA) 0 else 1,
            ) { i ->
                scope = if (i == 0) Scope.INDIA else Scope.STATE
                if (scope == Scope.STATE && viewState == null) pickState = true
            }
            Gap(8.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Period.entries.forEach { per ->
                    val on = per == period
                    Box(
                        Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50))
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { period = per }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(per.label, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
                Box(Modifier.weight(1f))
                if (scope == Scope.STATE) {
                    Text("Change state", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { pickState = true })
                }
            }
            Gap(8.dp)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && rows.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Message("😕 $error", "Try again") { refresh++ }
                scope == Scope.STATE && viewState == null -> Message("Pick a state to see its top players.", "Pick state") { pickState = true }
                rows.isEmpty() -> Message(
                    if (period == Period.TODAY) "No scores yet today. Finish the daily challenge and be #1!"
                    else "No scores yet. Be the first!",
                    null,
                ) {}
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(rows, key = { "${it.rank}-${it.name}" }) { r ->
                        if (r.isMe && r.rank > 100) Text("…", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        RankRow(r, showState = scope == Scope.INDIA)
                    }
                }
            }
        }
        BannerAd()
    }

    if (showSignUp) SignUpDialog(vm) { showSignUp = false }
    if (pickState) StatePicker(onDismiss = { pickState = false }) { viewState = it; scope = Scope.STATE; pickState = false }
}

@Composable
private fun ProfileCard(vm: AppViewModel, onSignUp: () -> Unit) {
    val p = vm.progress
    val player = p.player
    Card(padding = 14.dp) {
        if (player == null) {
            Column {
                Text("Join the scoreboard", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Just a username and your state. No email, no phone number.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(10.dp)
                BigButton("Sign up", onClick = onSignUp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👤", fontSize = 28.sp)
                HGap()
                Column(Modifier.weight(1f)) {
                    Text(player.name, style = MaterialTheme.typography.titleMedium)
                    val status = when {
                        p.lastSubmittedDay == Days.today() -> "Today's score is on the board ✓"
                        p.playedDailyToday -> "Sending today's score…"
                        else -> "Finish today's challenge to get ranked"
                    }
                    Text("${player.state} · $status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onSignUp) { Text("Edit") }
            }
        }
    }
}

@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(l, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RankRow(r: BoardRow, showState: Boolean) {
    val medal = when (r.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> null }
    val bg = when {
        r.isMe -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        r.isPremium -> Color(0xFFFFC857).copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(48.dp)) {
            if (medal != null) Text(medal, fontSize = 24.sp)
            else Text("#${r.rank}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.name + if (r.isMe) "  (you)" else "",
                    fontWeight = if (r.isMe) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (r.isPremium) Color(0xFFD4A017) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (r.isPremium) { HGap(6.dp); AnimatedCrown(16) }
            }
            if (showState) Text(r.state, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text("${r.score}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Message(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        if (action != null) { Gap(); TextButton(onClick = onAction) { Text(action) } }
    }
}

/** Username + state. Used for signing up and for editing later. */
@Composable
fun SignUpDialog(vm: AppViewModel, onClose: () -> Unit) {
    val existing = vm.progress.player
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var state by remember { mutableStateOf(existing?.state) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(if (existing == null) "Join the scoreboard" else "Edit profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(16); error = null },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Gap()
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { picking = true }
                        .padding(16.dp),
                ) {
                    Text(state ?: "Select your state ▾", color = if (state == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
                error?.let { Gap(8.dp); Text(it, color = Brand.bad, style = MaterialTheme.typography.bodySmall) }
                Gap(8.dp)
                Text("Your username and state are shown on the public scoreboard.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val problem = Scoreboard.nameProblem(name)
                when {
                    problem != null -> error = problem
                    state == null -> error = "Please pick your state."
                    else -> {
                        busy = true
                        vm.signUp(name, state!!) { err ->
                            busy = false
                            if (err == null) onClose() else error = err
                        }
                    }
                }
            }) { Text(if (busy) "Saving…" else if (existing == null) "Join" else "Save") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Cancel") } },
    )
    if (picking) StatePicker(onDismiss = { picking = false }) { state = it; picking = false; error = null }
}

@Composable
private fun StatePicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select your state") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(IndianStates.all) { s ->
                    Text(
                        s,
                        Modifier.fillMaxWidth().clickable { onPick(s) }.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
