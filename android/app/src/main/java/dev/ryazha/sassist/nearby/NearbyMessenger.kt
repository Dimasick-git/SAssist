package dev.ryazha.sassist.nearby

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

data class NearbyPeer(val endpointId: String, val displayName: String)
data class NearbyTransfer(
    val payloadId: Long,
    val name: String,
    val mime: String,
    val progress: Int,
    val incoming: Boolean,
    val state: String,
    val completedFile: File? = null
)

private data class NearbyFileMeta(val name: String, val mime: String)

/**
 * Local, explicit peer-to-peer transport. Nearby Connections picks Bluetooth,
 * BLE and Wi-Fi Direct based on the radios both nearby devices can use.
 */
class NearbyMessenger(
    context: Context,
    private val onPeers: (List<NearbyPeer>, List<NearbyPeer>) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onPayload: (String) -> Unit,
    private val onFileTransfer: (NearbyTransfer) -> Unit
) {
    companion object { private const val SERVICE_ID = "dev.ryazha.sassist.offline.v1" }

    private val app = context.applicationContext
    private val client: ConnectionsClient = Nearby.getConnectionsClient(app)
    private val found = linkedMapOf<String, NearbyPeer>()
    private val pending = linkedMapOf<String, NearbyPeer>()
    private val connected = linkedSetOf<String>()
    private val incomingFiles = ConcurrentHashMap<Long, Payload>()
    private val completedFiles = ConcurrentHashMap<Long, Payload>()
    private val fileMeta = ConcurrentHashMap<Long, NearbyFileMeta>()
    private val outgoingFiles = ConcurrentHashMap<Long, NearbyFileMeta>()
    private val outgoingTempFiles = ConcurrentHashMap<Long, File>()
    private var localName = "SAssist"

    private fun publishPeers() = onPeers(found.values.toList(), pending.values.toList())

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            pending[endpointId] = NearbyPeer(endpointId, info.endpointName.ifBlank { "SAssist device" })
            publishPeers(); onStatus("pairing")
        }
        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            pending.remove(endpointId)
            if (resolution.status.isSuccess) {
                connected.add(endpointId); found.remove(endpointId); onStatus("connected")
            } else onStatus("connection_failed")
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
        override fun onEndpointLost(endpointId: String) { found.remove(endpointId); publishPeers() }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> payload.asBytes()?.toString(Charsets.UTF_8)?.let(::receiveBytes)
                Payload.Type.FILE -> {
                    incomingFiles[payload.id] = payload
                    val meta = fileMeta[payload.id] ?: NearbyFileMeta("Файл", "application/octet-stream")
                    onFileTransfer(NearbyTransfer(payload.id, meta.name, meta.mime, 0, incoming = true, state = "receiving"))
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val id = update.payloadId
            val incoming = incomingFiles.containsKey(id)
            val meta = (if (incoming) fileMeta[id] else outgoingFiles[id]) ?: return
            val progress = if (update.totalBytes > 0) ((update.bytesTransferred * 100L) / update.totalBytes).toInt().coerceIn(0, 100) else 0
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> onFileTransfer(NearbyTransfer(id, meta.name, meta.mime, progress, incoming, if (incoming) "receiving" else "sending"))
                PayloadTransferUpdate.Status.SUCCESS -> {
                    if (incoming) {
                        val filePayload = incomingFiles.remove(id)
                        if (filePayload != null) completedFiles[id] = filePayload
                        finishIncomingFile(id)
                    } else {
                        outgoingTempFiles.remove(id)?.delete()
                        outgoingFiles.remove(id)
                        onFileTransfer(NearbyTransfer(id, meta.name, meta.mime, 100, incoming = false, state = "sent"))
                    }
                }
                else -> {
                    outgoingTempFiles.remove(id)?.delete()
                    incomingFiles.remove(id); completedFiles.remove(id); outgoingFiles.remove(id)
                    onFileTransfer(NearbyTransfer(id, meta.name, meta.mime, progress, incoming, state = "failed"))
                }
            }
        }
    }

    private fun receiveBytes(raw: String) {
        val frame = try { JSONObject(raw) } catch (_: Exception) { onPayload(raw); return }
        if (frame.optString("type") != "nearbyFileMeta") { onPayload(raw); return }
        val id = frame.optLong("payloadId", -1L)
        if (id < 0L) return
        fileMeta[id] = NearbyFileMeta(
            sanitizeName(frame.optString("name", "file")),
            frame.optString("mime", "application/octet-stream")
        )
        finishIncomingFile(id)
    }

    private fun finishIncomingFile(id: Long) {
        val payload = completedFiles[id] ?: return
        val meta = fileMeta[id] ?: return
        completedFiles.remove(id); fileMeta.remove(id)
        thread(name = "nearby-file-save") {
            try {
                val source = payload.asFile()?.asUri() ?: throw IllegalStateException("No received file URI")
                val targetDir = File(app.cacheDir, "shared_attachments").apply { mkdirs() }
                val target = File(targetDir, "nearby_${System.currentTimeMillis()}_${sanitizeName(meta.name)}")
                app.contentResolver.openInputStream(source)?.use { input -> target.outputStream().use(input::copyTo) }
                    ?: throw IllegalStateException("Cannot read received file")
                app.contentResolver.delete(source, null, null)
                onFileTransfer(NearbyTransfer(id, meta.name, meta.mime, 100, incoming = true, state = "received", completedFile = target))
            } catch (_: Exception) {
                onFileTransfer(NearbyTransfer(id, meta.name, meta.mime, 0, incoming = true, state = "failed"))
            }
        }
    }

    fun start(endpointName: String) {
        localName = endpointName.take(40).ifBlank { "SAssist" }
        client.stopDiscovery(); client.stopAdvertising()
        client.startAdvertising(localName, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
            .addOnFailureListener { onStatus("unavailable") }
        client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
            .addOnSuccessListener { onStatus("discovering") }
            .addOnFailureListener { onStatus("unavailable") }
    }

    fun requestConnection(endpointId: String) {
        val peer = found[endpointId] ?: return
        pending[endpointId] = peer; publishPeers()
        client.requestConnection(localName, endpointId, lifecycle).addOnFailureListener {
            pending.remove(endpointId); publishPeers(); onStatus("connection_failed")
        }
    }

    fun accept(endpointId: String) {
        if (endpointId !in pending) return
        client.acceptConnection(endpointId, payloadCallback).addOnFailureListener { onStatus("connection_failed") }
    }

    fun reject(endpointId: String) { pending.remove(endpointId); client.rejectConnection(endpointId); publishPeers() }

    fun send(text: String): Boolean {
        if (connected.isEmpty()) return false
        client.sendPayload(connected.toList(), Payload.fromBytes(text.toByteArray(Charsets.UTF_8)))
            .addOnFailureListener { onStatus("send_failed") }
        return true
    }

    /** Copies only to app cache, then sends file bytes directly to paired nearby devices -- never to the server. */
    fun sendFile(uri: Uri): Boolean {
        if (connected.isEmpty()) return false
        return try {
            val name = displayName(uri)
            val mime = app.contentResolver.getType(uri) ?: "application/octet-stream"
            val outDir = File(app.cacheDir, "nearby_outgoing").apply { mkdirs() }
            val copy = File(outDir, "${System.currentTimeMillis()}_${sanitizeName(name)}")
            app.contentResolver.openInputStream(uri)?.use { input -> copy.outputStream().use(input::copyTo) }
                ?: return false
            val payload = Payload.fromFile(copy)
            val meta = NearbyFileMeta(name, mime)
            outgoingFiles[payload.id] = meta
            outgoingTempFiles[payload.id] = copy
            val metadata = JSONObject().put("type", "nearbyFileMeta").put("payloadId", payload.id).put("name", name).put("mime", mime)
            client.sendPayload(connected.toList(), Payload.fromBytes(metadata.toString().toByteArray(Charsets.UTF_8)))
            client.sendPayload(connected.toList(), payload).addOnFailureListener { onStatus("send_failed") }
            onFileTransfer(NearbyTransfer(payload.id, name, mime, 0, incoming = false, state = "sending"))
            true
        } catch (_: Exception) {
            onStatus("send_failed"); false
        }
    }

    fun stop() {
        client.stopAllEndpoints(); client.stopAdvertising(); client.stopDiscovery()
        outgoingTempFiles.values.forEach { it.delete() }
        found.clear(); pending.clear(); connected.clear(); incomingFiles.clear(); completedFiles.clear(); fileMeta.clear(); outgoingFiles.clear(); outgoingTempFiles.clear()
        publishPeers(); onStatus("stopped")
    }

    private fun displayName(uri: Uri): String {
        var name = "file"
        app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index) ?: name
        }
        return sanitizeName(name)
    }

    private fun sanitizeName(value: String): String = value.replace(Regex("[^A-Za-zА-Яа-я0-9._ -]"), "_").take(120).ifBlank { "file" }
}
