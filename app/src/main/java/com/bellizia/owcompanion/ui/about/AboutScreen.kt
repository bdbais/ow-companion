package com.bellizia.owcompanion.ui.about

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.data.DatasetRepository
import com.bellizia.owcompanion.data.DatasetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                    datasetVersion = DatasetUpdater.installedVersion(getApplication(), 1),
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
        Text(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.headlineMedium,
        )
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

@Composable
private fun Section(titleRes: Int, content: @Composable () -> Unit) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 4.dp),
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
