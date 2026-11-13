package org.johnbrenof.dowtube.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.johnbrenof.dowtube.R
import org.johnbrenof.dowtube.download.DownloadStatus
import org.johnbrenof.dowtube.download.DownloadViewModel
import org.johnbrenof.dowtube.download.QualityOption
import org.johnbrenof.dowtube.ui.components.DownloadItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DownloadViewModel = viewModel(),
) {
    val url by viewModel.urlInput.collectAsState()
    val quality by viewModel.quality.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val clipboard = LocalClipboardManager.current

    val busy = downloads.any {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
    }
    val urlValid = isValidUrl(url)

    Scaffold(
        topBar = { AppTopBar() },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            DownloadPanel(
                url = url,
                urlValid = urlValid,
                busy = busy,
                quality = quality,
                options = viewModel.qualityOptions,
                onUrlChange = viewModel::onUrlChange,
                onQualityChange = viewModel::onQualityChange,
                onStart = viewModel::startDownload,
                onPaste = {
                    val text = clipboard.getText()?.text?.trim().orEmpty()
                    if (text.isNotEmpty()) viewModel.onUrlChange(text)
                },
            )

            SectionHeader(
                count = downloads.size,
                hasFinished = downloads.any {
                    it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.ERROR
                },
                onClear = viewModel::clearFinished,
            )

            if (downloads.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(downloads, key = { it.id }) { item ->
                        DownloadItemCard(
                            item = item,
                            onRetry = { viewModel.retry(item) },
                            onRemove = { viewModel.remove(item) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar() {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "DowTube",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Vídeos e músicas em um só lugar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

@Composable
private fun DownloadPanel(
    url: String,
    urlValid: Boolean,
    busy: Boolean,
    quality: QualityOption,
    options: List<QualityOption>,
    onUrlChange: (String) -> Unit,
    onQualityChange: (QualityOption) -> Unit,
    onStart: () -> Unit,
    onPaste: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text("Link do vídeo") },
                placeholder = { Text("https://www.youtube.com/watch?v=…") },
                singleLine = true,
                isError = url.isNotBlank() && !urlValid,
                supportingText = {
                    if (url.isNotBlank() && !urlValid) {
                        Text("Use um link válido, começando com http")
                    }
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (url.isNotEmpty()) {
                            IconButton(onClick = { onUrlChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Limpar link",
                                )
                            }
                        }
                        IconButton(onClick = onPaste) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = "Colar da área de transferência",
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (urlValid) onStart() },
                ),
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Qualidade",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = quality.value == option.value,
                        onClick = { onQualityChange(option) },
                        label = {
                            Text(
                                text = option.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val buttonColors = if (busy) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                ButtonDefaults.buttonColors()
            }

            Button(
                onClick = onStart,
                enabled = urlValid && !busy,
                colors = buttonColors,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Baixando…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Baixar", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    count: Int,
    hasFinished: Boolean,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Downloads",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (hasFinished) {
            TextButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Limpar", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(bottom = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Nenhum download ainda",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Cole um link acima e toque em Baixar\npara começar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
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
