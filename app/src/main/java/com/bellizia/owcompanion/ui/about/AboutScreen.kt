package com.bellizia.owcompanion.ui.about

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bellizia.owcompanion.BuildConfig
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.data.DatasetRepository
import com.bellizia.owcompanion.data.DatasetUpdater
import com.bellizia.owcompanion.data.Feedback
import com.bellizia.owcompanion.ui.common.SegmentPanel
import java.text.NumberFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SECTION_STEP = 7

data class AboutUiState(
    val datasetVersion: Int = 0,
    val heroes: Int = 0,
    val weapons: Int = 0,
    val checking: Boolean = false,
    val updateResult: DatasetUpdater.Result? = null,
    val updatesConfigured: Boolean = false,
)

class AboutViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DatasetRepository(application)
    private val updater = DatasetUpdater(application)

    private val _state = MutableStateFlow(AboutUiState())
    val state: StateFlow<AboutUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val set = repository.weapons()
            _state.update {
                it.copy(
                    datasetVersion = DatasetUpdater.installedVersion(
                        getApplication(),
                        BuildConfig.DATASET_VERSION,
                    ),
                    heroes = set.heroes.size,
                    weapons = set.weapons.size,
                    updatesConfigured = updater.isConfigured,
                )
            }
        }
    }

    fun checkForUpdates() {
        _state.update { it.copy(checking = true, updateResult = null) }
        viewModelScope.launch {
            val result = updater.check(_state.value.datasetVersion)
            _state.update { it.copy(checking = false, updateResult = result) }
        }
    }
}

/**
 * Credits and dataset status.
 *
 * This exists because the app is built almost entirely out of other people's work: the
 * wiki's numbers, Blizzard's artwork, yfp's simulation model, two open typefaces. Saying so
 * is both the licence terms and the decent thing.
 */
