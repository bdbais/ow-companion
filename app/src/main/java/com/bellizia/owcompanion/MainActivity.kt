package com.bellizia.owcompanion

import android.content.Context
import android.os.Bundle
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bellizia.owcompanion.data.DatasetSync
import com.bellizia.owcompanion.data.AppUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import com.bellizia.owcompanion.data.ReleaseChecker
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.bellizia.owcompanion.ui.about.AboutScreen
import com.bellizia.owcompanion.ui.board.BoardScreen
import com.bellizia.owcompanion.ui.comics.ComicsScreen
import com.bellizia.owcompanion.ui.chart.ChartScreen
import com.bellizia.owcompanion.ui.stadium.StadiumScreen
import com.bellizia.owcompanion.ui.leaderboard.LeaderboardScreen
import com.bellizia.owcompanion.ui.meta.MetaTab
import com.bellizia.owcompanion.ui.wiki.WikiScreen
import com.bellizia.owcompanion.ui.common.ProvideNames
import com.bellizia.owcompanion.ui.theme.OwCompanionTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OwCompanionTheme {
                // Around everything, because a hero's name is wanted on six screens and the
                // answer depends only on the locale.
                ProvideNames {
                    AppRoot()
                }
            }
        }
    }
}

private enum class Section(val labelRes: Int) {
    Chart(R.string.tab_chart),
    Leaderboard(R.string.tab_rankings),
    Wiki(R.string.tab_wiki),
    Stadium(R.string.tab_stadium),
    Board(R.string.tab_board),
    Comics(R.string.tab_comics),
    Meta(R.string.tab_meta),
    About(R.string.tab_about),
}

/**
 * Where the app opens: wherever it was last closed.
 *
 * Someone who spends a session on the board does not want to start on the chart every time,
 * and the tab you were on is the cheapest possible statement of what you came for.
 */
