package com.primez.oneminutemind.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** One question/board inside a game. Pure data, so it is easy to test. */
sealed interface Round

enum class OptionLayout { GRID, ROW }

/**
 * A multiple-choice question.
 * @param promptInk index into [Palette] for coloured prompts (Colour Clash), or null.
 * @param free when true the answer gives no points and cannot be wrong (first Match Back card).
 */
data class ChoiceRound(
    val prompt: String,
    val options: List<String>,
    val correct: Int,
    val caption: String? = null,
    val promptInk: Int? = null,
    val layout: OptionLayout = OptionLayout.GRID,
    val bigPrompt: Boolean = true,
    val free: Boolean = false,
) : Round

/** Tiles in [lit] flash for [showMs], then the player taps them from memory. */
data class MemoryRound(val side: Int, val lit: Set<Int>, val showMs: Long) : Round

/** A grid of one colour with one tile slightly lighter/darker. */
data class OddRound(
    val side: Int,
    val oddIndex: Int,
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val oddValue: Float,
) : Round

/** Numbers 1..side² shuffled on a grid, tapped in order. */
data class RushRound(val side: Int, val numbers: List<Int>) : Round

/** Colour names used by Colour Clash; the UI maps each index to a real colour. */
object Palette {
    val names = listOf("RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "ORANGE")
}

/**
 * Makes rounds for one game. [level] is how many answers the player has got right so far,
 * so every game gets harder as you go.
 */
class RoundGenerator(private val game: GameId, private val rnd: Random) {

    private var lastSymbol: Int = -1

    fun next(level: Int): Round = when (game) {
        GameId.QUICK_MATH -> quickMath(level)
        GameId.TRUE_FALSE -> trueFalse(level)
        GameId.BIGGER -> bigger(level)
        GameId.COLOUR_CLASH -> colourClash(level)
        GameId.MEMORY_GRID -> memory(level)
        GameId.ODD_ONE_OUT -> oddOneOut(level)
        GameId.NEXT_NUMBER -> nextNumber(level)
        GameId.NUMBER_RUSH -> rush(level)
        GameId.MATCH_BACK -> matchBack(level)
        GameId.ARROW_FOCUS -> arrows(level)
    }

    // ---------- maths helpers ----------

    private data class Sum(val text: String, val value: Int)

    private fun sum(level: Int): Sum {
        val tier = when {
            level < 5 -> 0
            level < 12 -> 1
            level < 20 -> 2
            else -> 3
        }
        val ops = when (tier) {
            0 -> listOf('+', '-')
            1 -> listOf('+', '-', '×')
            else -> listOf('+', '-', '×', '÷')
        }
        return when (ops[rnd.nextInt(ops.size)]) {
            '+' -> {
                val hi = listOf(12, 40, 90, 200)[tier]
                val a = rnd.nextInt(2, hi); val b = rnd.nextInt(2, hi)
                Sum("$a + $b", a + b)
            }
            '-' -> {
                val hi = listOf(15, 50, 100, 250)[tier]
                val a = rnd.nextInt(5, hi); val b = rnd.nextInt(1, a)
                Sum("$a − $b", a - b)
            }
            '×' -> {
                val hi = listOf(6, 10, 13, 20)[tier]
                val a = rnd.nextInt(2, hi); val b = rnd.nextInt(2, if (tier >= 3) 13 else hi)
                Sum("$a × $b", a * b)
            }
            else -> {
                val b = rnd.nextInt(2, if (tier >= 3) 13 else 10)
                val q = rnd.nextInt(2, if (tier >= 3) 16 else 11)
                Sum("${b * q} ÷ $b", q)
            }
        }
    }

    /** Correct answer plus 3 believable wrong ones, shuffled. */
    private fun options(answer: Int, spread: Int): Pair<List<String>, Int> {
        val set = linkedSetOf(answer)
        var guard = 0
        while (set.size < 4 && guard++ < 100) {
            val off = rnd.nextInt(1, max(2, spread) + 1) * if (rnd.nextBoolean()) 1 else -1
            val candidate = if (rnd.nextInt(4) == 0 && answer >= 10) answer + 10 * off.coerceIn(-1, 1) else answer + off
            if (candidate >= 0) set.add(candidate)
        }
        var extra = 1
        while (set.size < 4) set.add(answer + spread + extra++)
        val list = set.shuffled(rnd)
        return list.map { it.toString() } to list.indexOf(answer)
    }

    private fun quickMath(level: Int): Round {
        val s = sum(level)
        val (opts, correct) = options(s.value, max(3, s.value / 8))
        return ChoiceRound(prompt = "${s.text} = ?", options = opts, correct = correct)
    }

    private fun trueFalse(level: Int): Round {
        val s = sum(level)
        val isTrue = rnd.nextBoolean()
        val shown = if (isTrue) s.value else {
            val off = listOf(1, 2, 10, -1, -2, -10).random(rnd)
            if (s.value + off < 0) s.value + abs(off) else s.value + off
        }
        return ChoiceRound(
            prompt = "${s.text} = $shown",
            options = listOf("True", "False"),
            correct = if (isTrue) 0 else 1,
            layout = OptionLayout.ROW,
        )
    }

    private fun bigger(level: Int): Round {
        fun side(): Sum = if (level < 3) {
            val v = rnd.nextInt(1, 100); Sum("$v", v)
        } else sum(level)
        var a = side(); var b = side()
        var guard = 0
        while (a.value == b.value && guard++ < 20) b = side()
        if (a.value == b.value) b = Sum("${a.value + 1}", a.value + 1)
        return ChoiceRound(
            prompt = "Which is bigger?",
            options = listOf(a.text, b.text),
            correct = if (a.value > b.value) 0 else 1,
            layout = OptionLayout.ROW,
            bigPrompt = false,
        )
    }

