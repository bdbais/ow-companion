package com.bellizia.owcompanion.ui.about

import androidx.annotation.StringRes
import com.bellizia.owcompanion.R

/**
 * Places to find other people to play with.
 *
 * The rest of this app answers questions on its own. This one cannot: finding a group is
 * something other people do, so all the app can honestly offer is a list of doors and then
 * get out of the way.
 *
 * Everything here was either given to us or is a public address that has been stable for
 * years. Nothing is guessed. An expired invite is worse than no invite - it costs a tap and
 * says the app has stopped being maintained - so a community with no verified address for a
 * platform simply does not appear under it.
 */
enum class Platform(@StringRes val labelRes: Int) {
    Discord(R.string.social_discord),
    Telegram(R.string.social_telegram),
    WhatsApp(R.string.social_whatsapp),
    Reddit(R.string.social_reddit),
    Twitch(R.string.social_twitch),
    Web(R.string.social_web),
}

data class SocialLink(
    val platform: Platform,
    /** What to call it: a proper name, so not a string resource. */
    val name: String,
    val url: String,
    /**
     * Somewhere the author of the app is personally, rather than a public room.
     *
     * Worth marking, because "come and play with me" and "here is where the game's players
     * are" are different offers and it would be a small dishonesty to present them alike.
     */
    val mine: Boolean = false,
)

/**
 * Every link, grouped for display by [Platform] rather than listed in this order.
 *
 * Zero Hour also runs a Telegram channel for finding players for a match. It is absent
 * because nobody has given us its address yet, and a plausible guess would send people
 * nowhere. The Telegram and WhatsApp rows will appear on their own once there is something
 * real to put in them.
 */
val SocialLinks: List<SocialLink> = listOf(
    SocialLink(Platform.Twitch, "bais73m", "https://www.twitch.tv/bais73m", mine = true),
    SocialLink(
        Platform.Discord,
        "Zero Hour Community",
        "https://discord.gg/VWz4HwZ5rM",
        mine = true,
    ),
    SocialLink(Platform.Discord, "Community 3.0", "https://discord.gg/pkPWQMbt7A", mine = true),
    SocialLink(Platform.Reddit, "r/Overwatch", "https://www.reddit.com/r/Overwatch/"),
    SocialLink(
        Platform.Reddit,
        "r/OverwatchUniversity",
        "https://www.reddit.com/r/OverwatchUniversity/",
    ),
    SocialLink(
        Platform.Reddit,
        "r/CompetitiveOverwatch",
        "https://www.reddit.com/r/CompetitiveOverwatch/",
    ),
    SocialLink(Platform.Web, "Overwatch Forums", "https://us.forums.blizzard.com/en/overwatch/"),
    SocialLink(Platform.Web, "Overwatch Wiki", "https://overwatch.fandom.com/"),
)

/** Only the platforms that actually have something behind them. */
val SocialPlatforms: List<Platform> =
    Platform.entries.filter { platform -> SocialLinks.any { it.platform == platform } }

/**
 * The links matching a typed query and a chosen platform.
 *
 * The search is over names rather than addresses: someone looking for "zero hour" should
 * find it, and nobody types a discord.gg code from memory.
 */
fun socialMatching(query: String, platform: Platform?): List<SocialLink> {
    val trimmed = query.trim()
    return SocialLinks.filter { link ->
        (platform == null || link.platform == platform) &&
            (trimmed.isEmpty() || link.name.contains(trimmed, ignoreCase = true))
    }
}
