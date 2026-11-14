package org.johnbrenof.dowtube.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.johnbrenof.dowtube.BuildConfig
import org.johnbrenof.dowtube.R
import org.johnbrenof.dowtube.download.DownloadViewModel
import org.johnbrenof.dowtube.settings.AppSettings
import org.johnbrenof.dowtube.ui.components.AppDivider
import org.johnbrenof.dowtube.ui.components.SectionHeader
import org.johnbrenof.dowtube.ui.components.UnderlineOption
import org.johnbrenof.dowtube.ui.components.openUrl

private val LANGUAGE_OPTIONS = listOf(
    Triple(R.string.lang_system, "system", "Sistema"),
    Triple(R.string.lang_pt, "pt", "Português"),
    Triple(R.string.lang_en, "en", "English"),
)

private val THEME_OPTIONS = listOf(
    Triple(R.string.theme_system, "system", "Sistema"),
    Triple(R.string.theme_light, "light", "Claro"),
    Triple(R.string.theme_dark, "dark", "Escuro"),
)

private val LIBRARIES = listOf(
    Triple(R.string.lib_ytdlp, "yt-dlp", "https://github.com/yt-dlp/yt-dlp"),
    Triple(R.string.lib_ffmpeg, "FFmpeg", "https://ffmpeg.org/"),
    Triple(R.string.lib_chaquopy, "Chaquopy", "https://chaquo.com/chaquopy/"),
    Triple(R.string.lib_compose, "Jetpack Compose", "https://developer.android.com/jetpack/compose"),
    Triple(R.string.lib_material3, "Material 3", "https://m3.material.io/"),
    Triple(R.string.lib_mutagen, "Mutagen", "https://mutagen.readthedocs.io/"),
)

@Composable
fun SettingsScreen(
    viewModel: DownloadViewModel = viewModel(),
) {
    val context = LocalContext.current
    val theme by AppSettings.theme.collectAsState()
    val language by AppSettings.language.collectAsState()
    val maxQuality by AppSettings.maxQualityVideo.collectAsState()
    val autoClear by AppSettings.autoClearHistory.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
            )
        }

        item {
            SectionHeader(stringResource(R.string.section_downloads))
            SwitchRow(
                title = stringResource(R.string.setting_max_quality),
                description = stringResource(R.string.setting_max_quality_desc),
                checked = maxQuality,
                onChange = AppSettings::setMaxQualityVideo,
            )
            AppDivider(Modifier.padding(horizontal = 20.dp))
        }

        item {
            SectionHeader(stringResource(R.string.section_history))
            SwitchRow(
                title = stringResource(R.string.setting_auto_clear),
                description = stringResource(R.string.setting_auto_clear_desc),
                checked = autoClear,
                onChange = { value ->
                    AppSettings.setAutoClearHistory(value)
                    if (value) viewModel.purgeOldHistory()
                },
            )
            AppDivider(Modifier.padding(horizontal = 20.dp))
        }

        item {
            SectionHeader(stringResource(R.string.section_appearance))
            OptionRow(
                title = stringResource(R.string.setting_language),
                options = LANGUAGE_OPTIONS,
                selected = language,
                onSelect = { value ->
                    if (value != language) {
                        AppSettings.setLanguage(value)
                        (context as? Activity)?.recreate()
                    }
                },
            )
            AppDivider(Modifier.padding(horizontal = 20.dp))
            OptionRow(
                title = stringResource(R.string.setting_theme),
                options = THEME_OPTIONS,
                selected = theme,
                onSelect = AppSettings::setTheme,
            )
            AppDivider(Modifier.padding(horizontal = 20.dp))
        }

        item {
            SectionHeader(stringResource(R.string.section_about))
            AboutRow(
                label = stringResource(R.string.about_developer),
                value = "John-BrenoF",
                url = "https://github.com/John-BrenoF",
                onClick = { openUrl(context, "https://github.com/John-BrenoF") },
            )
            AboutRow(
                label = stringResource(R.string.about_source),
                value = "github.com/Tasma-tech/dowtubepy-android",
                url = "https://github.com/Tasma-tech/dowtubepy-android",
                onClick = { openUrl(context, "https://github.com/Tasma-tech/dowtubepy-android") },
            )
            AboutRow(
                label = stringResource(R.string.about_license),
                value = stringResource(R.string.about_license_value),
                url = "https://www.gnu.org/licenses/gpl-3.0.html",
                onClick = { openUrl(context, "https://www.gnu.org/licenses/gpl-3.0.html") },
            )
            AboutRow(
                label = stringResource(R.string.about_version),
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
            AppDivider(Modifier.padding(horizontal = 20.dp))
            LIBRARIES.forEach { library ->
                AboutRow(
                    label = stringResource(library.first),
                    value = library.second,
                    url = library.third,
                    onClick = { openUrl(context, library.third) },
                )
            }
            AppDivider(Modifier.padding(horizontal = 20.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

@Composable
private fun OptionRow(
    title: String,
    options: List<Triple<Int, String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEach { option ->
                UnderlineOption(
                    label = stringResource(option.first),
                    selected = selected == option.second,
                    onClick = { onSelect(option.second) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun AboutRow(
    label: String,
    value: String,
    url: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (url != null) {
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.open),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
