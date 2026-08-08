package com.bellizia.owcompanion.ui.meta

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bellizia.owcompanion.R
import com.bellizia.owcompanion.ui.player.PlayerScreen

/**
 * The two halves of "what is actually happening in the game": everyone's, and yours.
 *
 * They share a tab because they are the same kind of thing - measured from real matches,
 * fetched rather than shipped, true this week and not next - and because an eighth entry in
 * the navigation bar would leave no room for a legible label on a phone.
 */
@Composable
fun MetaTab(modifier: Modifier = Modifier) {
    var mine by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            SegmentedButton(
                selected = !mine,
                onClick = { mine = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.meta_tab_everyone)) }
            SegmentedButton(
                selected = mine,
                onClick = { mine = true },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.meta_tab_you)) }
        }

        if (mine) {
            PlayerScreen(modifier = Modifier.fillMaxSize())
        } else {
            MetaScreen(modifier = Modifier.fillMaxSize())
        }
    }
}