private const val PREFS = "app"
private const val KEY_SECTION = "last_section"

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var section by remember {
        mutableStateOf(
            // A tab that no longer exists - after an update that removed one - falls back
            // rather than crashing.
            runCatching { Section.valueOf(prefs.getString(KEY_SECTION, "").orEmpty()) }
                .getOrDefault(Section.Chart),
        )
    }

    LaunchedEffect(section) {
        prefs.edit().putString(KEY_SECTION, section.name).apply()
    }

    Scaffold(
        topBar = { UpdateBanner() },
        bottomBar = {
            NavigationBar {
                Section.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = section == entry,
                        onClick = { section = entry },
                        icon = {
                            Icon(
                                imageVector = when (entry) {
                                    Section.Chart -> Icons.Filled.BarChart
                                    Section.Leaderboard -> Icons.Filled.EmojiEvents
                                    Section.Wiki -> Icons.Filled.Groups
                                    Section.Stadium -> Icons.Filled.Stadium
                                    Section.Board -> Icons.Filled.Dashboard
                                    Section.Comics -> Icons.AutoMirrored.Filled.MenuBook
                                    Section.Meta -> Icons.Filled.Whatshot
                                    Section.About -> Icons.Filled.Info
                                },
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(entry.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (section) {
                Section.Chart -> ChartScreen()
                Section.Leaderboard -> LeaderboardScreen()
                Section.Wiki -> WikiScreen()
                Section.Stadium -> StadiumScreen()
                Section.Board -> BoardScreen()
                Section.Comics -> ComicsScreen()
                Section.Meta -> MetaTab()
                Section.About -> AboutScreen()
            }
        }
    }
}

/**
 * A one-line offer when a newer build exists, and nothing at all otherwise.
 *
 * There is no Play Store behind this app, so a reader who installed months ago has no way of
 * finding out that a wrong number has since been corrected. The check runs once per launch,
 * fails silently, and remembers a dismissal so that saying no costs one tap forever rather
 * than one tap per launch.
 */
@Composable
private fun UpdateBanner() {
    val context = LocalContext.current
    var release by remember { mutableStateOf<ReleaseChecker.Release?>(null) }
    var explaining by remember { mutableStateOf(false) }
    val update by AppUpdate.state.collectAsState()

    LaunchedEffect(Unit) {
        val found = ReleaseChecker(context).newerRelease()
        release = found
        when {
            // Nothing newer, which is also the state right after an update went in.
            found == null -> AppUpdate.tidy(context)
            // A changed key cannot go in over this copy, and a release without a checkable
            // APK cannot be fetched safely: both keep the release page.
            found.needsReinstall || found.apk == null -> Unit
            else -> AppUpdate.prepare(context, found, download = !AppUpdate.isMetered(context))
        }
    }

    // I dati degli eroi si aggiornano da soli, e in silenzio.
    //
    // Sono due aggiornamenti diversi e vanno trattati diversamente. Una versione
    // nuova dell'app la deve installare una persona, quindi si chiede. I numeri
    // degli eroi no: sono la stessa app con dati piu' recenti, e chiedere il
    // permesso di correggere un danno sbagliato sarebbe solo un fastidio.
    //
    // Prima stava solo dietro un pulsante nella schermata Info, dove non lo
    // premeva nessuno: chi non ci passava restava coi numeri del giorno in cui
    // aveva installato. Ora parte all'avvio, costa una richiesta di poche
    // centinaia di byte quando non c'e' niente di nuovo, e il file grosso arriva
    // solo quando il dataset e' davvero cambiato.
    LaunchedEffect(Unit) {
        DatasetSync.run(context)
    }

    val newer = release ?: return

    val open = {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(newer.url))
        runCatching { context.startActivity(intent) }
            .onFailure { if (it !is ActivityNotFoundException) throw it }
        Unit
    }

    // Installing closes the app, so it is asked rather than done: once, when the file is
    // ready, and after that from the banner whenever the reader chooses.
    var announced by rememberSaveable(newer.version) { mutableStateOf(false) }
    val ready = update as? AppUpdate.State.Ready
    if (ready != null && !announced) {
        RestartDialog(
            version = ready.version,
            onInstall = { announced = true; AppUpdate.install(context, ready.version) },
            onLater = { announced = true },
        )
    }

    // An update that changes the signing key cannot be installed over this one, and Android
    // says so with "App not installed" and nothing else. Explaining it before the reader
    // meets that message is the whole point of the extra tap.
    if (explaining) {
        ReinstallDialog(
            version = newer.version,
            onOpen = { explaining = false; open() },
            onUninstall = {
                // The system uninstaller, which asks for its own confirmation. Nothing is
                // removed by this alone, and an Android without one is not a thing that
                // exists - but the app should not die here if it somehow is.
                val intent = Intent(
                    Intent.ACTION_DELETE,
                    Uri.parse("package:${context.packageName}"),
                )
                runCatching { context.startActivity(intent) }
                    .onFailure { if (it !is ActivityNotFoundException) throw it }
            },
            onDismiss = { explaining = false },
        )
    }

    val apk = newer.apk
    val inApp = !newer.needsReinstall && apk != null
    val message: String
    val action: Pair<String, () -> Unit>?
    when {
        newer.needsReinstall -> {
            message = stringResource(R.string.update_available_reinstall, newer.version)
            action = stringResource(R.string.update_reinstall_how) to { explaining = true }
        }
        apk == null -> {
            message = stringResource(R.string.update_available, newer.version)
            action = stringResource(R.string.update_open) to open
        }
        else -> when (val state = update) {
            is AppUpdate.State.Downloading -> {
                message = stringResource(R.string.update_downloading, state.version, state.percent)
                action = null
            }
            is AppUpdate.State.Ready -> {
                message = stringResource(R.string.update_ready_banner, state.version)
                action = stringResource(R.string.update_install) to {
                    AppUpdate.install(context, state.version)
                }
            }
            is AppUpdate.State.Installing -> {
                message = stringResource(R.string.update_installing, state.version)
                action = null
            }
            is AppUpdate.State.Failed -> {
                message = stringResource(R.string.update_failed, state.version)
                action = stringResource(R.string.update_open) to open
            }
            AppUpdate.State.Idle -> {
                // Only reached on a metered connection: the download waits for a tap.
                val megabytes = ((apk.size + 999_999) / 1_000_000).toInt()
                message = stringResource(R.string.update_available, newer.version)
                action = stringResource(R.string.update_download, megabytes) to {
                    AppUpdate.prepare(context, newer, download = true)
                }
            }
        }
    }

    val container = if (newer.needsReinstall) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val content = if (newer.needsReinstall) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(color = container) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = content,
                    modifier = Modifier.weight(1f),
                )
                if (action != null) {
                    TextButton(onClick = action.second) { Text(action.first) }
                }
                IconButton(onClick = {
                    ReleaseChecker(context).dismiss(newer.version)
                    AppUpdate.forget(context)
                    release = null
                }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.update_dismiss),
                        tint = content,
                    )
                }
            }
            val downloading = update as? AppUpdate.State.Downloading
            if (inApp && downloading != null) {
                LinearProgressIndicator(
                    progress = { downloading.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The one question an update has to ask: installing closes the app.
 *
 * Said before it happens, because an app that vanishes mid-use reads as a crash. It also says
 * the boards survive, which is the thing anyone who has drawn one wants to know.
 */
@Composable
private fun RestartDialog(version: String, onInstall: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.update_ready_title, version)) },
        text = { Text(stringResource(R.string.update_ready_body)) },
        confirmButton = {
            TextButton(onClick = onInstall) { Text(stringResource(R.string.update_install)) }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text(stringResource(R.string.update_reinstall_later)) }
        },
    )
}

/**
 * What has to happen, in what order, and what it costs.
 *
 * The order is the part worth insisting on: uninstalling before the new file is downloaded
 * leaves the reader with no app and no obvious way back to the page it came from. So the
 * download is the button that looks like the action, and the shortcut to the system
 * uninstaller sits below the explanation rather than beside it.
 *
 * It also says plainly that saved boards do not survive, because finding that out afterwards
 * is worse than being warned, and the board's export buttons are right there.
 */
@Composable
private fun ReinstallDialog(
    version: String,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_reinstall_title, version)) },
        text = {
            Column {
                Text(stringResource(R.string.update_reinstall_body))
                TextButton(
                    onClick = onUninstall,
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text(stringResource(R.string.update_reinstall_uninstall)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.update_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_reinstall_later)) }
        },
    )
}
