package com.mediaplayer.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mediaplayer.app.AppContainer

@Composable
fun AppRoot(container: AppContainer, openPlayerSignal: State<Int>) {
    val vm: MainViewModel = viewModel(factory = MainViewModel.factory(container))
    val theme by vm.theme.collectAsState()
    MediaPlayerTheme(theme) {
        val nav = rememberNavController()
        val pm = container.player
        LaunchedEffect(openPlayerSignal.value) {
            if (openPlayerSignal.value > 0) nav.navigate("player")
        }
        NavHost(
            navController = nav,
            startDestination = if (vm.welcomeShown) "home" else "welcome",
            enterTransition = { slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut() }
        ) {
            composable("welcome") {
                WelcomeScreen {
                    vm.markWelcomeShown()
                    nav.navigate("home") { popUpTo("welcome") { inclusive = true } }
                }
            }
            composable("home") { HomeScreen(vm, pm, nav) }
            composable("folder") { FolderScreen(vm, pm, nav) }
            composable("playlist") { PlaylistScreen(vm, pm, nav) }
            composable("player") { PlayerScreen(pm) { nav.popBackStack() } }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("about") { AboutScreen(nav) }
            composable("share") { ShareScreen(nav) }
            composable("rate") { RateScreen(vm, nav) }
        }
    }
}
