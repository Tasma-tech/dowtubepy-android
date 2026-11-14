package org.johnbrenof.dowtube.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.johnbrenof.dowtube.R
import org.johnbrenof.dowtube.download.DownloadItem
import org.johnbrenof.dowtube.download.DownloadViewModel
import org.johnbrenof.dowtube.settings.AppSettings
import org.johnbrenof.dowtube.ui.components.AppDivider
import org.johnbrenof.dowtube.ui.components.UnderlineOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DownloadViewModel = viewModel(),
    onDownloadStarted: () -> Unit = {},
) {
    val url by viewModel.urlInput.collectAsState()
    val maxQuality by AppSettings.maxQualityVideo.collectAsState()
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    var showOptions by rememberSaveable { mutableStateOf(false) }

    val urlValid = isValidUrl(url)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(42.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        TextField(
            value = url,
            onValueChange = viewModel::onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.url_hint)) },
            singleLine = true,
            isError = url.isNotBlank() && !urlValid,
            supportingText = {
                if (url.isNotBlank() && !urlValid) {
                    Text(stringResource(R.string.url_invalid))
                }
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (url.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onUrlChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.clear_link),
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val text = clipboard.getText()?.text?.trim().orEmpty()
                            if (text.isNotEmpty()) viewModel.onUrlChange(text)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = stringResource(R.string.paste),
                        )
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                errorTextColor = MaterialTheme.colorScheme.onBackground,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                errorIndicatorColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (urlValid) {
                        focusManager.clearFocus()
                        showOptions = true
                    }
                },
            ),
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                focusManager.clearFocus()
                showOptions = true
            },
            enabled = urlValid,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.download),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }

    if (showOptions) {
        ModalBottomSheet(
            onDismissRequest = { showOptions = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Spacer(
                        modifier = Modifier
                            .width(36.dp)
                            .height(3.dp)
                            .background(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(50),
                            ),
                    )
                }
            },
        ) {
            DownloadOptionsContent(
                url = url,
                maxQualityDefault = maxQuality,
                onDismiss = { showOptions = false },
                onStart = { media, quality, playlist ->
                    showOptions = false
                    viewModel.startDownload(url, media, quality, playlist)
                    onDownloadStarted()
                },
            )
        }
    }
}

@Composable
private fun DownloadOptionsContent(
    url: String,
    maxQualityDefault: Boolean,
    onDismiss: () -> Unit,
    onStart: (media: String, quality: String, playlist: Boolean) -> Unit,
) {
    val isPlaylist = remember(url) { looksLikePlaylist(url) }
    var media by remember { mutableStateOf(DownloadItem.MEDIA_VIDEO) }
    var quality by remember {
        mutableStateOf(
            if (maxQualityDefault) DownloadItem.QUALITY_MAX else DownloadItem.QUALITY_MEDIUM,
        )
    }
    var downloadAll by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.options_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.clear_link),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (isPlaylist) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { downloadAll = !downloadAll },
                ) {
                    Text(
                        text = stringResource(R.string.playlist_all),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            if (downloadAll) R.string.playlist_hint else R.string.playlist_single,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(
                    checked = downloadAll,
                    onCheckedChange = { downloadAll = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
            AppDivider(Modifier.padding(top = 16.dp))
        }

        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.type_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            UnderlineOption(
                label = stringResource(R.string.type_video),
                selected = media == DownloadItem.MEDIA_VIDEO,
                onClick = {
                    media = DownloadItem.MEDIA_VIDEO
                    if (maxQualityDefault) quality = DownloadItem.QUALITY_MAX
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            UnderlineOption(
                label = stringResource(R.string.type_audio),
                selected = media == DownloadItem.MEDIA_AUDIO,
                onClick = { media = DownloadItem.MEDIA_AUDIO },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.quality_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            UnderlineOption(
                label = stringResource(R.string.quality_max),
                selected = quality == DownloadItem.QUALITY_MAX,
                onClick = { quality = DownloadItem.QUALITY_MAX },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            UnderlineOption(
                label = stringResource(R.string.quality_medium),
                selected = quality == DownloadItem.QUALITY_MEDIUM,
                onClick = { quality = DownloadItem.QUALITY_MEDIUM },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            UnderlineOption(
                label = stringResource(R.string.quality_low),
                selected = quality == DownloadItem.QUALITY_LOW,
                onClick = { quality = DownloadItem.QUALITY_LOW },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(
                when {
                    media == DownloadItem.MEDIA_AUDIO && quality == DownloadItem.QUALITY_MAX ->
                        R.string.hint_audio_max
                    media == DownloadItem.MEDIA_AUDIO && quality == DownloadItem.QUALITY_LOW ->
                        R.string.hint_audio_low
                    media == DownloadItem.MEDIA_AUDIO ->
                        R.string.hint_audio_medium
                    quality == DownloadItem.QUALITY_MAX ->
                        R.string.hint_video_max
                    quality == DownloadItem.QUALITY_LOW ->
                        R.string.hint_video_low
                    else ->
                        R.string.hint_video_medium
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(26.dp))
        Button(
            onClick = { onStart(media, quality, downloadAll && isPlaylist) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.download),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(
            modifier = Modifier
                .navigationBarsPadding()
                .height(12.dp),
        )
    }
}

private fun looksLikePlaylist(url: String): Boolean {
    val value = url.lowercase()
    return value.contains("list=") ||
        value.contains("/playlist") ||
        value.contains("/set?") ||
        value.contains("/album") ||
        value.contains("/mixes")
}

private fun isValidUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
    val body = trimmed
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("//")
    return body.contains('.') && body.length > 3
}
