package com.bellizia.owcompanion.ui.board

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.data.MapsRepository

/**
 * Choosing what the plan is drawn on.
 *
 * The board used to open a file picker and nothing else, which made the whole screen depend
 * on the reader already having a map on their phone. Blizzard publish a photograph of every
 * map and it ships with the app, so there is now something to start from - and the file
 * picker stays, because a top-down diagram is still better to plan on than a photograph and
 * those belong to whoever drew them.
 */
@Composable
fun MapPicker(
    onPick: (String) -> Unit,
    onPickFromDevice: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var maps by remember { mutableStateOf<List<MapsRepository.GameMap>>(emptyList()) }

    LaunchedEffect(Unit) {
        maps = MapsRepository(context).maps().filter { it.uri != null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onPickFromDevice) {
                Text(stringResource(R.string.board_map_own))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.board_map_cancel)) }
        },
        title = { Text(stringResource(R.string.board_map_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.board_map_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 128.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    items(maps, key = { it.key }) { map ->
                        MapCard(map = map, onClick = { map.uri?.let(onPick) })
                    }
                }
            }
        },
    )
}

@Composable
private fun MapCard(map: MapsRepository.GameMap, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(
                model = map.uri,
                contentDescription = map.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        Text(
            text = map.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
        val guide = videoFor(map.key)
        if (guide == null) {
            Text(
                // The mode matters more than the country when picking something to plan on.
                text = map.modes.firstOrNull()?.replaceFirstChar(Char::uppercase).orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            )
        } else {
            // Named, because pointing at someone's work without saying whose is not on.
            val context = LocalContext.current
            Text(
                text = stringResource(R.string.board_map_guide, guide.author),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(guide.url()))
                        runCatching { context.startActivity(intent) }
                    }
                    .padding(start = 6.dp, bottom = 6.dp),
            )
        }
    }
}

/**
 * Which of a sheet's diagrams to plan on.
 *
 * Only shown when the picture actually holds more than one, which is what Panels decides.
 */
@Composable
fun PanelChooser(
    panels: List<Panels.Panel>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    if (panels.size < 2) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.board_panel),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        panels.indices.forEach { index ->
            androidx.compose.material3.FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text("${index + 1}") },
            )
        }
        androidx.compose.material3.FilterChip(
            selected = selected < 0,
            onClick = { onSelect(-1) },
            label = { Text(stringResource(R.string.board_panel_all)) },
        )
    }
}
