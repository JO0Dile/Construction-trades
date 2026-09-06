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
import androidx.compose.ui.text.style.TextOverflow
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
import il.co.tradesmanager.ui.concrete.ConcreteScreen
import il.co.tradesmanager.ui.excavation.ExcavationScreen
import il.co.tradesmanager.ui.handover.HandoverScreen
import il.co.tradesmanager.ui.home.HomeScreen
import il.co.tradesmanager.ui.inventory.InventoryEditScreen
import il.co.tradesmanager.ui.inventory.InventoryScreen
import il.co.tradesmanager.ui.lifting.LiftingScreen
import il.co.tradesmanager.ui.money.MoneyScreen
import il.co.tradesmanager.ui.onboarding.OnboardingScreen
import il.co.tradesmanager.ui.orders.OrderDetailScreen
import il.co.tradesmanager.ui.orders.OrdersScreen
import il.co.tradesmanager.ui.payments.PaymentsScreen
import il.co.tradesmanager.ui.plant.PlantScreen
import il.co.tradesmanager.ui.people.PeopleScreen
import il.co.tradesmanager.ui.projects.ProjectDetailScreen
import il.co.tradesmanager.ui.projects.ProjectsScreen
import il.co.tradesmanager.ui.scaffold.ScaffoldRegisterScreen
import il.co.tradesmanager.ui.scanner.BarcodeScannerScreen
import il.co.tradesmanager.ui.evidence.DailyLogScreen
import il.co.tradesmanager.ui.evidence.PermitDetailScreen
import il.co.tradesmanager.ui.evidence.PermitsScreen
import il.co.tradesmanager.ui.evidence.SnagDetailScreen
import il.co.tradesmanager.ui.evidence.SnagsScreen
import il.co.tradesmanager.ui.evidence.TalkDetailScreen
import il.co.tradesmanager.ui.evidence.TalksScreen
import il.co.tradesmanager.ui.safety.ChecklistRunScreen
import il.co.tradesmanager.ui.safety.SafetyScreen
import il.co.tradesmanager.ui.schedule.ScheduleScreen
import il.co.tradesmanager.ui.settings.SettingsScreen
import il.co.tradesmanager.ui.tempworks.TemporaryWorksScreen
import il.co.tradesmanager.ui.timesheet.TimesheetScreen

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
    const val TALKS = "safety/talks"
    const val TALK_DETAIL = "safety/talks/detail"
    const val PERMITS = "safety/permits"
    const val PERMIT_DETAIL = "safety/permits/detail"
    const val SNAGS = "safety/snags"
    const val SNAG_DETAIL = "safety/snags/detail"
    const val DAILY_LOG = "projects/log"
    const val CONCRETE = "projects/concrete"
    const val SCAFFOLDS = "projects/scaffolds"
    const val LIFTS = "projects/lifts"
    const val TEMP_WORKS = "projects/tempworks"
    const val EXCAVATIONS = "projects/excavations"
    const val HANDOVER = "projects/handover"
    const val PEOPLE = "people"
    const val MONEY = "money"
    const val PAYMENTS = "money/applications"
    const val TIMESHEET = "money/timesheet"
    const val PLANT = "plant"
    const val ORDERS = "orders"
    const val ORDER_DETAIL = "orders/detail"
    const val SETTINGS = "settings"
    const val SCANNER = "scanner"

    /** Key the scanner writes its result under, read by whoever launched it. */
    const val SCAN_RESULT = "scan_result"

    /** Key the item editor reports the item it saved under. */
    const val SAVED_ITEM = "saved_item"

    fun inventoryEdit(itemId: String?) = "$INVENTORY_EDIT?itemId=${itemId.orEmpty()}"
    fun projectDetail(projectId: String) = "$PROJECT_DETAIL/$projectId"
    fun money(projectId: String) = "$MONEY/$projectId"
    fun payments(projectId: String) = "$PAYMENTS/$projectId"
    fun timesheet(projectId: String) = "$TIMESHEET/$projectId"
    fun orderDetail(orderId: String) = "$ORDER_DETAIL/$orderId"
    fun checklistRun(templateId: String) = "$CHECKLIST_RUN/$templateId"
    fun talkDetail(talkId: String) = "$TALK_DETAIL/$talkId"
    fun permitDetail(permitId: String) = "$PERMIT_DETAIL/$permitId"
    fun snagDetail(snagId: String) = "$SNAG_DETAIL/$snagId"
    fun dailyLog(projectId: String) = "$DAILY_LOG/$projectId"
    fun concrete(projectId: String) = "$CONCRETE/$projectId"
    fun scaffolds(projectId: String) = "$SCAFFOLDS/$projectId"
    fun lifts(projectId: String) = "$LIFTS/$projectId"
    fun temporaryWorks(projectId: String) = "$TEMP_WORKS/$projectId"
    fun excavations(projectId: String) = "$EXCAVATIONS/$projectId"
    fun handover(projectId: String) = "$HANDOVER/$projectId"
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
                            label = {
                                // Six tabs on a narrow phone leaves each one
                                // about fifty pixels. A label that wraps reads
                                // as "Inventor / y", so it is cut instead —
                                // and the labels themselves are kept short.
                                Text(
                                    text = stringResource(tab.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
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
                    onOpenPeople = { navController.switchTab(Routes.PEOPLE) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.INVENTORY) { entry ->
                InventoryScreen(
                    container = container,
                    onAddItem = { navController.navigate(Routes.inventoryEdit(null)) },
                    onEditItem = { navController.navigate(Routes.inventoryEdit(it)) },
                    onScan = { navController.navigate(Routes.SCANNER) },
                    onOpenPlant = { navController.navigate(Routes.PLANT) },
                    onOpenOrders = { navController.navigate(Routes.ORDERS) },
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
                    onDone = { savedItemId ->
                        // Handed back to the list so it can find the row, even
                        // when a filter or the sort order would have hidden it.
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(Routes.SAVED_ITEM, savedItemId)
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.PROJECTS) {
                ProjectsScreen(
                    container = container,
                    onOpenProject = { navController.navigate(Routes.projectDetail(it)) },
                )
            }
            composable("${Routes.PROJECT_DETAIL}/{projectId}") { entry ->
                val id = entry.arguments?.getString("projectId").orEmpty()
                ProjectDetailScreen(
                    container = container,
                    projectId = id,
                    onOpenMoney = { navController.navigate(Routes.money(id)) },
                    onOpenDailyLog = { navController.navigate(Routes.dailyLog(id)) },
                    onOpenConcrete = { navController.navigate(Routes.concrete(id)) },
                    onOpenScaffolds = { navController.navigate(Routes.scaffolds(id)) },
                    onOpenLifts = { navController.navigate(Routes.lifts(id)) },
                    onOpenTemporaryWorks = {
                        navController.navigate(Routes.temporaryWorks(id))
                    },
                    onOpenExcavations = { navController.navigate(Routes.excavations(id)) },
                    onOpenHandover = { navController.navigate(Routes.handover(id)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.MONEY}/{projectId}") { entry ->
                val id = entry.arguments?.getString("projectId").orEmpty()
                MoneyScreen(
                    container = container,
                    projectId = id,
                    onOpenPayments = { navController.navigate(Routes.payments(id)) },
                    onOpenTimesheet = { navController.navigate(Routes.timesheet(id)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.PAYMENTS}/{projectId}") { entry ->
                PaymentsScreen(
                    container = container,
                    projectId = entry.arguments?.getString("projectId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.TIMESHEET}/{projectId}") { entry ->
                TimesheetScreen(
                    container = container,
                    projectId = entry.arguments?.getString("projectId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SCHEDULE) { ScheduleScreen(container = container) }
            composable(Routes.PEOPLE) { PeopleScreen(container = container) }
            composable(Routes.PLANT) {
                PlantScreen(container = container, onBack = { navController.popBackStack() })
            }
            composable(Routes.ORDERS) {
                OrdersScreen(
                    container = container,
                    onOpenOrder = { navController.navigate(Routes.orderDetail(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.ORDER_DETAIL}/{orderId}") { entry ->
                OrderDetailScreen(
                    container = container,
                    orderId = entry.arguments?.getString("orderId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SAFETY) {
                SafetyScreen(
                    container = container,
                    onRunChecklist = { navController.navigate(Routes.checklistRun(it)) },
                    onOpenTalks = { navController.navigate(Routes.TALKS) },
                    onOpenPermits = { navController.navigate(Routes.PERMITS) },
                    onOpenSnags = { navController.navigate(Routes.SNAGS) },
                )
            }
            composable(Routes.TALKS) {
                TalksScreen(
                    container = container,
                    onOpenTalk = { navController.navigate(Routes.talkDetail(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.TALK_DETAIL}/{talkId}") { entry ->
                TalkDetailScreen(
                    container = container,
                    talkId = entry.arguments?.getString("talkId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PERMITS) {
                PermitsScreen(
                    container = container,
                    onOpenPermit = { navController.navigate(Routes.permitDetail(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.PERMIT_DETAIL}/{permitId}") { entry ->
                PermitDetailScreen(
                    container = container,
                    permitId = entry.arguments?.getString("permitId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.DAILY_LOG}/{projectId}") { entry ->
                DailyLogScreen(
                    container = container,
                    projectId = entry.arguments?.getString("projectId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.CONCRETE}/{projectId}") { entry ->
                ConcreteScreen(
                    container = container,
                    projectId = entry.arguments?.getString("projectId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.SCAFFOLDS}/{projectId}") { entry ->
                ScaffoldRegisterScreen(
                    container = container,
                    projectId = entry.arguments?.getString("projectId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.LIFTS}/{projectId}") { entry ->
                LiftingScreen(
                    container = container,
                    projectId = entry.arguments?.getString("projectId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.TEMP_WORKS}/{projectId}") { entry ->
                TemporaryWorksScreen(
                    container = container,
                    projectId = entry.arguments?.getString("projectId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.EXCAVATIONS}/{projectId}") { entry ->
                ExcavationScreen(
                    container = container,
                    projectId = entry.arguments?.getString("projectId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.HANDOVER}/{projectId}") { entry ->
                HandoverScreen(
                    container = container,
                    projectId = entry.arguments?.getString("projectId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SNAGS) {
                SnagsScreen(
                    container = container,
                    onOpenSnag = { navController.navigate(Routes.snagDetail(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("${Routes.SNAG_DETAIL}/{snagId}") { entry ->
                SnagDetailScreen(
                    container = container,
                    snagId = entry.arguments?.getString("snagId").orEmpty(),
                    onBack = { navController.popBackStack() },
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
