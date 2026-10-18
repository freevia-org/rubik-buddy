package org.freevia.rubikbuddy

import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

fun MainViewController() = ComposeUIViewController {
    val store = remember { ViewModelStore() }
    val vm = remember {
        ViewModelProvider.create(store, viewModelFactory { initializer { AppViewModel() } })[AppViewModel::class]
    }
    DisposableEffect(Unit) { onDispose { store.clear() } }
    RubikApp(vm)
}
