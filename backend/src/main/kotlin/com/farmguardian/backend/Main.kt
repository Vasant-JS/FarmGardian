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
                    if (frame !is Frame.Text) return@consumeEach
                    val envelope = runCatching { json.decodeFromString<GuardianMessage>(frame.readText()) }.getOrNull()
                        ?: return@consumeEach

                    val currentRegistration = registration
                    if (currentRegistration == null) {
                        registration = sessions.tryRegister(envelope, this)
                        return@consumeEach
                    }

                    sessions.route(currentRegistration, envelope, this)
                }
            } finally {
                registration?.let { sessions.unregister(it, this) }
            }
        }
    }
}

private class FarmSessions {
    private val nodes = ConcurrentHashMap<String, WebSocketSession>()
    private val controllers = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val lastStatus = ConcurrentHashMap<String, NodeStatusPayload>()
    private val secret = System.getenv("FARM_GUARDIAN_SECRET").orEmpty().ifBlank { "secret" }

    suspend fun tryRegister(envelope: GuardianMessage, session: WebSocketSession): DeviceRegistration? {
        if (envelope.type != "HELLO") {
            session.sendAck(envelope.id, AckStatus.FAILED, "ExpectedHello")
            return null
        }

        val hello = envelope.hello
        if (hello == null) {
            session.sendAck(envelope.id, AckStatus.FAILED, "MissingHello")
            return null
        }

        if (hello.secretKey != secret) {
            session.sendAck(envelope.id, AckStatus.FAILED, "InvalidSecret")
            return null
        }

        val registration = DeviceRegistration(hello.role, hello.nodeId)
        when (registration.role) {
            DeviceRole.NODE -> {
                nodes[registration.nodeId] = session
                val onlineStatus = (lastStatus[registration.nodeId] ?: NodeStatusPayload()).copy(online = true)
                lastStatus[registration.nodeId] = onlineStatus
                broadcastStatus(registration.nodeId, onlineStatus)
            }
            DeviceRole.CONTROLLER -> {
                controllers.computeIfAbsent(registration.nodeId) { ConcurrentHashMap.newKeySet() }.add(session)
                lastStatus[registration.nodeId]?.let { session.sendStatus(it) }
            }
        }
        session.sendAck(envelope.id, AckStatus.SUCCESS)
        return registration
    }

    suspend fun unregister(registration: DeviceRegistration, session: WebSocketSession) {
        when (registration.role) {
            DeviceRole.NODE -> {
                nodes.remove(registration.nodeId, session)
                val offline = (lastStatus[registration.nodeId] ?: NodeStatusPayload()).copy(
                    online = false,
                    lastSeen = System.currentTimeMillis(),
                )
                lastStatus[registration.nodeId] = offline
                broadcastStatus(registration.nodeId, offline)
            }
            DeviceRole.CONTROLLER -> controllers[registration.nodeId]?.remove(session)
        }
    }

    suspend fun route(from: DeviceRegistration, envelope: GuardianMessage, session: WebSocketSession) {
        if (envelope.type == "PING") {
            session.sendMessage(GuardianMessage(type = "PONG"))
            return
        }

        when (from.role) {
            DeviceRole.CONTROLLER -> routeControllerCommand(from.nodeId, envelope)
            DeviceRole.NODE -> routeNodeMessage(from.nodeId, envelope)
        }
    }

    private suspend fun routeControllerCommand(nodeId: String, envelope: GuardianMessage) {
        val node = nodes[nodeId]
        if (node == null) {
            controllers[nodeId]?.forEach { it.sendAck(envelope.id, AckStatus.FAILED, "NodeOffline") }
            return
        }
        node.sendMessage(envelope)
    }

    private suspend fun routeNodeMessage(nodeId: String, envelope: GuardianMessage) {
        val status = envelope.status
        if (status != null) {
            lastStatus[nodeId] = status.copy(online = true, lastSeen = System.currentTimeMillis())
        }

        when (envelope.type) {
            "STATUS", "HEARTBEAT", "ACK" -> controllers[nodeId]?.forEach { it.sendMessage(envelope) }
            else -> controllers[nodeId]?.forEach { it.sendMessage(envelope) }
        }
    }

    private suspend fun broadcastStatus(nodeId: String, status: NodeStatusPayload) {
        controllers[nodeId]?.forEach { it.sendStatus(status) }
    }
}

private suspend fun WebSocketSession.sendMessage(message: GuardianMessage) {
    send(Frame.Text(json.encodeToString(GuardianMessage.serializer(), message)))
}

private suspend fun WebSocketSession.sendStatus(status: NodeStatusPayload) {
    sendMessage(GuardianMessage(type = "STATUS", status = status))
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
    val nodeId: String,
)

@Serializable
data class GuardianMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val hello: HelloPayload? = null,
    val sound: String? = null,
    val volume: Int? = null,
    val loops: Int? = null,
    val intervalSeconds: Int? = null,
    val status: NodeStatusPayload? = null,
    val ack: AckPayload? = null,
    val reason: String? = null,
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
    val lastSeen: Long = System.currentTimeMillis(),
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
