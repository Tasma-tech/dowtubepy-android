package org.johnbrenof.dowtube.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.johnbrenof.dowtube.R
import org.johnbrenof.dowtube.download.DownloadViewModel

private enum class AppTab { HOME, HISTORY, SETTINGS }

@Composable
fun AppRoot(
    viewModel: DownloadViewModel = viewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.HOME.name) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(
                    selected = tab == AppTab.HOME.name,
                    onClick = { tab = AppTab.HOME.name },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.nav_home)) },
                    colors = tabColors(),
                )
                NavigationBarItem(
                    selected = tab == AppTab.HISTORY.name,
                    onClick = { tab = AppTab.HISTORY.name },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.nav_history)) },
                    colors = tabColors(),
                )
                NavigationBarItem(
                    selected = tab == AppTab.SETTINGS.name,
                    onClick = { tab = AppTab.SETTINGS.name },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    colors = tabColors(),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (tab) {
                AppTab.HISTORY.name -> HistoryScreen(viewModel)
                AppTab.SETTINGS.name -> SettingsScreen(viewModel)
                else -> HomeScreen(
                    viewModel = viewModel,
                    onDownloadStarted = { tab = AppTab.HISTORY.name },
                )
            }
        }
    }
}

@Composable
private fun tabColors(): NavigationBarItemColors =
    NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = Color.Transparent,
    )
