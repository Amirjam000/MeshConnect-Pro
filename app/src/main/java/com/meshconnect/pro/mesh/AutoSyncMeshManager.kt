package com.meshconnect.pro.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import com.meshconnect.pro.model.MeshPacket
import com.meshconnect.pro.model.PacketType
import com.meshconnect.pro.model.PeerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.LinkedHashMap

/**
 * مدیر مش و همگام‌سازی خودکار و بی‌درنگ چت عمومی (Auto-Sync Mesh Manager)
 * بدون نیاز به تایید دستی - اتصال خودکار تمام دستگاه‌های اطراف در چت عمومی
 */
class AutoSyncMeshManager(
    private val context: Context,
    val myDeviceId: String,
    val myDeviceName: String
) {
    companion object {
        private const val TAG = "AutoSyncMesh"
        private const val SERVICE_ID = "com.meshconnect.pro.mesh"
        private val STRATEGY = Strategy.P2P_CLUSTER
        private const val MAX_SEEN_CACHE = 5000
    }

    private val nearbyClient = Nearby.getConnectionsClient(context)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    // کش مهار بسته‌های تکراری مش (LRU Deduplication Cache)
    private val seenPackets = Collections.synchronizedSet(
        Collections.newSetFromMap(
            object : LinkedHashMap<String, Boolean>(MAX_SEEN_CACHE, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                    return size > MAX_SEEN_CACHE
                }
            }
        )
    )

    // اتصالات فعال
    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    private val endpointMap = Collections.synchronizedMap(mutableMapOf<String, String>()) // endpointId -> deviceName

    // کالبک‌ها
    var onPacketReceived: ((MeshPacket) -> Unit)? = null
    var onPayloadStreamReceived: ((Payload) -> Unit)? = null

    fun startAutoMesh() {
        startAdvertising()
        startDiscovery()
        Log.d(TAG, "شبکه مش خودکار فعال شد: شناسه $myDeviceId")
    }

    fun stopAutoMesh() {
        try {
            nearbyClient.stopAdvertising()
            nearbyClient.stopDiscovery()
            nearbyClient.stopAllEndpoints()
        } catch (e: Exception) {}
        endpointMap.clear()
        _connectedPeers.value = emptyList()
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        nearbyClient.startAdvertising(
            myDeviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "تبلیغ حضور در شبکه مش آغاز شد.")
        }.addOnFailureListener {
            Log.w(TAG, "خطا در شروع تبلیغ: ${it.message}")
        }
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        nearbyClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "کشف خودکار همتاها آغاز شد.")
        }.addOnFailureListener {
            Log.w(TAG, "خطا در شروع کشف: ${it.message}")
        }
    }

    // کشف همتای جدید -> درخواست اتصال خودکار
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "همتای جدید کشف شد: ${info.endpointName} ($endpointId) - درخواست اتصال خودکار...")
            // اتصال کاملاً خودکار برای چت عمومی بدون دخالت کاربر
            nearbyClient.requestConnection(myDeviceName, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { Log.w(TAG, "خطا در اتصال به $endpointId: ${it.message}") }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "همتا از دسترس خارج شد: $endpointId")
        }
    }

    // چرخه حیات اتصال -> تایید اتصال کاملاً خودکار
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "درخواست اتصال دریافت شد از ${connectionInfo.endpointName} - تایید خودکار چت عمومی...")
            endpointMap[endpointId] = connectionInfo.endpointName
            // تایید خودکار اتصال برای تشکیل سریع و بی‌دردسر شبکه مش
            nearbyClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                val peerName = endpointMap[endpointId] ?: "همتای ناشناس"
                Log.d(TAG, "اتصال خودکار مش برقرار شد: $peerName")
                _connectedPeers.update { current ->
                    val updated = current.toMutableList()
                    if (updated.none { it.id == endpointId }) {
                        updated.add(PeerDevice(id = endpointId, name = peerName, isConnected = true))
                    }
                    updated
                }
            } else {
                endpointMap.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            endpointMap.remove(endpointId)
            _connectedPeers.update { current -> current.filter { it.id != endpointId } }
        }
    }

    // دریافت داده‌ها و پردازش بسته مش
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    try {
                        val json = String(bytes, Charsets.UTF_8)
                        val packet = gson.fromJson(json, MeshPacket::class.java)
                        handleIncomingMeshPacket(packet, endpointId)
                    } catch (e: Exception) {
                        Log.e(TAG, "خطا در پردازش بسته مش: ${e.message}")
                    }
                }
                Payload.Type.STREAM -> {
                    onPayloadStreamReceived?.invoke(payload)
                }
                else -> {}
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun handleIncomingMeshPacket(packet: MeshPacket, incomingEndpointId: String) {
        // جلوگیری از بسته‌های تکراری و مهار طوفان برودکست
        if (seenPackets.contains(packet.packetId)) return
        seenPackets.add(packet.packetId)

        // بررسی مخاطب: آیا برای من است یا عمومی؟
        val isForMe = packet.recipientId == null || packet.recipientId == myDeviceId
        if (isForMe) {
            onPacketReceived?.invoke(packet)
        }

        // رله به سایر همتاها بر اساس TTL
        if (packet.ttl > 1) {
            packet.ttl -= 1
            packet.hopCount += 1
            packet.relayNodes.add(myDeviceId)
            relayPacketToOthers(packet, excludeEndpointId = incomingEndpointId)
        }
    }

    fun broadcastPacket(packet: MeshPacket) {
        seenPackets.add(packet.packetId)
        val json = gson.toJson(packet)
        val payload = Payload.fromBytes(json.toByteArray(Charsets.UTF_8))
        val endpoints = endpointMap.keys.toList()
        if (endpoints.isNotEmpty()) {
            nearbyClient.sendPayload(endpoints, payload)
        }
    }

    private fun relayPacketToOthers(packet: MeshPacket, excludeEndpointId: String) {
        val targets = endpointMap.keys.filter { it != excludeEndpointId }
        if (targets.isEmpty()) return
        val json = gson.toJson(packet)
        val payload = Payload.fromBytes(json.toByteArray(Charsets.UTF_8))
        nearbyClient.sendPayload(targets, payload)
    }

    fun sendStreamPayload(payload: Payload) {
        val endpoints = endpointMap.keys.toList()
        if (endpoints.isNotEmpty()) {
            nearbyClient.sendPayload(endpoints, payload)
        }
    }
}
