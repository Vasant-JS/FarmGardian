package com.farmguardian.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class GuardianSocketClient(
    private val scope: CoroutineScope,
    private val role: DeviceRole,
    private val username: String,
    private val password: String,
    private val friendlyName: String,
    private val backendUrl: String = GuardianConfig.DEFAULT_BACKEND_URL,
    private val nodeId: String = GuardianConfig.DEFAULT_NODE_ID,
    private val onState: (ConnectionState) -> Unit,
    private val onMessage: (GuardianMessage) -> Unit,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val client = OkHttpClient()
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var manuallyStopped = false
    private var reconnectDelayMs = GuardianConfig.RECONNECT_INITIAL_DELAY_MS

    fun start() {
        manuallyStopped = false
        scheduleConnect(0)
    }

    fun stop() {
        manuallyStopped = true
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        socket?.close(1000, "Stopped")
        socket = null
    }

    fun send(message: GuardianMessage): Boolean =
        socket?.send(json.encodeToString(GuardianMessage.serializer(), message)) == true

    private fun scheduleConnect(delayMs: Long = reconnectDelayMs) {
        if (manuallyStopped || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            connect()
        }
    }

    private fun connect() {
        onState(ConnectionState.Connecting)
        val request = Request.Builder().url(backendUrl).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectDelayMs = GuardianConfig.RECONNECT_INITIAL_DELAY_MS
                onState(ConnectionState.Connected)
                send(
                    GuardianMessage(
                        type = MessageType.LOGIN,
                        login = LoginPayload(
                            role = role,
                            username = username,
                            password = password,
                            nodeId = nodeId,
                            friendlyName = friendlyName,
                        ),
                    ),
                )
                startHeartbeats()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { json.decodeFromString<GuardianMessage>(text) }.getOrNull() ?: return
                onMessage(message)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleDisconnect()
            }
        })
    }

    private fun startHeartbeats() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (!manuallyStopped) {
                delay(GuardianConfig.HEARTBEAT_INTERVAL_MS)
                send(GuardianMessage(type = MessageType.PING))
            }
        }
    }

    private fun handleDisconnect() {
        if (manuallyStopped) return
        heartbeatJob?.cancel()
        socket = null
        onState(ConnectionState.Disconnected)
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(GuardianConfig.RECONNECT_MAX_DELAY_MS)
        scheduleConnect()
    }
}

enum class ConnectionState {
    Connecting,
    Connected,
    Disconnected,
}
