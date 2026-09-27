package com.primez.oneminutemind.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.primez.oneminutemind.data.AppTheme
import com.primez.oneminutemind.game.Skill

object Brand {
    val violet = Color(0xFF7C5CFF)
    val violetDeep = Color(0xFF5B3DF5)
    val coral = Color(0xFFFF6B6B)
    val teal = Color(0xFF2EC4B6)
    val gold = Color(0xFFFFC857)
    val good = Color(0xFF22C55E)
    val bad = Color(0xFFEF4444)

    fun skill(s: Skill): Color = when (s) {
        Skill.MATH -> Color(0xFF3B82F6)
        Skill.MEMORY -> Color(0xFFA855F7)
        Skill.FOCUS -> Color(0xFFF97316)
        Skill.SPEED -> Color(0xFFEAB308)
        Skill.LOGIC -> Color(0xFF14B8A6)
    }

    /** Real colours for Colour Clash, same order as Palette.names. */
    val ink = listOf(
        Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF22C55E),
        Color(0xFFEAB308), Color(0xFFA855F7), Color(0xFFF97316),
    )
}

private val MidnightColors = darkColorScheme(
    primary = Brand.violet,
    onPrimary = Color.White,
    secondary = Brand.coral,
    tertiary = Brand.teal,
    background = Color(0xFF0E0F22),
    onBackground = Color(0xFFECEBFF),
    surface = Color(0xFF181936),
    onSurface = Color(0xFFECEBFF),
    surfaceVariant = Color(0xFF23244A),
    onSurfaceVariant = Color(0xFFB9B8DA),
    outline = Color(0xFF3A3B66),
)

private val OceanColors = darkColorScheme(
    primary = Color(0xFF22B8CF),
    onPrimary = Color(0xFF00212A),
    secondary = Color(0xFFFF8A65),
    tertiary = Color(0xFF4ADE80),
    background = Color(0xFF051A26),
    onBackground = Color(0xFFE3F4FB),
    surface = Color(0xFF0B2A3B),
    onSurface = Color(0xFFE3F4FB),
    surfaceVariant = Color(0xFF123B52),
    onSurfaceVariant = Color(0xFFA5C6D6),
    outline = Color(0xFF235671),
)

private val DaylightColors = lightColorScheme(
    primary = Brand.violetDeep,
    onPrimary = Color.White,
    secondary = Color(0xFFE5484D),
    tertiary = Color(0xFF0F9D8F),
    background = Color(0xFFF5F4FF),
    onBackground = Color(0xFF15152E),
    surface = Color.White,
    onSurface = Color(0xFF15152E),
    surfaceVariant = Color(0xFFEAE8FF),
    onSurfaceVariant = Color(0xFF55557A),
    outline = Color(0xFFD2D0F0),
)

private val base = Typography()
private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
)

val MonoNumbers = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp)

fun AppTheme.isDark() = this != AppTheme.DAYLIGHT

@Composable
fun MindTheme(theme: AppTheme, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = when (theme) {
            AppTheme.DAYLIGHT -> DaylightColors
            AppTheme.MIDNIGHT -> MidnightColors
            AppTheme.OCEAN -> OceanColors
        },
        typography = AppTypography,
        content = content,
    )
}

/** Short sounds and vibrations. Uses built-in tones, so no audio files are needed. */
class Feedback(context: Context) {
    var sound = true
    var haptics = true

    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 45)
    } catch (_: RuntimeException) {
        null
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun buzz(ms: Long, amp: Int) {
        if (!haptics) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (_: Exception) {
        }
    }

    private fun play(t: Int, ms: Int) {
        if (sound) try { tone?.startTone(t, ms) } catch (_: Exception) {}
    }

    fun correct() { play(ToneGenerator.TONE_PROP_BEEP, 60); buzz(12, 60) }
    fun wrong() { play(ToneGenerator.TONE_PROP_NACK, 120); buzz(60, 180) }
    fun tap() { buzz(8, 40) }
    fun comboUp() { play(ToneGenerator.TONE_PROP_BEEP2, 90); buzz(20, 120) }
    fun finish() { play(ToneGenerator.TONE_PROP_ACK, 200); buzz(40, 120) }

    fun release() { tone?.release() }
}
