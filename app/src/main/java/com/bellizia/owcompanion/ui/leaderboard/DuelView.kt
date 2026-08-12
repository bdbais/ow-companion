package com.bellizia.owcompanion.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.sim.Breakpoints
import com.bellizia.owcompanion.sim.Duel
import com.bellizia.owcompanion.sim.WeaponModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Can this kill that.
 *
 * The question every other screen dances around. A damage chart says what a weapon does; a
 * healing chart says what a support does; neither will put the two against each other and
 * tell you the fight is unwinnable, which is the thing worth knowing before you take it.
 *
 * Two answers, because the honest reply has two halves. The breakpoint - how many shots if
 * nothing heals - is what decides almost every real fight, since almost nothing lasts long
 * enough for a rate to matter. The race is what decides the ones that do.
 */
@Composable
internal fun DuelView(state: LeaderboardUiState) {
    val targets = remember(state.heroes) {
        Breakpoints.targetsFrom(
            state.heroes.values.map { hero ->
                Breakpoints.Target(hero.name, hero.health ?: 0, hero.armor ?: 0, hero.shields ?: 0)
            },
        )
    }
    // Weapons someone actually duels with: the generated quick melee on every hero would
    // otherwise fill half the list with the same 40 damage swing.
    val attackers = remember(state.weapons) {
        state.weapons.filter { it.name != "Quick Melee" }.sortedBy { it.hero + it.name }
    }
    if (targets.isEmpty() || attackers.isEmpty()) return

    var attacker by rememberSaveable { mutableIntStateOf(0) }
    var target by rememberSaveable { mutableIntStateOf(0) }
    var healer by rememberSaveable { mutableIntStateOf(0) }

    val weapon = attackers[attacker.coerceIn(attackers.indices)]
    val chosen = targets[target.coerceIn(targets.indices)]
    // Position zero is nobody healing, which is the honest default: assuming a pocket
    // Mercy would make every tank look immortal.
    val healers = state.healing
    val healing = healers.getOrNull(healer - 1)
    val defender = Duel.Defender(
        target = chosen,
        healingPerSecond = healing?.healingPerSecond ?: 0.0,
    )

    // Off the main thread: resolving is one simulation, but the list of who could help is
    // one per weapon in the game, and that is a second of work on a slow phone.
    val outcome by produceState<Duel.Outcome?>(null, weapon, chosen, defender.healingPerSecond) {
        value = null
        value = withContext(Dispatchers.Default) {
            Duel.resolve(weapon, WeaponModel(weapon), defender)
        }
    }
    val help by produceState<List<Duel.Contributor>>(emptyList(), outcome) {
        val current = outcome
        value = if (current == null || current.verdict == Duel.Verdict.Kills) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                Duel.helpFor(
                    outcome = current,
                    candidates = attackers.map { it to WeaponModel(it) },
                    defender = defender,
                )
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    text = stringResource(R.string.duel_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )

                PickerRow(R.string.duel_attacker) {
                    itemsIndexed(attackers) { index, option ->
                        FilterChip(
                            selected = index == attacker,
                            onClick = { attacker = index },
                            label = {
                                Text("${option.hero} · ${option.name}", style = MaterialTheme.typography.labelSmall)
                            },
                        )
                    }
                }

                PickerRow(R.string.duel_target) {
                    itemsIndexed(targets) { index, option ->
                        FilterChip(
                            selected = index == target,
                            onClick = { target = index },
                            label = {
                                Text("${option.name} · ${option.total}", style = MaterialTheme.typography.labelSmall)
                            },
                        )
                    }
                }

                PickerRow(R.string.duel_healer) {
                    item {
                        FilterChip(
                            selected = healer == 0,
                            onClick = { healer = 0 },
                            label = {
                                Text(stringResource(R.string.duel_no_healer), style = MaterialTheme.typography.labelSmall)
                            },
                        )
                    }
                    itemsIndexed(healers) { index, option ->
                        FilterChip(
                            selected = healer == index + 1,
                            onClick = { healer = index + 1 },
                            label = {
                                Text(
                                    "${option.source.hero} · ${option.healingPerSecond.roundToInt()} hps",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }
        }

        val current = outcome
        if (current == null) {
            item {
                Text(
                    text = stringResource(R.string.duel_working),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            return@LazyColumn
        }

        item { Verdict(current) }

        if (help.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.duel_help_title, current.shortfall.roundToInt()),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
                )
            }
            items(help.size) { index ->
                val row = help[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.weapon.hero, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = row.weapon.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${row.damagePerSecond.roundToInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (row.enough) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Verdict(outcome: Duel.Outcome) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            text = stringResource(
                when (outcome.verdict) {
                    Duel.Verdict.Kills -> R.string.duel_kills
                    Duel.Verdict.Stalemate -> R.string.duel_stalemate
                    Duel.Verdict.Outhealed -> R.string.duel_outhealed
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            color = when (outcome.verdict) {
                Duel.Verdict.Kills -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            },
        )

        // The breakpoint stands whatever the race says: how many shots it would take with
        // nothing healing is worth knowing even when something is.
        outcome.shotsToKill?.let { body ->
            val head = outcome.headshotsToKill
            Text(
                text = if (head != null && head < body) {
                    stringResource(R.string.duel_shots_head, body, head)
                } else {
                    stringResource(R.string.duel_shots, body)
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Text(
            text = stringResource(
                R.string.duel_rates,
                outcome.damagePerSecond.roundToInt(),
                outcome.healingPerSecond.roundToInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        outcome.secondsToKill?.let { seconds ->
            Text(
                text = stringResource(R.string.duel_seconds, seconds),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun PickerRow(
    titleRes: Int,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 3.dp),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}
