package com.app.mindunload.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.app.mindunload.ui.theme.PlannerColors
import kotlinx.coroutines.launch

private val mainRoutes = listOf("home", "chat", "tasks", "dates", "shop")

/**
 * Top-level navigation graph for the app. Reads as the *route table*; the visual chrome
 * (Drawer content in [DrawerContent], bottom navigation in [BottomNav], shared feedback
 * snackbar in [rememberFeedback]) lives in its own files.
 */
@Composable
fun PlannerNavHost() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val drawerViewModel: DrawerViewModel = viewModel()
    val feedback = rememberFeedback()

    fun navigate(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Going back from a screen opened via the drawer should land back in the
    // drawer, not bare on the Today tab.
    fun backToDrawer() {
        navController.popBackStack()
        scope.launch { drawerState.open() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                viewModel = drawerViewModel,
                onNavigate = {
                    scope.launch { drawerState.close() }
                    navigate(it)
                },
            )
        },
    ) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showBottomNav = currentRoute == null || mainRoutes.any { currentRoute == it }

        Scaffold(
            containerColor = PlannerColors.background,
            snackbarHost = { SnackbarHost(feedback.hostState) },
            bottomBar = {
                if (showBottomNav) {
                    BottomNav(currentRoute = currentRoute) { navigate(it) }
                }
            },
        ) { padding ->
            CompositionLocalProvider(LocalFeedback provides feedback) {
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    // consumeWindowInsets: otherwise imePadding in the chat counts the scaffold
                    // padding (bottom nav + navigation bar) twice and the field floats above the keyboard.
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding),
                ) {
                    composable("home") {
                        TodayScreen(
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onItemClick = { navController.navigate("detail/$it") },
                        )
                    }
                    composable("chat") {
                        ChatScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
                    }
                    composable("tasks") {
                        TasksScreen(
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onItemClick = { navController.navigate("detail/$it") },
                        )
                    }
                    composable("dates") {
                        AppointmentsScreen(
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onItemClick = { navController.navigate("detail/$it") },
                        )
                    }
                    composable("shop") {
                        ShoppingScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
                    }
                    composable("wiki") {
                        KnowledgeScreen(
                            onBack = { backToDrawer() },
                            onItemClick = { navController.navigate("detail/$it") },
                        )
                    }
                    composable("ideas") {
                        IdeasScreen(
                            onBack = { backToDrawer() },
                            onItemClick = { navController.navigate("detail/$it") },
                        )
                    }
                    composable("goals") {
                        GoalsScreen(
                            onBack = { backToDrawer() },
                            onItemClick = { navController.navigate("detail/$it") },
                        )
                    }
                    composable("category/{name}") { entry ->
                        // Navigation already decodes the URL-encoded argument.
                        val name = entry.arguments?.getString("name") ?: return@composable
                        CategoryScreen(
                            name = name,
                            onBack = { backToDrawer() },
                            onItemClick = { navController.navigate("detail/$it") },
                        )
                    }
                    composable("usage") {
                        UsageScreen(onBack = { backToDrawer() })
                    }
                    composable("stats") {
                        StatsScreen(onBack = { backToDrawer() })
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { backToDrawer() })
                    }
                    composable("detail/{itemId}") { entry ->
                        val itemId =
                            entry.arguments?.getString("itemId")?.toLongOrNull()
                                ?: return@composable
                        DetailScreen(
                            itemId = itemId,
                            onBack = { navController.popBackStack() },
                            onNavigateToItem = { navController.navigate("detail/$it") },
                            onOpenResearch = { noteId -> navController.navigate("research/$noteId") },
                        )
                    }
                    composable("research/{noteId}") { entry ->
                        val noteId =
                            entry.arguments?.getString("noteId")?.toLongOrNull()
                                ?: return@composable
                        ResearchScreen(
                            noteId = noteId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
