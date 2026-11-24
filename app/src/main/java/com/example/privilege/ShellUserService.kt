package com.example.privilege

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.annotation.Keep
import com.example.IShellService
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

@Keep
class ShellUserService : IShellService.Stub {

    @Keep
    constructor() : super()

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : super()

    override fun destroy() {
        exitProcess(0)
    }

    override fun getUid(): Int {
        return Process.myUid()
    }

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command).start()
            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val outThread = Thread {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (stdoutBuilder.isNotEmpty()) stdoutBuilder.append('\n')
                        stdoutBuilder.append(line)
                    }
                }
            }
            val errThread = Thread {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (stderrBuilder.isNotEmpty()) stderrBuilder.append('\n')
                        stderrBuilder.append(line)
                    }
                }
            }
            outThread.start()
            errThread.start()
            val exitCode = process.waitFor()
            outThread.join(3000)
            errThread.join(3000)
            "$exitCode\u0000$stdoutBuilder\u0000$stderrBuilder"
        } catch (e: Exception) {
            "-1\u0000\u0000${e.message ?: e.javaClass.simpleName}"
        }
    }

    override fun installApks(
        fds: Array<out ParcelFileDescriptor>?,
        sizes: LongArray?,
        names: Array<out String>?
    ): String {
        if (fds == null || sizes == null || names == null || fds.isEmpty()) {
            return "-1\u0000\u0000Không có tệp APK để cài đặt"
        }
        return try {
            val createResult = execRaw("pm install-create -r")
            if (createResult.first != 0) {
                return "${createResult.first}\u0000${createResult.second}\u0000${createResult.third}"
            }
            val match = Regex("\\[(\\d+)]").find(createResult.second)
                ?: return "-1\u0000${createResult.second}\u0000Không lấy được session ID"
            val sessionId = match.groupValues[1]

            for (i in fds.indices) {
                val fd = fds[i]
                val size = sizes.getOrElse(i) { -1L }
                val name = names.getOrElse(i) { "split_$i.apk" }
                val writeCmd = if (size > 0L) {
                    "pm install-write -S $size $sessionId $name -"
                } else {
                    "pm install-write $sessionId $name -"
                }
                val proc = ProcessBuilder("sh", "-c", writeCmd).start()
                ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                    proc.outputStream.use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                val out = proc.inputStream.bufferedReader().readText()
                val err = proc.errorStream.bufferedReader().readText()
                val code = proc.waitFor()
                if (code != 0) {
                    execRaw("pm install-abandon $sessionId")
                    return "$code\u0000$out\u0000$err"
                }
            }

            val commitResult = execRaw("pm install-commit $sessionId")
            "${commitResult.first}\u0000${commitResult.second}\u0000${commitResult.third}"
        } catch (e: Exception) {
            "-1\u0000\u0000${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun execRaw(command: String): Triple<Int, String, String> {
        val process = ProcessBuilder("sh", "-c", command).start()
        val out = process.inputStream.bufferedReader().readText().trim()
        val err = process.errorStream.bufferedReader().readText().trim()
        val code = process.waitFor()
        return Triple(code, out, err)
    }
}