@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // The version sits beside the name rather than buried further down: it is the first
        // thing anyone is asked for when they report something, and the first thing to check
        // against the update banner.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.about_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 3.dp),
            )
        }
        Text(
            text = stringResource(R.string.about_intro),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )

        LanguagePicker()

        Section(R.string.about_data_title) {
            Paragraph(R.string.about_data_wiki)
            Paragraph(R.string.about_data_overfast)
            Paragraph(R.string.about_data_owdmgchart)
        }

        var count by rememberSaveable { mutableIntStateOf(0) }
        var panel by rememberSaveable { mutableStateOf(false) }

        Section(
            titleRes = R.string.about_thanks_title,
            onTitleClick = if (LocalConfiguration.current.locales[0].language == "en") {
                { count += 1; if (count % SECTION_STEP == 0) panel = true }
            } else {
                null
            },
        ) {
            Paragraph(R.string.about_thanks)
        }

        if (panel) {
            SegmentPanel(onDismiss = { panel = false })
        }

        // Only when there is something measured to show: a build made from a checkout with
        // no transcripts to count would otherwise claim the work cost nothing.
        if (BuildConfig.DEV_TOKENS > 0) {
            Section(R.string.about_cost_title) {
                Text(
                    text = stringResource(
                        R.string.about_cost,
                        formatTokens(BuildConfig.DEV_TOKENS),
                        BuildConfig.VERSION_NAME,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.about_cost_detail,
                        formatTokens(BuildConfig.DEV_TOKENS_OUTPUT),
                        formatTokens(BuildConfig.DEV_TOKENS_CACHE_READ),
                        BuildConfig.DEV_MEASURED,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        ReportsSection()

        FeedbackSection()

        Section(R.string.about_art_title) {
            Paragraph(R.string.about_art)
        }

        Section(R.string.about_fonts_title) {
            Paragraph(R.string.about_fonts)
        }

        Section(R.string.about_dataset_title) {
            Text(
                text = stringResource(
                    R.string.about_dataset_version,
                    state.datasetVersion,
                    state.heroes,
                    state.weapons,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.updatesConfigured) {
                Button(
                    onClick = viewModel::checkForUpdates,
                    enabled = !state.checking,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    if (state.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(stringResource(R.string.about_checking))
                    } else {
                        Text(stringResource(R.string.about_check_updates))
                    }
                }
            }
            val message = when (val result = state.updateResult) {
                null -> if (state.updatesConfigured) null
                else stringResource(R.string.about_updates_unconfigured)
                DatasetUpdater.Result.NotConfigured ->
                    stringResource(R.string.about_updates_unconfigured)
                DatasetUpdater.Result.UpToDate -> stringResource(R.string.about_up_to_date)
                is DatasetUpdater.Result.Updated ->
                    stringResource(R.string.about_updated, result.version)
                is DatasetUpdater.Result.Failed ->
                    stringResource(R.string.about_update_failed)
            }
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * Grouped digits rather than a scale word: "1.045.188.268" needs no translating, and every
 * locale groups it the way its readers expect.
 */
private fun formatTokens(value: Long): String =
    NumberFormat.getIntegerInstance().format(value)

@Composable
private fun Section(
    titleRes: Int,
    onTitleClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .then(
                if (onTitleClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onTitleClick,
                    )
                },
            )
            .padding(bottom = 4.dp),
    )
    content()
}

@Composable
private fun Paragraph(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/**
 * Where an opinion can go.
 *
 * Two different things, kept apart on purpose. Stars are Google's business and go to the
 * listing; a wrong number is this project's business and goes to the issue tracker, where
 * it can be answered. Rolling them together would send bug reports somewhere nobody reads
 * them and lose the fix.
 *
 * Side-loaded there is no listing yet, so it says so rather than opening a page that does
 * not exist.
 */
@Composable
private fun FeedbackSection() {
    val context = LocalContext.current
    val activity = context as? Activity
    val feedback = remember { Feedback(context) }
    val scope = rememberCoroutineScope()

    Section(R.string.about_feedback_title) {
        Paragraph(
            if (feedback.published) R.string.about_feedback else R.string.about_feedback_sideloaded,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (feedback.published) {
                Button(
                    onClick = {
                        scope.launch {
                            // Play refuses far more often than it accepts, and gives no
                            // reason; the listing is the answer either way.
                            val shown = activity?.let { feedback.requestReview(it) } ?: false
                            if (!shown) feedback.openListing()
                        }
                    },
                ) { Text(stringResource(R.string.about_rate)) }
            }
            TextButton(onClick = feedback::openIssues) {
                Text(stringResource(R.string.about_report))
            }
        }
    }
}

/**
 * What players reported, and what came of it.
 *
 * Shown to everyone rather than kept in the repository: the people who find a wrong number
 * are not the people who read commit messages, and someone who took the trouble to report
 * one should be able to see whether it was believed.
 *
 * Three states, and the third is the point. A report that turned out to be wrong is marked
 * as such rather than left open, because pretending to agree is a worse answer than
 * disagreeing with a reason.
 */
@Composable
private fun ReportsSection() {
    var open by rememberSaveable { mutableStateOf(false) }

    Section(R.string.about_reports_title, onTitleClick = { open = !open }) {
        Text(
            text = stringResource(R.string.about_reports, fixedCount, Reports.size),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.about_reports_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
        )

        if (!open) {
            Text(
                text = stringResource(R.string.about_reports_show),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { open = true }.padding(vertical = 4.dp),
            )
            return@Section
        }

        Reports.forEach { report ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.Top,
            ) {
                val (label, colour) = when (report.status) {
                    Report.Status.Fixed -> stringResource(
                        R.string.about_report_fixed,
                        report.version.orEmpty(),
                    ) to MaterialTheme.colorScheme.primary
                    Report.Status.AsDesigned ->
                        stringResource(R.string.about_report_as_designed) to
                            MaterialTheme.colorScheme.onSurfaceVariant
                    Report.Status.Open ->
                        stringResource(R.string.about_report_open) to
                            MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colour,
                    modifier = Modifier.width(96.dp),
                )
                Text(
                    text = stringResource(report.title),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
