package com.example.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.manager.AppItem
import com.example.manager.AppRoleType
import com.example.ui.MainUiState

@Composable
fun ForceDefaultScreen(
    uiState: MainUiState,
    allApps: List<AppItem>,
    disableCompeting: Boolean,
    iconProvider: (String) -> Drawable?,
    onToggleDisableCompeting: (Boolean) -> Unit,
    onForceDefault: (AppItem, AppRoleType, Boolean) -> Unit
) {
    var selectedRoleForPicker by remember { mutableStateOf<AppRoleType?>(null) }

    val installedApps = remember(allApps) {
        allApps.filter { it.isInstalledForUser }
    }

    val roleGroups = remember(installedApps) {
        listOf(
            AppRoleType.HOME,
            AppRoleType.BROWSER,
            AppRoleType.DIALER,
            AppRoleType.SMS,
            AppRoleType.ASSISTANT,
            AppRoleType.IME,
            AppRoleType.CALL_SCREENING
        ).map { role ->
            val candidates = installedApps.filter { it.supportedRoles.contains(role) }
                .sortedWith(
                    compareByDescending<AppItem> { it.currentDefaultRoles.contains(role) }
                        .thenBy { it.isSystemApp }
                        .thenBy { it.appName.lowercase() }
                )
            val currentDefault = candidates.firstOrNull { it.currentDefaultRoles.contains(role) }
            Triple(role, currentDefault, candidates)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
        ) {
            if (uiState.isExecutingBatch) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = uiState.batchProgressText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            if (uiState.isLoadingApps && allApps.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Ép ứng dụng bên thứ 3 làm mặc định hệ thống",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Sử dụng Role Manager & Package Manager đặc quyền (bỏ qua giới hạn kiểm tra của ROM) để ép trình duyệt, launcher, bàn phím, gọi điện hoặc mở liên kết mặc định.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Tắt app mặc định cũ của hãng",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Hữu ích trên MIUI/HyperOS, ColorOS nếu máy tự trả về app gốc",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = disableCompeting,
                                    onCheckedChange = onToggleDisableCompeting,
                                    modifier = Modifier.testTag("disable_competing_switch")
                                )
                            }
                        }
                    }
                }

                items(roleGroups, key = { it.first.name }) { (role, currentDefault, candidates) ->
                    RoleCategoryCard(
                        role = role,
                        currentDefault = currentDefault,
                        candidates = candidates,
                        iconProvider = iconProvider,
                        disableCompeting = disableCompeting,
                        onForceDefault = onForceDefault,
                        onForceAnyApp = { selectedRoleForPicker = role }
                    )
                }

                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = AppRoleType.APP_LINKS.titleVi,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = AppRoleType.APP_LINKS.subtitleVi,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { selectedRoleForPicker = AppRoleType.APP_LINKS },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pick_applinks_app_button")
                            ) {
                                Text("Chọn ứng dụng bất kỳ để ép mở liên kết")
                            }
                        }
                    }
                }
            }
            }
        }
    }

    selectedRoleForPicker?.let { targetRole ->
        AnyAppPickerDialog(
            role = targetRole,
            apps = installedApps.filter { !it.isSystemApp }.ifEmpty { installedApps },
            allAppsFallback = installedApps,
            iconProvider = iconProvider,
            onSelect = { chosen ->
                selectedRoleForPicker = null
                onForceDefault(chosen, targetRole, disableCompeting)
            },
            onDismiss = { selectedRoleForPicker = null }
        )
    }
}

@Composable
private fun RoleCategoryCard(
    role: AppRoleType,
    currentDefault: AppItem?,
    candidates: List<AppItem>,
    iconProvider: (String) -> Drawable?,
    disableCompeting: Boolean,
    onForceDefault: (AppItem, AppRoleType, Boolean) -> Unit,
    onForceAnyApp: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("role_card_${role.name.lowercase()}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = roleIcon(role),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(role.titleVi, style = MaterialTheme.typography.titleMedium)
                    Text(
                        role.subtitleVi,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (currentDefault != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Mặc định hiện tại: ${currentDefault.appName}",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (candidates.isNotEmpty()) {
                var expanded by remember(role) { mutableStateOf(false) }
                val visibleCandidates = if (expanded) candidates else candidates.take(4)

                Spacer(Modifier.height(10.dp))
                visibleCandidates.forEach { candidate ->
                    val isCurrent = candidate.currentDefaultRoles.contains(role)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    ) {
                        AppIconThumbnail(
                            packageName = candidate.packageName,
                            appName = candidate.appName,
                            iconProvider = iconProvider,
                            size = 36.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = candidate.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (candidate.isSystemApp) "Hệ thống" else "Bên thứ 3",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (candidate.isSystemApp) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        if (isCurrent) {
                            StatusBadge(
                                text = "Đang dùng",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            FilledTonalButton(
                                onClick = { onForceDefault(candidate, role, disableCompeting) },
                                modifier = Modifier.testTag("force_btn_${role.name}_${candidate.packageName}")
                            ) {
                                Text("Ép mặc định")
                            }
                        }
                    }
                }
                if (candidates.size > 4) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            if (expanded) "Thu gọn" else "Xem thêm ${candidates.size - 4} ứng dụng"
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onForceAnyApp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ép ứng dụng bất kỳ vào vai trò này")
            }
        }
    }
}

@Composable
private fun AnyAppPickerDialog(
    role: AppRoleType,
    apps: List<AppItem>,
    allAppsFallback: List<AppItem>,
    iconProvider: (String) -> Drawable?,
    onSelect: (AppItem) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var includeSystem by remember { mutableStateOf(false) }
    val source = if (includeSystem) allAppsFallback else apps
    val list = remember(source, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) source
        else source.filter {
            it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Chọn app cho ${role.titleVi}", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Tìm ứng dụng...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Hiện cả app hệ thống", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = includeSystem, onCheckedChange = { includeSystem = it })
                }
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    items(list, key = { it.packageName }) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            AppIconThumbnail(
                                packageName = item.packageName,
                                appName = item.appName,
                                iconProvider = iconProvider,
                                size = 34.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.appName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    item.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        }
    )
}

private fun roleIcon(role: AppRoleType): ImageVector = when (role) {
    AppRoleType.HOME -> Icons.Rounded.Home
    AppRoleType.BROWSER -> Icons.Rounded.Language
    AppRoleType.DIALER -> Icons.Rounded.Call
    AppRoleType.SMS -> Icons.Rounded.Sms
    AppRoleType.ASSISTANT -> Icons.Rounded.RecordVoiceOver
    AppRoleType.CALL_SCREENING -> Icons.Rounded.Shield
    AppRoleType.IME -> Icons.Rounded.Keyboard
    AppRoleType.APP_LINKS -> Icons.Rounded.Link
}
