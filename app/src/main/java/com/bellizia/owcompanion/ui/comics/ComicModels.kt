package com.bellizia.owcompanion.ui.comics

import kotlinx.serialization.Serializable

/**
 * The shape of a balloon, which is how a comic says *how* a line is spoken.
 *
 * Four is the whole vocabulary on purpose. A tool with fifteen bubble styles is a tool
 * nobody finishes a strip in, and these four cover everything a match story needs: someone
 * says a thing, someone thinks a thing, someone shouts, and the narrator sets the scene.
 */
enum class Balloon { Speech, Thought, Shout, Caption }

/**
 * One line of dialogue, placed on the panel.
 *
 * `x` and `y` are fractions of the panel rather than pixels, for the same reason the tactics
 * board uses fractions: a strip written on a phone has to mean the same thing in an exported
 * page four times the size.
 *
 * There is no tail field: where the pointer meets the speaker is worked out from who is in
 * the panel, so dragging a balloon across two heroes re-aims it at whoever it now sits over
 * instead of leaving it pointing at nobody. One less control, and one less way to be wrong.
 */
@Serializable
data class Line(
    val id: String,
    val text: String = "",
    val kind: Balloon = Balloon.Speech,
    val x: Float = 0.5f,
    val y: Float = 0.18f,
)

/**
 * A hero standing in a panel.
 *
 * `scale` is a multiplier on a size that is itself a fraction of the panel, so a character
 * keeps its proportions at any export size. `flipped` mirrors the portrait horizontally,
 * which is the cheapest way to make two heroes face each other rather than both stare right.
 */
@Serializable
data class Actor(
    val id: String,
    val heroKey: String,
    val heroName: String,
    val portrait: String?,
    val x: Float = 0.5f,
    val y: Float = 0.6f,
    val scale: Float = 1f,
    val flipped: Boolean = false,
)

/** One frame of the story: a background, whoever is in it, and what they say. */
@Serializable
data class Panel(
    val id: String,
    val background: String? = null,
    val actors: List<Actor> = emptyList(),
    val lines: List<Line> = emptyList(),
)

@Serializable
data class Strip(
    val name: String = "",
    val panels: List<Panel> = listOf(Panel(id = "1")),
) {
    fun panel(index: Int): Panel = panels.getOrElse(index) { panels.first() }
}
