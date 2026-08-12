package com.bellizia.owcompanion.ui.wiki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.data.model.HeroWiki
import com.bellizia.owcompanion.sim.Breakpoints
import com.bellizia.owcompanion.sim.WeaponSpec

/**
 * How many shots this hero needs, against everyone.
 *
 * The app has always been able to work this out and never showed it where it is wanted.
 * Sustained damage per second decides long fights and almost nothing in this game is a long
 * fight: what settles a duel is whether the other one dies to two shots or three, and that
 * is a whole number a player can hold in their head and act on.
 *
 * One column per distinct health pool rather than per hero, because fifty-two columns would
 * be a wall and most of them repeat - every 225 health hero dies to exactly the same count.
 * Point blank, and the heading says so: past the falloff these numbers change, and a table
 * that quietly averaged over range would be worse than no table.
 */
@Composable
internal fun HeroBreakpoints(
    hero: HeroWiki,
    roster: List<HeroWiki>,
    weapons: List<WeaponSpec>,
    modifier: Modifier = Modifier,
) {
    val pools = remember(roster) {
        Breakpoints.targetsFrom(
            roster.map { other ->
                Breakpoints.Target(
                    other.name,
                    other.health ?: 0,
                    other.armor ?: 0,
                    other.shields ?: 0,
                )
            },
        )
    }
    // The generated quick melee is on every hero and says the same thing about all of them.
    val mine = remember(hero.name, weapons) {
        weapons.filter { it.hero == hero.name && it.name != "Quick Melee" }
    }
    if (pools.isEmpty() || mine.isEmpty()) return

    Column(modifier = modifier.padding(start = 12.dp, top = 16.dp, end = 12.dp)) {
        Text(
            text = stringResource(R.string.wiki_breakpoints),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.wiki_breakpoints_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )

        mine.forEach { weapon ->
            val counts = remember(weapon, pools) {
                pools.map { pool -> pool to Breakpoints.shotsToKill(weapon, pool) }
                    // Beyond a couple of dozen the answer is "it does not", and a count
                    // there would only look precise.
                    .filter { (_, result) -> result.body in 1..30 }
            }
            if (counts.isEmpty()) return@forEach

            Text(
                text = weapon.name,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 3.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(counts, key = { (pool, _) -> pool.name }) { (pool, result) ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "${pool.total}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${result.body}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                // The head count earns its place only when it saves a shot.
                                if (result.headSaves) {
                                    Text(
                                        text = stringResource(
                                            R.string.wiki_breakpoints_head,
                                            result.head ?: 0,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 3.dp, bottom = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
