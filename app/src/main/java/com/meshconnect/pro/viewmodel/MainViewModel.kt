package com.meshconnect.pro.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.nearby.connection.Payload
import com.google.gson.Gson
import com.meshconnect.pro.audio.AudioStreamManager
import com.meshconnect.pro.mesh.AutoSyncMeshManager
import com.meshconnect.pro.model.*
import com.meshconnect.pro.transfer.HighSpeedTransferEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val PREFS_NAME = "meshconnect_pro_prefs"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    val myId: String = prefs.getString("user_id", null) ?: run {
        val newId = "usr_" + UUID.randomUUID().toString().substring(0, 6)
        prefs.edit().putString("user_id", newId).apply()
        newId
    }

    private val _myName = MutableStateFlow(prefs.getString("user_name", "User_${myId.takeLast(4)}") ?: "User")
    val myName: StateFlow<String> = _myName.asStateFlow()

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // موتورهای اصلی
    val meshManager = AutoSyncMeshManager(application, myId, _myName.value)
    val highSpeedEngine = HighSpeedTransferEngine(application)
    val audioStreamManager = AudioStreamManager()

    // وضعیت‌های چت و پیام‌ها
    private val _publicMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val publicMessages: StateFlow<List<ChatMessage>> = _publicMessages.asStateFlow()

    private val _pinnedMessage = MutableStateFlow<ChatMessage?>(null)
    val pinnedMessage: StateFlow<ChatMessage?> = _pinnedMessage.asStateFlow()

    private val _replyingTo = MutableStateFlow<QuotedMessage?>(null)
    val replyingTo: StateFlow<QuotedMessage?> = _replyingTo.asStateFlow()

    private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
    val editingMessage: StateFlow<ChatMessage?> = _editingMessage.asStateFlow()

    // تماس تصویری WebRTC
    private val _incomingCallSignal = MutableStateFlow<WebRtcSignal?>(null)
    val incomingCallSignal: StateFlow<WebRtcSignal?> = _incomingCallSignal.asStateFlow()

    init {
        // شروع مش خودکار پس‌زمینه
        meshManager.onPacketReceived = { packet ->
            handleIncomingMeshPacket(packet)
        }
        meshManager.onPayloadStreamReceived = { payload ->
            payload.asStream()?.asInputStream()?.let { inputStream ->
                audioStreamManager.playIncomingAudioStream(inputStream)
            }
        }
        meshManager.startAutoMesh()
    }

    fun toggleDarkMode() {
        _isDarkMode.update { current ->
            val next = !current
            prefs.edit().putBoolean("dark_mode", next).apply()
            next
        }
    }

    fun updateMyName(newName: String) {
        val clean = newName.trim()
        if (clean.isNotEmpty()) {
            _myName.value = clean
            prefs.edit().putString("user_name", clean).apply()
        }
    }

    fun setReplyMessage(message: ChatMessage) {
        _replyingTo.value = QuotedMessage(
            id = message.id,
            senderName = message.senderName,
            text = message.text
        )
    }

    fun cancelReply() {
        _replyingTo.value = null
    }

    fun startEditingMessage(message: ChatMessage) {
        if (message.isFromMe) {
            _editingMessage.value = message
        }
    }

    fun cancelEditing() {
        _editingMessage.value = null
    }

    // =================================================================
    // ارسال پیام متنی، نقل‌قول و ویرایش
    // =================================================================

    fun sendTextMessage(text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        val editing = _editingMessage.value
        if (editing != null) {
            // ویرایش پیام موجود
            editMessage(editing.id, cleanText)
            _editingMessage.value = null
            return
        }

        val currentReply = _replyingTo.value
        val chatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            chatId = "PUBLIC",
            senderId = myId,
            senderName = _myName.value,
            text = cleanText,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT,
            isFromMe = true,
            replyTo = currentReply
        )

        _publicMessages.update { it + chatMessage }
        _replyingTo.value = null

        val packet = MeshPacket(
            packetId = chatMessage.id,
            type = PacketType.TEXT,
            senderId = myId,
            senderName = _myName.value,
            recipientId = null,
            content = gson.toJson(chatMessage)
        )
        meshManager.broadcastPacket(packet)
    }

    fun editMessage(messageId: String, newText: String) {
        _publicMessages.update { list ->
            list.map { msg ->
                if (msg.id == messageId && msg.isFromMe) {
                    msg.copy(text = newText, isEdited = true)
                } else msg
            }
        }
        val editContent = mapOf("messageId" to messageId, "newText" to newText)
        val packet = MeshPacket(
            type = PacketType.EDIT_MESSAGE,
            senderId = myId,
            senderName = _myName.value,
            content = gson.toJson(editContent)
        )
        meshManager.broadcastPacket(packet)
    }

    fun deleteMessage(messageId: String, forEveryone: Boolean) {
        _publicMessages.update { list -> list.filter { it.id != messageId } }
        if (forEveryone) {
            val packet = MeshPacket(
                type = PacketType.DELETE_MESSAGE,
                senderId = myId,
                senderName = _myName.value,
                content = messageId
            )
            meshManager.broadcastPacket(packet)
        }
    }

    fun pinMessage(message: ChatMessage) {
        val updated = message.copy(isPinned = true)
        _pinnedMessage.value = updated
        _publicMessages.update { list ->
            list.map { if (it.id == message.id) updated else it }
        }
        val packet = MeshPacket(
            type = PacketType.PIN_MESSAGE,
            senderId = myId,
            senderName = _myName.value,
            content = message.id
        )
        meshManager.broadcastPacket(packet)
    }

    fun unpinMessage() {
        val current = _pinnedMessage.value ?: return
        _pinnedMessage.value = null
        _publicMessages.update { list ->
            list.map { if (it.id == current.id) it.copy(isPinned = false) else it }
        }
        val packet = MeshPacket(
            type = PacketType.PIN_MESSAGE,
            senderId = myId,
            senderName = _myName.value,
            content = "UNPIN"
        )
        meshManager.broadcastPacket(packet)
    }

    fun toggleReaction(messageId: String, emoji: String) {
        _publicMessages.update { list ->
            list.map { msg ->
                if (msg.id == messageId) {
                    val currentReactions = msg.reactions.toMutableMap()
                    val currentUsers = currentReactions[emoji]?.toMutableList() ?: mutableListOf()
                    if (currentUsers.contains(_myName.value)) {
                        currentUsers.remove(_myName.value)
                        if (currentUsers.isEmpty()) currentReactions.remove(emoji)
                        else currentReactions[emoji] = currentUsers
                    } else {
                        currentUsers.add(_myName.value)
                        currentReactions[emoji] = currentUsers
                    }
                    msg.copy(reactions = currentReactions)
                } else msg
            }
        }
        val reactionData = mapOf("messageId" to messageId, "emoji" to emoji, "userName" to _myName.value)
        val packet = MeshPacket(
            type = PacketType.REACTION,
            senderId = myId,
            senderName = _myName.value,
            content = gson.toJson(reactionData)
        )
        meshManager.broadcastPacket(packet)
    }

    fun markMessageAsSeen(messageId: String) {
        val msg = _publicMessages.value.find { it.id == messageId } ?: return
        if (msg.isFromMe) return
        if (msg.seenBy.any { it.userId == myId }) return

        val seenInfo = SeenInfo(userId = myId, userName = _myName.value)
        _publicMessages.update { list ->
            list.map {
                if (it.id == messageId) it.copy(seenBy = it.seenBy + seenInfo) else it
            }
        }

        val packet = MeshPacket(
            type = PacketType.READ_RECEIPT,
            senderId = myId,
            senderName = _myName.value,
            content = gson.toJson(mapOf("messageId" to messageId, "seenInfo" to seenInfo))
        )
        meshManager.broadcastPacket(packet)
    }

    // =================================================================
    // ارسال فایل هوشمند (>20 MB با انتقال مستقیم پرسرعت)
    // =================================================================

    fun sendFileFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                var fileName = "file_${System.currentTimeMillis()}"
                var fileSize = 0L

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }

                // کپی موقت به کش برای انتقال
                val tempFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                fileSize = tempFile.length()

                if (fileSize > HighSpeedTransferEngine.HIGH_SPEED_THRESHOLD_BYTES) {
                    // فایل بزرگتر از ۲۰ مگابایت -> انتقال مستقیم فوق سریع
                    highSpeedEngine.startSenderTransfer(tempFile) { transferId, port, ip ->
                        val invite = mapOf(
                            "transferId" to transferId,
                            "fileName" to fileName,
                            "fileSize" to fileSize,
                            "port" to port,
                            "ip" to ip,
                            "senderName" to _myName.value
                        )
                        val packet = MeshPacket(
                            type = PacketType.HIGH_SPEED_TRANSFER_INVITE,
                            senderId = myId,
                            senderName = _myName.value,
                            content = gson.toJson(invite)
                        )
                        meshManager.broadcastPacket(packet)
                    }
                } else {
                    // فایل معمولی زیر ۲۰ مگابایت
                    val attachment = FileAttachmentInfo(
                        fileName = fileName,
                        fileSize = fileSize,
                        fileUri = uri.toString(),
                        isHighSpeedTransfer = false
                    )
                    val chatMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        chatId = "PUBLIC",
                        senderId = myId,
                        senderName = _myName.value,
                        text = "📁 فایل ارسالی: $fileName",
                        isFromMe = true,
                        fileAttachment = attachment
                    )
                    _publicMessages.update { it + chatMessage }
                    val packet = MeshPacket(
                        packetId = chatMessage.id,
                        type = PacketType.FILE_META,
                        senderId = myId,
                        senderName = _myName.value,
                        content = gson.toJson(chatMessage)
                    )
                    meshManager.broadcastPacket(packet)
                }

            } catch (e: Exception) {
                Log.e(TAG, "خطا در آماده‌سازی فایل: ${e.message}")
            }
        }
    }

    // واکی-تاکی بی‌سیم
    fun startWalkieTalkie() {
        audioStreamManager.startStreamingAudio { payload ->
            meshManager.sendStreamPayload(payload)
        }
    }

    fun stopWalkieTalkie() {
        audioStreamManager.stopStreamingAudio()
    }

    // پردازش بسته‌های دریافتی مش
    private fun handleIncomingMeshPacket(packet: MeshPacket) {
        when (packet.type) {
            PacketType.TEXT -> {
                try {
                    val message = gson.fromJson(packet.content, ChatMessage::class.java).copy(
                        isFromMe = (packet.senderId == myId),
                        status = MessageStatus.DELIVERED,
                        hopCount = packet.hopCount
                    )
                    _publicMessages.update { list ->
                        if (list.none { it.id == message.id }) list + message else list
                    }
                } catch (e: Exception) {}
            }
            PacketType.EDIT_MESSAGE -> {
                try {
                    val map = gson.fromJson(packet.content, Map::class.java)
                    val msgId = map["messageId"] as? String ?: return
                    val newText = map["newText"] as? String ?: return
                    _publicMessages.update { list ->
                        list.map { if (it.id == msgId) it.copy(text = newText, isEdited = true) else it }
                    }
                } catch (e: Exception) {}
            }
            PacketType.DELETE_MESSAGE -> {
                val msgId = packet.content
                _publicMessages.update { list -> list.filter { it.id != msgId } }
            }
            PacketType.PIN_MESSAGE -> {
                if (packet.content == "UNPIN") {
                    _pinnedMessage.value = null
                } else {
                    val msgId = packet.content
                    val msg = _publicMessages.value.find { it.id == msgId }
                    if (msg != null) _pinnedMessage.value = msg.copy(isPinned = true)
                }
            }
            PacketType.REACTION -> {
                try {
                    val map = gson.fromJson(packet.content, Map::class.java)
                    val msgId = map["messageId"] as? String ?: return
                    val emoji = map["emoji"] as? String ?: return
                    val user = map["userName"] as? String ?: return

                    _publicMessages.update { list ->
                        list.map { msg ->
                            if (msg.id == msgId) {
                                val reactions = msg.reactions.toMutableMap()
                                val users = reactions[emoji]?.toMutableList() ?: mutableListOf()
                                if (!users.contains(user)) users.add(user)
                                reactions[emoji] = users
                                msg.copy(reactions = reactions)
                            } else msg
                        }
                    }
                } catch (e: Exception) {}
            }
            PacketType.READ_RECEIPT -> {
                try {
                    val map = gson.fromJson(packet.content, Map::class.java)
                    val msgId = map["messageId"] as? String ?: return
                    val seenInfoMap = map["seenInfo"] as? Map<*, *> ?: return
                    val seenInfo = SeenInfo(
                        userId = seenInfoMap["userId"] as? String ?: "",
                        userName = seenInfoMap["userName"] as? String ?: "",
                        timestamp = (seenInfoMap["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    )

                    _publicMessages.update { list ->
                        list.map { msg ->
                            if (msg.id == msgId) {
                                val currentSeen = msg.seenBy.toMutableList()
                                if (currentSeen.none { it.userId == seenInfo.userId }) {
                                    currentSeen.add(seenInfo)
                                }
                                msg.copy(status = MessageStatus.SEEN, seenBy = currentSeen)
                            } else msg
                        }
                    }
                } catch (e: Exception) {}
            }
            PacketType.HIGH_SPEED_TRANSFER_INVITE -> {
                try {
                    val map = gson.fromJson(packet.content, Map::class.java)
                    val transferId = map["transferId"] as String
                    val fileName = map["fileName"] as String
                    val fileSize = (map["fileSize"] as Number).toLong()
                    val port = (map["port"] as Number).toInt()
                    val ip = map["ip"] as String
                    val senderName = map["senderName"] as String

                    // اضافه کردن اعلان فایل در چت با دکمه دریافت مستقیم
                    val attachment = FileAttachmentInfo(
                        fileName = fileName,
                        fileSize = fileSize,
                        isHighSpeedTransfer = true,
                        transferId = transferId
                    )
                    val chatMessage = ChatMessage(
                        id = transferId,
                        chatId = "PUBLIC",
                        senderId = packet.senderId,
                        senderName = senderName,
                        text = "⚡ فایل پرسرعت مستقیم: $fileName (${fileSize / (1024 * 1024)} MB)",
                        isFromMe = false,
                        fileAttachment = attachment
                    )
                    _publicMessages.update { list ->
                        if (list.none { it.id == transferId }) list + chatMessage else list
                    }

                    // شروع خودکار یا با تایید دریافت
                    highSpeedEngine.startReceiverTransfer(transferId, fileName, fileSize, ip, port) {
                        Log.d(TAG, "دریافت فایل پرسرعت به پایان رسید: ${it.absolutePath}")
                    }
                } catch (e: Exception) {}
            }
            PacketType.WEBRTC_SIGNAL -> {
                try {
                    val signal = gson.fromJson(packet.content, WebRtcSignal::class.java)
                    if (signal.targetId == myId || signal.targetId.isEmpty()) {
                        _incomingCallSignal.value = signal
                    }
                } catch (e: Exception) {}
            }
            else -> {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        meshManager.stopAutoMesh()
        audioStreamManager.stopStreamingAudio()
    }
}
