package com.example.privilege

enum class PrivilegeMode(val displayName: String, val shortLabel: String) {
    SHIZUKU("Shizuku (ADB / Root)", "Shizuku"),
    ROOT("Root (su)", "Root"),
    ADB_TCP("ADB Cục bộ (TCP 5555)", "ADB")
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    PERMISSION_REQUIRED,
    CONNECTED,
    UNAVAILABLE
}

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val command: String = ""
) {
    val isSuccess: Boolean get() = exitCode == 0
    val combinedOutput: String
        get() = listOf(stdout.trim(), stderr.trim())
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .ifEmpty { if (isSuccess) "Thành công (exit 0)" else "Lỗi (exit $exitCode)" }
}

data class PrivilegeStatus(
    val activeMode: PrivilegeMode = PrivilegeMode.SHIZUKU,
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val uidLabel: String = "Chưa kết nối",
    val detailMessage: String = "Chưa có quyền đặc biệt",
    val isShizukuInstalled: Boolean = false,
    val isShizukuRunning: Boolean = false,
    val hasShizukuPermission: Boolean = false,
    val isShizukuServiceBound: Boolean = false,
    val isRootAvailable: Boolean = false,
    val isAdbTcpConnected: Boolean = false
) {
    val isReady: Boolean get() = state == ConnectionState.CONNECTED
}

data class CommandLogItem(
    val id: Long,
    val timestamp: Long,
    val mode: PrivilegeMode,
    val command: String,
    val exitCode: Int,
    val output: String
)
