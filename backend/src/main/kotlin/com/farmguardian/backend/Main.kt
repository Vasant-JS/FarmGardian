package com.farmguardian.backend

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun main() {
    embeddedServer(
        Netty,
        host = "0.0.0.0",
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
    ) {
        farmGuardianBackend()
    }.start(wait = true)
}

fun Application.farmGuardianBackend() {
    install(WebSockets)
    val sessions = FarmSessions()

    routing {
        webSocket("/ws") {
            var registration: DeviceRegistration? = null
            try {
                incoming.consumeEach { frame ->
                    val currentRegistration = registration
                    when (frame) {
                        is Frame.Text -> {
                            val envelope = runCatching { json.decodeFromString<GuardianMessage>(frame.readText()) }.getOrNull()
                                ?: return@consumeEach
                            if (currentRegistration == null) {
                                registration = sessions.tryRegister(envelope, this)
                                return@consumeEach
                            }
                            sessions.route(currentRegistration, envelope, this)
                        }
                        is Frame.Binary -> {
                            if (currentRegistration != null) sessions.routeBinary(currentRegistration, frame.data)
                        }
                        else -> Unit
                    }
                }
            } finally {
                registration?.let { sessions.unregister(it, this) }
            }
        }
    }
}

private class FarmSessions {
    private val users = ConcurrentHashMap<String, String>()
    private val nodes = ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSession>>()
    private val nodeNames = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
    private val controllers = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val lastStatus = ConcurrentHashMap<String, ConcurrentHashMap<String, NodeStatusPayload>>()

    suspend fun tryRegister(envelope: GuardianMessage, session: WebSocketSession): DeviceRegistration? {
        if (envelope.type != "LOGIN") {
            session.sendAck(envelope.id, AckStatus.FAILED, "ExpectedLogin")
            return null
        }

        val login = envelope.login
        if (login == null || login.username.isBlank() || login.password.isBlank()) {
            session.sendAck(envelope.id, AckStatus.FAILED, "MissingLogin")
            return null
        }

        val existingPassword = users.putIfAbsent(login.username, login.password)
        if (existingPassword != null && existingPassword != login.password) {
            session.sendAck(envelope.id, AckStatus.FAILED, "InvalidLogin")
            return null
        }

        val nodeId = login.nodeId?.takeIf { it.isNotBlank() } ?: "controller"
        val registration = DeviceRegistration(login.role, login.username, nodeId)
        when (registration.role) {
            DeviceRole.NODE -> {
                nodes.computeIfAbsent(registration.username) { ConcurrentHashMap() }[registration.nodeId] = session
                nodeNames.computeIfAbsent(registration.username) { ConcurrentHashMap() }[registration.nodeId] =
                    login.friendlyName ?: registration.nodeId
                val statuses = lastStatus.computeIfAbsent(registration.username) { ConcurrentHashMap() }
                val onlineStatus = (statuses[registration.nodeId] ?: NodeStatusPayload()).copy(online = true)
                statuses[registration.nodeId] = onlineStatus
                broadcastNodeList(registration.username)
                broadcastStatus(registration.username, registration.nodeId, onlineStatus)
            }
            DeviceRole.CONTROLLER -> {
                controllers.computeIfAbsent(registration.username) { ConcurrentHashMap.newKeySet() }.add(session)
                session.sendNodeList(nodeSummaries(registration.username))
            }
        }
        session.sendAck(envelope.id, AckStatus.SUCCESS)
        return registration
    }

    suspend fun unregister(registration: DeviceRegistration, session: WebSocketSession) {
        when (registration.role) {
            DeviceRole.NODE -> {
                nodes[registration.username]?.remove(registration.nodeId, session)
                val statuses = lastStatus.computeIfAbsent(registration.username) { ConcurrentHashMap() }
                val offline = (statuses[registration.nodeId] ?: NodeStatusPayload()).copy(
                    online = false,
                    lastSeen = System.currentTimeMillis(),
                )
                statuses[registration.nodeId] = offline
                broadcastNodeList(registration.username)
                broadcastStatus(registration.username, registration.nodeId, offline)
            }
            DeviceRole.CONTROLLER -> controllers[registration.username]?.remove(session)
        }
    }

