package com.dyd.contable.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dyd.contable.data.EntryType
import com.dyd.contable.ui.auth.SessionViewModel
import com.dyd.contable.ui.crm.ContactDetailScreen
import com.dyd.contable.ui.dashboard.DashboardScreen
import com.dyd.contable.ui.entries.EntriesScreen
import com.dyd.contable.ui.entries.EntryEditorScreen
import com.dyd.contable.ui.inventory.ExplosionScreen
import com.dyd.contable.ui.inventory.InventoryScreen
import com.dyd.contable.ui.inventory.ItemDetailScreen
import com.dyd.contable.ui.invoices.InvoiceDetailScreen
import com.dyd.contable.ui.invoices.InvoiceEditorScreen
import com.dyd.contable.ui.invoices.InvoicesScreen
import com.dyd.contable.ui.more.AccountsScreen
import com.dyd.contable.ui.more.CompanyScreen
import com.dyd.contable.ui.more.CategoriesScreen
import com.dyd.contable.ui.more.ContactsScreen
import com.dyd.contable.ui.more.MoreScreen
import com.dyd.contable.ui.more.UsersScreen
import com.dyd.contable.ui.pending.PendingScreen
import com.dyd.contable.ui.reports.ReportsScreen
import com.dyd.contable.ui.taxes.InterestsScreen
import com.dyd.contable.ui.LocalProfile
import androidx.compose.runtime.CompositionLocalProvider

object Routes {
    const val DASHBOARD = "dashboard"
    const val ENTRIES = "entries"
    const val INVENTORY = "inventory"
    const val REPORTS = "reports"
    const val MORE = "more"
    const val PENDING = "pending"
    const val ACCOUNTS = "accounts"
    const val CONTACTS = "contacts"
    const val CATEGORIES = "categories"
    const val USERS = "users"
    const val EDITOR = "entry?id={id}&type={type}&itemId={itemId}"
    const val ITEM = "item/{id}"
    const val EXPLOSION = "explosion?productId={productId}"
    const val CONTACT = "contact/{id}"
    const val INVOICES = "invoices"
    const val INVOICE_NEW = "invoice/new"
    const val INVOICE = "invoice/{id}"
    const val COMPANY = "company"
    const val INTERESTS = "interests"
    fun invoice(id: Long) = "invoice/$id"

