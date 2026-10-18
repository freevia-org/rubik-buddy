package org.freevia.rubikbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.freevia.rubikbuddy.ui.HomeScreen
import org.freevia.rubikbuddy.ui.MainScreen
import org.freevia.rubikbuddy.ui.ScreenProgressBar

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AndroidApp(vm) }
    }
}

@Composable
fun AndroidApp(vm: AppViewModel) {
    BackHandler(enabled = vm.currentScreen == Screen.SCAN) { vm.currentScreen = Screen.HOME }
    RubikApp(vm)
}
