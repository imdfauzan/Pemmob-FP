package com.lumiposture.app.presentation.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lumiposture.app.domain.model.StudyMode
import com.lumiposture.app.presentation.components.FloatingPillNavBar
import com.lumiposture.app.presentation.history.HistoryScreen
import com.lumiposture.app.presentation.home.HomeDashboardScreen
import com.lumiposture.app.presentation.onboarding.CalibrationScreen
import com.lumiposture.app.presentation.report.SessionReportScreen
import com.lumiposture.app.presentation.session.ActiveSessionScreen
import com.lumiposture.app.presentation.settings.SettingsScreen
import com.lumiposture.app.presentation.theme.LumiTheme
import dev.chrisbanes.haze.rememberHazeState

object LumiRoutes {
    const val CALIBRATION = "calibration?fromSettings={fromSettings}"
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SESSION = "session/{mode}"
    const val REPORT = "report/{sessionId}"

    fun calibration(fromSettings: Boolean = false) = "calibration?fromSettings=$fromSettings"
    fun session(mode: StudyMode) = "session/${mode.apiValue}"
    fun report(sessionId: Long) = "report/$sessionId"
}

@Composable
fun LumiNavGraph(
    isCalibrated: Boolean,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val hazeState = rememberHazeState()

    val hasCameraPermission = remember(currentRoute) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    val startDestination = if (isCalibrated && hasCameraPermission) {
        LumiRoutes.HOME
    } else {
        LumiRoutes.CALIBRATION
    }

    val showBottomNav = (currentRoute == LumiRoutes.HOME) ||
        (currentRoute == LumiRoutes.HISTORY) ||
        (currentRoute == LumiRoutes.SETTINGS)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LumiTheme.colors.canvas),
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(
                route = LumiRoutes.CALIBRATION,
                arguments = listOf(
                    navArgument("fromSettings") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val fromSettings = entry.arguments?.getBoolean("fromSettings") ?: false
                CalibrationScreen(
                    fromSettings = fromSettings,
                    onCalibrationFinished = {
                        if (fromSettings) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(LumiRoutes.HOME) {
                                popUpTo(LumiRoutes.CALIBRATION) { inclusive = true }
                            }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(LumiRoutes.HOME) {
                HomeDashboardScreen(
                    hazeState = hazeState,
                    onStartSession = { mode ->
                        val canRunCamera = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (canRunCamera) {
                            navController.navigate(LumiRoutes.session(mode))
                        } else {
                            navController.navigate(LumiRoutes.calibration(fromSettings = true))
                        }
                    },
                    onOpenReport = { sessionId ->
                        navController.navigate(LumiRoutes.report(sessionId))
                    },
                    onOpenCalibration = {
                        navController.navigate(LumiRoutes.calibration(fromSettings = true))
                    },
                )
            }

            composable(LumiRoutes.HISTORY) {
                HistoryScreen(
                    hazeState = hazeState,
                    onOpenReport = { sessionId ->
                        navController.navigate(LumiRoutes.report(sessionId))
                    },
                )
            }

            composable(LumiRoutes.SETTINGS) {
                SettingsScreen(
                    hazeState = hazeState,
                    onRecalibrateClick = {
                        navController.navigate(LumiRoutes.calibration(fromSettings = true))
                    },
                )
            }

            composable(
                route = LumiRoutes.SESSION,
                arguments = listOf(
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = StudyMode.HANDHELD.apiValue
                    },
                ),
            ) {
                ActiveSessionScreen(
                    onSessionFinished = { sessionId ->
                        navController.navigate(LumiRoutes.report(sessionId)) {
                            popUpTo(LumiRoutes.HOME) { inclusive = false }
                        }
                    },
                    onMissingCameraPermission = {
                        navController.navigate(LumiRoutes.calibration(fromSettings = false)) {
                            popUpTo(LumiRoutes.HOME) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = LumiRoutes.REPORT,
                arguments = listOf(
                    navArgument("sessionId") {
                        type = NavType.LongType
                    },
                ),
            ) {
                SessionReportScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }

        if (showBottomNav) {
            FloatingPillNavBar(
                currentRoute = currentRoute,
                onSelectTab = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
