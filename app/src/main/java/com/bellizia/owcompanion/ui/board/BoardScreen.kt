@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.bellizia.owcompanion.ui.board

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.data.WikiRepository

/** Blue for your side, red for theirs - the same way the game colours them. */
internal val OurBlue = Color(0xFF4C8DF6)
internal val TheirRed = Color(0xFFE0645C)

internal fun ringFor(side: Side): Color = if (side == Side.Ours) OurBlue else TheirRed

private enum class ExportKind { Pdf, Video }

/**
 * A tactics board for a team briefing: hero tokens on a picture, one frame per phase.
 *
 * The background is an image the reader picks. That is not a shortcut around a missing
 * feature - it is the honest answer to there being no published top-down maps. The good
 * community ones belong to the people who drew them, so rather than shipping someone else's
 * pictures the app takes whichever view you already have and puts your plan on top.
 */
@Composable
fun BoardScreen(
    modifier: Modifier = Modifier,
    viewModel: BoardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var exporting by remember { mutableStateOf<ExportKind?>(null) }

    // Kicked off from a side effect rather than the click handler so the button can go flat
    // while it runs: encoding a dozen phases is not instant, and a button that looks idle
    // gets pressed twice.
    LaunchedEffect(exporting) {
        val kind = exporting ?: return@LaunchedEffect
        val uri = runCatching {
            when (kind) {
                ExportKind.Pdf -> BoardExport.toPdf(context, state.board)
                ExportKind.Video -> BoardExport.toVideo(context, state.board)
            }
        }.getOrNull()
        exporting = null
        if (uri != null) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = if (kind == ExportKind.Pdf) "application/pdf" else "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(send, null)) }
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.setBackground(it.toString()) } }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        FrameStrip(state = state, viewModel = viewModel)

        OutlinedTextField(
            value = state.frame.caption,
            onValueChange = viewModel::setCaption,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.board_caption_hint)) },
        )

        BoardSurface(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Side.entries.forEach { side ->
                FilterChip(
                    selected = state.adding == side,
                    onClick = { viewModel.setAddingSide(side) },
                    label = {
                        Text(
                            stringResource(
                                if (side == Side.Ours) R.string.board_side_ours
                                else R.string.board_side_theirs,
                            ),
                        )
                    },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            TextButton(onClick = {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Text(
                    text = stringResource(R.string.board_background),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Box(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { exporting = ExportKind.Pdf },
                enabled = exporting == null,
            ) {
                Icon(
                    Icons.Filled.PictureAsPdf,
                    contentDescription = stringResource(R.string.board_export_pdf),
                )
            }
            IconButton(
                onClick = { exporting = ExportKind.Video },
                enabled = exporting == null,
            ) {
                Icon(
                    Icons.Filled.Movie,
                    contentDescription = stringResource(R.string.board_export_video),
                )
            }
        }

        HeroStrip(state = state, viewModel = viewModel)
    }
}

@Composable
private fun FrameStrip(state: BoardUiState, viewModel: BoardViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.board.frames.size) { index ->
                FilterChip(
                    selected = index == state.frameIndex,
                    onClick = { viewModel.selectFrame(index) },
                    label = { Text(stringResource(R.string.board_frame, index + 1)) },
                )
            }
        }
        IconButton(onClick = viewModel::addFrame) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.board_add_frame))
        }
        IconButton(
            onClick = viewModel::removeFrame,
            enabled = state.board.frames.size > 1,
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.board_remove_frame),
            )
        }
    }
}

