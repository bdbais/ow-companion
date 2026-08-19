package com.bellizia.owcompanion.ui.comics

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.data.WikiRepository
import com.bellizia.owcompanion.ui.common.localised
import kotlin.math.abs

/** Reading what Blizzard published, or making your own. */
private enum class Mode { Read, Make }

private enum class Share { Png, Pdf, Video }

/**
 * Comics: the official ones, and a small workshop for your own.
 *
 * Two halves that have nothing to do with each other technically and everything to do with
 * each other in a reader's head - you come here either to read a story or to tell one. They
 * share a tab rather than taking two, because the bottom bar is already fuller than Material
 * recommends and neither half is big enough to earn its own.
 */
@Composable
fun ComicsScreen(
    modifier: Modifier = Modifier,
    viewModel: ComicViewModel = viewModel(),
) {
    // Saveable, not just remembered: opening the keyboard to type a line is a configuration
    // change on some devices, and losing the tab you were on every time you started typing
    // would make the workshop unusable.
    var mode by rememberSaveable { mutableStateOf(Mode.Read) }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Row(modifier = Modifier.padding(bottom = 8.dp)) {
            Mode.entries.forEach { entry ->
                FilterChip(
                    selected = mode == entry,
                    onClick = { mode = entry },
                    label = {
                        Text(
                            stringResource(
                                if (entry == Mode.Read) R.string.comics_read else R.string.comics_make,
                            ),
                        )
                    },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
        when (mode) {
            Mode.Read -> OfficialSection()
            Mode.Make -> Workshop(viewModel)
        }
    }
}

/**
 * A door to Blizzard's gallery, in the reader's language where there is one.
 *
 * Deliberately one button. The app does not mirror the stories, does not list them, and does
 * not cache their titles - they are Blizzard's to publish and a bundled list is a list that
 * goes out of date. What it can do that a bookmark cannot is know which of the fifteen
 * languages the gallery actually exists in, and say so instead of dumping a reader onto a
 * redirect.
 */
@Composable
private fun OfficialSection() {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val translated = OfficialComics.published(locale)

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.comics_official_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.comics_official_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (!translated) {
                    Text(
                        text = stringResource(R.string.comics_official_english),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(OfficialComics.gallery(locale)))
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Text(
                        text = stringResource(R.string.comics_official_open),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.comics_official_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun Workshop(viewModel: ComicViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sharing by remember { mutableStateOf<Share?>(null) }

    // Started from a side effect so the buttons can go flat while it runs: encoding a
    // six-panel clip takes seconds, and a button that still looks idle gets pressed twice.
    LaunchedEffect(sharing) {
        val kind = sharing ?: return@LaunchedEffect
        val uri = runCatching {
            when (kind) {
                Share.Png -> ComicExport.toPng(context, state.strip)
                Share.Pdf -> ComicExport.toPdf(context, state.strip)
                Share.Video -> ComicExport.toVideo(context, state.strip)
            }
        }.getOrNull()
        sharing = null
        if (uri != null) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = when (kind) {
                    Share.Png -> "image/png"
                    Share.Pdf -> "application/pdf"
                    Share.Video -> "video/mp4"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(send, null)) }
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.setBackground(it.toString()) } }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = state.strip.name,
            onValueChange = viewModel::setName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.comics_name_hint)) },
        )

        PanelStrip(state, viewModel)

        PanelCanvas(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        Selected(state, viewModel)

        // Labelled, because the selected balloon's own kind chips carry these same four
        // words a few pixels above. Two identical rows meaning different things is the sort
        // of thing that is obvious the moment you look at it and invisible while writing it.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.comics_add_line),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Balloon.entries.forEach { kind ->
                OutlinedButton(
                    onClick = { viewModel.addLine(kind) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 4.dp,
                    ),
                ) {
                    Text(stringResource(kind.label), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                pickImage.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            }) { Text(stringResource(R.string.comics_background)) }
            TextButton(onClick = viewModel::save, enabled = state.strip.name.isNotBlank()) {
                Text(stringResource(R.string.comics_save))
            }
            TextButton(onClick = viewModel::reset) { Text(stringResource(R.string.comics_new)) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(onClick = { sharing = Share.Png }, enabled = sharing == null) {
                Text(stringResource(R.string.comics_export_png))
            }
            TextButton(onClick = { sharing = Share.Pdf }, enabled = sharing == null) {
                Text(stringResource(R.string.comics_export_pdf))
            }
            TextButton(onClick = { sharing = Share.Video }, enabled = sharing == null) {
                Text(stringResource(R.string.comics_export_video))
            }
        }

        HeroPicker(state, viewModel)
        SavedStrips(state, viewModel)
    }
}

/** The panels, in order, with the one being worked on marked. */
@Composable
private fun PanelStrip(state: ComicUiState, viewModel: ComicViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(modifier = Modifier.weight(1f)) {
            items(state.strip.panels.size) { index ->
                FilterChip(
                    selected = state.panelIndex == index,
                    onClick = { viewModel.selectPanel(index) },
                    label = { Text(stringResource(R.string.comics_panel, index + 1)) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
        IconButton(onClick = viewModel::addPanel) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.comics_add_panel))
        }
        IconButton(
            onClick = viewModel::removePanel,
            enabled = state.strip.panels.size > 1,
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.comics_remove_panel),
            )
        }
    }
}

/**
 * The panel itself, drawn by the exporter's own routine.
 *
 * Not a Compose reconstruction of a comic panel: the real [ComicPainter], on the native
 * canvas underneath. Anything else and the balloon that looked right on the phone would sit
 * somewhere else in the PNG, which is the sort of difference you only notice after sending
 * it to somebody.
 */
