package com.example.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.manager.AppFilterTab
import com.example.manager.AppItem
import com.example.manager.AppSortOrder
import com.example.ui.MainUiState

@Composable
fun AppsScreen(
    uiState: MainUiState,
    allApps: List<AppItem>,
    filteredApps: List<AppItem>,
    iconProvider: (String) -> Drawable?,
    onSearchChange: (String) -> Unit,
    onFilterSelect: (AppFilterTab) -> Unit,
    onSortChange: (AppSortOrder) -> Unit,
    onRefresh: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenDetail: (AppItem) -> Unit,
    onUninstallApps: (List<AppItem>) -> Unit,
    onSetAppsHidden: (List<AppItem>, Boolean) -> Unit,
    onRestoreSystemApp: (AppItem) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var pendingBatchDelete by remember { mutableStateOf<List<AppItem>?>(null) }

    val counts = remember(allApps) {
        mapOf(
            AppFilterTab.ALL to allApps.count { it.isInstalledForUser },
            AppFilterTab.SYSTEM to allApps.count { it.isInstalledForUser && it.isSystemApp },
            AppFilterTab.THIRD_PARTY to allApps.count { it.isInstalledForUser && !it.isSystemApp },
            AppFilterTab.HIDDEN to allApps.count { it.isHiddenOrDisabled },
            AppFilterTab.UNINSTALLED to allApps.count { !it.isInstalledForUser }
        )
    }

    val selectedItems = remember(allApps, uiState.selectedPackages) {
        allApps.filter { uiState.selectedPackages.contains(it.packageName) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
        ) {
            // Full-width Search Bar (clean, never cramped on 360dp phones)
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Tìm tên ứng dụng hoặc tên gói...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = "Tìm kiếm")
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Xóa từ khóa")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("apps_search_input")
            )

            // Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                AppFilterTab.entries.forEach { tab ->
                    val count = counts[tab] ?: 0
                    FilterChip(
                        selected = uiState.activeFilter == tab,
                        onClick = { onFilterSelect(tab) },
                        label = { Text("${tab.labelVi} ($count)") },
                        modifier = Modifier.testTag("filter_chip_${tab.name.lowercase()}")
                    )
                }
            }

            // Compact Toolbar Row (Result count + Sort + Multi-select + Refresh)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${filteredApps.size} ứng dụng",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        AssistChip(
                            onClick = { showSortMenu = true },
                            label = { Text(uiState.sortOrder.labelVi, maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Sort,
                                    contentDescription = "Sắp xếp danh sách",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("sort_button")
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            AppSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = order.labelVi,
                                            fontWeight = if (uiState.sortOrder == order) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onSortChange(order)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    AssistChip(
                        onClick = {
                            if (uiState.isSelectionMode) onClearSelection() else onSelectAll()
                        },
                        label = { Text(if (uiState.isSelectionMode) "Xong" else "Chọn") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (uiState.isSelectionMode) Icons.Rounded.Close else Icons.Rounded.Checklist,
                                contentDescription = if (uiState.isSelectionMode) "Thoát chọn nhiều" else "Chọn nhiều ứng dụng",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("toggle_multiselect_button")
                    )

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("refresh_apps_button")
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Làm mới danh sách",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (uiState.isExecutingBatch) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = uiState.batchProgressText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            if (uiState.isLoadingApps && filteredApps.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Đang quét toàn bộ ứng dụng hệ thống & bên thứ 3...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Không có ứng dụng nào trong mục này",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Thử đổi bộ lọc hoặc xóa từ khóa tìm kiếm.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = if (uiState.isSelectionMode) 96.dp else 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName }
                    ) { app ->
                        AppRowCard(
                            app = app,
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = uiState.selectedPackages.contains(app.packageName),
                            iconProvider = iconProvider,
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    if (!app.isProtectedSettings) onToggleSelect(app.packageName)
                                } else {
                                    onOpenDetail(app)
                                }
                            },
                            onLongClick = {
                                if (!app.isProtectedSettings) {
                                    onToggleSelect(app.packageName)
                                }
                            },
                            onQuickHideToggle = {
                                onSetAppsHidden(listOf(app), !app.isHiddenOrDisabled)
                            },
                            onQuickDelete = {
                                pendingBatchDelete = listOf(app)
                            },
                            onQuickRestore = {
                                onRestoreSystemApp(app)
                            }
                        )
                    }
                }
            }
        }

        // Batch Action Floating Bar
        AnimatedVisibility(
            visible = uiState.isSelectionMode && selectedItems.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = "Chọn tất cả")
                        }
                        Text(
                            text = "Đã chọn ${selectedItems.size}",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalIconButton(
                            onClick = { onSetAppsHidden(selectedItems, true) },
                            modifier = Modifier.testTag("batch_hide_button")
                        ) {
                            Icon(Icons.Rounded.VisibilityOff, contentDescription = "Ẩn các ứng dụng đã chọn")
                        }
                        FilledTonalIconButton(
                            onClick = { onSetAppsHidden(selectedItems, false) },
                            modifier = Modifier.testTag("batch_unhide_button")
                        ) {
                            Icon(Icons.Rounded.Visibility, contentDescription = "Hiện các ứng dụng đã chọn")
                        }
                        Button(
                            onClick = { pendingBatchDelete = selectedItems },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.testTag("batch_delete_button")
                        ) {
                            Icon(
                                Icons.Rounded.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Xóa")
                        }
                    }
                }
            }
        }
    }

    pendingBatchDelete?.let { targets ->
        val hasCore = targets.any { it.isCriticalCore }
        val namesPreview = targets.take(4).joinToString(", ") { it.appName } +
            if (targets.size > 4) " và ${targets.size - 4} app khác" else ""
        ConfirmActionDialog(
            title = if (targets.size == 1) "Xóa hoàn toàn ${targets.first().appName}?" else "Xóa hoàn toàn ${targets.size} ứng dụng?",
            message = buildString {
                append("Các ứng dụng sau sẽ bị gỡ hoàn toàn khỏi User 0, biến mất khỏi màn hình chính và không thể tự chạy lại: $namesPreview.")
                if (hasCore) {
                    append("\n\nCảnh báo: Có gói hệ thống cốt lõi trong danh sách chọn. Bạn có thể khôi phục trong tab Khôi phục hoặc qua ADB nếu cần.")
                }
            },
            confirmLabel = "Xóa hoàn toàn",
            isDestructive = true,
            onConfirm = {
                pendingBatchDelete = null
                onUninstallApps(targets)
            },
            onDismiss = { pendingBatchDelete = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AppRowCard(
    app: AppItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    iconProvider: (String) -> Drawable?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickHideToggle: () -> Unit,
    onQuickDelete: () -> Unit,
    onQuickRestore: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("app_card_${app.packageName}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    enabled = !app.isProtectedSettings,
                    onCheckedChange = { onClick() }
                )
                Spacer(Modifier.width(4.dp))
            }

            AppIconThumbnail(
                packageName = app.packageName,
                appName = app.appName,
                iconProvider = iconProvider,
                alpha = if (app.isHiddenOrDisabled || !app.isInstalledForUser) 0.48f else 1f
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusBadge(
                        text = if (app.isSystemApp) "Hệ thống" else "Bên thứ 3",
                        containerColor = if (app.isSystemApp) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = if (app.isSystemApp) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )

                    if (!app.isInstalledForUser) {
                        StatusBadge(
                            text = "Đã gỡ",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else if (app.isHiddenOrDisabled) {
                        StatusBadge(
                            text = "Đang ẩn",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    if (app.isProtectedSettings) {
                        StatusBadge(
                            text = "Bảo vệ",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else if (app.currentDefaultRoles.isNotEmpty()) {
                        StatusBadge(
                            text = "Mặc định",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        text = formatBytes(app.apkSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }

            if (!isSelectionMode) {
                Spacer(Modifier.width(4.dp))
                when {
                    app.isProtectedSettings -> {
                        IconButton(onClick = onClick) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Cài đặt được bảo vệ",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    !app.isInstalledForUser && app.isSystemApp -> {
                        IconButton(
                            onClick = onQuickRestore,
                            modifier = Modifier.testTag("restore_btn_${app.packageName}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Restore,
                                contentDescription = "Khôi phục ${app.appName}",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    app.isInstalledForUser -> {
                        IconButton(
                            onClick = onQuickHideToggle,
                            modifier = Modifier.testTag("hide_btn_${app.packageName}")
                        ) {
                            Icon(
                                imageVector = if (app.isHiddenOrDisabled) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = if (app.isHiddenOrDisabled) "Hiện lại ${app.appName}" else "Ẩn ${app.appName}",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        IconButton(
                            onClick = onQuickDelete,
                            modifier = Modifier.testTag("delete_btn_${app.packageName}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteForever,
                                contentDescription = "Xóa hoàn toàn ${app.appName}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
