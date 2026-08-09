package com.bellizia.owcompanion.ui.wiki

/**
 * Guides for playing a hero, made by people who actually play them.
 *
 * The app writes none of this itself, and that is a decision rather than a shortcut. Every
 * number in here is traceable to the wiki or to Blizzard, and it stays believable precisely
 * because nothing is invented. "How to play Ana at Diamond" is judgement, not data: writing
 * it would mean inventing expertise, which is the one thing this project has refused to do
 * from the start - the annotated maps, the past-season ranks, Ashe's scope.
 *
 * So it points at the people who have the expertise, and names them.
 *
 * Every identifier below was checked against YouTube's own oEmbed service, which confirms
 * the video resolves, that its author permits embedding, and what the author is actually
 * called - rather than a name typed from memory.
 */
data class HeroGuide(
    val hero: String,
    val id: String,
    val author: String,
    val title: String,
    val level: Level,
) {
    /**
     * Roughly who a guide is for.
     *
     * Two steps rather than a rung per rank: nobody makes a guide "for Platinum", and
     * pretending the distinction is finer than it is would be a fiction of its own.
     */
    enum class Level { Basics, Deeper }

    val url: String get() = "https://www.youtube.com/watch?v=$id"
}

val HeroGuides: List<HeroGuide> = listOf(
    HeroGuide(
        hero = "Ana",
        id = "Br9QUAoGcP4",
        author = "OhDough",
        title = "Ana Guide for Beginners and Pros",
        level = HeroGuide.Level.Basics,
    ),
    HeroGuide(
        hero = "Ana",
        id = "SE8BDyXrRE8",
        author = "Samito",
        title = "The Ultimate Ana Guide",
        level = HeroGuide.Level.Deeper,
    ),
    HeroGuide(
        hero = "Reinhardt",
        id = "8OcwtIWNdZk",
        author = "Recall",
        title = "Reinhardt For Dummies",
        level = HeroGuide.Level.Basics,
    ),
    HeroGuide(
        hero = "Reinhardt",
        id = "fyhtUwYia8w",
        author = "Kajor",
        title = "The Best Reinhardt Guide",
        level = HeroGuide.Level.Deeper,
    ),
)

private val byHero = HeroGuides.groupBy { it.hero }

/** The guides for a hero, basics first. Most heroes have none until the list grows. */
fun guidesFor(hero: String): List<HeroGuide> =
    byHero[hero].orEmpty().sortedBy { it.level.ordinal }