    suspend fun route(from: DeviceRegistration, envelope: GuardianMessage, session: WebSocketSession) {
        if (envelope.type == "PING") {
            session.sendMessage(GuardianMessage(type = "PONG"))
            return
        }

        when (from.role) {
            DeviceRole.CONTROLLER -> routeControllerCommand(from.username, envelope)
            DeviceRole.NODE -> routeNodeMessage(from.username, from.nodeId, envelope)
        }
    }

    suspend fun routeBinary(from: DeviceRegistration, bytes: ByteArray) {
        if (from.role != DeviceRole.NODE || bytes.isEmpty()) return
        controllers[from.username]?.forEach { it.sendBinaryFrame(from.nodeId, bytes) }
    }

    private suspend fun routeControllerCommand(username: String, envelope: GuardianMessage) {
        val nodeId = envelope.targetNodeId
        if (nodeId.isNullOrBlank()) {
            controllers[username]?.forEach { it.sendAck(envelope.id, AckStatus.FAILED, "NoNodeSelected") }
            return
        }

        if (envelope.type == "DISCONNECT_NODE") {
            nodes[username]?.remove(nodeId)?.sendMessage(envelope)
            val statuses = lastStatus.computeIfAbsent(username) { ConcurrentHashMap() }
            statuses[nodeId] = (statuses[nodeId] ?: NodeStatusPayload()).copy(online = false, lastSeen = System.currentTimeMillis())
            broadcastNodeList(username)
            controllers[username]?.forEach { it.sendAck(envelope.id, AckStatus.SUCCESS) }
            return
        }

        val node = nodes[username]?.get(nodeId)
        if (node == null) {
            controllers[username]?.forEach { it.sendAck(envelope.id, AckStatus.FAILED, "NodeOffline") }
            return
        }
        node.sendMessage(envelope)
    }

    private suspend fun routeNodeMessage(username: String, nodeId: String, envelope: GuardianMessage) {
        val status = envelope.status
        if (status != null) {
            lastStatus.computeIfAbsent(username) { ConcurrentHashMap() }[nodeId] =
                status.copy(online = true, lastSeen = System.currentTimeMillis())
            broadcastNodeList(username)
        }

        when (envelope.type) {
            "STATUS", "HEARTBEAT", "ACK" -> controllers[username]?.forEach { it.sendMessage(envelope.copy(nodeId = nodeId)) }
            else -> controllers[username]?.forEach { it.sendMessage(envelope.copy(nodeId = nodeId)) }
        }
    }

    private suspend fun broadcastStatus(username: String, nodeId: String, status: NodeStatusPayload) {
        controllers[username]?.forEach { it.sendStatus(nodeId, status) }
    }

    private suspend fun broadcastNodeList(username: String) {
        controllers[username]?.forEach { it.sendNodeList(nodeSummaries(username)) }
    }

    private fun nodeSummaries(username: String): List<NodeSummary> {
        val sessions = nodes[username].orEmpty()
        val names = nodeNames[username].orEmpty()
        val statuses = lastStatus[username].orEmpty()
        return (names.keys + statuses.keys + sessions.keys)
            .distinct()
            .sorted()
            .map { nodeId ->
                val status = statuses[nodeId]
                NodeSummary(
                    nodeId = nodeId,
                    friendlyName = names[nodeId] ?: nodeId,
                    online = sessions.containsKey(nodeId) && status?.online != false,
                    lastSeen = status?.lastSeen ?: System.currentTimeMillis(),
                    status = status,
                )
            }
    }
}

private suspend fun WebSocketSession.sendMessage(message: GuardianMessage) {
    send(Frame.Text(json.encodeToString(GuardianMessage.serializer(), message)))
}

