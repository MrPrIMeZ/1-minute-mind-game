package com.primez.oneminutemind.game

/** The five brain skills. Every game trains one of them. */
enum class Skill(val label: String, val emoji: String) {
    MATH("Math", "🧮"),
    MEMORY("Memory", "🧠"),
    FOCUS("Focus", "🎯"),
    SPEED("Speed", "⚡"),
    LOGIC("Logic", "🧩"),
}

/**
 * All the mini-games. [expertScore] is roughly what a very good player scores in
 * one minute; it is used to turn scores into a 0–100 skill rating.
 */
enum class GameId(
    val title: String,
    val tagline: String,
    val emoji: String,
    val skill: Skill,
    val expertScore: Int,
    val howTo: String,
) {
    QUICK_MATH(
        "Quick Math", "Solve as many sums as you can", "➕", Skill.MATH, 450,
        "Pick the right answer. Sums get harder the more you get right.",
    ),
    TRUE_FALSE(
        "True or False", "Is the equation right?", "✅", Skill.SPEED, 420,
        "Decide fast whether each equation is true or false.",
    ),
    BIGGER(
        "Which Is Bigger", "Pick the larger value", "⚖️", Skill.MATH, 420,
        "Tap the side with the bigger value. Watch out for tricky sums.",
    ),
    COLOUR_CLASH(
        "Colour Clash", "Tap the ink, not the word", "🎨", Skill.FOCUS, 420,
        "Choose the COLOUR the word is painted in and ignore what it says.",
    ),
    MEMORY_GRID(
        "Memory Grid", "Remember the lit tiles", "🟪", Skill.MEMORY, 380,
        "Some tiles light up for a moment. Tap all of them from memory.",
    ),
    ODD_ONE_OUT(
        "Odd One Out", "Spot the different shade", "🔍", Skill.FOCUS, 400,
        "One tile is a slightly different shade. Find it before time runs out.",
    ),
    NEXT_NUMBER(
        "Next Number", "Crack the pattern", "🔢", Skill.LOGIC, 330,
        "Work out the rule behind the sequence and pick the next number.",
    ),
    NUMBER_RUSH(
        "Number Rush", "Tap the numbers in order", "🚀", Skill.SPEED, 360,
        "Tap 1, 2, 3… as fast as you can. Every cleared board scores big.",
    ),
    MATCH_BACK(
        "Match Back", "Same as the last one?", "🔁", Skill.MEMORY, 420,
        "Is this symbol the SAME as the one just before it?",
    ),
    ARROW_FOCUS(
        "Arrow Focus", "Follow the middle arrow", "🏹", Skill.FOCUS, 460,
        "Which way does the MIDDLE arrow point? Ignore the others.",
    ),
    ;

    /** Turns a score into a 0–100 rating (100 = expert level). */
    fun ratingFor(score: Int): Int = (score * 100 / expertScore).coerceIn(0, 100)

    companion object {
        fun fromName(name: String?): GameId? = entries.firstOrNull { it.name == name }
    }
}
