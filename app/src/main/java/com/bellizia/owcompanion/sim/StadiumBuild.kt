package com.bellizia.owcompanion.sim

import kotlinx.serialization.Serializable

@Serializable
data class StadiumBuff(
    val stat: String,
    val value: Double? = null,
    val percent: Boolean = false,
    /** Name of the engine field this maps to, or null when the model cannot act on it. */
    val simulated: String? = null,
)

@Serializable
data class StadiumItem(
    val name: String,
    val hero: String = "All heroes",
    /** `Weapon`, `Ability`, `Survival` or `Gadget`. */
    val category: String = "",
    val rarity: String = "",
    val cost: Double? = null,
    val buffs: List<StadiumBuff> = emptyList(),
    val description: String = "",
) {
    /** Whether any of this item's buffs change what the damage model computes. */
    val affectsDamage: Boolean get() = buffs.any { it.simulated != null }

    fun percentOf(field: String): Double =
        buffs.filter { it.simulated == field }.sumOf { it.value ?: 0.0 }
}

/** One item in a proposed build, with what adding it was worth. */
data class BuildStep(
    val item: StadiumItem,
    /** Damage per second after adding this item. */
    val dps: Double,
    /** What this item added on its own, given everything picked before it. */
    val gain: Double,
    val spent: Double,
)

data class StadiumBuild(
    val steps: List<BuildStep>,
    val baseDps: Double,
    val finalDps: Double,
    val cost: Double,
)

/**
 * Proposes a Stadium build rather than asking someone to assemble one.
 *
 * The combinatorial space is far too large to search exhaustively, and a list of ninety
 * items with percentages next to them is a spreadsheet, not a tool. So this works the way a
 * player actually reasons: repeatedly take whichever affordable item adds the most, and
 * report what each one was worth. Seeing that one item is worth 180 dps and the next 12 is
 * the part that teaches something.
 *
 * Only Weapon Power and Attack Speed are considered, because they are the only buffs the
 * damage model computes. Survival and ability items are real and often better - they are
 * simply not what this ranks, and the screen says so.
 */
class StadiumOptimizer(
    private val optimizer: DamageOptimizer = DamageOptimizer(),
) {
    fun bestBuild(
        spec: WeaponSpec,
        items: List<StadiumItem>,
        budget: Double,
        slots: Int = DEFAULT_SLOTS,
        modifiers: Modifiers = Modifiers.NONE,
    ): StadiumBuild {
        val candidates = items.filter {
            it.affectsDamage && (it.hero == "All heroes" || it.hero == spec.hero)
        }

        val chosen = mutableListOf<BuildStep>()
        val remaining = candidates.toMutableList()
        var spent = 0.0

        val baseDps = dpsWith(spec, emptyList(), modifiers)
        var currentDps = baseDps

        repeat(slots) {
            // Chosen by gain per unit of cash, not by gain alone. With a budget, the
            // biggest single upgrade is often the wrong buy: a 13,000 item worth nine
            // damage per second loses to three 1,000 items worth two each.
            var best: Pair<StadiumItem, Double>? = null
            var bestValue = 0.0
            for (item in remaining) {
                val cost = item.cost ?: 0.0
                if (spent + cost > budget) continue
                val dps = dpsWith(spec, chosen.map { it.item } + item, modifiers)
                val gain = dps - currentDps
                if (gain <= 0) continue
                val value = if (cost > 0) gain / cost else Double.MAX_VALUE
                if (best == null || value > bestValue) {
                    best = item to dps
                    bestValue = value
                }
            }
            val (item, dps) = best ?: return@repeat

            spent += item.cost ?: 0.0
            chosen += BuildStep(item = item, dps = dps, gain = dps - currentDps, spent = spent)
            currentDps = dps
            remaining -= item
        }

        return StadiumBuild(
            steps = chosen,
            baseDps = baseDps,
            finalDps = currentDps,
            cost = spent,
        )
    }

    /** Applies a set of items to the weapon and measures its best damage per second. */
    fun dpsWith(
        spec: WeaponSpec,
        items: List<StadiumItem>,
        modifiers: Modifiers = Modifiers.NONE,
    ): Double {
        val weaponPower = 1 + items.sumOf { it.percentOf("weaponPower") } / 100.0
        val attackSpeed = 1 + items.sumOf { it.percentOf("attackSpeed") } / 100.0

        val boosted = spec.copy(
            damage = spec.damage.copy(
                dpshot = spec.damage.dpshot.map { it * weaponPower },
                chargeDamage = spec.damage.chargeDamage?.map { it * weaponPower },
                dpshotBall = spec.damage.dpshotBall?.times(weaponPower),
                dps = spec.damage.dps?.times(weaponPower),
            ),
        )
        // Attack speed rides the same path as Kitsune Rush: it compresses the firing cycle
        // rather than scaling each hit.
        return optimizer.bestFor(WeaponModel(boosted, speedFactor = attackSpeed), modifiers).dps
    }

    companion object {
        /** Stadium gives six item slots. */
        const val DEFAULT_SLOTS = 6
    }
}