private suspend fun WebSocketSession.sendBinaryFrame(nodeId: String, bytes: ByteArray) {
    val nodeIdBytes = nodeId.encodeToByteArray()
    val header = ByteBuffer.allocate(2 + nodeIdBytes.size + bytes.size)
        .putShort(nodeIdBytes.size.toShort())
        .put(nodeIdBytes)
        .put(bytes)
        .array()
    send(Frame.Binary(true, header))
}

private suspend fun WebSocketSession.sendStatus(nodeId: String, status: NodeStatusPayload) {
    sendMessage(GuardianMessage(type = "STATUS", nodeId = nodeId, status = status))
}

private suspend fun WebSocketSession.sendNodeList(nodes: List<NodeSummary>) {
    sendMessage(GuardianMessage(type = "NODE_LIST", nodes = nodes))
}

private suspend fun WebSocketSession.sendAck(commandId: String, ackStatus: AckStatus, reason: String? = null) {
    sendMessage(
        GuardianMessage(
            type = "ACK",
            ack = AckPayload(
                commandId = commandId,
                status = ackStatus,
                reason = reason,
            ),
        ),
    )
}

private data class DeviceRegistration(
    val role: DeviceRole,
    val username: String,
    val nodeId: String,
)

@Serializable
data class GuardianMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val hello: HelloPayload? = null,
    val login: LoginPayload? = null,
    val targetNodeId: String? = null,
    val nodeId: String? = null,
    val friendlyName: String? = null,
    val camera: CameraConfigPayload? = null,
    val audio: AudioConfigPayload? = null,
    val frame: CameraFramePayload? = null,
    val sound: String? = null,
    val volume: Int? = null,
    val loops: Int? = null,
    val intervalSeconds: Int? = null,
    val status: NodeStatusPayload? = null,
    val nodes: List<NodeSummary> = emptyList(),
    val ack: AckPayload? = null,
    val reason: String? = null,
)

@Serializable
data class LoginPayload(
    val role: DeviceRole,
    val username: String,
    val password: String,
    val nodeId: String? = null,
    val friendlyName: String? = null,
)

@Serializable
data class HelloPayload(
    val role: DeviceRole,
    val nodeId: String,
    val secretKey: String,
    val friendlyName: String? = null,
)

@Serializable
data class NodeStatusPayload(
    val online: Boolean = true,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val network: String? = null,
    val bluetoothConnected: Boolean? = null,
    val speakerName: String? = null,
    val appVersion: String? = null,
    val playbackState: String = "Stopped",
    val currentSound: String? = null,
    val durationSeconds: Int? = null,
    val remainingSeconds: Int? = null,
    val phoneTemperatureCelsius: Float? = null,
    val cameraActive: Boolean = false,
    val micActive: Boolean = false,
    val activeCameraId: String? = null,
    val cameras: List<CameraDevicePayload> = emptyList(),
    val lastSeen: Long = System.currentTimeMillis(),
)

@Serializable
data class CameraConfigPayload(
    val enabled: Boolean,
    val lensFacing: String = "BACK",
    val cameraId: String? = null,
    val fps: Int = 2,
    val quality: Int = 60,
    val width: Int = 640,
    val height: Int = 480,
    val torch: Boolean = false,
)

@Serializable
data class AudioConfigPayload(
    val enabled: Boolean,
    val sampleRate: Int = 16_000,
)

@Serializable
data class CameraDevicePayload(
    val id: String,
    val label: String,
    val lensFacing: String? = null,
    val external: Boolean = false,
)

@Serializable
data class CameraFramePayload(
    val dataBase64: String,
    val width: Int,
    val height: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class NodeSummary(
    val nodeId: String,
    val friendlyName: String,
    val online: Boolean,
    val lastSeen: Long = System.currentTimeMillis(),
    val status: NodeStatusPayload? = null,
)

@Serializable
data class AckPayload(
    val commandId: String,
    val status: AckStatus,
    val reason: String? = null,
)

@Serializable
enum class DeviceRole {
    CONTROLLER,
    NODE,
}

@Serializable
enum class AckStatus {
    SUCCESS,
    FAILED,
}
