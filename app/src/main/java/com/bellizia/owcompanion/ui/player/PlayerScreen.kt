@file:OptIn(ExperimentalLayoutApi::class)

package com.bellizia.owcompanion.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.ui.rememberFilterTaps
import com.bellizia.owcompanion.data.PlayerRepository
import com.bellizia.owcompanion.ui.theme.StatNumber

/**
 * A player's own career, looked up by BattleTag rather than by signing in.
 *
 * The absence of a login is the design, not a shortcut: Blizzard already publish a career
 * profile for anyone who has made theirs public, so a name is enough and the app never
 * handles a password. The other half of that bargain - a private profile shows nothing - is
 * stated on screen, because otherwise it looks like the app is broken.
 */
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.favourites.isNotEmpty()) {
            item { FavouritesRow(state = state, viewModel = viewModel) }
        }

        if (state.selected == null) {
            item { SearchBox(state = state, viewModel = viewModel) }
            state.error?.let { message ->
                item {
                    Text(
                        text = stringResource(R.string.player_failed) + "  " + message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(state.results, key = { it.id }) { hit ->
                SearchResult(hit = hit, onClick = { viewModel.select(hit) })
            }
            // Only once a search has actually run: otherwise this appears on the first
            // keystroke and reads as "you do not exist".
            if (state.searched && state.results.isEmpty() && state.error == null) {
                item {
                    Text(
                        text = stringResource(R.string.player_no_results),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            return@LazyColumn
        }

        item {
            ProfileHeader(
                state = state,
                onBack = viewModel::back,
                onToggleFavourite = viewModel::toggleFavourite,
            )
        }

        when {
            state.loading -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            state.isPrivate -> item {
                Text(
                    text = stringResource(R.string.player_private),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            state.error != null -> item {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        text = stringResource(R.string.player_failed),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = state.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                val summary = state.summary
                summary?.general?.let { general ->
                    item { Overall(general) }
                }
                if (summary?.roles?.isNotEmpty() == true) {
                    item { SectionTitle(stringResource(R.string.player_by_role)) }
                    // Fixed order rather than whatever the map iterates in, so the three
                    // rows do not swap places between one player and the next.
                    listOf("tank", "damage", "support").forEach { role ->
                        summary.roles[role]?.let { block ->
                            item { RoleRow(role = role, block = block) }
                        }
                    }
                }
                if (summary?.heroes?.isNotEmpty() == true) {
                    item { SectionTitle(stringResource(R.string.player_by_hero)) }
                    item { RoleFilter(state = state, viewModel = viewModel) }
                    items(state.heroes, key = { it.key }) { hero -> HeroRow(hero) }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.player_credit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

/**
 * The accounts already looked up, as a row of chips.
 *
 * A player commonly has more than one, and the search cannot tell them apart - three
 * different people are called "byz" and the number after the hash is not returned - so
 * having found the right one once, it stays found.
 */
@Composable
private fun FavouritesRow(state: PlayerUiState, viewModel: PlayerViewModel) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = stringResource(R.string.player_saved),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.favourites.forEach { hit ->
                InputChip(
                    selected = state.selected?.id == hit.id,
                    onClick = { viewModel.select(hit) },
                    label = { Text(hit.name) },
                    avatar = {
                        AsyncImage(
                            model = hit.avatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(22.dp).clip(CircleShape),
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.player_remove),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { viewModel.removeFavourite(hit) },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchBox(state: PlayerUiState, viewModel: PlayerViewModel) {
    Column {
        Text(
            text = stringResource(R.string.player_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.player_battletag)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = viewModel::search, enabled = !state.searching) {
                Text(stringResource(R.string.player_search))
            }
            if (state.searching) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(start = 12.dp).size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchResult(hit: PlayerRepository.Hit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = hit.avatar,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(text = hit.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = when {
                    hit.games != null && hit.winrate != null ->
                        stringResource(R.string.player_hit_line, hit.games, hit.winrate) +
                            (hit.title?.let { "  ·  $it" } ?: "")
                    !hit.isPublic -> stringResource(R.string.player_hidden)
                    else -> hit.title ?: stringResource(R.string.player_public)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    state: PlayerUiState,
    onBack: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    val hit = state.selected ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Only when there is a list to go back to: after opening a starred account straight
        // from the chips, back would lead nowhere.
        if (state.canGoBack) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.player_back),
                )
            }
        }
        AsyncImage(
            model = hit.avatar,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = hit.name, style = MaterialTheme.typography.headlineSmall)
            hit.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onToggleFavourite) {
            Icon(
                imageVector = if (state.isFavourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = stringResource(
                    if (state.isFavourite) R.string.player_unstar else R.string.player_star,
                ),
                tint = if (state.isFavourite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun Overall(block: PlayerRepository.Block) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Stat(stringResource(R.string.player_games), block.games.toString())
        Stat(stringResource(R.string.player_winrate), "%.1f%%".format(block.winrate))
        Stat(stringResource(R.string.player_kda), "%.2f".format(block.kda))
        Stat(stringResource(R.string.player_time), PlayerRepository.hours(block.seconds))
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(text = value, style = StatNumber, color = MaterialTheme.colorScheme.primary)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
    Text(text = title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun RoleRow(role: String, block: PlayerRepository.Block) {
    StatLine(
        name = role.replaceFirstChar(Char::uppercase),
        games = block.games,
        winrate = block.winrate,
        kda = block.kda,
        seconds = block.seconds,
        portrait = null,
    )
}

@Composable
private fun RoleFilter(state: PlayerUiState, viewModel: PlayerViewModel) {
    val roleTaps = rememberFilterTaps<PlayerRole>()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        PlayerRole.entries.forEach { role ->
            FilterChip(
                selected = role in state.roles,
                onClick = { viewModel.setRoles(roleTaps.onTap(role, state.roles, PlayerRole.entries.toSet())) },
                label = { Text(stringResource(role.labelRes)) },
            )
        }
    }
}

@Composable
private fun HeroRow(hero: HeroStat) {
    StatLine(
        name = hero.name,
        games = hero.games,
        winrate = hero.winrate,
        kda = hero.kda,
        seconds = hero.seconds,
        portrait = hero.portrait,
    )
}

@Composable
private fun StatLine(
    name: String,
    games: Int,
    winrate: Double,
    kda: Double,
    seconds: Long,
    portrait: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        portrait?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(end = 10.dp).size(34.dp).clip(CircleShape),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(
                R.string.player_line,
                games,
                winrate,
                kda,
                PlayerRepository.hours(seconds),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
