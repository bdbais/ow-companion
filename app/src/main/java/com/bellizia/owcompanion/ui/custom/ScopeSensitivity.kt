package com.bellizia.owcompanion.ui.custom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.sim.Sensitivity
import kotlin.math.roundToInt

/**
 * Works out the zoom sensitivity that gives one scope the feel another already has.
 *
 * It lives here rather than on a hero's page because the question involves two heroes: the
 * one whose setting is already right, and the one that is not.
 */
@Composable
fun ScopeSensitivity(modifier: Modifier = Modifier) {
    var fov by rememberSaveable { mutableFloatStateOf(Sensitivity.DEFAULT_FOV.toFloat()) }
    var match by rememberSaveable { mutableStateOf(Sensitivity.Match.Centre) }
    var fromSens by rememberSaveable { mutableStateOf("37.89") }
    var targetFov by rememberSaveable { mutableStateOf("") }

    val known = Sensitivity.KNOWN.first()

    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.sens_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.sens_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        Text(
            text = stringResource(R.string.sens_fov, fov.roundToInt()),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = fov,
            onValueChange = { fov = it },
            valueRange = 80f..103f,
            steps = 22,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = match == Sensitivity.Match.Centre,
                onClick = { match = Sensitivity.Match.Centre },
                label = { Text(stringResource(R.string.sens_match_centre)) },
            )
            FilterChip(
                selected = match == Sensitivity.Match.Ratio,
                onClick = { match = Sensitivity.Match.Ratio },
                label = { Text(stringResource(R.string.sens_match_ratio)) },
            )
        }
        Text(
            text = stringResource(
                if (match == Sensitivity.Match.Centre) {
                    R.string.sens_match_centre_note
                } else {
                    R.string.sens_match_ratio_note
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )

        // What to set on the scopes that have been measured.
        Sensitivity.KNOWN.forEach { scope ->
            val value = Sensitivity.relative(fov.toDouble(), scope.fov, match)
            Answer(
                label = "${scope.hero} · ${scope.weapon}",
                value = value,
                detail = stringResource(R.string.sens_scope_fov, format(scope.fov)),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = stringResource(R.string.sens_convert_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.sens_convert_intro, known.hero),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = fromSens,
                onValueChange = { fromSens = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(stringResource(R.string.sens_from, known.hero)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = targetFov,
                onValueChange = { targetFov = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(stringResource(R.string.sens_target_fov)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        val from = fromSens.toDoubleOrNull()
        val target = targetFov.toDoubleOrNull()
        if (from != null && target != null && target > 0) {
            Answer(
                label = stringResource(R.string.sens_result),
                value = Sensitivity.convert(from, known.fov, target, match),
                detail = stringResource(R.string.sens_result_note),
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.sens_measure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Answer(
    label: String,
    value: Double,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${format(value)}%",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Two decimals, because the setting takes them and the difference is visible. */
private fun format(value: Double): String = ((value * 100).roundToInt() / 100.0).toString()
