package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ThemeSetting
import com.example.ui.MainViewModel
import com.example.ui.screens.AppDetailBottomSheet
import com.example.ui.screens.AppsScreen
import com.example.ui.screens.ForceDefaultScreen
import com.example.ui.screens.PrivilegeScreen
import com.example.ui.screens.PrivilegeStatusPill
import com.example.ui.screens.RestoreScreen
import com.example.ui.theme.AppForgeTheme

enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    APPS("Ứng dụng", Icons.Rounded.Apps, Icons.Outlined.Apps),
    RESTORE("Khôi phục", Icons.Rounded.Restore, Icons.Outlined.Restore),
    DEFAULTS("Mặc định", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
    PRIVILEGE("Quyền", Icons.Rounded.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val prefs by viewModel.userPrefs.collectAsStateWithLifecycle()
            val darkTheme = when (prefs.themeSetting) {
                ThemeSetting.SYSTEM -> isSystemInDarkTheme()
                ThemeSetting.DARK -> true
                ThemeSetting.LIGHT -> false
            }

            AppForgeTheme(darkTheme = darkTheme) {
                AppForgeRoot(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppForgeRoot(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val restorableItems by viewModel.restorableItems.collectAsStateWithLifecycle()
    val privilegeStatus by viewModel.privilegeStatus.collectAsStateWithLifecycle()
    val userPrefs by viewModel.userPrefs.collectAsStateWithLifecycle()
    val commandLogs by viewModel.commandLogs.collectAsStateWithLifecycle()
    val detailApp by viewModel.detailApp.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(AppTab.APPS) }
    val snackbarHostState = remember { SnackbarHostState() }

    val hiddenApps = remember(allApps) {
        allApps.filter { it.isHiddenOrDisabled }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (uiState.isSelectionMode) {
        BackHandler { viewModel.clearSelection() }
    } else if (currentTab != AppTab.APPS) {
        BackHandler { currentTab = AppTab.APPS }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "AppForge",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    },
                    actions = {
                        PrivilegeStatusPill(
                            status = privilegeStatus,
                            onClick = { currentTab = AppTab.PRIVILEGE }
                        )
                        Spacer(Modifier.width(12.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        AppTab.entries.forEach { tab ->
                            val badgeCount = if (tab == AppTab.RESTORE) restorableItems.size else 0
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        selected = currentTab == tab,
                                        badgeCount = badgeCount
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        AppTab.entries.forEach { tab ->
                            val badgeCount = if (tab == AppTab.RESTORE) restorableItems.size else 0
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        selected = currentTab == tab,
                                        badgeCount = badgeCount
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentTab) {
                        AppTab.APPS -> AppsScreen(
                            uiState = uiState,
                            allApps = allApps,
                            filteredApps = filteredApps,
                            iconProvider = viewModel::getAppIcon,
                            onSearchChange = viewModel::setSearchQuery,
                            onFilterSelect = viewModel::setActiveFilter,
                            onSortChange = viewModel::setSortOrder,
                            onRefresh = viewModel::refreshApps,
                            onToggleSelect = viewModel::toggleSelection,
                            onSelectAll = viewModel::selectAllFiltered,
                            onClearSelection = viewModel::clearSelection,
                            onOpenDetail = { viewModel.openAppDetail(it.packageName) },
                            onUninstallApps = viewModel::uninstallApps,
                            onSetAppsHidden = viewModel::setAppsHidden,
                            onRestoreSystemApp = viewModel::restoreSystemApp
                        )

                        AppTab.RESTORE -> RestoreScreen(
                            uiState = uiState,
                            restorableItems = restorableItems,
                            hiddenApps = hiddenApps,
                            iconProvider = viewModel::getAppIcon,
                            onRestoreItem = viewModel::restoreApp,
                            onDeleteBackup = viewModel::deleteBackup,
                            onUnhideApp = { app -> viewModel.setAppsHidden(listOf(app), false) }
                        )

                        AppTab.DEFAULTS -> ForceDefaultScreen(
                            uiState = uiState,
                            allApps = allApps,
                            disableCompeting = userPrefs.disableCompetingOnForceDefault,
                            iconProvider = viewModel::getAppIcon,
                            onToggleDisableCompeting = viewModel::setDisableCompetingOnForceDefault,
                            onForceDefault = viewModel::forceSetDefaultApp
                        )

                        AppTab.PRIVILEGE -> PrivilegeScreen(
                            status = privilegeStatus,
                            prefs = userPrefs,
                            logs = commandLogs,
                            onSelectMode = viewModel::selectPrivilegeMode,
                            onConnectOrRequest = viewModel::connectOrRequestPrivilege,
                            onAutoBackupChange = viewModel::setAutoBackup,
                            onThemeChange = viewModel::setThemeSetting,
                            onRunShell = viewModel::runCustomShell,
                            onClearLogs = viewModel.privilegeManager::clearLogs,
                            onMessage = viewModel::postMessage
                        )
                    }
                }
            }
        }
    }

    detailApp?.let { app ->
        AppDetailBottomSheet(
            app = app,
            iconProvider = viewModel::getAppIcon,
            disableCompetingOnDefault = userPrefs.disableCompetingOnForceDefault,
            onDismiss = { viewModel.openAppDetail(null) },
            onUninstall = { target -> viewModel.uninstallApps(listOf(target)) },
            onRestoreSystem = viewModel::restoreSystemApp,
            onToggleHide = { target, hide -> viewModel.setAppsHidden(listOf(target), hide) },
            onForceDefault = viewModel::forceSetDefaultApp,
            onForceStop = viewModel::forceStopApp,
            onClearData = viewModel::clearAppData,
            onTogglePermission = viewModel::togglePermission,
            onSetAppOp = viewModel::setAppOp,
            onCopiedPackage = viewModel::postMessage
        )
    }
}

@Composable
private fun TabIconWithBadge(
    tab: AppTab,
    selected: Boolean,
    badgeCount: Int
) {
    val iconVector = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(badgeCount.toString()) }
            }
        ) {
            Icon(iconVector, contentDescription = tab.label)
        }
    } else {
        Icon(iconVector, contentDescription = tab.label)
    }
}
