package com.bellizia.owcompanion.sim

/**
 * What a hero can put back into themselves, without anybody helping.
 *
 * The duel takes incoming healing as a number, which was fine for a support pocketing
 * somebody and useless for the case people actually ask about: a tank who heals himself.
 * Reading Mauga's self-heal off a menu of other people's abilities was the wrong shape
 * entirely - it is a property of the target, not a choice the reader should have to make.
 *
 * Two shapes, because the game has two:
 *
 * - **A rate.** Roadhog breathes for 150 a second whatever is happening. Straightforward.
 * - **A share of their own damage.** Mauga's Cardiac Overdrive returns everything he deals;
 *   Reaper's passive returns a third. These cannot be written down as a number, because
 *   they depend on what the healer is shooting at the time - so the app works them out from
 *   that hero's own weapon rather than pretending a constant exists.
 *
 * Every figure is the wiki's, from the ability's own `heal` field. Abilities that heal only
 * allies are absent: this is what a hero does for themselves.
 */
sealed interface SelfHeal {
    val hero: String
    val ability: String

    /** Healing a second while it is running. */
    data class PerSecond(
        override val hero: String,
        override val ability: String,
        val rate: Double,
    ) : SelfHeal

    /**
     * A fraction of the damage this hero is dealing, returned to them.
     *
     * The fraction is exact; what it multiplies is an estimate, because it depends on what
     * they happen to be shooting. The app uses their own best weapon, which is the
     * favourable reading - and the favourable reading is the right one here, since the
     * question being asked is whether they can be killed *anyway*.
     */
    data class ShareOfOwnDamage(
        override val hero: String,
        override val ability: String,
        val share: Double,
    ) : SelfHeal

    companion object {
        /**
         * Every self-heal the wiki states a figure for, by hero.
         *
         * Abilities with a duration and a cooldown are still listed at their full rate:
         * this answers "can it survive while the ability is up", which is when the fight is
         * actually decided. Nobody dives a Roadhog who has already breathed.
         */
        val All: List<SelfHeal> = listOf(
            // Flat rates, straight from the wiki's heal field.
            PerSecond("Roadhog", "Take a Breather", 150.0),
            PerSecond("Ramattra", "Nanite Repair", 100.0),
            PerSecond("Bastion", "Self-Repair", 90.0),
            PerSecond("Mei", "Cryo-Freeze", 62.5),
            PerSecond("Junker Queen", "Rampage", 50.0),
            PerSecond("Mei", "Skating Rink", 50.0),
            PerSecond("Soldier: 76", "Biotic Field", 40.0),
            PerSecond("Junker Queen", "Carnage", 33.3),
            PerSecond("Moira", "Biotic Grasp Alt Fire", 30.0),

            // Proportional to what they are dealing.
            ShareOfOwnDamage("Mauga", "Cardiac Overdrive", 1.0),
            ShareOfOwnDamage("Hazard", "Anarchic Zeal", 0.40),
            ShareOfOwnDamage("Reaper", "The Reaping", 0.30),
        )

        /** What this hero can do for themselves, best first. */
        fun forHero(hero: String): List<SelfHeal> =
            All.filter { it.hero.equals(hero, ignoreCase = true) }

        /**
         * The rate this works out to, given what the hero is capable of dealing.
         *
         * @param ownDamagePerSecond that hero's own output; ignored by the flat rates.
         */
        fun rateOf(heal: SelfHeal, ownDamagePerSecond: Double): Double = when (heal) {
            is PerSecond -> heal.rate
            is ShareOfOwnDamage -> heal.share * ownDamagePerSecond
        }
    }
}
