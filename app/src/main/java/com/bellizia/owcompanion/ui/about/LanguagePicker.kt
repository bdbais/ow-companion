package com.bellizia.owcompanion.ui.about

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.bellizia.owcompanion.R

/**
 * A language and the flag people recognise it by.
 *
 * A flag is not a language - Spanish is not only Spain's, and English is not only
 * Britain's - but a flag is what a reader scans for, so each entry carries both a flag and
 * the language's own name. `tag` is the Android language tag the resources are filed under.
 */
private data class Language(val tag: String, val flag: String, val name: String)

private val Languages = listOf(
    Language("en", "🇬🇧", "English"),
    Language("it", "🇮🇹", "Italiano"),
    Language("es", "🇪🇸", "Español"),
    Language("pt-BR", "🇧🇷", "Português"),
    Language("fr", "🇫🇷", "Français"),
    Language("de", "🇩🇪", "Deutsch"),
    Language("pl", "🇵🇱", "Polski"),
    Language("sv", "🇸🇪", "Svenska"),
    Language("tr", "🇹🇷", "Türkçe"),
    Language("ru", "🇷🇺", "Русский"),
    Language("uk", "🇺🇦", "Українська"),
    Language("ar", "🇸🇦", "العربية"),
    Language("ja", "🇯🇵", "日本語"),
    Language("ko", "🇰🇷", "한국어"),
    Language("zh-CN", "🇨🇳", "简体中文"),
    Language("zh-TW", "🇹🇼", "繁體中文"),
)

/**
 * Picks the app's language independently of the phone's.
 *
 * Android 13 and later has a per-app language setting in system settings, but almost nobody
 * knows it is there, and below 13 there is nothing at all. So the app carries its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagePicker(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val currentTag = remember(expanded) {
        AppCompatDelegate.getApplicationLocales().toLanguageTags().takeIf { it.isNotBlank() }
    }
    val current = Languages.firstOrNull { language ->
        currentTag?.startsWith(language.tag) == true
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = current?.flag ?: "🌐",
                style = MaterialTheme.typography.headlineSmall,
            )
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)) {
                Text(
                    text = stringResource(R.string.language_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    // No explicit choice means the app is following the phone.
                    text = current?.name ?: stringResource(R.string.language_system),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }

        if (!expanded) return@Column

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            LanguageChip(
                flag = "🌐",
                name = stringResource(R.string.language_system),
                selected = current == null,
            ) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                expanded = false
            }
            Languages.forEach { language ->
                LanguageChip(
                    flag = language.flag,
                    name = language.name,
                    selected = language == current,
                ) {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(language.tag),
                    )
                    expanded = false
                }
            }
        }
    }
}

@Composable
private fun LanguageChip(
    flag: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = flag, style = MaterialTheme.typography.titleMedium)
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