@Composable
private fun BoardSurface(
    state: BoardUiState,
    viewModel: BoardViewModel,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val tokenPx = with(density) { TokenSize.toPx() }

    // Busan is three maps in one picture, and most map images are far wider than a phone.
    // Without this the board is a letterboxed strip nobody can work on, so the whole
    // thing - background and tokens together - pinches and pans as one.
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 8f)
                    // Panning is pointless at rest and disorientating past the edges, so
                    // the offset is bounded by however much the zoom actually revealed.
                    val limitX = size.width * (scale - 1f) / 2f
                    val limitY = size.height * (scale - 1f) / 2f
                    pan = Offset(
                        (pan.x + panChange.x).coerceIn(-limitX, limitX),
                        (pan.y + panChange.y).coerceIn(-limitY, limitY),
                    )
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = pan.x,
                    translationY = pan.y,
                ),
        ) {
            if (state.board.background != null) {
                AsyncImage(
                    model = state.board.background,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // A grid rather than an empty rectangle, so positions can be talked about
                // even when nobody has brought a picture of the map.
                val line = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = this.size.minDimension / 10f
                    var x = step
                    while (x < this.size.width) {
                        drawLine(line, Offset(x, 0f), Offset(x, this.size.height))
                        x += step
                    }
                    var y = step
                    while (y < this.size.height) {
                        drawLine(line, Offset(0f, y), Offset(this.size.width, y))
                        y += step
                    }
                }
            }

            state.frame.tokens.forEach { token ->
                TokenView(
                    token = token,
                    boardSize = size,
                    // Tokens keep their size on screen however far the map is zoomed: a
                    // hero's face shrinking to nothing would defeat the point of zooming in.
                    tokenPx = tokenPx / scale,
                    scale = scale,
                    onMove = { x, y -> viewModel.move(token.id, x, y) },
                    onRemove = { viewModel.remove(token.id) },
                )
            }
        }

        if (scale > 1f) {
            TextButton(
                onClick = {
                    scale = 1f
                    pan = Offset.Zero
                },
                modifier = Modifier.align(Alignment.TopEnd),
            ) { Text(stringResource(R.string.board_reset_zoom)) }
        }
    }
}

private val TokenSize = 46.dp

@Composable
private fun TokenView(
    token: Token,
    boardSize: IntSize,
    tokenPx: Float,
    scale: Float,
    onMove: (Float, Float) -> Unit,
    onRemove: () -> Unit,
) {
    val density = LocalDensity.current
    // The gesture block is set up once and would otherwise close over the token as it was
    // then, so every drag event added its delta to the *starting* position: the token
    // shook and snapped back to where it began. This keeps the lambda reading the live one.
    val current by rememberUpdatedState(token)
    // Fractions are of the board, and the token is drawn from its top left, so half of it
    // comes back off to keep the hero's face on the spot the reader put it.
    val left = (boardSize.width * token.x - tokenPx / 2).coerceAtLeast(0f)
    val top = (boardSize.height * token.y - tokenPx / 2).coerceAtLeast(0f)

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { left.toDp() },
                y = with(density) { top.toDp() },
            )
            .size(with(density) { tokenPx.toDp() })
            .clip(CircleShape)
            .border(3.dp, ringFor(token.side), CircleShape)
            // Long press removes; the drag is immediate. Two gesture detectors on one
            // element fight over the first touch - the tap detector consumed it and the
            // drag never began - so movement is reported as deltas from a single detector
            // and removal rides on the clickable underneath it.
            .combinedClickable(
                onClick = {},
                onLongClick = onRemove,
            )
            .pointerInput(token.id, boardSize) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (boardSize.width == 0 || boardSize.height == 0) return@detectDragGestures
                    onMove(
                        current.x + dragAmount.x / boardSize.width,
                        current.y + dragAmount.y / boardSize.height,
                    )
                }
            },
    ) {
        AsyncImage(
            model = WikiRepository.imageUri(token.portrait),
            contentDescription = token.heroName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
    }
}

@Composable
private fun HeroStrip(state: BoardUiState, viewModel: BoardViewModel) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = stringResource(R.string.board_tap_to_add),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.roster, key = { it.key }) { hero ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { viewModel.add(hero) }
                        .padding(2.dp),
                ) {
                    AsyncImage(
                        model = WikiRepository.imageUri(hero.portrait),
                        contentDescription = hero.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(2.dp, ringFor(state.adding), CircleShape),
                    )
                    Text(
                        text = hero.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.size(width = 48.dp, height = 14.dp),
                    )
                }
            }
        }
    }
}
