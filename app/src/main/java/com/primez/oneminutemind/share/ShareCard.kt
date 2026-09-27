package com.primez.oneminutemind.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.primez.oneminutemind.data.Progress
import com.primez.oneminutemind.game.Skill
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Makes a phone-status-sized (1080×1920) picture of the player's Brain Score and shares it
 * with the app link. Premium players get a gold card with a "PREMIUM MEMBER" watermark.
 */
object ShareCard {
    private const val W = 1080
    private const val H = 1920

    fun playLink(context: Context) = "https://play.google.com/store/apps/details?id=${context.packageName}"

    /** @param headline optional extra line, e.g. today's daily challenge total. */
    fun draw(p: Progress, headline: String? = null): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val premium = p.premium
        val gold = 0xFFFFC857.toInt()
        val white = 0xFFFFFFFF.toInt()

        // background
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.shader = if (premium) {
            LinearGradient(0f, 0f, W.toFloat(), H.toFloat(),
                intArrayOf(0xFF140B2E.toInt(), 0xFF2B1650.toInt(), 0xFF4A2A0A.toInt()), null, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, 0f, W.toFloat(), H.toFloat(),
                intArrayOf(0xFF3B22C9.toInt(), 0xFFA13CF0.toInt(), 0xFFFF6B8A.toInt()), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        fun text(size: Float, color: Int = white, bold: Boolean = true, alpha: Int = 255) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                this.alpha = alpha
                typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }

        if (premium) {
            // sparkles and a big faint crown watermark behind the score
            val rnd = Random(p.xp.toLong() + 7)
            repeat(26) {
                c.drawText("✦", rnd.nextInt(60, W - 60).toFloat(), rnd.nextInt(120, H - 120).toFloat(),
                    text(rnd.nextInt(22, 54).toFloat(), gold, alpha = rnd.nextInt(60, 170)))
            }
            c.drawText("👑", W / 2f, 1010f, text(520f, alpha = 40))
            // gold frame
            val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 18f; color = gold
            }
            c.drawRoundRect(RectF(30f, 30f, W - 30f, H - 30f), 60f, 60f, frame)
        }

        var y = 150f
        c.drawText("🧠 1 Minute Mind Game", W / 2f, y, text(58f))
        if (premium) {
            y += 100f
            val ribbon = RectF(W / 2f - 300f, y - 70f, W / 2f + 300f, y + 26f)
            c.drawRoundRect(ribbon, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold })
            c.drawText("👑  PREMIUM MEMBER", W / 2f, y, text(52f, 0xFF2A1A00.toInt()))
        }
        y += 150f
        c.drawText("MY BRAIN SCORE TODAY", W / 2f, y, text(50f, if (premium) gold else white, alpha = 230).apply { letterSpacing = 0.15f })
        y += 260f
        c.drawText("${p.brainScore}", W / 2f, y, text(260f, if (premium) gold else white))
        y += 60f
        c.drawText("out of 100", W / 2f, y, text(44f, bold = false, alpha = 210))
        y += 80f
        c.drawText("Level ${p.level.level} · ${p.level.title}", W / 2f, y, text(52f))
        headline?.let { y += 70f; c.drawText(it, W / 2f, y, text(46f, if (premium) gold else white)) }

        // today's three ranked games
        y += 50f
        val rows = p.todayBrain
        val panel = RectF(90f, y, W - 90f, y + 80f + 110f * rows.size.coerceAtLeast(1))
        c.drawRoundRect(panel, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white; alpha = 38 })
        var rowY = y + 95f
        rows.forEach { (g, v) ->
            val label = text(44f).apply { textAlign = Paint.Align.LEFT }
            c.drawText("${g.skill.emoji} ${g.skill.label}", 130f, rowY, label)
            val barL = 590f; val barR = W - 200f
            val track = RectF(barL, rowY - 30f, barR, rowY + 2f)
            c.drawRoundRect(track, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white; alpha = 60 })
            val fill = RectF(barL, rowY - 30f, barL + (barR - barL) * v / 100f, rowY + 2f)
            if (v > 0) c.drawRoundRect(fill, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (premium) gold else white })
            c.drawText("$v", W - 150f, rowY, text(44f))
            rowY += 110f
        }
        y = panel.bottom + 90f

        // stats row
        val cols = listOf("🔥 ${p.liveStreak}" to "day streak", "🏆 ${p.bestDaily()}" to "best daily", "🎮 ${p.gamesPlayed}" to "games")
        cols.forEachIndexed { i, (v, l) ->
            val x = W * (i * 2 + 1) / 6f
            c.drawText(v, x, y, text(58f))
            c.drawText(l, x, y + 56f, text(36f, bold = false, alpha = 210))
        }
        y += 160f
        c.drawText("Can you beat me? 🤔", W / 2f, y, text(66f, if (premium) gold else white))
        p.player?.let { c.drawText("— ${it.name}, ${it.state}", W / 2f, y + 70f, text(40f, bold = false, alpha = 220)) }
        c.drawText("Free on Google Play · search “1 Minute Mind”", W / 2f, H - 70f, text(38f, bold = false, alpha = 230))
        return bmp
    }

    private fun save(context: Context, bmp: Bitmap): android.net.Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "brain-score.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Opens WhatsApp (or the share menu) with the picture and the app link.
     * Returns true if something was opened, so the daily share reward can be given.
     */
    fun share(context: Context, p: Progress, toWhatsApp: Boolean, headline: String? = null): Boolean {
        val uri = save(context, draw(p, headline))
        val text = buildString {
            append("🧠 My Brain Score today is ${p.brainScore}/100")
            if (p.liveStreak > 1) append(" and I'm on a ${p.liveStreak}-day streak 🔥")
            append("! Can you beat me? Train your brain in 1 minute a day: ")
            append(playLink(context))
        }
        val base = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri("Brain Score", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (toWhatsApp) {
            for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                try {
                    context.startActivity(Intent(base).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return true
                } catch (_: ActivityNotFoundException) {
                }
            }
        }
        return try {
            context.startActivity(Intent.createChooser(base, "Share your Brain Score").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
