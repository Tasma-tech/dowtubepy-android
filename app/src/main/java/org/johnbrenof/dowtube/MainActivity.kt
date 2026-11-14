package org.johnbrenof.dowtube

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.johnbrenof.dowtube.settings.AppLocale
import org.johnbrenof.dowtube.settings.AppSettings
import org.johnbrenof.dowtube.ui.AppRoot
import org.johnbrenof.dowtube.ui.theme.DowTubeTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        setContent {
            DowTubeApp()
        }
    }
}

@Composable
private fun DowTubeApp() {
    val theme by AppSettings.theme.collectAsState()
    val darkTheme = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    DowTubeTheme(darkTheme = darkTheme) {
        AppRoot()
    }
}
