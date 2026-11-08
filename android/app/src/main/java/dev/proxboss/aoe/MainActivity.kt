package dev.proxboss.aoe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.proxboss.aoe.data.AppSettings
import dev.proxboss.aoe.notify.NotificationHelper
import dev.proxboss.aoe.notify.NotificationService
import dev.proxboss.aoe.ui.create.CreateSessionScreen
import dev.proxboss.aoe.ui.create.CreateSessionViewModel
import dev.proxboss.aoe.ui.detail.SessionDetailScreen
import dev.proxboss.aoe.ui.detail.SessionDetailViewModel
import dev.proxboss.aoe.ui.diff.DiffScreen
import dev.proxboss.aoe.ui.diff.DiffViewModel
import dev.proxboss.aoe.ui.management.ManagementScreen
import dev.proxboss.aoe.ui.management.ManagementViewModel
import dev.proxboss.aoe.ui.sessions.SessionsScreen
import dev.proxboss.aoe.ui.sessions.SessionsViewModel
import dev.proxboss.aoe.ui.settings.SettingsScreen
import dev.proxboss.aoe.ui.settings.SettingsViewModel
import dev.proxboss.aoe.ui.settings.SetupScreen
import dev.proxboss.aoe.ui.theme.AoETheme
import dev.proxboss.aoe.ui.theme.SpaceBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val container = (application as AoEApplication).container
            val settingsState by produceState<AppSettings?>(initialValue = null) {
                container.settings.settings.collect { value = it }
            }
            val settings = settingsState
            if (settings == null) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                AoETheme(settings.theme) {
                    SpaceBackground {
                        AoEApp(container, settings)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val sessionId = intent?.getStringExtra(NotificationHelper.EXTRA_SESSION_ID).orEmpty()
        if (sessionId.isNotBlank()) {
            (application as AoEApplication).container.pendingOpenSession.value = sessionId
        }
    }
}

@Composable
private fun AoEApp(container: AppContainer, settings: AppSettings) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val pendingSession by container.pendingOpenSession.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(settings.permissionAsked) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !settings.permissionAsked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            container.settings.setPermissionAsked()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(settings.configured, settings.notificationsEnabled) {
        val intent = Intent(context, NotificationService::class.java)
        runCatching {
            if (settings.configured && settings.notificationsEnabled) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }

    LaunchedEffect(settings.configured) {
        if (!settings.configured) return@LaunchedEffect
        container.authFailure.collect {
            if (settings.configured && navController.currentDestination?.route != "setup") {
                navController.navigate("setup") {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(pendingSession, settings.configured) {
        val sessionId = pendingSession
        if (!sessionId.isNullOrBlank() && settings.configured) {
            container.pendingOpenSession.value = null
            navController.navigate("session/$sessionId") { launchSingleTop = true }
        }
    }

    val startDestination = remember { if (settings.configured) "sessions" else "setup" }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("setup") {
            val viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            SetupScreen(
                viewModel = viewModel,
                onSaved = {
                    navController.navigate("sessions") {
                        popUpTo("setup") { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable("sessions") {
            val viewModel: SessionsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            SessionsScreen(
                viewModel = viewModel,
                onOpenSession = { sessionId -> navController.navigate("session/$sessionId") },
                onOpenSettings = { navController.navigate("settings") },
                onCreateSession = { navController.navigate("create") },
            )
        }

        composable("create") {
            val viewModel: CreateSessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            CreateSessionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCreated = { sessionId ->
                    navController.navigate("session/$sessionId") {
                        popUpTo("sessions") { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable("settings") {
            val viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate("setup") {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenManagement = { navController.navigate("management") },
            )
        }

        composable("management") {
            val viewModel: ManagementViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            ManagementScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            "session/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.arguments?.getString("id").orEmpty()
            val viewModel: SessionDetailViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = SessionDetailViewModel.Factory(
                        context.applicationContext as android.app.Application,
                        sessionId,
                    ),
                )
            SessionDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenDiff = { id -> navController.navigate("diff/$id") },
            )
        }

        composable(
            "diff/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.arguments?.getString("id").orEmpty()
            val viewModel: DiffViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = DiffViewModel.Factory(
                        context.applicationContext as android.app.Application,
                        sessionId,
                    ),
                )
            DiffScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
