package dev.ryazha.sassist.nearby

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

data class NearbyPeer(val endpointId: String, val displayName: String)

/**
 * Local, explicit peer-to-peer transport. Nearby Connections may use Bluetooth,
 * BLE and Wi-Fi Direct depending on what the two nearby devices support.
 */
class NearbyMessenger(
    context: Context,
    private val onPeers: (List<NearbyPeer>, List<NearbyPeer>) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onPayload: (String) -> Unit
) {
    companion object { private const val SERVICE_ID = "dev.ryazha.sassist.offline.v1" }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val found = linkedMapOf<String, NearbyPeer>()
    private val pending = linkedMapOf<String, NearbyPeer>()
    private val connected = linkedSetOf<String>()
    private var localName = "SAssist"

    private fun publishPeers() = onPeers(found.values.toList(), pending.values.toList())

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            pending[endpointId] = NearbyPeer(endpointId, info.endpointName.ifBlank { "SAssist device" })
            publishPeers()
            onStatus("pairing")
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            pending.remove(endpointId)
            if (resolution.status.isSuccess) {
                connected.add(endpointId)
                found.remove(endpointId)
                onStatus("connected")
            } else {
                onStatus("connection_failed")
            }
            publishPeers()
        }

        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            onStatus(if (connected.isEmpty()) "discovering" else "connected")
            publishPeers()
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (endpointId !in connected && endpointId !in pending) {
                found[endpointId] = NearbyPeer(endpointId, info.endpointName.ifBlank { "SAssist device" })
                publishPeers()
            }
        }

        override fun onEndpointLost(endpointId: String) {
            found.remove(endpointId)
            publishPeers()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.toString(Charsets.UTF_8)?.let(onPayload)
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    fun start(endpointName: String) {
        localName = endpointName.take(40).ifBlank { "SAssist" }
        client.stopDiscovery()
        client.stopAdvertising()
        client.startAdvertising(localName, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
            .addOnFailureListener { onStatus("unavailable") }
        client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
            .addOnSuccessListener { onStatus("discovering") }
            .addOnFailureListener { onStatus("unavailable") }
    }

    fun requestConnection(endpointId: String) {
        val peer = found[endpointId] ?: return
        pending[endpointId] = peer
        publishPeers()
        client.requestConnection(localName, endpointId, lifecycle).addOnFailureListener {
            pending.remove(endpointId); publishPeers(); onStatus("connection_failed")
        }
    }

    /** Accept is only called after the user explicitly taps a displayed pairing request. */
    fun accept(endpointId: String) {
        if (endpointId !in pending) return
        client.acceptConnection(endpointId, payloadCallback).addOnFailureListener { onStatus("connection_failed") }
    }

    fun reject(endpointId: String) {
        pending.remove(endpointId)
        client.rejectConnection(endpointId)
        publishPeers()
    }

    fun send(text: String): Boolean {
        if (connected.isEmpty()) return false
        client.sendPayload(connected.toList(), Payload.fromBytes(text.toByteArray(Charsets.UTF_8)))
            .addOnFailureListener { onStatus("send_failed") }
        return true
    }

    fun stop() {
        client.stopAllEndpoints()
        client.stopAdvertising()
        client.stopDiscovery()
        found.clear(); pending.clear(); connected.clear()
        publishPeers()
        onStatus("stopped")
    }
}
