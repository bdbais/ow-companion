package com.bellizia.owcompanion

import android.os.Bundle
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
import com.bellizia.owcompanion.ui.chart.ChartScreen
import com.bellizia.owcompanion.ui.custom.CustomScreen
import com.bellizia.owcompanion.ui.stadium.StadiumScreen
import com.bellizia.owcompanion.ui.leaderboard.LeaderboardScreen
import com.bellizia.owcompanion.ui.meta.MetaScreen
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
    Meta(R.string.tab_meta),
    About(R.string.tab_about),
}

@Composable
private fun AppRoot() {
    var section by remember { mutableStateOf(Section.Chart) }

    Scaffold(
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
                Section.Meta -> MetaScreen()
                Section.About -> AboutScreen()
            }
        }
    }
}
