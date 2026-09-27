package com.primez.oneminutemind.game

import kotlin.math.min
import kotlin.random.Random

const val ROUND_MS = 60_000L

/** What happened after an answer, for the UI to animate. */
data class Outcome(val correct: Boolean, val points: Int, val penaltyMs: Long, val comboUp: Boolean)

/** Final numbers for one 60-second game. */
data class GameResult(
    val game: GameId,
    val score: Int,
    val correct: Int,
    val wrong: Int,
    val maxCombo: Int,
    val bestMemory: Int = 0,
) {
    val answered get() = correct + wrong
    val accuracy: Int get() = if (answered == 0) 0 else (correct * 100) / answered
    /** 0–100 rating used for the skill chart. */
    val rating: Int get() = game.ratingFor(score)
}

/**
 * The rules for one game: scoring, combos and time penalties. Time itself is kept by the UI.
 * Combo multiplier: ×1, then ×2 after 5 in a row, ×3 after 10 in a row.
 */
class GameSession(val game: GameId, seed: Long) {
    private val generator = RoundGenerator(game, Random(seed))

    var score = 0; private set
    var correct = 0; private set
    var wrong = 0; private set
    var combo = 0; private set
    var maxCombo = 0; private set
    var bestMemory = 0; private set
    var round: Round = generator.next(0); private set

    val multiplier: Int get() = 1 + min(combo / 5, 2)

    private fun basePoints(r: Round): Int = when (r) {
        is MemoryRound -> 4 * r.lit.size
        is RushRound -> 2 * r.numbers.size
        is OddRound -> 8 + r.side
        is ChoiceRound -> 10
    }

    /** Call when a round is finished, right or wrong. Moves on to the next round. */
    fun answer(isCorrect: Boolean): Outcome {
        val r = round
        if (r is ChoiceRound && r.free) {
            round = generator.next(correct)
            return Outcome(true, 0, 0L, false)
        }
        val outcome = if (isCorrect) {
            val before = multiplier
            combo++
            maxCombo = maxOf(maxCombo, combo)
            correct++
            if (r is MemoryRound) bestMemory = maxOf(bestMemory, r.lit.size)
            val pts = basePoints(r) * multiplier
            score += pts
            Outcome(true, pts, 0L, multiplier > before)
        } else {
            combo = 0
            wrong++
            Outcome(false, 0, if (r is MemoryRound) 1_000L else 2_000L, false)
        }
        round = generator.next(correct)
        return outcome
    }

    /** Number Rush: a wrong tap costs time and the combo but keeps the board. */
    fun rushMistake(): Outcome {
        combo = 0
        wrong++
        return Outcome(false, 0, 1_000L, false)
    }

    fun result() = GameResult(game, score, correct, wrong, maxCombo, bestMemory)
}
