package com.bellizia.owcompanion.ui.board

/**
 * A guide video for a map, when someone has made one worth pointing at.
 *
 * This is a hand-kept list and makes no apology for it. There is no source that maps a
 * Overwatch map to a good video: the judgement of which guide is worth a player's twelve
 * minutes is exactly the part a program cannot do, so it is written down by someone who
 * watched it.
 *
 * The author is carried alongside the identifier and shown with the link, because pointing
 * at somebody's work without naming them is not on. Nothing is re-hosted and nothing is
 * stripped: the link opens their video, on their channel, with their advertising.
 *
 * To add one: watch it, and put the eleven characters after `watch?v=` here. Videos that
 * their author has stopped from being embedded still open in the YouTube app, which is why
 * this opens rather than embeds.
 */
data class MapVideo(val map: String, val id: String, val author: String, val title: String)

/**
 * One to begin with, checked against YouTube's own oEmbed service so the identifier is
 * known to resolve and the author's name is theirs rather than my guess at it.
 */
val MapVideos: List<MapVideo> = listOf(
    MapVideo(
        map = "kings-row",
        id = "JP0_CwskQUM",
        author = "Kajor",
        title = "The COMPLETE Kings Row Map Guide",
    ),
)

private val byMap = MapVideos.associateBy { it.map }

/** The guide for a map, or null - which is most of them until the list grows. */
fun videoFor(mapKey: String): MapVideo? = byMap[mapKey]

/**
 * Where the video lives.
 *
 * The YouTube app takes this and so does a browser, which is the whole reason it is a plain
 * address rather than anything cleverer.
 */
fun MapVideo.url(seconds: Int? = null): String =
    "https://www.youtube.com/watch?v=$id" + (seconds?.let { "&t=${it}s" } ?: "")