@Composable
private fun PanelCanvas(
    state: ComicUiState,
    viewModel: ComicViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val panel = state.panel
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .aspectRatio(ComicPainter.WIDTH.toFloat() / ComicPainter.HEIGHT)
            .clip(RoundedCornerShape(6.dp))
            .pointerInput(panel.id) {
                detectTapGestures { at ->
                    val hit = nearest(panel, at, this.size.width.toFloat(), this.size.height.toFloat())
                    when (hit) {
                        null -> viewModel.edit(null)
                        else -> if (hit.startsWith("l")) viewModel.edit(hit) else viewModel.pick(hit)
                    }
                }
            }
            .pointerInput(panel.id) {
                var dragging: String? = null
                detectDragGestures(
                    onDragStart = { at ->
                        dragging = nearest(
                            panel,
                            at,
                            this.size.width.toFloat(),
                            this.size.height.toFloat(),
                        )
                        dragging?.let {
                            if (it.startsWith("l")) viewModel.edit(it) else viewModel.pick(it)
                        }
                    },
                    onDragEnd = { dragging = null },
                    onDragCancel = { dragging = null },
                ) { change, _ ->
                    change.consume()
                    val id = dragging ?: return@detectDragGestures
                    val w = this.size.width.toFloat()
                    val h = this.size.height.toFloat()
                    if (w == 0f || h == 0f) return@detectDragGestures
                    val x = change.position.x / w
                    val y = change.position.y / h
                    if (id.startsWith("l")) viewModel.moveLine(id, x, y)
                    else viewModel.moveActor(id, x, y)
                }
            },
    ) {
        drawIntoCanvas { canvas ->
            ComicPainter.draw(
                context,
                canvas.nativeCanvas,
                panel,
                this.size.width.toInt(),
                this.size.height.toInt(),
            )
        }
    }
}

/**
 * Whatever the tap landed on, or nothing.
 *
 * Balloons win ties against characters: a balloon usually sits over the head of whoever is
 * saying it, and when they overlap the words are what a person means to grab.
 */
private fun nearest(panel: Panel, at: Offset, w: Float, h: Float): String? {
    if (w == 0f || h == 0f) return null
    val x = at.x / w
    val y = at.y / h
    val reach = 0.13f

    panel.lines
        .filter { abs(it.x - x) < reach * 2 && abs(it.y - y) < reach }
        .minByOrNull { abs(it.x - x) + abs(it.y - y) }
        ?.let { return it.id }

    return panel.actors
        .filter { abs(it.x - x) < reach * it.scale && abs(it.y - y) < reach * it.scale }
        .minByOrNull { abs(it.x - x) + abs(it.y - y) }
        ?.id
}

/** The controls for whatever is selected, and nothing when nothing is. */
@Composable
private fun Selected(state: ComicUiState, viewModel: ComicViewModel) {
    val line = state.line
    val actor = state.panel.actors.firstOrNull { it.id == state.picked }

    when {
        line != null -> Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = line.text,
                onValueChange = { viewModel.setLineText(line.id, it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.comics_line_hint)) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Balloon.entries.forEach { kind ->
                    FilterChip(
                        selected = line.kind == kind,
                        onClick = { viewModel.setLineKind(line.id, kind) },
                        label = {
                            Text(
                                stringResource(kind.label),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                IconButton(onClick = { viewModel.removeLine(line.id) }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.comics_remove_line),
                    )
                }
            }
        }

        actor != null -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = localised(actor.heroName),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.scaleActor(actor.id, 0.85f) }) {
                Icon(
                    Icons.Filled.ZoomOut,
                    contentDescription = stringResource(R.string.comics_smaller),
                )
            }
            IconButton(onClick = { viewModel.scaleActor(actor.id, 1.18f) }) {
                Icon(Icons.Filled.ZoomIn, contentDescription = stringResource(R.string.comics_bigger))
            }
            IconButton(onClick = { viewModel.flipActor(actor.id) }) {
                Icon(Icons.Filled.Flip, contentDescription = stringResource(R.string.comics_flip))
            }
            IconButton(onClick = { viewModel.removeActor(actor.id) }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.comics_remove_actor),
                )
            }
        }

        else -> Text(
            text = stringResource(R.string.comics_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun HeroPicker(state: ComicUiState, viewModel: ComicViewModel) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.comics_cast),
            style = MaterialTheme.typography.titleSmall,
        )
        LazyRow(modifier = Modifier.padding(top = 4.dp)) {
            items(state.roster) { hero ->
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(hero.key) {
                            detectTapGestures { viewModel.addActor(hero) }
                        },
                ) {
                    AsyncImage(
                        model = WikiRepository.imageUri(hero.portrait),
                        contentDescription = localised(hero.name),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedStrips(state: ComicUiState, viewModel: ComicViewModel) {
    if (state.saved.isEmpty()) return
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            text = stringResource(R.string.comics_saved),
            style = MaterialTheme.typography.titleSmall,
        )
        state.saved.forEach { strip ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.open(strip) }, modifier = Modifier.weight(1f)) {
                    Text(
                        text = strip.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { viewModel.delete(strip.name) }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.comics_remove_saved),
                    )
                }
            }
        }
    }
}

private val Balloon.label: Int
    get() = when (this) {
        Balloon.Speech -> R.string.comics_balloon_speech
        Balloon.Thought -> R.string.comics_balloon_thought
        Balloon.Shout -> R.string.comics_balloon_shout
        Balloon.Caption -> R.string.comics_balloon_caption
    }