    // ---------- attention games ----------

    private fun colourClash(level: Int): Round {
        val count = if (level < 8) 4 else Palette.names.size
        val colours = (0 until count).shuffled(rnd)
        val word = colours[0]
        // Mostly mismatched; later on almost always mismatched.
        val ink = if (rnd.nextInt(100) < (if (level < 5) 30 else 10)) word else colours[1]
        val choices = (listOf(ink) + (0 until count).filter { it != ink }.shuffled(rnd).take(3)).shuffled(rnd)
        return ChoiceRound(
            prompt = Palette.names[word],
            options = choices.map { Palette.names[it] },
            correct = choices.indexOf(ink),
            caption = "Tap the INK colour",
            promptInk = ink,
        )
    }

    private val arrowsLR = listOf("←", "→")
    private val arrowsUD = listOf("↑", "↓")

    private fun arrows(level: Int): Round {
        val useVertical = level >= 15 && rnd.nextBoolean()
        val set = if (useVertical) arrowsUD else arrowsLR
        val middle = rnd.nextInt(2)
        val congruent = rnd.nextInt(100) < (if (level < 6) 50 else 25)
        val flank = if (congruent) middle else 1 - middle
        val n = if (level < 10) 2 else 3 // arrows on each side
        val row = buildString {
            repeat(n) { append(set[flank]).append(' ') }
            append(set[middle])
            repeat(n) { append(' ').append(set[flank]) }
        }
        return ChoiceRound(
            prompt = row,
            options = set,
            correct = middle,
            caption = "Middle arrow points…",
            layout = OptionLayout.ROW,
        )
    }

    private val symbols = listOf("●", "■", "▲", "◆", "★", "♥", "✚", "⬟")

    private fun matchBack(level: Int): Round {
        val pool = if (level < 8) 4 else if (level < 16) 6 else symbols.size
        if (lastSymbol < 0) {
            lastSymbol = rnd.nextInt(pool)
            return ChoiceRound(
                prompt = symbols[lastSymbol],
                options = listOf("Got it"),
                correct = 0,
                caption = "Remember this symbol",
                layout = OptionLayout.ROW,
                free = true,
            )
        }
        val same = rnd.nextInt(100) < 40
        val next = if (same) lastSymbol else (0 until pool).filter { it != lastSymbol }.random(rnd)
        lastSymbol = next
        return ChoiceRound(
            prompt = symbols[next],
            options = listOf("Same", "Different"),
            correct = if (same) 0 else 1,
            caption = "Same as the last one?",
            layout = OptionLayout.ROW,
        )
    }

    private fun memory(level: Int): Round {
        val lit = min(3 + level / 2, 12)
        val side = when {
            lit <= 4 -> 3
            lit <= 8 -> 4
            else -> 5
        }
        val cells = (0 until side * side).shuffled(rnd).take(lit).toSet()
        val showMs = (900L + lit * 180L).coerceAtMost(2400L)
        return MemoryRound(side, cells, showMs)
    }

    private fun oddOneOut(level: Int): Round {
        val side = when {
            level < 2 -> 2
            level < 5 -> 3
            level < 10 -> 4
            level < 16 -> 5
            else -> 6
        }
        val hue = rnd.nextFloat() * 360f
        val sat = 0.55f + rnd.nextFloat() * 0.3f
        val value = 0.6f + rnd.nextFloat() * 0.25f
        val delta = max(0.05f, 0.30f - level * 0.013f)
        val oddValue = if (value + delta <= 1f && rnd.nextBoolean()) value + delta else value - delta
        return OddRound(side, rnd.nextInt(side * side), hue, sat, value, oddValue)
    }

    private fun rush(level: Int): Round {
        // level here counts cleared boards
        val side = when {
            level < 2 -> 3
            level < 6 -> 4
            else -> 5
        }
        return RushRound(side, (1..side * side).shuffled(rnd))
    }

    // ---------- logic ----------

    private fun nextNumber(level: Int): Round {
        val kinds = when {
            level < 4 -> 2
            level < 10 -> 4
            else -> 6
        }
        val seq: List<Int> = when (rnd.nextInt(kinds)) {
            0 -> { // add constant
                val start = rnd.nextInt(1, 20); val d = rnd.nextInt(2, 10)
                List(5) { start + it * d }
            }
            1 -> { // subtract constant
                val d = rnd.nextInt(2, 9); val start = rnd.nextInt(5 * d, 5 * d + 40)
                List(5) { start - it * d }
            }
            2 -> { // multiply
                val start = rnd.nextInt(1, 6); val r = rnd.nextInt(2, 4)
                List(5) { var v = start; repeat(it) { v *= r }; v }
            }
            3 -> { // growing gaps
                val start = rnd.nextInt(1, 10); val d = rnd.nextInt(1, 4)
                val out = mutableListOf(start)
                for (i in 1 until 5) out.add(out.last() + d * i)
                out
            }
            4 -> { // squares shifted
                val off = rnd.nextInt(0, 5); val s = rnd.nextInt(1, 5)
                List(5) { (s + it) * (s + it) + off }
            }
            else -> { // Fibonacci-like
                val a = rnd.nextInt(1, 6); val b = rnd.nextInt(a, a + 6)
                val out = mutableListOf(a, b)
                while (out.size < 5) out.add(out[out.size - 1] + out[out.size - 2])
                out
            }
        }
        val shown = seq.take(4).joinToString(",  ")
        val (opts, correct) = options(seq[4], max(3, abs(seq[4] - seq[3]) / 2))
        return ChoiceRound(prompt = "$shown,  ?", options = opts, correct = correct, bigPrompt = false)
    }
}