    fun editor(id: Long = 0, type: EntryType = EntryType.EXPENSE, itemId: Long = 0) =
        "entry?id=$id&type=${type.name}&itemId=$itemId"
    fun item(id: Long) = "item/$id"
    fun explosion(productId: Long = 0) = "explosion?productId=$productId"
    fun contact(id: Long) = "contact/$id"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
fun DydNavHost(session: SessionViewModel, nav: NavHostController = rememberNavController()) {
    val profile by session.profile.collectAsStateWithLifecycle()
    val accounting = profile?.canManageAccounting == true
    // Bodega, producción, calidad e I+D no ven dinero: entran directo al inventario.
    val seesMoney = profile?.canSeeEntries == true
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { session.errors.collect { snackbar.showSnackbar(it) } }

    val topLevel = buildList {
        if (seesMoney) {
            add(TopLevel(Routes.DASHBOARD, "Inicio", Icons.Outlined.Home, Icons.Filled.Home))
            add(TopLevel(Routes.ENTRIES, "Movimientos", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong))
        }
        add(TopLevel(Routes.INVENTORY, "Inventario", Icons.Outlined.Inventory2, Icons.Filled.Inventory2))
        if (profile?.canReadAccounting == true) add(TopLevel(Routes.REPORTS, "Reportes", Icons.Outlined.Assessment, Icons.Filled.Assessment))
        add(TopLevel(Routes.MORE, "Más", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz))
    }
    val showBottomBar = topLevel.any { it.route == currentRoute }

    CompositionLocalProvider(LocalProfile provides profile) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevel.forEach { item ->
                        val selected = item.route == currentRoute
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                            label = { Text(item.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val openEditor: (Long, EntryType) -> Unit = { id, type -> nav.navigate(Routes.editor(id, type)) }
        val back: () -> Unit = { nav.popBackStack() }
        NavHost(
            navController = nav,
            startDestination = if (seesMoney) Routes.DASHBOARD else Routes.INVENTORY,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onNewEntry = { type -> openEditor(0, type) },
                    onOpenEntry = { id -> openEditor(id, EntryType.EXPENSE) },
                    onSeeAll = { nav.navigate(Routes.ENTRIES) { launchSingleTop = true } },
                    onOpenPending = { nav.navigate(Routes.PENDING) },
                    onOpenAccounts = { nav.navigate(Routes.ACCOUNTS) },
                    onOpenInventory = { nav.navigate(Routes.INVENTORY) { launchSingleTop = true } },
                    onOpenContact = { nav.navigate(Routes.contact(it)) },
                )
            }
            composable(Routes.ENTRIES) {
                EntriesScreen(
                    onNewEntry = if (profile?.canCreateEntries == true) {
                        { openEditor(0, if (accounting) EntryType.EXPENSE else EntryType.INCOME) }
                    } else null,
                    // El auditor consulta pero no edita.
                    onOpenEntry = { id -> if (profile?.canCreateEntries == true) openEditor(id, EntryType.EXPENSE) },
                )
            }
            composable(Routes.INVENTORY) {
                InventoryScreen(
                    onOpenItem = { nav.navigate(Routes.item(it)) },
                    onExplosion = { nav.navigate(Routes.explosion()) },
                    profile = profile,
                )
            }
            composable(Routes.REPORTS) { ReportsScreen() }
            composable(Routes.MORE) {
                MoreScreen(
                    profile = profile,
                    onPending = { nav.navigate(Routes.PENDING) },
                    onAccounts = { nav.navigate(Routes.ACCOUNTS) },
                    onContacts = { nav.navigate(Routes.CONTACTS) },
                    onCategories = { nav.navigate(Routes.CATEGORIES) },
                    onUsers = { nav.navigate(Routes.USERS) },
                    onInvoices = { nav.navigate(Routes.INVOICES) },
                    onCompany = { nav.navigate(Routes.COMPANY) },
                    onInterests = { nav.navigate(Routes.INTERESTS) },
                    onChangePassword = session::changePassword,
                    onSignOut = session::signOut,
                )
            }
            composable(Routes.PENDING) {
                PendingScreen(
                    onNewEntry = { type -> openEditor(0, type) },
                    onOpenEntry = { id -> openEditor(id, EntryType.EXPENSE) },
                    onBack = back,
                )
            }
            composable(Routes.ACCOUNTS) { AccountsScreen(onBack = back) }
            composable(Routes.CONTACTS) { ContactsScreen(onBack = back, onOpenContact = { nav.navigate(Routes.contact(it)) }) }
            composable(Routes.CATEGORIES) { CategoriesScreen(onBack = back) }
            composable(Routes.USERS) { UsersScreen(onBack = back) }
            composable(Routes.INVOICES) {
                InvoicesScreen(onBack = back, onNew = { nav.navigate(Routes.INVOICE_NEW) }, onOpen = { nav.navigate(Routes.invoice(it)) })
            }
            composable(Routes.INVOICE_NEW) {
                InvoiceEditorScreen(onBack = back, onCreated = { id ->
                    nav.navigate(Routes.invoice(id)) { popUpTo(Routes.INVOICE_NEW) { inclusive = true } }
                })
            }
            composable(Routes.INVOICE, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                InvoiceDetailScreen(onBack = back)
            }
            composable(Routes.COMPANY) { CompanyScreen(onBack = back) }
            composable(Routes.INTERESTS) { InterestsScreen(onBack = back) }
            composable(
                Routes.EDITOR,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("type") { type = NavType.StringType; defaultValue = EntryType.EXPENSE.name },
                    navArgument("itemId") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) {
                EntryEditorScreen(onDone = back)
            }
            composable(Routes.ITEM, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                ItemDetailScreen(
                    onBack = back,
                    onPurchase = { itemId -> nav.navigate(Routes.editor(0, EntryType.EXPENSE, itemId)) },
                    onExplosion = { nav.navigate(Routes.explosion(it)) },
                    onOpenItem = { nav.navigate(Routes.item(it)) },
                    profile = profile,
                )
            }
            composable(
                Routes.EXPLOSION,
                arguments = listOf(navArgument("productId") { type = NavType.LongType; defaultValue = 0L }),
            ) {
                ExplosionScreen(onBack = back)
            }
            composable(Routes.CONTACT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                ContactDetailScreen(onBack = back, onOpenEntry = { id -> openEditor(id, EntryType.EXPENSE) })
            }
        }
    }
    }
}
