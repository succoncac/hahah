package com.example.privilege

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * Pure-Kotlin ADB over TCP client (default 127.0.0.1:5555).
 * Supports CNXN + RSA AUTH handshake and shell:<command> streams.
 */
class AdbTcpClient(private val context: Context) {

    companion object {
        private const val CMD_AUTH = 0x48545541
        private const val CMD_CNXN = 0x4e584e43
        private const val CMD_OPEN = 0x4e45504f
        private const val CMD_OKAY = 0x59414b4f
        private const val CMD_CLSE = 0x45534c43
        private const val CMD_WRTE = 0x45545257

        private const val ADB_VERSION = 0x01000000
        private const val MAX_PAYLOAD = 256 * 1024

        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3

        private val SHA1_DIGEST_INFO_PREFIX = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
        )
    }

    private val mutex = Mutex()
    private val keyPair: KeyPair by lazy { loadOrCreateKeyPair() }

    suspend fun testConnection(host: String = "127.0.0.1", port: Int = 5555): ShellResult =
        executeCommand("id", host, port)

    suspend fun executeCommand(
        command: String,
        host: String = "127.0.0.1",
        port: Int = 5555
    ): ShellResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, port), 3500)
                socket.soTimeout = 15000

                val input = DataInputStream(socket.getInputStream())
                val output = socket.getOutputStream()

                sendPacket(
                    output,
                    CMD_CNXN,
                    ADB_VERSION,
                    MAX_PAYLOAD,
                    "host::features=shell_v2,cmd\u0000".toByteArray(Charsets.UTF_8)
                )

                var sentSignature = false
                var connected = false
                while (!connected) {
                    val pkt = readPacket(input)
                    when (pkt.command) {
                        CMD_AUTH -> {
                            if (pkt.arg0 == AUTH_TOKEN) {
                                if (!sentSignature) {
                                    sentSignature = true
                                    val sig = signToken(pkt.payload)
                                    sendPacket(output, CMD_AUTH, AUTH_SIGNATURE, 0, sig)
                                } else {
                                    val pubBytes = encodeAdbPublicKey()
                                    sendPacket(output, CMD_AUTH, AUTH_RSAPUBLICKEY, 0, pubBytes)
                                }
                            }
                        }
                        CMD_CNXN -> {
                            connected = true
                        }
                        else -> {
                            return@withLock ShellResult(
                                exitCode = -1,
                                stdout = "",
                                stderr = "Phản hồi ADB không hợp lệ (0x${pkt.command.toString(16)})",
                                command = command
                            )
                        }
                    }
                }

                val localId = 1
                val wrapped = "shell:($command); echo \"\\n__EXIT__:\$?\"\u0000"
                sendPacket(output, CMD_OPEN, localId, 0, wrapped.toByteArray(Charsets.UTF_8))

                var remoteId = 0
                val buffer = ByteArrayOutputStream()
                while (true) {
                    val pkt = readPacket(input)
                    when (pkt.command) {
                        CMD_OKAY -> {
                            if (remoteId == 0 && pkt.arg1 == localId) {
                                remoteId = pkt.arg0
                            }
                        }
                        CMD_WRTE -> {
                            if (pkt.arg1 == localId) {
                                if (remoteId == 0) remoteId = pkt.arg0
                                buffer.write(pkt.payload)
                                sendPacket(output, CMD_OKAY, localId, remoteId, ByteArray(0))
                            }
                        }
                        CMD_CLSE -> {
                            if (pkt.arg1 == localId || pkt.arg0 == remoteId) {
                                sendPacket(output, CMD_CLSE, localId, remoteId, ByteArray(0))
                                break
                            }
                        }
                    }
                }

                val rawOutput = buffer.toString(Charsets.UTF_8.name()).replace("\r\n", "\n")
                val markerIdx = rawOutput.lastIndexOf("__EXIT__:")
                if (markerIdx >= 0) {
                    val cleanOut = rawOutput.substring(0, markerIdx).trimEnd()
                    val codeStr = rawOutput.substring(markerIdx + "__EXIT__:".length).trim()
                    val exitCode = codeStr.toIntOrNull() ?: 0
                    ShellResult(
                        exitCode = exitCode,
                        stdout = if (exitCode == 0) cleanOut else "",
                        stderr = if (exitCode != 0) cleanOut else "",
                        command = command
                    )
                } else {
                    ShellResult(0, rawOutput.trim(), "", command)
                }
            } catch (e: Exception) {
                ShellResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Không kết nối được ADB TCP ($host:$port): ${e.message ?: "Kết nối bị từ chối"}",
                    command = command
                )
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private data class AdbPacket(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payload: ByteArray
    )

    private fun readPacket(input: DataInputStream): AdbPacket {
        val header = ByteArray(24)
        input.readFully(header)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = bb.int
        val arg0 = bb.int
        val arg1 = bb.int
        val length = bb.int
        bb.int // crc32
        bb.int // magic
        val payload = if (length > 0) {
            ByteArray(length).also { input.readFully(it) }
        } else {
            ByteArray(0)
        }
        return AdbPacket(command, arg0, arg1, payload)
    }

    private fun sendPacket(
        output: OutputStream,
        command: Int,
        arg0: Int,
        arg1: Int,
        payload: ByteArray
    ) {
        var crc = 0
        for (b in payload) {
            crc += (b.toInt() and 0xFF)
        }
        val bb = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(command)
        bb.putInt(arg0)
        bb.putInt(arg1)
        bb.putInt(payload.size)
        bb.putInt(crc)
        bb.putInt(command xor -1)
        output.write(bb.array())
        if (payload.isNotEmpty()) {
            output.write(payload)
        }
        output.flush()
    }

    private fun signToken(token: ByteArray): ByteArray {
        val digestInfo = ByteArray(SHA1_DIGEST_INFO_PREFIX.size + token.size)
        System.arraycopy(SHA1_DIGEST_INFO_PREFIX, 0, digestInfo, 0, SHA1_DIGEST_INFO_PREFIX.size)
        System.arraycopy(token, 0, digestInfo, SHA1_DIGEST_INFO_PREFIX.size, token.size)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.private)
        return cipher.doFinal(digestInfo)
    }

    private fun encodeAdbPublicKey(): ByteArray {
        val rsaPub = keyPair.public as RSAPublicKey
        val n = rsaPub.modulus
        val words = 64
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = r32.subtract(n.mod(r32).modInverse(r32)).toInt()
        val rr = BigInteger.ONE.shiftLeft(words * 64).mod(n)

        val struct = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        struct.putInt(words)
        struct.putInt(n0inv)
        writeBigIntWords(struct, n, words)
        writeBigIntWords(struct, rr, words)
        struct.putInt(rsaPub.publicExponent.toInt())

        val b64 = Base64.encodeToString(struct.array(), Base64.NO_WRAP)
        return "$b64 appforge@android\u0000".toByteArray(Charsets.UTF_8)
    }

    private fun writeBigIntWords(bb: ByteBuffer, value: BigInteger, words: Int) {
        val mask = BigInteger.valueOf(0xFFFFFFFFL)
        var current = value
        for (i in 0 until words) {
            bb.putInt(current.and(mask).toInt())
            current = current.shiftRight(32)
        }
    }

    private fun loadOrCreateKeyPair(): KeyPair {
        val dir = File(context.filesDir, "adb_keys").apply { mkdirs() }
        val privFile = File(dir, "adb_priv.der")
        val pubFile = File(dir, "adb_pub.der")
        if (privFile.exists() && pubFile.exists()) {
            try {
                val kf = KeyFactory.getInstance("RSA")
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privFile.readBytes()))
                val pub = kf.generatePublic(X509EncodedKeySpec(pubFile.readBytes()))
                return KeyPair(pub, priv)
            } catch (_: Exception) {
            }
        }
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val pair = gen.generateKeyPair()
        privFile.writeBytes(pair.private.encoded)
        pubFile.writeBytes(pair.public.encoded)
        return pair
    }
}
