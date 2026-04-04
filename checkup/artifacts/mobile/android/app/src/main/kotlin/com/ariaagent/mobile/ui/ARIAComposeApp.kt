package com.ariaagent.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ariaagent.mobile.ui.screens.*
import com.ariaagent.mobile.ui.theme.ARIAColors
import com.ariaagent.mobile.ui.theme.ARIATheme
import com.ariaagent.mobile.ui.viewmodel.AgentViewModel

/**
 * ARIAComposeApp — root composable for Phase 11 native Android UI.
 *
 * Hosts all 5 ARIA screens via Navigation Compose + Material3 NavigationBar.
 * A single AgentViewModel is shared across all screens through a common viewModel()
 * call at this level and passed down. This ensures all screens observe the same
 * StateFlows from the same ViewModel instance.
 *
 * Navigation destinations:
 *   dashboard  → DashboardScreen  — at-a-glance status
 *   control    → ControlScreen    — start/pause/stop + goal input
 *   activity   → ActivityScreen   — live action log + token stream
 *   modules    → ModulesScreen    — hardware/model readiness
 *   settings   → SettingsScreen   — inference parameter editing
 *
 * Phase: 11 (Jetpack Compose UI — registered in ComposeMainActivity, NOT launcher yet)
 */

private sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Control   : Screen("control",   "Control",   Icons.Default.SmartToy)
    object Activity  : Screen("activity",  "Activity",  Icons.Default.Timeline)
    object Modules   : Screen("modules",   "Modules",   Icons.Default.Memory)
    object Settings  : Screen("settings",  "Settings",  Icons.Default.Settings)
}

private val bottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.Control,
    Screen.Activity,
    Screen.Modules,
    Screen.Settings,
)

@Composable
fun ARIAComposeApp() {
    ARIATheme {
        val navController = rememberNavController()
        // Single shared ViewModel — all screens observe the same StateFlows
        val vm: AgentViewModel = viewModel()
        val agentState by vm.agentState.collectAsState()

        Scaffold(
            containerColor = ARIAColors.Background,
            bottomBar = {
                NavigationBar(
                    containerColor = ARIAColors.Surface,
                    tonalElevation = androidx.compose.ui.unit.Dp(0f),
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    bottomNavScreens.forEach { screen ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == screen.route } == true

                        NavigationBarItem(
                            icon = {
                                BadgedBox(
                                    badge = {
                                        // Show dot on Control tab when agent is running
                                        if (screen is Screen.Control && agentState.status == "running") {
                                            Badge(containerColor = ARIAColors.Success)
                                        }
                                    }
                                ) {
                                    Icon(
                                        screen.icon,
                                        contentDescription = screen.label,
                                    )
                                }
                            },
                            label = {
                                Text(
                                    screen.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    // Pop to start destination to avoid a large back stack
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor      = ARIAColors.Primary,
                                selectedTextColor      = ARIAColors.Primary,
                                unselectedIconColor    = ARIAColors.Muted,
                                unselectedTextColor    = ARIAColors.Muted,
                                indicatorColor         = ARIAColors.Primary.copy(alpha = 0.15f),
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ARIAColors.Background)
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Dashboard.route
                ) {
                    composable(Screen.Dashboard.route) { DashboardScreen(vm) }
                    composable(Screen.Control.route)   { ControlScreen(vm) }
                    composable(Screen.Activity.route)  { ActivityScreen(vm) }
                    composable(Screen.Modules.route)   { ModulesScreen(vm) }
                    composable(Screen.Settings.route)  { SettingsScreen(vm) }
                }
            }
        }
    }
}
