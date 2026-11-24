package com.example.ui

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.RemovedAppEntity
import com.example.data.ThemeSetting
import com.example.data.UserPreferencesRepository
import com.example.data.UserPrefs
import com.example.manager.AppFilterTab
import com.example.manager.AppItem
import com.example.manager.AppRepository
import com.example.manager.AppRoleType
import com.example.manager.AppSortOrder
import com.example.manager.RestorableItem
import com.example.privilege.CommandLogItem
import com.example.privilege.PrivilegeManager
import com.example.privilege.PrivilegeMode
import com.example.privilege.PrivilegeStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class MainUiState(
    val isLoadingApps: Boolean = true,
    val isExecutingBatch: Boolean = false,
    val batchProgressText: String = "",
    val searchQuery: String = "",
    val activeFilter: AppFilterTab = AppFilterTab.ALL,
    val sortOrder: AppSortOrder = AppSortOrder.NAME_ASC,
    val selectedPackages: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val detailAppPackage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val removedDao = db.removedAppDao()
    private val prefsRepo = UserPreferencesRepository(application)
    val privilegeManager = PrivilegeManager(application)
    private val appRepo = AppRepository(application, privilegeManager, removedDao)

    private val iconCache = ConcurrentHashMap<String, Drawable>()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppItem>>(emptyList())
    val allApps: StateFlow<List<AppItem>> = _allApps.asStateFlow()

    val privilegeStatus: StateFlow<PrivilegeStatus> = privilegeManager.status
    val commandLogs: StateFlow<List<CommandLogItem>> = privilegeManager.commandLogs

    val userPrefs: StateFlow<UserPrefs> = prefsRepo.prefs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UserPrefs()
    )

    private val removedEntities: StateFlow<List<RemovedAppEntity>> = removedDao.observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val filteredApps: StateFlow<List<AppItem>> = combine(
        _allApps,
        _uiState
    ) { apps, state ->
        val query = state.searchQuery.trim().lowercase()
        val filtered = apps.filter { app ->
            val matchesTab = when (state.activeFilter) {
                AppFilterTab.ALL -> app.isInstalledForUser
                AppFilterTab.SYSTEM -> app.isInstalledForUser && app.isSystemApp
                AppFilterTab.THIRD_PARTY -> app.isInstalledForUser && !app.isSystemApp
                AppFilterTab.HIDDEN -> app.isHiddenOrDisabled
                AppFilterTab.UNINSTALLED -> !app.isInstalledForUser
            }
            val matchesQuery = query.isEmpty() ||
                app.appName.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query)
            matchesTab && matchesQuery
        }
        when (state.sortOrder) {
            AppSortOrder.NAME_ASC -> filtered.sortedBy { it.appName.lowercase() }
            AppSortOrder.SIZE_DESC -> filtered.sortedByDescending { it.apkSizeBytes }
            AppSortOrder.RECENT_INSTALL -> filtered.sortedByDescending { it.firstInstallTime }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val restorableItems: StateFlow<List<RestorableItem>> = combine(
        _allApps,
        removedEntities
    ) { apps, entities ->
        val entityMap = entities.associateBy { it.packageName }
        val merged = linkedMapOf<String, RestorableItem>()

        // 1. Everything tracked in Room DB (uninstalled user apps with APK backups + uninstalled system apps)
        entities.forEach { entity ->
            val liveApp = apps.find { it.packageName == entity.packageName }
            val stillInstalled = liveApp?.isInstalledForUser == true
            if (!stillInstalled) {
                merged[entity.packageName] = RestorableItem(
                    packageName = entity.packageName,
                    appName = entity.appName,
                    isSystemApp = entity.isSystemApp,
                    versionName = entity.versionName,
                    removedAt = entity.removedAt,
                    hasLocalApkBackup = !entity.backupApkDir.isNullOrBlank(),
                    backupApkDir = entity.backupApkDir,
                    totalSizeBytes = entity.totalSizeBytes,
                    isCurrentlyInstalled = false
                )
            }
        }

        // 2. Any system app uninstalled for user 0 (even prior to installing AppForge)
        apps.filter { it.canBeRestoredFromSystem }.forEach { app ->
            if (!merged.containsKey(app.packageName)) {
                val tracked = entityMap[app.packageName]
                merged[app.packageName] = RestorableItem(
                    packageName = app.packageName,
                    appName = app.appName,
                    isSystemApp = true,
                    versionName = app.versionName,
                    removedAt = tracked?.removedAt ?: 0L,
                    hasLocalApkBackup = false,
                    backupApkDir = null,
                    totalSizeBytes = app.apkSizeBytes,
                    isCurrentlyInstalled = false
                )
            }
        }

        merged.values.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val detailApp: StateFlow<AppItem?> = combine(_allApps, _uiState) { apps, state ->
        state.detailAppPackage?.let { pkg -> apps.find { it.packageName == pkg } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            launch {
                privilegeManager.permissionEvents.collect { msg ->
                    _events.tryEmit(msg)
                }
            }
            val savedPrefs = prefsRepo.prefs.first()
            privilegeManager.setModeInitial(savedPrefs.preferredMode)
            val status = privilegeManager.refreshStatus()
            if (!status.isReady) {
                privilegeManager.autoDetectAndConnect()
            }
            refreshApps()
        }
    }

    fun onAppResumed() {
        viewModelScope.launch {
            privilegeManager.refreshStatus()
        }
    }

    override fun onCleared() {
        super.onCleared()
        privilegeManager.release()
    }

    fun getAppIcon(packageName: String): Drawable? {
        return iconCache[packageName] ?: appRepo.loadAppIcon(packageName)?.also {
            iconCache[packageName] = it
        }
    }

    fun postMessage(message: String) {
        _events.tryEmit(message)
    }

    fun refreshApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val loaded = appRepo.loadAllApps()
            _allApps.value = loaded
            _uiState.update { it.copy(isLoadingApps = false) }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setActiveFilter(tab: AppFilterTab) {
        _uiState.update { it.copy(activeFilter = tab) }
    }

    fun setSortOrder(order: AppSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun toggleSelection(packageName: String) {
        _uiState.update { state ->
            val next = if (state.selectedPackages.contains(packageName)) {
                state.selectedPackages - packageName
            } else {
                state.selectedPackages + packageName
            }
            state.copy(
                selectedPackages = next,
                isSelectionMode = next.isNotEmpty()
            )
        }
    }

    fun selectAllFiltered() {
        val pkgs = filteredApps.value
            .filter { !it.isProtectedSettings && it.packageName != getApplication<Application>().packageName }
            .map { it.packageName }
            .toSet()
        _uiState.update { it.copy(selectedPackages = pkgs, isSelectionMode = pkgs.isNotEmpty()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPackages = emptySet(), isSelectionMode = false) }
    }

    fun openAppDetail(packageName: String?) {
        _uiState.update { it.copy(detailAppPackage = packageName) }
    }

    fun selectPrivilegeMode(mode: PrivilegeMode) {
        viewModelScope.launch {
            prefsRepo.setPreferredMode(mode)
            privilegeManager.setMode(mode)
        }
    }

    fun connectOrRequestPrivilege() {
        viewModelScope.launch {
            val current = privilegeManager.refreshStatus()
            when (current.activeMode) {
                PrivilegeMode.SHIZUKU -> {
                    when {
                        !current.isShizukuInstalled -> {
                            _events.tryEmit("Chưa cài ứng dụng Shizuku. Hãy cài Shizuku hoặc chọn Root / ADB.")
                        }
                        !current.isShizukuRunning -> {
                            _events.tryEmit("Dịch vụ Shizuku chưa chạy. Bấm \"Mở Shizuku\" để khởi động trước.")
                        }
                        else -> {
                            privilegeManager.requestShizukuPermission()
                        }
                    }
                }
                PrivilegeMode.ROOT, PrivilegeMode.ADB_TCP -> {
                    _events.tryEmit(current.detailMessage)
                }
            }
        }
    }

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoBackup(enabled) }
    }

    fun setDisableCompetingOnForceDefault(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDisableCompeting(enabled) }
    }

    fun setThemeSetting(theme: ThemeSetting) {
        viewModelScope.launch { prefsRepo.setTheme(theme) }
    }

    fun uninstallApps(apps: List<AppItem>) {
        if (apps.isEmpty()) return
        viewModelScope.launch {
            if (!ensurePrivilegeReady()) return@launch
            val backup = userPrefs.value.autoBackupUserApks
            var okCount = 0
            var failCount = 0
            var lastMsg = ""
            _uiState.update { it.copy(isExecutingBatch = true) }
            apps.forEachIndexed { idx, app ->
                _uiState.update {
                    it.copy(batchProgressText = "Đang gỡ (${idx + 1}/${apps.size}): ${app.appName}")
                }
                val res = appRepo.uninstallCompletely(app, backup)
                if (res.success) okCount++ else failCount++
                lastMsg = res.message
            }
            _uiState.update {
                it.copy(
                    isExecutingBatch = false,
                    batchProgressText = "",
                    selectedPackages = emptySet(),
                    isSelectionMode = false
                )
            }
            refreshApps()
            val summary = if (apps.size == 1) lastMsg else "Đã gỡ $okCount ứng dụng ($failCount lỗi)"
            _events.tryEmit(summary)
        }
    }

    fun setAppsHidden(apps: List<AppItem>, hide: Boolean) {
        if (apps.isEmpty()) return
        viewModelScope.launch {
            if (!ensurePrivilegeReady()) return@launch
            var okCount = 0
            var failCount = 0
            var lastMsg = ""
            _uiState.update { it.copy(isExecutingBatch = true) }
            apps.forEachIndexed { idx, app ->
                _uiState.update {
                    it.copy(
                        batchProgressText = "${if (hide) "Đang ẩn" else "Đang hiện"} (${idx + 1}/${apps.size}): ${app.appName}"
                    )
                }
                val res = appRepo.setAppHidden(app, hide)
                if (res.success) okCount++ else failCount++
                lastMsg = res.message
            }
            _uiState.update {
                it.copy(
                    isExecutingBatch = false,
                    batchProgressText = "",
                    selectedPackages = emptySet(),
                    isSelectionMode = false
                )
            }
            refreshApps()
            val summary = if (apps.size == 1) lastMsg else "${if (hide) "Đã ẩn" else "Đã hiện"} $okCount ứng dụng ($failCount lỗi)"
            _events.tryEmit(summary)
        }
    }

    fun restoreApp(item: RestorableItem) {
        viewModelScope.launch {
            if (!ensurePrivilegeReady()) return@launch
            _uiState.update {
                it.copy(isExecutingBatch = true, batchProgressText = "Đang khôi phục ${item.appName}...")
            }
            val res = appRepo.restoreApp(
                packageName = item.packageName,
                appName = item.appName,
                isSystemApp = item.isSystemApp,
                backupDirPath = item.backupApkDir
            )
            _uiState.update { it.copy(isExecutingBatch = false, batchProgressText = "") }
            refreshApps()
            _events.tryEmit(res.message)
        }
    }

    fun restoreSystemApp(app: AppItem) {
        viewModelScope.launch {
            if (!ensurePrivilegeReady()) return@launch
            _uiState.update {
                it.copy(isExecutingBatch = true, batchProgressText = "Đang khôi phục ${app.appName}...")
            }
            val res = appRepo.restoreApp(app.packageName, app.appName, app.isSystemApp, null)
            _uiState.update { it.copy(isExecutingBatch = false, batchProgressText = "") }
            refreshApps()
            _events.tryEmit(res.message)
        }
    }

    fun deleteBackup(packageName: String) {
        viewModelScope.launch {
            appRepo.deleteBackupDir(packageName)
            _events.tryEmit("Đã xóa bản sao lưu $packageName")
        }
    }

    fun forceSetDefaultApp(app: AppItem, roleType: AppRoleType, disableCompeting: Boolean) {
        viewModelScope.launch {
            if (!ensurePrivilegeReady()) return@launch
            _uiState.update {
                it.copy(
                    isExecutingBatch = true,
                    batchProgressText = "Đang ép ${app.appName} làm ${roleType.titleVi}..."
                )
            }
            val res = appRepo.forceSetDefaultApp(app, roleType, disableCompeting)
            _uiState.update { it.copy(isExecutingBatch = false, batchProgressText = "") }
            refreshApps()
            _events.tryEmit(res.message)
        }
    }

    fun togglePermission(packageName: String, permission: String, grant: Boolean) {
        viewModelScope.launch {
            if (!ensurePrivilegeReady()) return@launch
            val res = if (grant) {
                appRepo.grantPermission(packageName, permission)
            } else {
                appRepo.revokePermission(packageName, permission)
            }
            refreshApps()
            _events.tryEmit(res.message)
        }
    }

    fun setAppOp(packageName: String, op: String, allow: Boolean) {
        viewModelScope.launch {
            if (!ensurePrivilegeReady()) return@launch
            val res = appRepo.setAppOpAllow(packageName, op, allow)
            _events.tryEmit(res.message)
        }
    }

    fun forceStopApp(packageName: String) {
        viewModelScope.launch {
            if (!ensurePrivilegeReady()) return@launch
            val res = appRepo.forceStop(packageName)
            _events.tryEmit(res.message)
        }
    }

    fun clearAppData(packageName: String) {
        viewModelScope.launch {
            if (!ensurePrivilegeReady()) return@launch
            val res = appRepo.clearData(packageName)
            _events.tryEmit(res.message)
        }
    }

    fun runCustomShell(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch {
            if (!ensurePrivilegeReady()) return@launch
            val res = privilegeManager.execute(command.trim())
            _events.tryEmit(if (res.isSuccess) "Lệnh chạy thành công" else "Lệnh lỗi (exit ${res.exitCode})")
        }
    }

    private suspend fun ensurePrivilegeReady(): Boolean {
        val status = privilegeManager.refreshStatus()
        if (!status.isReady) {
            _events.tryEmit("Chưa kết nối quyền (${status.activeMode.shortLabel}). Vào tab Quyền để cấp quyền trước.")
            return false
        }
        return true
    }
}
