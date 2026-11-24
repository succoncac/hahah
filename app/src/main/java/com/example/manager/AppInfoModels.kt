package com.example.manager

import android.graphics.drawable.Drawable

enum class AppRoleType(
    val roleId: String?,
    val titleVi: String,
    val subtitleVi: String
) {
    HOME(
        roleId = "android.app.role.HOME",
        titleVi = "Màn hình chính (Launcher)",
        subtitleVi = "Ép làm launcher mặc định khi bấm nút Home"
    ),
    BROWSER(
        roleId = "android.app.role.BROWSER",
        titleVi = "Trình duyệt web",
        subtitleVi = "Mở mọi liên kết HTTP/HTTPS mặc định"
    ),
    DIALER(
        roleId = "android.app.role.DIALER",
        titleVi = "Điện thoại & Cuộc gọi",
        subtitleVi = "Trình quay số và nhận cuộc gọi mặc định"
    ),
    SMS(
        roleId = "android.app.role.SMS",
        titleVi = "Tin nhắn SMS/MMS",
        subtitleVi = "Ứng dụng nhắn tin mặc định hệ thống"
    ),
    ASSISTANT(
        roleId = "android.app.role.ASSISTANT",
        titleVi = "Trợ lý ảo hệ thống",
        subtitleVi = "Thay thế trợ lý mặc định khi giữ phím nguồn/Home"
    ),
    CALL_SCREENING(
        roleId = "android.app.role.CALL_SCREENING",
        titleVi = "Lọc & Chặn cuộc gọi",
        subtitleVi = "Xử lý nhận diện và chặn số gọi đến"
    ),
    IME(
        roleId = null,
        titleVi = "Bàn phím mặc định (IME)",
        subtitleVi = "Kích hoạt và đặt làm bàn phím nhập liệu hệ thống"
    ),
    APP_LINKS(
        roleId = null,
        titleVi = "Ép mở mọi liên kết hỗ trợ",
        subtitleVi = "Xác thực App Links để mở thẳng vào ứng dụng"
    )
}

enum class AppFilterTab(val labelVi: String) {
    ALL("Tất cả"),
    SYSTEM("Hệ thống"),
    THIRD_PARTY("Bên thứ 3"),
    HIDDEN("Đang ẩn"),
    UNINSTALLED("Đã gỡ")
}

enum class AppSortOrder(val labelVi: String) {
    NAME_ASC("Tên A → Z"),
    SIZE_DESC("Dung lượng lớn nhất"),
    RECENT_INSTALL("Cài gần đây")
}

data class AppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean,
    val isEnabled: Boolean,
    val isSuspended: Boolean,
    val isInstalledForUser: Boolean,
    val isProtectedSettings: Boolean,
    val isCriticalCore: Boolean,
    val apkSizeBytes: Long,
    val firstInstallTime: Long,
    val supportedRoles: List<AppRoleType>,
    val currentDefaultRoles: List<AppRoleType>,
    val homeActivityName: String?,
    val imeId: String?,
    val requestedPermissions: List<String>,
    val grantedPermissions: Set<String>
) {
    val isHiddenOrDisabled: Boolean get() = isInstalledForUser && (!isEnabled || isSuspended)
    val canBeRestoredFromSystem: Boolean get() = !isInstalledForUser && isSystemApp
}

data class RestorableItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val versionName: String,
    val removedAt: Long,
    val hasLocalApkBackup: Boolean,
    val backupApkDir: String?,
    val totalSizeBytes: Long,
    val isCurrentlyInstalled: Boolean
)

data class ActionOutcome(
    val success: Boolean,
    val message: String,
    val commandOutput: String = ""
)

data class AppIconKey(val packageName: String)

sealed interface UiIconState {
    data class Loaded(val drawable: Drawable) : UiIconState
    data object Missing : UiIconState
}
