package com.example.manager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.view.inputmethod.InputMethodManager
import com.example.data.RemovedAppDao
import com.example.data.RemovedAppEntity
import com.example.privilege.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AppRepository(
    private val context: Context,
    private val privilegeManager: PrivilegeManager,
    private val removedAppDao: RemovedAppDao
) {

    companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
        private const val FLAG_INSTALLED = 1 shl 23
        private const val FLAG_SUSPENDED = 1 shl 30

        private val CRITICAL_CORE_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.providers.settings",
            "com.android.phone",
            "com.android.shell",
            "com.android.webview",
            "com.google.android.webview",
            "moe.shizuku.privileged.api"
        )
    }

    private val pm: PackageManager = context.packageManager

    fun loadAppIcon(packageName: String): Drawable? {
        return try {
            pm.getApplicationIcon(packageName)
        } catch (_: Exception) {
            try {
                val info = pm.getApplicationInfo(
                    packageName,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
                )
                info.loadIcon(pm)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun loadAllApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or
            PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.GET_PERMISSIONS

        val packages = pm.getInstalledPackages(flags)

        val homeCandidates = queryHandlers(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        )
        val browserHttps = queryHandlers(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
        )
        val browserHttp = queryHandlers(
            Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
        )
        val browserCandidates = browserHttp + browserHttps
        val dialerCandidates = queryHandlers(Intent(Intent.ACTION_DIAL))
        val smsCandidates = queryHandlers(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
        val assistCandidates = queryHandlers(Intent(Intent.ACTION_ASSIST))

        val defaultHomePkg = resolveDefaultPkg(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        )
        val defaultBrowserPkg = resolveDefaultPkg(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
        )
        val defaultDialerPkg = resolveDefaultPkg(Intent(Intent.ACTION_DIAL))
        val defaultSmsPkg = resolveDefaultPkg(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val imeMap = imm?.inputMethodList?.associate { it.packageName to it.id }.orEmpty()

        packages.mapNotNull { pkgInfo ->
            val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
            val pkgName = pkgInfo.packageName
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val isInstalled = (appInfo.flags and FLAG_INSTALLED) != 0
            val isSuspended = (appInfo.flags and FLAG_SUSPENDED) != 0
            val enabledSetting = runCatching { pm.getApplicationEnabledSetting(pkgName) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            val isEnabled = appInfo.enabled &&
                enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER &&
                enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
            val label = runCatching {
                appInfo.loadLabel(pm).toString().trim()
            }.getOrNull().takeUnless { it.isNullOrBlank() } ?: pkgName

            val roles = buildList {
                if (homeCandidates.containsKey(pkgName)) add(AppRoleType.HOME)
                if (browserCandidates.containsKey(pkgName)) add(AppRoleType.BROWSER)
                if (dialerCandidates.containsKey(pkgName)) add(AppRoleType.DIALER)
                if (smsCandidates.containsKey(pkgName)) add(AppRoleType.SMS)
                if (assistCandidates.containsKey(pkgName)) add(AppRoleType.ASSISTANT)
                if (imeMap.containsKey(pkgName)) add(AppRoleType.IME)
                add(AppRoleType.APP_LINKS)
            }

            val defaults = buildList {
                if (pkgName == defaultHomePkg) add(AppRoleType.HOME)
                if (pkgName == defaultBrowserPkg) add(AppRoleType.BROWSER)
                if (pkgName == defaultDialerPkg) add(AppRoleType.DIALER)
                if (pkgName == defaultSmsPkg) add(AppRoleType.SMS)
            }

            val reqPerms = pkgInfo.requestedPermissions?.toList().orEmpty()
            val grantedPerms = buildSet {
                val reqFlags = pkgInfo.requestedPermissionsFlags
                if (reqFlags != null) {
                    reqPerms.forEachIndexed { idx, perm ->
                        if (idx < reqFlags.size &&
                            (reqFlags[idx] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                        ) {
                            add(perm)
                        }
                    }
                }
            }

            val sizeBytes = computeApkSize(appInfo)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }

            AppItem(
                packageName = pkgName,
                appName = label,
                versionName = pkgInfo.versionName ?: "1.0",
                versionCode = versionCode,
                isSystemApp = isSystem,
                isUpdatedSystemApp = isUpdatedSystem,
                isEnabled = isEnabled,
                isSuspended = isSuspended,
                isInstalledForUser = isInstalled,
                isProtectedSettings = pkgName == SETTINGS_PACKAGE,
                isCriticalCore = CRITICAL_CORE_PACKAGES.contains(pkgName) || pkgName == context.packageName,
                apkSizeBytes = sizeBytes,
                firstInstallTime = pkgInfo.firstInstallTime,
                supportedRoles = roles,
                currentDefaultRoles = defaults,
                homeActivityName = homeCandidates[pkgName],
                imeId = imeMap[pkgName],
                requestedPermissions = reqPerms,
                grantedPermissions = grantedPerms
            )
        }.sortedBy { it.appName.lowercase() }
    }

    suspend fun uninstallCompletely(
        app: AppItem,
        backupUserApk: Boolean
    ): ActionOutcome = withContext(Dispatchers.IO) {
        if (app.isProtectedSettings) {
            return@withContext ActionOutcome(
                false,
                "Ứng dụng Cài đặt (Settings) được bảo vệ, không thể gỡ bỏ."
            )
        }
        if (app.packageName == context.packageName) {
            return@withContext ActionOutcome(
                false,
                "Không thể tự gỡ chính AppForge từ bên trong ứng dụng."
            )
        }

        var backupDir: File? = null
        var apkCount = 0
        if (!app.isSystemApp && backupUserApk) {
            val backupResult = backupAppApks(app.packageName)
            backupDir = backupResult.first
            apkCount = backupResult.second
        }

        // Stop running processes and disable components first so the app cannot self-restart or persist
        privilegeManager.execute("am force-stop ${app.packageName}")
        privilegeManager.execute("pm disable-user --user 0 ${app.packageName}")

        // For updated system apps, remove the update in /data/app as well
        if (app.isSystemApp && app.isUpdatedSystemApp) {
            privilegeManager.execute("pm uninstall ${app.packageName}")
        }

        val result = privilegeManager.execute("pm uninstall --user 0 ${app.packageName}")
        val uninstallSucceeded = result.isSuccess ||
            result.combinedOutput.contains("Success", ignoreCase = true)

        if (uninstallSucceeded) {
            removedAppDao.upsert(
                RemovedAppEntity(
                    packageName = app.packageName,
                    appName = app.appName,
                    isSystemApp = app.isSystemApp,
                    versionName = app.versionName,
                    versionCode = app.versionCode,
                    removedAt = System.currentTimeMillis(),
                    backupApkDir = backupDir?.absolutePath,
                    apkCount = apkCount,
                    totalSizeBytes = app.apkSizeBytes
                )
            )
            ActionOutcome(
                true,
                "Đã gỡ hoàn toàn ${app.appName}" +
                    if (app.isSystemApp) " (có thể khôi phục)" else if (apkCount > 0) " (đã sao lưu APK)" else "",
                result.combinedOutput
            )
        } else {
            // Some OEM ROMs whitelist certain system apps from pm uninstall --user 0;
            // fall back to full hide + suspend + disable so it disappears completely and cannot run
            val hideRes = privilegeManager.execute("pm hide ${app.packageName}")
            val suspendRes = privilegeManager.execute("pm suspend --user 0 ${app.packageName}")
            val hiddenFallbackOk = hideRes.isSuccess || suspendRes.isSuccess
            if (hiddenFallbackOk) {
                removedAppDao.upsert(
                    RemovedAppEntity(
                        packageName = app.packageName,
                        appName = app.appName,
                        isSystemApp = app.isSystemApp,
                        versionName = app.versionName,
                        versionCode = app.versionCode,
                        removedAt = System.currentTimeMillis(),
                        backupApkDir = backupDir?.absolutePath,
                        apkCount = apkCount,
                        totalSizeBytes = app.apkSizeBytes
                    )
                )
                ActionOutcome(
                    true,
                    "ROM khóa gỡ trực tiếp — đã ẩn & khóa chạy ngầm hoàn toàn ${app.appName}",
                    "${result.combinedOutput}\n${hideRes.combinedOutput}"
                )
            } else {
                ActionOutcome(
                    false,
                    "Không thể gỡ ${app.appName}: ${result.combinedOutput}",
                    result.combinedOutput
                )
            }
        }
    }

    suspend fun restoreApp(
        packageName: String,
        appName: String,
        isSystemApp: Boolean,
        backupDirPath: String?
    ): ActionOutcome = withContext(Dispatchers.IO) {
        val result = if (isSystemApp || backupDirPath.isNullOrBlank()) {
            val cmd1 = privilegeManager.execute("cmd package install-existing $packageName")
            val ok1 = cmd1.isSuccess || cmd1.combinedOutput.contains("installed", ignoreCase = true)
            val finalCmd = if (ok1) {
                cmd1
            } else {
                privilegeManager.execute("pm install-existing --user 0 $packageName")
            }
            privilegeManager.execute("pm unhide $packageName")
            privilegeManager.execute("pm unsuspend --user 0 $packageName")
            privilegeManager.execute("pm enable $packageName")
            finalCmd
        } else {
            val dir = File(backupDirPath)
            val apks = dir.listFiles { f -> f.isFile && f.name.endsWith(".apk") }
                ?.sortedBy { if (it.name == "base.apk") "" else it.name }
                .orEmpty()
            if (apks.isEmpty()) {
                return@withContext ActionOutcome(
                    false,
                    "Không tìm thấy tệp APK sao lưu cho $appName"
                )
            }
            val installRes = privilegeManager.installApkFiles(apks)
            if (installRes.isSuccess) {
                privilegeManager.execute("pm unhide $packageName")
                privilegeManager.execute("pm enable $packageName")
            }
            installRes
        }

        if (result.isSuccess || result.combinedOutput.contains("installed", ignoreCase = true) ||
            result.combinedOutput.contains("Success", ignoreCase = true)
        ) {
            removedAppDao.deleteByPackage(packageName)
            ActionOutcome(true, "Đã khôi phục $appName thành công", result.combinedOutput)
        } else {
            ActionOutcome(
                false,
                "Khôi phục $appName thất bại: ${result.combinedOutput}",
                result.combinedOutput
            )
        }
    }

    suspend fun setAppHidden(app: AppItem, hide: Boolean): ActionOutcome = withContext(Dispatchers.IO) {
        if (hide && app.isProtectedSettings) {
            return@withContext ActionOutcome(
                false,
                "Cài đặt (com.android.settings) được bảo vệ và không thể ẩn."
            )
        }
        if (hide && app.packageName == context.packageName) {
            return@withContext ActionOutcome(false, "Không thể tự ẩn chính AppForge.")
        }

        val result = if (hide) {
            privilegeManager.execute("am force-stop ${app.packageName}")
            val disableRes = privilegeManager.execute("pm disable-user --user 0 ${app.packageName}")
            val hideRes = privilegeManager.execute("pm hide ${app.packageName}")
            privilegeManager.execute("pm suspend --user 0 ${app.packageName}")
            if (disableRes.isSuccess || disableRes.combinedOutput.contains("new state", ignoreCase = true)) {
                disableRes
            } else {
                hideRes
            }
        } else {
            privilegeManager.execute("pm unhide ${app.packageName}")
            privilegeManager.execute("pm unsuspend --user 0 ${app.packageName}")
            privilegeManager.execute("pm enable ${app.packageName}")
        }

        if (result.isSuccess || result.combinedOutput.contains("new state", ignoreCase = true)) {
            ActionOutcome(
                true,
                if (hide) "Đã ẩn & khóa chạy ngầm ${app.appName}" else "Đã hiện lại ${app.appName}",
                result.combinedOutput
            )
        } else {
            ActionOutcome(
                false,
                "${if (hide) "Ẩn" else "Hiện"} ${app.appName} thất bại: ${result.combinedOutput}",
                result.combinedOutput
            )
        }
    }

    suspend fun forceSetDefaultApp(
        app: AppItem,
        roleType: AppRoleType,
        disableCompeting: Boolean
    ): ActionOutcome = withContext(Dispatchers.IO) {
        if (!app.isInstalledForUser) {
            return@withContext ActionOutcome(false, "${app.appName} chưa được cài đặt cho người dùng hiện tại.")
        }
        if (!app.isEnabled) {
            privilegeManager.execute("pm unhide ${app.packageName}")
            privilegeManager.execute("pm enable ${app.packageName}")
        }

        val outputs = mutableListOf<String>()
        var allOk = false

        when (roleType) {
            AppRoleType.HOME,
            AppRoleType.BROWSER,
            AppRoleType.DIALER,
            AppRoleType.SMS,
            AppRoleType.ASSISTANT,
            AppRoleType.CALL_SCREENING -> {
                val roleId = roleType.roleId ?: ""
                privilegeManager.execute("cmd role set-bypassing-role-qualification true")
                val res = privilegeManager.execute(
                    "cmd role add-role-holder --user 0 $roleId ${app.packageName}"
                )
                outputs.add(res.combinedOutput)
                if (res.isSuccess) allOk = true

                if (roleType == AppRoleType.HOME && !app.homeActivityName.isNullOrBlank()) {
                    val homeRes = privilegeManager.execute(
                        "cmd package set-home-activity --user 0 ${app.packageName}/${app.homeActivityName}"
                    )
                    outputs.add(homeRes.combinedOutput)
                    if (homeRes.isSuccess) allOk = true
                }

                if (roleType == AppRoleType.BROWSER) {
                    val brRes = privilegeManager.execute("pm set-default-browser --user 0 ${app.packageName}")
                    if (brRes.isSuccess) allOk = true
                    privilegeManager.execute(
                        "pm set-app-links-user-selection --package ${app.packageName} --user 0 true all"
                    )
                }

                if (roleType == AppRoleType.DIALER) {
                    val dlRes = privilegeManager.execute("telecom set-default-dialer ${app.packageName}")
                    if (dlRes.isSuccess) allOk = true
                }

                if (roleType == AppRoleType.SMS) {
                    val smsRes = privilegeManager.execute(
                        "settings put secure sms_default_application ${app.packageName}"
                    )
                    if (smsRes.isSuccess) allOk = true
                }

                if (disableCompeting) {
                    val safeToDisableCompetitors =
                        roleType != AppRoleType.BROWSER || app.supportedRoles.contains(AppRoleType.BROWSER)
                    if (safeToDisableCompetitors) {
                        val competitors = findCompetingDefaultPackages(roleType, app.packageName)
                        competitors.forEach { compPkg ->
                            if (compPkg != SETTINGS_PACKAGE && compPkg != context.packageName) {
                                val dRes = privilegeManager.execute("pm disable-user --user 0 $compPkg")
                                outputs.add("Tắt $compPkg: ${dRes.combinedOutput}")
                            }
                        }
                    } else {
                        outputs.add("Bỏ qua tắt trình duyệt gốc vì ${app.appName} không phải trình duyệt web.")
                    }
                }
            }

            AppRoleType.IME -> {
                val imeId = app.imeId
                    ?: return@withContext ActionOutcome(false, "${app.appName} không cung cấp dịch vụ bàn phím (IME).")
                val enRes = privilegeManager.execute("ime enable $imeId")
                val setRes = privilegeManager.execute("ime set $imeId")
                outputs.add(enRes.combinedOutput)
                outputs.add(setRes.combinedOutput)
                allOk = setRes.isSuccess
            }

            AppRoleType.APP_LINKS -> {
                val r1 = privilegeManager.execute("pm set-app-links --package ${app.packageName} 2 all")
                val r2 = privilegeManager.execute(
                    "pm set-app-links-user-selection --package ${app.packageName} --user 0 true all"
                )
                outputs.add(r1.combinedOutput)
                outputs.add(r2.combinedOutput)
                allOk = r1.isSuccess || r2.isSuccess
            }
        }

        val summary = outputs.filter { it.isNotBlank() }.joinToString("\n")
        if (allOk) {
            ActionOutcome(
                true,
                "Đã ép ${app.appName} làm ${roleType.titleVi} mặc định!",
                summary
            )
        } else {
            ActionOutcome(
                false,
                "Không thể ép mặc định cho ${app.appName}: $summary",
                summary
            )
        }
    }

    suspend fun grantPermission(packageName: String, permission: String): ActionOutcome =
        withContext(Dispatchers.IO) {
            val res = privilegeManager.execute("pm grant $packageName $permission")
            ActionOutcome(
                res.isSuccess,
                if (res.isSuccess) "Đã cấp quyền $permission" else "Lỗi cấp quyền: ${res.combinedOutput}",
                res.combinedOutput
            )
        }

    suspend fun revokePermission(packageName: String, permission: String): ActionOutcome =
        withContext(Dispatchers.IO) {
            val res = privilegeManager.execute("pm revoke $packageName $permission")
            ActionOutcome(
                res.isSuccess,
                if (res.isSuccess) "Đã thu hồi quyền $permission" else "Lỗi thu hồi: ${res.combinedOutput}",
                res.combinedOutput
            )
        }

    suspend fun setAppOpAllow(packageName: String, op: String, allow: Boolean): ActionOutcome =
        withContext(Dispatchers.IO) {
            val mode = if (allow) "allow" else "ignore"
            val res = privilegeManager.execute("appops set $packageName $op $mode")
            ActionOutcome(
                res.isSuccess,
                if (res.isSuccess) "Đã đặt $op = $mode" else "Lỗi AppOps: ${res.combinedOutput}",
                res.combinedOutput
            )
        }

    suspend fun forceStop(packageName: String): ActionOutcome = withContext(Dispatchers.IO) {
        val res = privilegeManager.execute("am force-stop $packageName")
        ActionOutcome(
            res.isSuccess,
            if (res.isSuccess) "Đã buộc dừng $packageName" else "Lỗi buộc dừng: ${res.combinedOutput}",
            res.combinedOutput
        )
    }

    suspend fun clearData(packageName: String): ActionOutcome = withContext(Dispatchers.IO) {
        if (packageName == SETTINGS_PACKAGE || packageName == context.packageName) {
            return@withContext ActionOutcome(false, "Không thể xóa dữ liệu ứng dụng được bảo vệ.")
        }
        val res = privilegeManager.execute("pm clear $packageName")
        ActionOutcome(
            res.isSuccess,
            if (res.isSuccess) "Đã xóa dữ liệu $packageName" else "Lỗi xóa dữ liệu: ${res.combinedOutput}",
            res.combinedOutput
        )
    }

    suspend fun deleteBackupDir(packageName: String) = withContext(Dispatchers.IO) {
        val entity = removedAppDao.getByPackage(packageName)
        entity?.backupApkDir?.let { path ->
            runCatching { File(path).deleteRecursively() }
        }
        removedAppDao.deleteByPackage(packageName)
    }

    private fun backupAppApks(packageName: String): Pair<File?, Int> {
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            val rootBackup = context.getExternalFilesDir("apk_backups")
                ?: File(context.filesDir, "apk_backups")
            val pkgDir = File(rootBackup, packageName).apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }
            var count = 0
            info.sourceDir?.let { src ->
                val srcFile = File(src)
                if (srcFile.exists()) {
                    srcFile.copyTo(File(pkgDir, "base.apk"), overwrite = true)
                    count++
                }
            }
            info.splitSourceDirs?.forEachIndexed { idx, splitPath ->
                val splitFile = File(splitPath)
                if (splitFile.exists()) {
                    val outName = splitFile.name.ifBlank { "split_$idx.apk" }
                    splitFile.copyTo(File(pkgDir, outName), overwrite = true)
                    count++
                }
            }
            if (count > 0) pkgDir to count else null to 0
        } catch (_: Exception) {
            null to 0
        }
    }

    private fun computeApkSize(info: ApplicationInfo): Long {
        var total = 0L
        info.sourceDir?.let { path ->
            runCatching { total += File(path).length() }
        }
        info.splitSourceDirs?.forEach { path ->
            runCatching { total += File(path).length() }
        }
        return total
    }

    private fun queryHandlers(intent: Intent): Map<String, String> {
        val flags = PackageManager.MATCH_ALL or PackageManager.MATCH_DISABLED_COMPONENTS
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, flags)
        }
        return resolved.associate { it.activityInfo.packageName to it.activityInfo.name }
    }

    private fun resolveDefaultPkg(intent: Intent): String? {
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolved?.activityInfo?.packageName
    }

    private fun findCompetingDefaultPackages(roleType: AppRoleType, targetPkg: String): List<String> {
        val intent = when (roleType) {
            AppRoleType.HOME -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            AppRoleType.BROWSER -> Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
            AppRoleType.DIALER -> Intent(Intent.ACTION_DIAL)
            AppRoleType.SMS -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            AppRoleType.ASSISTANT -> Intent(Intent.ACTION_ASSIST)
            else -> return emptyList()
        }
        return queryHandlers(intent).keys.filter { pkg ->
            pkg != targetPkg && pkg != SETTINGS_PACKAGE && pkg != context.packageName
        }
    }
}
