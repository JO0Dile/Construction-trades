package il.co.tradesmanager.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.data.repository.SettingsRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.home.HomeScreen
import il.co.tradesmanager.ui.inventory.InventoryEditScreen
import il.co.tradesmanager.ui.inventory.InventoryScreen
import il.co.tradesmanager.ui.onboarding.OnboardingScreen
import il.co.tradesmanager.ui.people.PeopleScreen
import il.co.tradesmanager.ui.projects.ProjectDetailScreen
import il.co.tradesmanager.ui.projects.ProjectsScreen
import il.co.tradesmanager.ui.scanner.BarcodeScannerScreen
import il.co.tradesmanager.ui.safety.ChecklistRunScreen
import il.co.tradesmanager.ui.safety.SafetyScreen
import il.co.tradesmanager.ui.schedule.ScheduleScreen
import il.co.tradesmanager.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val INVENTORY = "inventory"
    const val INVENTORY_EDIT = "inventory/edit"
    const val PROJECTS = "projects"
    const val PROJECT_DETAIL = "projects/detail"
    const val SCHEDULE = "schedule"
    const val SAFETY = "safety"
    const val CHECKLIST_RUN = "safety/run"
    const val PEOPLE = "people"
    const val SETTINGS = "settings"
    const val SCANNER = "scanner"

    /** Key the scanner writes its result under, read by whoever launched it. */
    const val SCAN_RESULT = "scan_result"

    fun inventoryEdit(itemId: String?) = "$INVENTORY_EDIT?itemId=${itemId.orEmpty()}"
    fun projectDetail(projectId: String) = "$PROJECT_DETAIL/$projectId"
    fun checklistRun(templateId: String) = "$CHECKLIST_RUN/$templateId"
}

/**
 * A tab, and the lenses that justify showing it.
 *
 * [lenses] is an any-of: a job is all five lenses at once, so Projects earns
 * its place for anyone who can read any part of one, and the sections inside a
 * job are gated individually. A tab with no lenses is always shown.
 */
private data class Tab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val lenses: Set<Lens> = emptySet(),
)

private val TABS = listOf(
    Tab(Routes.HOME, R.string.nav_home, Icons.Filled.Dashboard),
    Tab(Routes.INVENTORY, R.string.nav_inventory, Icons.Filled.Inventory2, setOf(Lens.STUFF)),
    Tab(
        Routes.PROJECTS, R.string.nav_projects, Icons.Filled.Work,
        setOf(Lens.PLAN, Lens.STUFF, Lens.EVIDENCE, Lens.MONEY),
    ),
    Tab(Routes.SCHEDULE, R.string.nav_schedule, Icons.Filled.CalendarMonth, setOf(Lens.PLAN)),
    Tab(Routes.SAFETY, R.string.nav_safety, Icons.Filled.HealthAndSafety, setOf(Lens.EVIDENCE)),
    Tab(Routes.PEOPLE, R.string.people_title, Icons.Filled.Groups, setOf(Lens.PEOPLE)),
)

private fun Tab.isVisibleTo(role: Role): Boolean =
    lenses.isEmpty() || lenses.any(role::canRead)

@Composable
fun AppNavHost(
    container: AppContainer,
    settings: SettingsRepository.Settings,
    role: Role,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = TABS.filter { it.isVisibleTo(role) }
    val showBottomBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                            alwaysShowLabel = false,
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (settings.onboardingComplete) Routes.HOME else Routes.ONBOARDING,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(container = container) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            }
            composable(Routes.HOME) {
                HomeScreen(
                    container = container,
                    onOpenInventory = { navController.switchTab(Routes.INVENTORY) },
                    onOpenSchedule = { navController.switchTab(Routes.SCHEDULE) },
                    onOpenProjects = { navController.switchTab(Routes.PROJECTS) },
                    onOpenSafety = { navController.switchTab(Routes.SAFETY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.INVENTORY) { entry ->
                InventoryScreen(
                    container = container,
                    onAddItem = { navController.navigate(Routes.inventoryEdit(null)) },
                    onEditItem = { navController.navigate(Routes.inventoryEdit(it)) },
                    onScan = { navController.navigate(Routes.SCANNER) },
                    // The scanner hands its result back through this entry's
                    // saved state, which survives the process being killed
                    // behind the camera on a low-memory phone. The screen
                    // observes it and clears it once handled, so a scan is
                    // never replayed on the next recomposition.
                    savedStateHandle = entry.savedStateHandle,
                )
            }
            composable(Routes.SCANNER) {
                BarcodeScannerScreen(
                    onScanned = { code ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(Routes.SCAN_RESULT, code)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.INVENTORY_EDIT}?itemId={itemId}") { entry ->
                InventoryEditScreen(
                    container = container,
                    itemId = entry.arguments?.getString("itemId")?.takeIf { it.isNotBlank() },
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.PROJECTS) {
                ProjectsScreen(
                    container = container,
                    onOpenProject = { navController.navigate(Routes.projectDetail(it)) },
                )
            }
            composable("${Routes.PROJECT_DETAIL}/{projectId}") { entry ->
                ProjectDetailScreen(
                    container = container,
                    projectId = entry.arguments?.getString("projectId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SCHEDULE) { ScheduleScreen(container = container) }
            composable(Routes.PEOPLE) { PeopleScreen(container = container) }
            composable(Routes.SAFETY) {
                SafetyScreen(
                    container = container,
                    onRunChecklist = { navController.navigate(Routes.checklistRun(it)) },
                )
            }
            composable("${Routes.CHECKLIST_RUN}/{templateId}") { entry ->
                ChecklistRunScreen(
                    container = container,
                    templateId = entry.arguments?.getString("templateId").orEmpty(),
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(container = container, onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Tab switching that does not pile up a back stack of tabs. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
