package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.manager.AppItem
import com.example.manager.AppRoleType

private val SPECIAL_APP_OPS = listOf(
    "WRITE_SETTINGS" to "Sửa cài đặt hệ thống",
    "SYSTEM_ALERT_WINDOW" to "Hiển thị trên ứng dụng khác",
    "REQUEST_INSTALL_PACKAGES" to "Cài đặt gói APK",
    "GET_USAGE_STATS" to "Truy cập dữ liệu sử dụng",
    "RUN_IN_BACKGROUND" to "Chạy nền không giới hạn"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppDetailBottomSheet(
    app: AppItem,
    iconProvider: (String) -> Drawable?,
    disableCompetingOnDefault: Boolean,
    onDismiss: () -> Unit,
    onUninstall: (AppItem) -> Unit,
    onRestoreSystem: (AppItem) -> Unit,
    onToggleHide: (AppItem, Boolean) -> Unit,
    onForceDefault: (AppItem, AppRoleType, Boolean) -> Unit,
    onForceStop: (String) -> Unit,
    onClearData: (String) -> Unit,
    onTogglePermission: (String, String, Boolean) -> Unit,
    onSetAppOp: (String, String, Boolean) -> Unit,
    onCopiedPackage: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showUninstallConfirm by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppIconThumbnail(
                    packageName = app.packageName,
                    appName = app.appName,
                    iconProvider = iconProvider,
                    size = 56.dp
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("package", app.packageName))
                                onCopiedPackage("Đã sao chép ${app.packageName}")
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Sao chép tên gói",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "v${app.versionName} · ${formatBytes(app.apkSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val launchIntent = remember(app.packageName, app.isInstalledForUser, app.isEnabled) {
                    if (app.isInstalledForUser && app.isEnabled) {
                        context.packageManager.getLaunchIntentForPackage(app.packageName)
                    } else null
                }
                if (launchIntent != null) {
                    IconButton(
                        onClick = {
                            runCatching { context.startActivity(launchIntent) }
                                .onFailure { onCopiedPackage("Không thể mở ${app.appName}: ${it.message}") }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = "Mở ứng dụng ${app.appName}"
                        )
                    }
                }
                IconButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", app.packageName, null)
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure { onCopiedPackage("Không mở được trang Cài đặt ứng dụng") }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Mở thông tin ứng dụng trong Cài đặt"
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusBadge(
                    text = if (app.isSystemApp) "Ứng dụng hệ thống" else "Ứng dụng bên thứ 3",
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
                        text = "Đã gỡ khỏi User 0",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else if (app.isHiddenOrDisabled) {
                    StatusBadge(
                        text = "Đang ẩn / Vô hiệu hóa",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                if (app.isProtectedSettings) {
                    StatusBadge(
                        text = "Cài đặt hệ thống · Được bảo vệ",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            if (app.isProtectedSettings) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Được bảo vệ",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Ứng dụng Cài đặt (Settings) được khóa bảo vệ để tránh mất quyền điều khiển thiết bị. Không thể ẩn hoặc xóa.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Primary Action Grid
            Text(
                text = "Điều khiển ứng dụng",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))

            if (!app.isInstalledForUser && app.isSystemApp) {
                Button(
                    onClick = {
                        onRestoreSystem(app)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_restore_button")
                ) {
                    Icon(Icons.Rounded.Restore, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Khôi phục ứng dụng hệ thống")
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            onToggleHide(app, !app.isHiddenOrDisabled)
                            onDismiss()
                        },
                        enabled = !app.isProtectedSettings,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_hide_button")
                    ) {
                        Icon(
                            imageVector = if (app.isHiddenOrDisabled) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (app.isHiddenOrDisabled) "Hiện lại" else "Ẩn app")
                    }

                    Button(
                        onClick = { showUninstallConfirm = true },
                        enabled = !app.isProtectedSettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_uninstall_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Xóa hẳn")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { onForceStop(app.packageName) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Rounded.StopCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Buộc dừng")
                    }

                    OutlinedButton(
                        onClick = { showClearDataConfirm = true },
                        enabled = !app.isProtectedSettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Rounded.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Xóa dữ liệu")
                    }
                }
            }

            // Force Set Default Roles
            if (app.isInstalledForUser && app.supportedRoles.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Ép làm ứng dụng mặc định",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Ép hệ thống ghi nhận ${app.appName} làm ứng dụng mặc định cho các vai trò hỗ trợ:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    app.supportedRoles.forEach { role ->
                        val isCurrent = app.currentDefaultRoles.contains(role)
                        AssistChip(
                            onClick = {
                                onForceDefault(app, role, disableCompetingOnDefault)
                            },
                            label = {
                                Text(
                                    text = if (isCurrent) "${role.titleVi} (Đang mặc định)" else "Ép: ${role.titleVi}",
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            // Special AppOps & Runtime Permissions
            if (app.isInstalledForUser) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Cấp quyền đặc biệt (AppOps)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))

                SPECIAL_APP_OPS.forEach { (op, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                op,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onSetAppOp(app.packageName, op, false) }
                            ) {
                                Text("Chặn", style = MaterialTheme.typography.labelMedium)
                            }
                            FilledTonalButton(
                                onClick = { onSetAppOp(app.packageName, op, true) }
                            ) {
                                Text("Cấp", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                val grantablePerms = app.requestedPermissions.filter {
                    it.startsWith("android.permission.")
                }.take(20)

                if (grantablePerms.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Quyền ứng dụng yêu cầu (${grantablePerms.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    grantablePerms.forEach { perm ->
                        val granted = app.grantedPermissions.contains(perm)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = perm.removePrefix("android.permission."),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = granted,
                                onCheckedChange = { wantGrant ->
                                    onTogglePermission(app.packageName, perm, wantGrant)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUninstallConfirm) {
        ConfirmActionDialog(
            title = "Xóa hoàn toàn ${app.appName}?",
            message = if (app.isSystemApp) {
                "Ứng dụng hệ thống này sẽ bị gỡ hoàn toàn khỏi tài khoản hiện tại (không chỉ gỡ bản cập nhật). Bạn luôn có thể khôi phục lại trong tab Khôi phục."
            } else {
                "Ứng dụng sẽ bị gỡ hoàn toàn. Nếu bật sao lưu tự động, AppForge sẽ lưu tệp APK để bạn có thể cài lại sau."
            },
            confirmLabel = "Xóa hoàn toàn",
            isDestructive = true,
            onConfirm = {
                showUninstallConfirm = false
                onUninstall(app)
                onDismiss()
            },
            onDismiss = { showUninstallConfirm = false }
        )
    }

    if (showClearDataConfirm) {
        ConfirmActionDialog(
            title = "Xóa dữ liệu ${app.appName}?",
            message = "Toàn bộ bộ nhớ đệm, tài khoản đăng nhập và dữ liệu của ứng dụng này sẽ bị xóa sạch.",
            confirmLabel = "Xóa dữ liệu",
            isDestructive = true,
            onConfirm = {
                showClearDataConfirm = false
                onClearData(app.packageName)
            },
            onDismiss = { showClearDataConfirm = false }
        )
    }
}
