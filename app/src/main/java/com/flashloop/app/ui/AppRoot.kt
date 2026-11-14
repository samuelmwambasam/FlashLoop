package com.flashloop.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("study", "Study", Icons.Default.PlayArrow),
    Tab("cards", "My Cards", Icons.Default.Menu),
    Tab("settings", "Settings", Icons.Default.Settings)
)

@Composable
fun AppRoot(reviewRequest: Int) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(reviewRequest) {
        if (reviewRequest > 0) nav.navigate("review") { launchSingleTop = true }
    }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(
                    onStudy = { nav.navigate("study") },
                    onCreate = { nav.navigate("edit/0") },
                    onCards = { nav.navigate("cards") },
                    onSettings = { nav.navigate("settings") }
                )
            }
            composable("study") { StudyScreen() }
            composable("cards") {
                CardsScreen(
                    onAdd = { nav.navigate("edit/0") },
                    onEdit = { id -> nav.navigate("edit/$id") }
                )
            }
            composable("settings") { SettingsScreen() }
            composable(
                "edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStack ->
                EditScreen(
                    id = backStack.arguments?.getLong("id") ?: 0L,
                    onDone = { nav.popBackStack() }
                )
            }
            composable("review") {
                ReviewScreen(onClose = {
                    nav.navigate("home") { popUpTo("home") { inclusive = false } }
                })
            }
        }
    }
}
