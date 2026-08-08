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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.bellizia.owcompanion.ui.chart.ChartScreen
import com.bellizia.owcompanion.ui.custom.CustomScreen
import com.bellizia.owcompanion.ui.stadium.StadiumScreen
import com.bellizia.owcompanion.ui.leaderboard.LeaderboardScreen
import com.bellizia.owcompanion.ui.meta.MetaTab
import com.bellizia.owcompanion.ui.wiki.WikiScreen
import com.bellizia.owcompanion.ui.theme.OwCompanionTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OwCompanionTheme {
                AppRoot()
            }
        }
    }
}

private enum class Section(val labelRes: Int) {
    Chart(R.string.tab_chart),
    Leaderboard(R.string.tab_rankings),
    Wiki(R.string.tab_wiki),
    Custom(R.string.tab_custom),
    Stadium(R.string.tab_stadium),
    Board(R.string.tab_board),
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
                                    Section.Custom -> Icons.Filled.Science
                                    Section.Stadium -> Icons.Filled.Stadium
                                    Section.Board -> Icons.Filled.Dashboard
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
                Section.Custom -> CustomScreen()
                Section.Stadium -> StadiumScreen()
                Section.Board -> BoardScreen()
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

    LaunchedEffect(Unit) {
        release = ReleaseChecker(context).newerRelease()
    }

    val newer = release ?: return

    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.update_available, newer.version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(newer.url))
                runCatching { context.startActivity(intent) }
                    .onFailure { if (it !is ActivityNotFoundException) throw it }
            }) { Text(stringResource(R.string.update_open)) }
            IconButton(onClick = {
                ReleaseChecker(context).dismiss(newer.version)
                release = null
            }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.update_dismiss),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
