package org.johnbrenof.dowtube.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.trace
import androidx.lifecycle.viewmodel.compose.viewModel
import org.johnbrenof.dowtube.R
import org.johnbrenof.dowtube.download.DownloadItem
import org.johnbrenof.dowtube.download.DownloadViewModel
import org.johnbrenof.dowtube.download.PreviewState
import org.johnbrenof.dowtube.settings.AppSettings
import org.johnbrenof.dowtube.ui.components.AppDivider
import org.johnbrenof.dowtube.ui.components.Thumbnail
import org.johnbrenof.dowtube.ui.components.UnderlineOption
import org.johnbrenof.dowtube.ui.components.formatDuration
import org.johnbrenof.dowtube.util.UrlRules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DownloadViewModel = viewModel(),
    onDownloadStarted: () -> Unit = {},
) {
    val url by viewModel.urlInput.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val maxQuality by AppSettings.maxQualityVideo.collectAsState()
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    var showOptions by rememberSaveable { mutableStateOf(false) }

    val urlValid = UrlRules.isValidUrl(url)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        BrowserBar(
            url = url,
            valid = urlValid,
            onUrlChange = viewModel::onUrlChange,
            onPaste = {
                val text = clipboard.getText()?.text.orEmpty()
                if (text.isNotBlank()) viewModel.pasteFromClipboard(text)
            },
            onClear = { viewModel.onUrlChange("") },
            onDone = {
                if (urlValid) {
                    focusManager.clearFocus()
                    showOptions = true
                }
            },
        )

        if (url.isBlank()) {
            BrowserHomeHint()
        } else {
            PreviewSection(preview = preview, valid = urlValid)

            androidx.compose.animation.AnimatedVisibility(
                visible = url.isNotBlank(),
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.tween(280),
                ) + androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(280),
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(4.dp))
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
            }
        }

        Spacer(Modifier.height(28.dp))
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
                preview = preview,
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
private fun BrowserBar(
    url: String,
    valid: Boolean,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = null,
                tint = if (valid) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )

            TextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.browser_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                isError = url.isNotBlank() && !valid,
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = null,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (url.isNotEmpty()) {
                            IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.clear_link),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                        IconButton(onClick = onPaste, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = stringResource(R.string.paste),
                                modifier = Modifier.size(17.dp),
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
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onDone() }),
            )
        }

        if (url.isNotBlank() && !valid) {
            Text(
                text = stringResource(R.string.url_invalid),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 42.dp, end = 16.dp),
            )
        }

        AppDivider()
    }
}

@Composable
private fun BrowserHomeHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            modifier = Modifier.size(84.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.paste_to_download),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun PreviewSection(preview: PreviewState, valid: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            preview.loading -> Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.preview_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            preview.info != null -> PreviewCard(preview)

            !valid -> Text(
                text = stringResource(R.string.preview_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PreviewCard(preview: PreviewState) {
    val info = preview.info ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 128.dp, height = 74.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Thumbnail(
                url = info.thumbnail,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (!info.isPlaylist && info.thumbnail.isBlank()) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(26.dp),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (info.uploader.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = info.uploader,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info.isPlaylist) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.preview_playlist, info.entries),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                } else if (info.duration > 0) {
                    Text(
                        text = stringResource(R.string.preview_duration, formatDuration(info.duration)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    AppDivider(Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun DownloadOptionsContent(
    url: String,
    preview: PreviewState,
    maxQualityDefault: Boolean,
    onDismiss: () -> Unit,
    onStart: (media: String, quality: String, playlist: Boolean) -> Unit,
) {
    val isPlaylist = remember(url) { UrlRules.looksLikePlaylist(url) || preview.info?.isPlaylist == true }
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

        val info = preview.info
        if (info != null && info.title.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info.thumbnail.isNotBlank()) {
                    Thumbnail(
                        url = info.thumbnail,
                        modifier = Modifier
                            .size(width = 76.dp, height = 44.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (info.uploader.isNotBlank()) {
                        Text(
                            text = info.uploader,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

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
