package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ThemeSetting
import com.example.data.UserPrefs
import com.example.privilege.CommandLogItem
import com.example.privilege.PrivilegeMode
import com.example.privilege.PrivilegeStatus

@Composable
fun PrivilegeScreen(
    status: PrivilegeStatus,
    prefs: UserPrefs,
    logs: List<CommandLogItem>,
    onSelectMode: (PrivilegeMode) -> Unit,
    onConnectOrRequest: () -> Unit,
    onAutoBackupChange: (Boolean) -> Unit,
    onThemeChange: (ThemeSetting) -> Unit,
    onRunShell: (String) -> Unit,
    onClearLogs: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var shellInput by remember { mutableStateOf("") }
    var showShizukuHelpDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (status.isReady) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = status.uidLabel,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = status.detailMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onConnectOrRequest,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("grant_privilege_button")
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    when (status.activeMode) {
                                        PrivilegeMode.SHIZUKU -> if (status.hasShizukuPermission) "Kết nối lại Shizuku" else "Xin quyền Shizuku"
                                        PrivilegeMode.ROOT -> "Xin quyền Root (su)"
                                        PrivilegeMode.ADB_TCP -> "Kết nối ADB TCP"
                                    }
                                )
                            }

                            if (status.activeMode == PrivilegeMode.SHIZUKU) {
                                OutlinedButton(
                                    onClick = {
                                        val launch = context.packageManager
                                            .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                        if (launch != null) {
                                            runCatching { context.startActivity(launch) }
                                                .onFailure { onMessage("Không mở được Shizuku: ${it.message}") }
                                        } else {
                                            val playIntent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://shizuku.rikka.app/")
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            val opened = runCatching {
                                                if (playIntent.resolveActivity(context.packageManager) != null) {
                                                    context.startActivity(playIntent)
                                                    true
                                                } else {
                                                    false
                                                }
                                            }.getOrDefault(false)
                                            if (!opened) {
                                                showShizukuHelpDialog = true
                                                onMessage("Thiết bị chưa có trình duyệt khả dụng — xem hướng dẫn cài Shizuku")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (status.isShizukuInstalled) "Mở Shizuku" else "Tải Shizuku")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Chọn phương thức đặc quyền (hoạt động như nhau)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrivilegeModeCard(
                        mode = PrivilegeMode.SHIZUKU,
                        selected = status.activeMode == PrivilegeMode.SHIZUKU,
                        badgeText = when {
                            status.isShizukuRunning && status.hasShizukuPermission -> "Đã cấp quyền"
                            status.isShizukuRunning -> "Đang chạy · Cần cấp quyền"
                            status.isShizukuInstalled -> "Chưa bật dịch vụ"
                            else -> "Chưa cài"
                        },
                        description = "Chạy lệnh ADB/Root trực tiếp trên điện thoại thông qua Shizuku. Khuyên dùng cho máy chưa Root.",
                        icon = Icons.Rounded.Security,
                        onSelect = { onSelectMode(PrivilegeMode.SHIZUKU) }
                    )

                    PrivilegeModeCard(
                        mode = PrivilegeMode.ROOT,
                        selected = status.activeMode == PrivilegeMode.ROOT,
                        badgeText = if (status.isRootAvailable) "Sẵn sàng (UID 0)" else "Không phát hiện su",
                        description = "Thực thi trực tiếp qua nhị phân Superuser (Magisk / KernelSU / APatch).",
                        icon = Icons.Rounded.AdminPanelSettings,
                        onSelect = { onSelectMode(PrivilegeMode.ROOT) }
                    )

                    PrivilegeModeCard(
                        mode = PrivilegeMode.ADB_TCP,
                        selected = status.activeMode == PrivilegeMode.ADB_TCP,
                        badgeText = if (status.isAdbTcpConnected) "Đã kết nối :5555" else "Chưa bật :5555",
                        description = "Kết nối trực tiếp tới ADB TCP (127.0.0.1:5555). Bật bằng lệnh \"adb tcpip 5555\" từ máy tính.",
                        icon = Icons.Rounded.Usb,
                        onSelect = { onSelectMode(PrivilegeMode.ADB_TCP) }
                    )
                }
            }

            item {
                val adbGrantCmd = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS && adb tcpip 5555"
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lệnh ADB kích hoạt nhanh từ PC",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = adbGrantCmd,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("adb", adbGrantCmd))
                                onMessage("Đã sao chép lệnh ADB")
                            }
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Sao chép lệnh ADB")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Tùy chọn vận hành & Giao diện",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tự sao lưu APK ứng dụng bên thứ 3",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Lưu lại tệp APK trước khi xóa hoàn toàn để có thể khôi phục sau",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = prefs.autoBackupUserApks,
                                onCheckedChange = onAutoBackupChange
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("Giao diện hiển thị", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf(
                                ThemeSetting.SYSTEM to "Hệ thống",
                                ThemeSetting.DARK to "Tối",
                                ThemeSetting.LIGHT to "Sáng"
                            ).forEachIndexed { idx, (setting, label) ->
                                SegmentedButton(
                                    selected = prefs.themeSetting == setting,
                                    onClick = { onThemeChange(setting) },
                                    shape = SegmentedButtonDefaults.itemShape(idx, 3)
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Bảng lệnh & Nhật ký (${logs.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (logs.isNotEmpty()) {
                        FilledTonalButton(onClick = onClearLogs) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Xóa log")
                        }
                    }
                }
            }

            item {
                val presets = listOf(
                    "App đang tắt" to "pm list packages -d",
                    "App hệ thống" to "pm list packages -s",
                    "Trình duyệt mặc định" to "cmd role get-role-holders android.app.role.BROWSER",
                    "Launcher mặc định" to "cmd role get-role-holders android.app.role.HOME"
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        presets.forEach { (label, cmd) ->
                            AssistChip(
                                onClick = { shellInput = cmd },
                                label = { Text(label) }
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = shellInput,
                            onValueChange = { shellInput = it },
                            placeholder = { Text("Chạy lệnh shell (vd: pm list packages -d)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("shell_command_input")
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onRunShell(shellInput)
                                shellInput = ""
                            },
                            modifier = Modifier.testTag("shell_run_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Chạy lệnh")
                        }
                    }
                }
            }

            items(logs, key = { it.id }) { log ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "[${log.mode.shortLabel}] ${formatTimestamp(log.timestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            StatusBadge(
                                text = if (log.exitCode == 0) "EXIT 0" else "EXIT ${log.exitCode}",
                                containerColor = if (log.exitCode == 0) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                },
                                contentColor = if (log.exitCode == 0) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$ ${log.command}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = log.output,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showShizukuHelpDialog) {
        val url = "https://github.com/RikkaApps/Shizuku/releases"
        AlertDialog(
            onDismissRequest = { showShizukuHelpDialog = false },
            title = { Text("Tải & Cài đặt Shizuku") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Không mở được trình duyệt trên thiết bị/máy ảo này. Bạn có thể tải APK Shizuku từ máy tính hoặc điện thoại khác rồi cài đặt:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = url,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "1. Cài Shizuku APK lên máy thật.\n2. Bật Tùy chọn nhà phát triển → Gỡ lỗi không dây.\n3. Ghép nối & Khởi động Shizuku, sau đó bấm \"Xin quyền Shizuku\" trong AppForge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("shizuku_url", url))
                        onMessage("Đã sao chép liên kết tải Shizuku")
                        showShizukuHelpDialog = false
                    }
                ) {
                    Text("Sao chép link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShizukuHelpDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }
}

@Composable
private fun PrivilegeModeCard(
    mode: PrivilegeMode,
    selected: Boolean,
    badgeText: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSelect: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .testTag("mode_card_${mode.name.lowercase()}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(mode.displayName, style = MaterialTheme.typography.titleSmall)
                    if (selected) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Đang chọn",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
