package org.freevia.rubikbuddy

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.freevia.rubikbuddy.ui.*

@Composable
fun RubikApp(vm: AppViewModel) {
    MaterialTheme {
        Scaffold { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                ScreenProgressBar(vm.overallProgress, vm.progressPhase, vm.scanningFace,
                    vm.solvingStep, vm.solvingTotalSteps,
                    Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                Box(Modifier.weight(1f)) {
                    when(vm.currentScreen) {
                        Screen.HOME -> HomeScreen(onScanClicked = { vm.currentScreen = Screen.SCAN })
                        Screen.SCAN -> MainScreen(vm,
                            onBack = { vm.currentScreen = Screen.HOME },
                            onDone = { vm.currentScreen = Screen.HOME; vm.restartScan(); vm.resetProgress() })
                    }
                }
            }
        }
    }
}
