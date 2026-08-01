package com.app.ai.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.app.ai.planner.R
import com.app.ai.planner.ui.theme.PlannerColors
import kotlinx.coroutines.launch

private val mainRoutes = listOf("home", "chat", "tasks", "dates", "shop")

private data class BottomTab(
    val route: String,
    val labelRes: Int,
    val icon: @Composable (Color) -> Unit,
)

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

@Composable
private fun BottomNav(currentRoute: String?, onNavigate: (String) -> Unit) {
    val active = PlannerColors.primary
    val idle = Color(0xFF9A948B)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PlannerColors.background)
            .navigationBarsPadding()
            .padding(top = 4.dp, start = 6.dp, end = 6.dp, bottom = 2.dp),
    ) {
        val tabs = listOf(
            BottomTab("home", R.string.tab_today) { tint -> TabTodayIcon(tint = tint) },
            BottomTab("chat", R.string.tab_chat) { tint -> TabChatIcon(tint = tint) },
            BottomTab("tasks", R.string.tab_tasks) { tint -> TabTasksIcon(tint = tint) },
            BottomTab(
                "dates",
                R.string.tab_appointments
            ) { tint -> TabAppointmentsIcon(tint = tint) },
            BottomTab("shop", R.string.tab_shopping) { tint -> TabShoppingIcon(tint = tint) },
        )
        tabs.forEach { tab ->
            val tint = if (currentRoute == tab.route) active else idle
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Explicit size: the 48dp default minimum makes the bar unnecessarily tall.
                IconButton(
                    onClick = { onNavigate(tab.route) },
                    modifier = Modifier.size(36.dp),
                ) {
                    tab.icon(tint)
                }
                Text(
                    stringResource(tab.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint
                )
            }
        }
    }
}

@Composable
private fun DrawerContent(viewModel: DrawerViewModel, onNavigate: (String) -> Unit) {
    val noteCount by viewModel.noteCount.collectAsState()
    val ideaCount by viewModel.ideaCount.collectAsState()
    val goalCount by viewModel.goalCount.collectAsState()
    val categories by viewModel.categories.collectAsState()

    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = Color(0xFFFBFAF7),
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(20.dp, 22.dp, 20.dp, 14.dp)) {
                Text(
                    stringResource(R.string.drawer_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic),
                )
                Text(
                    stringResource(R.string.drawer_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerColors.muted,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
            ) {
                SectionLabel(stringResource(R.string.drawer_collections))
                DrawerItem(
                    stringResource(R.string.drawer_knowledge),
                    noteCount.toString(),
                    icon = { KnowledgeIcon(tint = PlannerColors.muted) },
                ) { onNavigate("wiki") }
                DrawerItem(
                    stringResource(R.string.drawer_ideas),
                    ideaCount.toString(),
                    icon = { IdeasIcon(tint = PlannerColors.muted) },
                ) { onNavigate("ideas") }
                DrawerItem(
                    stringResource(R.string.drawer_goals),
                    goalCount.toString(),
                    icon = { GoalsIcon(tint = PlannerColors.muted) },
                ) { onNavigate("goals") }
                SectionLabel(stringResource(R.string.drawer_functions))
                DrawerItem(
                    stringResource(R.string.usage_title),
                    null,
                    icon = { GoalsIcon(tint = PlannerColors.muted) },
                ) { onNavigate("usage") }
                SectionLabel(stringResource(R.string.drawer_categories))
                categories.forEach { cat ->
                    DrawerItem(
                        cat.name,
                        cat.count.toString(),
                        icon = { TypeIcon(cat.topType, tint = PlannerColors.muted) },
                    ) { onNavigate("category/${android.net.Uri.encode(cat.name)}") }
                }
            }
            HorizontalDivider(color = PlannerColors.divider)
            Column(Modifier.padding(12.dp)) {
                DrawerItem(
                    stringResource(R.string.settings_headline),
                    null,
                    icon = { SettingsIcon(tint = PlannerColors.muted) },
                ) { onNavigate("settings") }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    count: String?,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            it()
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        count?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = PlannerColors.faint
            )
        }
    }
}
