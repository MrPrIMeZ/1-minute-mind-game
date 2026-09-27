package com.primez.oneminutemind

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.primez.oneminutemind.ads.Ads
import com.primez.oneminutemind.ui.AppViewModel
import com.primez.oneminutemind.ui.AwardsScreen
import com.primez.oneminutemind.ui.DailyDoneScreen
import com.primez.oneminutemind.ui.GameScreen
import com.primez.oneminutemind.ui.HomeScreen
import com.primez.oneminutemind.ui.MindTheme
import com.primez.oneminutemind.ui.OnboardingScreen
import com.primez.oneminutemind.ui.ResultScreen
import com.primez.oneminutemind.ui.Screen
import com.primez.oneminutemind.ui.SettingsScreen
import com.primez.oneminutemind.ui.StatsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Ads.start(this)
        setContent {
            MindTheme { App() }
        }
    }
}

@Composable
private fun App(vm: AppViewModel = viewModel()) {
    // Ask for notification permission once (Android 13+), after the first game or when
    // the player turns the reminder on.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(vm.askNotificationPermission) {
        if (vm.askNotificationPermission) {
            vm.askNotificationPermission = false
            if (Build.VERSION.SDK_INT >= 33 && vm.progress.settings.reminder) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Surface sets the default text colour for every screen (light text in dark mode, dark in light mode).
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        val screen = vm.screen
        AnimatedContent(
            targetState = screen,
            contentKey = { s -> if (s is Screen.Play) "play${s.key}" else s::class.simpleName },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Onboarding -> OnboardingScreen(vm)
                Screen.Home -> HomeScreen(vm)
                Screen.Stats -> StatsScreen(vm)
                Screen.Awards -> AwardsScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
                is Screen.Play -> GameScreen(vm, s)
                is Screen.Result -> ResultScreen(vm, s.info)
                is Screen.DailyDone -> DailyDoneScreen(vm, s)
            }
        }
    }
}

