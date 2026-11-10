package org.johnbrenof.dowtube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.johnbrenof.dowtube.ui.HomeScreen
import org.johnbrenof.dowtube.ui.theme.DowTubeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DowTubeTheme {
                HomeScreen()
            }
        }
    }
}
