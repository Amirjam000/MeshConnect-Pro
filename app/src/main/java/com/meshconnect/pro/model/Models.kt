package com.meshconnect.pro.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

enum class PacketType {
    @SerializedName("TEXT") TEXT,
    @SerializedName("FILE_META") FILE_META,
    @SerializedName("PTT_AUDIO") PTT_AUDIO,
    @SerializedName("WEBRTC_SIGNAL") WEBRTC_SIGNAL,
    @SerializedName("ACK") ACK,
    @SerializedName("READ_RECEIPT") READ_RECEIPT,
    @SerializedName("EDIT_MESSAGE") EDIT_MESSAGE,
    @SerializedName("DELETE_MESSAGE") DELETE_MESSAGE,
    @SerializedName("REACTION") REACTION,
    @SerializedName("PIN_MESSAGE") PIN_MESSAGE,
    @SerializedName("HIGH_SPEED_TRANSFER_INVITE") HIGH_SPEED_TRANSFER_INVITE,
    @SerializedName("HIGH_SPEED_TRANSFER_ACK") HIGH_SPEED_TRANSFER_ACK
}

enum class MessageStatus {
    PENDING,
    SENT,
    DELIVERED,
    SEEN
}

enum class RouteType {
    DIRECT_P2P,
    MESH_RELAY,
    HIGH_SPEED_DIRECT
}

enum class TransferStatus {
    WAITING,
    CONNECTING,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class SeenInfo(
    val userId: String,
    val userName: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class QuotedMessage(
    val id: String,
    val senderName: String,
    val text: String
)

data class FileAttachmentInfo(
    val fileName: String,
    val fileSize: Long,
    val fileUri: String? = null,
    val mimeType: String = "*/*",
    val isHighSpeedTransfer: Boolean = false,
    val transferId: String? = null
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val chatId: String = "PUBLIC",
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val isFromMe: Boolean = false,
    val replyTo: QuotedMessage? = null,
    val isEdited: Boolean = false,
    val isPinned: Boolean = false,
    val reactions: Map<String, List<String>> = emptyMap(),
    val seenBy: List<SeenInfo> = emptyList(),
    val fileAttachment: FileAttachmentInfo? = null,
    val routeType: RouteType = RouteType.DIRECT_P2P,
    val hopCount: Int = 0
)

data class MeshPacket(
    @SerializedName("packet_id") val packetId: String = UUID.randomUUID().toString(),
    @SerializedName("type") val type: PacketType = PacketType.TEXT,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("sender_name") val senderName: String,
    @SerializedName("recipient_id") val recipientId: String? = null,
    @SerializedName("content") val content: String,
    @SerializedName("ttl") var ttl: Int = 5,
    @SerializedName("hop_count") var hopCount: Int = 0,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("relay_nodes") val relayNodes: MutableList<String> = mutableListOf()
)

data class HighSpeedFileTransfer(
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val senderIp: String,
    val senderPort: Int,
    val bytesTransferred: Long = 0,
    val speedBytesPerSec: Long = 0,
    val isOutgoing: Boolean = false,
    val status: TransferStatus = TransferStatus.WAITING
)

data class PeerDevice(
    val id: String,
    val name: String,
    val isConnected: Boolean = true,
    val ipAddress: String? = null,
    val lastSeen: Long = System.currentTimeMillis()
)

data class WebRtcSignal(
    @SerializedName("type") val type: String,
    @SerializedName("sdp") val sdp: String? = null,
    @SerializedName("candidate") val candidate: String? = null,
    @SerializedName("sdpMid") val sdpMid: String? = null,
    @SerializedName("sdpMLineIndex") val sdpMLineIndex: Int? = null,
    @SerializedName("senderId") val senderId: String = "",
    @SerializedName("senderName") val senderName: String = "",
    @SerializedName("targetId") val targetId: String = ""
)
