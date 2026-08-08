package com.bellizia.owcompanion.ui.board

import kotlinx.serialization.Serializable

/** Which side a token belongs to. */
enum class Side { Ours, Theirs }

/**
 * One hero on the board.
 *
 * Positions are fractions of the board rather than pixels, so a plan drawn on a phone still
 * means the same thing on a tablet, in landscape, or in an exported page.
 */
@Serializable
data class Token(
    val id: String,
    val heroKey: String,
    val heroName: String,
    val portrait: String?,
    val side: Side = Side.Ours,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
)

/**
 * One moment in the plan.
 *
 * A frame is a whole board, not a diff. Adding one copies the frame before it so you move
 * what changed and leave the rest alone, which is how a tactics board is used out loud -
 * "now Winston jumps, everyone else holds".
 */
@Serializable
data class Frame(
    val id: String,
    val caption: String = "",
    val tokens: List<Token> = emptyList(),
)

@Serializable
data class Board(
    val name: String = "",
    /** Content URI of the image behind the tokens; null draws a plain grid instead. */
    val background: String? = null,
    val frames: List<Frame> = listOf(Frame(id = "1")),
) {
    fun frame(index: Int): Frame = frames.getOrElse(index) { frames.first() }
}
