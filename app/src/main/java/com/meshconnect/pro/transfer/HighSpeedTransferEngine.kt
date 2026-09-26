package com.meshconnect.pro.transfer

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Environment
import android.text.format.Formatter
import android.util.Log
import com.meshconnect.pro.model.HighSpeedFileTransfer
import com.meshconnect.pro.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

/**
 * موتور انتقال مستقیم فوق سریع (High-Speed Direct Socket Engine)
 * برای انتقال فایل‌های حجیم (بالای ۲۰ مگابایت) با حداکثر پهنای باند وای‌فای
 * بدون عبور از گلوگاه رله یا سرویس‌های ابری
 */
class HighSpeedTransferEngine(private val context: Context) {

    companion object {
        private const val TAG = "HighSpeedTransfer"
        const val HIGH_SPEED_THRESHOLD_BYTES = 20 * 1024 * 1024L // 20 MB
        private const val BUFFER_SIZE = 262144 // 256 KB buffer for max throughput
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _activeTransfer = MutableStateFlow<HighSpeedFileTransfer?>(null)
    val activeTransfer: StateFlow<HighSpeedFileTransfer?> = _activeTransfer.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var isCancelled = false

    fun getLocalWifiIpAddress(): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    /**
     * شروع میزبانی و ارسال فایل با انتقال مستقیم پرسرعت
     */
    fun startSenderTransfer(
        file: File,
        onInviteReady: (transferId: String, port: Int, ip: String) -> Unit
    ) {
        scope.launch {
            isCancelled = false
            val transferId = UUID.randomUUID().toString()
            val localIp = getLocalWifiIpAddress()

            try {
                serverSocket = ServerSocket(0)
                val port = serverSocket!!.localPort

                val transfer = HighSpeedFileTransfer(
                    transferId = transferId,
                    fileName = file.name,
                    fileSize = file.length(),
                    senderIp = localIp,
                    senderPort = port,
                    isOutgoing = true,
                    status = TransferStatus.WAITING
                )
                _activeTransfer.value = transfer
                onInviteReady(transferId, port, localIp)

                Log.d(TAG, "سرور انتقال مستقیم پرسرعت در پورت $port فعال شد. در انتظار اتصال گیرنده...")
                val socket = serverSocket!!.accept()
                activeSocket = socket
                _activeTransfer.update { it?.copy(status = TransferStatus.TRANSFERRING) }

                streamFileToSocket(file, socket.getOutputStream())

            } catch (e: Exception) {
                if (!isCancelled) {
                    Log.e(TAG, "خطا در ارسال مستقیم: ${e.message}")
                    _activeTransfer.update { it?.copy(status = TransferStatus.FAILED) }
                }
            } finally {
                cleanUpSockets()
            }
        }
    }

    /**
     * شروع اتصال و دریافت فایل مستقیم پرسرعت از فرستنده
     */
    fun startReceiverTransfer(
        transferId: String,
        fileName: String,
        fileSize: Long,
        senderIp: String,
        senderPort: Int,
        onCompleted: (File) -> Unit
    ) {
        scope.launch {
            isCancelled = false
            val transfer = HighSpeedFileTransfer(
                transferId = transferId,
                fileName = fileName,
                fileSize = fileSize,
                senderIp = senderIp,
                senderPort = senderPort,
                isOutgoing = false,
                status = TransferStatus.CONNECTING
            )
            _activeTransfer.value = transfer

            try {
                val socket = Socket(InetAddress.getByName(senderIp), senderPort)
                activeSocket = socket
                _activeTransfer.update { it?.copy(status = TransferStatus.TRANSFERRING) }

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
                val destFile = File(downloadsDir, fileName)

                receiveFileFromSocket(socket.getInputStream(), destFile, fileSize)

                _activeTransfer.update { it?.copy(status = TransferStatus.COMPLETED) }
                onCompleted(destFile)

            } catch (e: Exception) {
                if (!isCancelled) {
                    Log.e(TAG, "خطا در دریافت مستقیم: ${e.message}")
                    _activeTransfer.update { it?.copy(status = TransferStatus.FAILED) }
                }
            } finally {
                cleanUpSockets()
            }
        }
    }

    private fun streamFileToSocket(file: File, out: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytesRead = 0L
        val fileSize = file.length()
        var lastTime = System.currentTimeMillis()
        var bytesSinceLastTime = 0L

        FileInputStream(file).use { fis ->
            while (!isCancelled) {
                val read = fis.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
                totalBytesRead += read
                bytesSinceLastTime += read

                val now = System.currentTimeMillis()
                if (now - lastTime >= 500) {
                    val speed = (bytesSinceLastTime * 1000) / (now - lastTime)
                    _activeTransfer.update {
                        it?.copy(bytesTransferred = totalBytesRead, speedBytesPerSec = speed)
                    }
                    lastTime = now
                    bytesSinceLastTime = 0L
                }
            }
            out.flush()
        }

        if (!isCancelled) {
            _activeTransfer.update {
                it?.copy(bytesTransferred = fileSize, status = TransferStatus.COMPLETED)
            }
        }
    }

    private fun receiveFileFromSocket(input: InputStream, destFile: File, expectedSize: Long) {
        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytesRead = 0L
        var lastTime = System.currentTimeMillis()
        var bytesSinceLastTime = 0L

        FileOutputStream(destFile).use { fos ->
            while (!isCancelled && totalBytesRead < expectedSize) {
                val read = input.read(buffer)
                if (read == -1) break
                fos.write(buffer, 0, read)
                totalBytesRead += read
                bytesSinceLastTime += read

                val now = System.currentTimeMillis()
                if (now - lastTime >= 500) {
                    val speed = (bytesSinceLastTime * 1000) / (now - lastTime)
                    _activeTransfer.update {
                        it?.copy(bytesTransferred = totalBytesRead, speedBytesPerSec = speed)
                    }
                    lastTime = now
                    bytesSinceLastTime = 0L
                }
            }
            fos.flush()
        }
    }

    fun cancelTransfer() {
        isCancelled = true
        _activeTransfer.update { it?.copy(status = TransferStatus.CANCELLED) }
        cleanUpSockets()
    }

    fun dismissTransfer() {
        _activeTransfer.value = null
    }

    private fun cleanUpSockets() {
        try { activeSocket?.close() } catch (e: Exception) {}
        try { serverSocket?.close() } catch (e: Exception) {}
        activeSocket = null
        serverSocket = null
    }
}
