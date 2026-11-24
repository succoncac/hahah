package com.example.privilege

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.example.IShellService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class PrivilegeManager(private val context: Context) {

    companion object {
        private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
        private const val SHIZUKU_REQ_CODE = 1001
        private const val USER_SERVICE_VERSION = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val adbTcpClient = AdbTcpClient(context)
    private val logIdGen = AtomicLong(1L)
    private val bindMutex = Mutex()
    private val refreshMutex = Mutex()

    private val _status = MutableStateFlow(PrivilegeStatus())
    val status: StateFlow<PrivilegeStatus> = _status.asStateFlow()

    private val _commandLogs = MutableStateFlow<List<CommandLogItem>>(emptyList())
    val commandLogs: StateFlow<List<CommandLogItem>> = _commandLogs.asStateFlow()

    private val _permissionEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val permissionEvents: SharedFlow<String> = _permissionEvents.asSharedFlow()

    @Volatile
    private var shellService: IShellService? = null

    @Volatile
    private var pendingServiceBind: CompletableDeferred<IShellService?>? = null

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShellUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("appforge_shell")
            .debuggable(false)
            .version(USER_SERVICE_VERSION)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val bound = IShellService.Stub.asInterface(service)
            shellService = bound
            pendingServiceBind?.complete(bound)
            pendingServiceBind = null
            scope.launch { refreshStatus() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
            pendingServiceBind?.complete(null)
            pendingServiceBind = null
            scope.launch { refreshStatus() }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        scope.launch { refreshStatus() }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        shellService = null
        scope.launch { refreshStatus() }
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQ_CODE) {
                scope.launch {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        ensureShizukuBound()
                        val st = refreshStatus()
                        if (st.isReady) {
                            _permissionEvents.tryEmit("Đã cấp quyền & kết nối ${st.uidLabel}!")
                        } else {
                            _permissionEvents.tryEmit("Đã cấp quyền Shizuku, đang hoàn tất liên kết...")
                        }
                    } else {
                        refreshStatus()
                        _permissionEvents.tryEmit("Bạn đã từ chối quyền Shizuku.")
                    }
                }
            }
        }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    fun release() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        try {
            shellService?.destroy()
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
        } catch (_: Exception) {
        }
    }

    fun setModeInitial(mode: PrivilegeMode) {
        _status.update { it.copy(activeMode = mode) }
    }

    fun setMode(mode: PrivilegeMode) {
        _status.update { it.copy(activeMode = mode) }
        scope.launch { refreshStatus() }
    }

    fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                _permissionEvents.tryEmit("Dịch vụ Shizuku chưa chạy. Hãy mở app Shizuku và khởi động trước.")
                scope.launch { refreshStatus() }
                return
            }
            if (Shizuku.isPreV11()) {
                _permissionEvents.tryEmit("Phiên bản Shizuku quá cũ (cần Shizuku v11 trở lên).")
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                scope.launch {
                    ensureShizukuBound()
                    val st = refreshStatus()
                    if (st.isReady) {
                        _permissionEvents.tryEmit("Đã kết nối ${st.uidLabel}")
                    } else {
                        _permissionEvents.tryEmit(st.detailMessage)
                    }
                }
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                _permissionEvents.tryEmit("Quyền Shizuku đã bị từ chối trước đó. Hãy bật cho AppForge trong ứng dụng Shizuku.")
                Shizuku.requestPermission(SHIZUKU_REQ_CODE)
            } else {
                Shizuku.requestPermission(SHIZUKU_REQ_CODE)
            }
        } catch (e: Exception) {
            _permissionEvents.tryEmit("Lỗi yêu cầu quyền Shizuku: ${e.message}")
            scope.launch { refreshStatus() }
        }
    }

    suspend fun autoDetectAndConnect(): PrivilegeStatus {
        val current = refreshStatus()
        if (current.isReady) return current

        when {
            current.isShizukuRunning && current.hasShizukuPermission -> {
                _status.update { it.copy(activeMode = PrivilegeMode.SHIZUKU) }
                ensureShizukuBound()
            }
            current.isRootAvailable -> {
                _status.update { it.copy(activeMode = PrivilegeMode.ROOT) }
            }
            adbTcpClient.testConnection().isSuccess -> {
                _status.update { it.copy(activeMode = PrivilegeMode.ADB_TCP) }
            }
        }
        return refreshStatus()
    }

    suspend fun refreshStatus(): PrivilegeStatus = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val shizukuInstalled = isPackageInstalled(SHIZUKU_PKG)
            val shizukuRunning = try {
                Shizuku.pingBinder()
            } catch (_: Throwable) {
                false
            }
            val shizukuGranted = if (shizukuRunning) {
                try {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                } catch (_: Throwable) {
                    false
                }
            } else {
                false
            }

            val active = _status.value.activeMode
            if (active == PrivilegeMode.SHIZUKU && shizukuRunning && shizukuGranted) {
                if (!isShellServiceAlive()) {
                    shellService = null
                    ensureShizukuBound()
                }
            }

            val rootOk = if (active == PrivilegeMode.ROOT) {
                checkRootSilent()
            } else {
                hasSuBinary()
            }
            val adbOk = if (active == PrivilegeMode.ADB_TCP) {
                adbTcpClient.testConnection().isSuccess
            } else {
                false
            }

            // Fallback check: if UserService failed to bind on OEM ROM, check if Shizuku.newProcess works
            val shizukuNewProcessOk = if (
                active == PrivilegeMode.SHIZUKU &&
                shizukuRunning &&
                shizukuGranted &&
                shellService == null
            ) {
                executeViaShizukuNewProcess("id").isSuccess
            } else {
                false
            }

            val (connState, uidLabel, detail) = when (active) {
                PrivilegeMode.SHIZUKU -> when {
                    !shizukuRunning && !shizukuInstalled -> Triple(
                        ConnectionState.UNAVAILABLE,
                        "Chưa cài Shizuku",
                        "Cài Shizuku hoặc chuyển sang chế độ Root / ADB"
                    )
                    !shizukuRunning -> Triple(
                        ConnectionState.DISCONNECTED,
                        "Shizuku chưa chạy",
                        "Mở ứng dụng Shizuku và khởi động dịch vụ bằng Gỡ lỗi không dây"
                    )
                    !shizukuGranted -> Triple(
                        ConnectionState.PERMISSION_REQUIRED,
                        "Chưa cấp quyền",
                        "Nếu đã bật quyền trong Shizuku, hãy bấm \"Xin quyền Shizuku\" hoặc tắt mở lại AppForge để đồng bộ"
                    )
                    shellService != null -> {
                        val uid = try {
                            shellService?.uid ?: Shizuku.getUid()
                        } catch (_: Exception) {
                            -1
                        }
                        val uidStr = when (uid) {
                            0 -> "Shizuku · Root"
                            2000 -> "Shizuku · ADB"
                            else -> "Shizuku · UID $uid"
                        }
                        Triple(
                            ConnectionState.CONNECTED,
                            uidStr,
                            "Đã kết nối (UID $uid) — sẵn sàng gỡ, ẩn và ép mặc định mọi ứng dụng"
                        )
                    }
                    shizukuNewProcessOk -> {
                        val uid = try {
                            Shizuku.getUid()
                        } catch (_: Throwable) {
                            2000
                        }
                        Triple(
                            ConnectionState.CONNECTED,
                            if (uid == 0) "Shizuku · Root" else "Shizuku · ADB",
                            "Đã cấp quyền Shizuku (chế độ tương thích) — hoạt động đầy đủ"
                        )
                    }
                    else -> Triple(
                        ConnectionState.DISCONNECTED,
                        "Lỗi liên kết dịch vụ",
                        "Đã cấp quyền Shizuku nhưng UserService không phản hồi. Hãy thử khởi động lại dịch vụ Shizuku hoặc bấm Kết nối lại."
                    )
                }

                PrivilegeMode.ROOT -> if (rootOk) {
                    Triple(
                        ConnectionState.CONNECTED,
                        "Root · UID 0",
                        "Đã có quyền Superuser (su) — hoạt động đầy đủ mọi tính năng"
                    )
                } else {
                    Triple(
                        ConnectionState.UNAVAILABLE,
                        "Không có Root",
                        "Thiết bị chưa Root hoặc chưa cấp quyền Superuser cho AppForge"
                    )
                }

                PrivilegeMode.ADB_TCP -> if (adbOk) {
                    Triple(
                        ConnectionState.CONNECTED,
                        "ADB · :5555",
                        "Đã kết nối ADB cục bộ qua TCP (127.0.0.1:5555) — hoạt động tương đương Shizuku"
                    )
                } else {
                    Triple(
                        ConnectionState.DISCONNECTED,
                        "ADB chưa mở",
                        "Chạy \"adb tcpip 5555\" từ máy tính hoặc bật gỡ lỗi mạng, rồi thử kết nối lại"
                    )
                }
            }

            val updated = PrivilegeStatus(
                activeMode = active,
                state = connState,
                uidLabel = uidLabel,
                detailMessage = detail,
                isShizukuInstalled = shizukuInstalled,
                isShizukuRunning = shizukuRunning,
                hasShizukuPermission = shizukuGranted,
                isShizukuServiceBound = shellService != null || shizukuNewProcessOk,
                isRootAvailable = rootOk,
                isAdbTcpConnected = adbOk
            )
            _status.value = updated
            updated
        }
    }

    suspend fun execute(command: String): ShellResult = withContext(Dispatchers.IO) {
        val mode = _status.value.activeMode
        val result = when (mode) {
            PrivilegeMode.SHIZUKU -> executeViaShizuku(command)
            PrivilegeMode.ROOT -> executeViaRoot(command)
            PrivilegeMode.ADB_TCP -> adbTcpClient.executeCommand(command)
        }
        appendLog(mode, command, result)
        result
    }

    suspend fun installApkFiles(apkFiles: List<File>): ShellResult = withContext(Dispatchers.IO) {
        if (apkFiles.isEmpty()) {
            return@withContext ShellResult(-1, "", "Không tìm thấy tệp APK sao lưu")
        }
        val mode = _status.value.activeMode
        val logCmd = "pm install (${apkFiles.size} tệp APK)"
        val result = when (mode) {
            PrivilegeMode.SHIZUKU -> installViaShizukuFds(apkFiles)
            PrivilegeMode.ROOT -> installViaRootPaths(apkFiles)
            PrivilegeMode.ADB_TCP -> installViaAdbPaths(apkFiles)
        }
        appendLog(mode, logCmd, result)
        result
    }

    private fun isShellServiceAlive(): Boolean {
        val svc = shellService ?: return false
        return try {
            svc.asBinder().isBinderAlive && svc.asBinder().pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun ensureShizukuBound(): IShellService? {
        if (isShellServiceAlive()) return shellService
        return bindMutex.withLock {
            if (isShellServiceAlive()) return@withLock shellService
            val ping = try {
                Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) {
                false
            }
            if (!ping) return@withLock null

            val deferred = CompletableDeferred<IShellService?>()
            pendingServiceBind = deferred
            try {
                withContext(Dispatchers.Main) {
                    Shizuku.bindUserService(userServiceArgs, serviceConnection)
                }
            } catch (_: Throwable) {
                pendingServiceBind = null
                return@withLock null
            }
            withTimeoutOrNull(4500L) { deferred.await() }
        }
    }

    private suspend fun executeViaShizuku(command: String): ShellResult {
        val svc = ensureShizukuBound()
        if (svc != null) {
            return try {
                val raw = svc.exec(command)
                parsePackedResult(raw, command)
            } catch (e: Exception) {
                shellService = null
                // Try fallback before failing
                val fb = executeViaShizukuNewProcess(command)
                if (fb.exitCode != -2) fb
                else ShellResult(-1, "", "Lỗi Shizuku IPC: ${e.message}", command)
            }
        }
        val fallback = executeViaShizukuNewProcess(command)
        if (fallback.exitCode != -2) return fallback
        return ShellResult(
            exitCode = -1,
            stdout = "",
            stderr = "Shizuku chưa kết nối hoặc chưa được cấp quyền.",
            command = command
        )
    }

    /**
     * Fallback execution via Shizuku.newProcess using reflection when UserService fails to spawn on OEM ROMs.
     */
    private fun executeViaShizukuNewProcess(command: String): ShellResult {
        return try {
            val ping = Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (!ping) return ShellResult(-2, "", "", command)

            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val out = process.inputStream.bufferedReader().readText().trim()
            val err = process.errorStream.bufferedReader().readText().trim()
            val code = process.waitFor()
            ShellResult(code, out, err, command)
        } catch (_: Throwable) {
            ShellResult(-2, "", "", command)
        }
    }

    private suspend fun installViaShizukuFds(apkFiles: List<File>): ShellResult {
        val svc = ensureShizukuBound()
        if (svc != null) {
            val fds = mutableListOf<ParcelFileDescriptor>()
            return try {
                val sizes = LongArray(apkFiles.size)
                val names = Array(apkFiles.size) { "" }
                apkFiles.forEachIndexed { idx, file ->
                    fds.add(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
                    sizes[idx] = file.length()
                    names[idx] = file.name
                }
                val raw = svc.installApks(fds.toTypedArray(), sizes, names)
                parsePackedResult(raw, "pm install-commit")
            } catch (e: Exception) {
                ShellResult(-1, "", "Lỗi cài lại APK qua Shizuku: ${e.message}")
            } finally {
                fds.forEach {
                    try {
                        it.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
        // Fallback to path-based install via Shizuku.newProcess if UserService unavailable
        if (apkFiles.size == 1) {
            return executeViaShizukuNewProcess("pm install -r \"${apkFiles.first().absolutePath}\"")
        }
        val create = executeViaShizukuNewProcess("pm install-create -r")
        if (!create.isSuccess) return create
        val sessionId = Regex("\\[(\\d+)]").find(create.stdout)?.groupValues?.get(1)
            ?: return ShellResult(-1, create.stdout, "Không tạo được phiên cài đặt Shizuku")
        apkFiles.forEach { file ->
            val write = executeViaShizukuNewProcess(
                "pm install-write -S ${file.length()} $sessionId ${file.name} \"${file.absolutePath}\""
            )
            if (!write.isSuccess) {
                executeViaShizukuNewProcess("pm install-abandon $sessionId")
                return write
            }
        }
        return executeViaShizukuNewProcess("pm install-commit $sessionId")
    }

    private fun executeViaRoot(command: String): ShellResult {
        return try {
            val process = ProcessBuilder("su", "-c", command).start()
            val out = process.inputStream.bufferedReader().readText().trim()
            val err = process.errorStream.bufferedReader().readText().trim()
            val code = process.waitFor()
            ShellResult(code, out, err, command)
        } catch (e: Exception) {
            ShellResult(-1, "", "Không thể chạy lệnh su: ${e.message}", command)
        }
    }

    private fun installViaRootPaths(apkFiles: List<File>): ShellResult {
        if (apkFiles.size == 1) {
            return executeViaRoot("pm install -r \"${apkFiles.first().absolutePath}\"")
        }
        val create = executeViaRoot("pm install-create -r")
        if (!create.isSuccess) return create
        val sessionId = Regex("\\[(\\d+)]").find(create.stdout)?.groupValues?.get(1)
            ?: return ShellResult(-1, create.stdout, "Không tạo được phiên cài đặt Root")
        apkFiles.forEach { file ->
            val write = executeViaRoot(
                "pm install-write -S ${file.length()} $sessionId ${file.name} \"${file.absolutePath}\""
            )
            if (!write.isSuccess) {
                executeViaRoot("pm install-abandon $sessionId")
                return write
            }
        }
        return executeViaRoot("pm install-commit $sessionId")
    }

    private suspend fun installViaAdbPaths(apkFiles: List<File>): ShellResult {
        if (apkFiles.size == 1) {
            return adbTcpClient.executeCommand("pm install -r \"${apkFiles.first().absolutePath}\"")
        }
        val create = adbTcpClient.executeCommand("pm install-create -r")
        if (!create.isSuccess) return create
        val sessionId = Regex("\\[(\\d+)]").find(create.stdout)?.groupValues?.get(1)
            ?: return ShellResult(-1, create.stdout, "Không tạo được phiên cài đặt ADB")
        apkFiles.forEach { file ->
            val write = adbTcpClient.executeCommand(
                "pm install-write -S ${file.length()} $sessionId ${file.name} \"${file.absolutePath}\""
            )
            if (!write.isSuccess) {
                adbTcpClient.executeCommand("pm install-abandon $sessionId")
                return write
            }
        }
        return adbTcpClient.executeCommand("pm install-commit $sessionId")
    }

    private fun hasSuBinary(): Boolean {
        val paths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/vendor/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun checkRootSilent(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id").start()
            val out = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            code == 0 && out.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    private fun parsePackedResult(raw: String, command: String): ShellResult {
        val parts = raw.split('\u0000', limit = 3)
        val code = parts.getOrNull(0)?.toIntOrNull() ?: -1
        val out = parts.getOrNull(1)?.trim() ?: ""
        val err = parts.getOrNull(2)?.trim() ?: ""
        return ShellResult(code, out, err, command)
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun appendLog(mode: PrivilegeMode, command: String, result: ShellResult) {
        val entry = CommandLogItem(
            id = logIdGen.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            mode = mode,
            command = command,
            exitCode = result.exitCode,
            output = result.combinedOutput
        )
        _commandLogs.update { list -> (listOf(entry) + list).take(120) }
    }

    fun clearLogs() {
        _commandLogs.value = emptyList()
    }
}
