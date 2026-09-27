package com.primez.oneminutemind.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primez.oneminutemind.ads.findActivity
import com.primez.oneminutemind.billing.Billing

/** A crown that gently bobs and glows. Used on the Premium screen and the scoreboard. */
@Composable
fun AnimatedCrown(size: Int = 22) {
    val t = rememberInfiniteTransition(label = "crown")
    val s by t.animateFloat(0.9f, 1.15f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "cs")
    val r by t.animateFloat(-10f, 10f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "cr")
    Text("👑", fontSize = size.sp, modifier = Modifier.scale(s).rotate(r))
}

@Composable
fun PremiumScreen(vm: AppViewModel) {
    val p = vm.progress
    val activity = LocalContext.current.findActivity()
    BackHandler { vm.back() }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF140B2E), Color(0xFF2B1650), Color(0xFF4A2A0A))),
        ),
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            CompositionLocalProvider(LocalContentColor provides Color.White) { TopBar("", onBack = { vm.back() }) }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedCrown(84)
                Gap()
                Text("1 Minute Mind Premium", style = MaterialTheme.typography.headlineMedium, color = Brand.gold, textAlign = TextAlign.Center)
                Text(
                    if (p.premium) "You're a Premium member. Thank you! 💛" else "One payment. Yours forever.",
                    color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                )
                Gap(28.dp)
                Perk("🔁", "Retry the daily ranked challenge",
                    "Up to 2 more tries every day with 3 different games. Your best total goes on the scoreboard.")
                Perk("🚫", "No ads",
                    "No banners and no full-screen ads. Rewards like +15 seconds and double XP are free, no video needed.")
                Perk("✨", "Gold share card & scoreboard crown",
                    "Your shared Brain Score picture gets a gold PREMIUM MEMBER design, and an animated 👑 shows next to your name on the scoreboard.")
                Perk("🎁", "Double daily rewards",
                    "Twice the XP from the daily-open calendar and from sharing.")
                Gap(28.dp)
                when {
                    p.premium -> Text("👑 Premium is active on this phone.", color = Brand.gold, fontWeight = FontWeight.Bold)
                    Billing.available -> {
                        BigButton(
                            if (Billing.busy) "Opening Google Play…" else "Get Premium · ${Billing.price ?: ""}".trimEnd(' ', '·'),
                            onClick = { activity?.let { Billing.buy(it) } },
                            enabled = !Billing.busy,
                            color = Color(0xFFE0A526),
                        )
                        Gap(8.dp)
                        TextButton(onClick = { Billing.restore(); vm.toast = "Checking Google Play for your purchase…" }) {
                            Text("Restore purchase", color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                    else -> Text(
                        "Premium can be bought once the app is on the Google Play Store.",
                        color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                    )
                }
                Gap(24.dp)
            }
            ToastHost(vm)
        }
    }
}

@Composable
private fun Perk(emoji: String, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(emoji, fontSize = 28.sp)
        HGap(14.dp)
        Column {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(body, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
