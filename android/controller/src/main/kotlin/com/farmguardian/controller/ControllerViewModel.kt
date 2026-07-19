package com.farmguardian.controller

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farmguardian.shared.AckStatus
import com.farmguardian.shared.CameraConfigPayload
import com.farmguardian.shared.CameraLensFacing
import com.farmguardian.shared.ConnectionState
import com.farmguardian.shared.DefaultSoundOptions
import com.farmguardian.shared.DeviceRole
import com.farmguardian.shared.GuardianMessage
import com.farmguardian.shared.GuardianSocketClient
import com.farmguardian.shared.MessageType
import com.farmguardian.shared.NodeSummary
import com.farmguardian.shared.NodeStatusPayload
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class ControllerViewModel : ViewModel() {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var socket: GuardianSocketClient? = null

    private val _state = MutableStateFlow(ControllerState(connecting = true))
    val state: StateFlow<ControllerState> = _state

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            log("Enter username and password")
            return
        }
        socket?.stop()
        _state.update {
            it.copy(
                username = username.trim(),
                password = password,
                loggedIn = true,
                connecting = true,
            )
        }
        socket = GuardianSocketClient(
            scope = viewModelScope,
            role = DeviceRole.CONTROLLER,
            username = username.trim(),
            password = password,
            friendlyName = "Home Phone",
            backendUrl = BuildConfig.BACKEND_WS_URL,
            onState = ::applyConnectionState,
            onMessage = ::handleMessage,
            onBinaryMessage = ::handleBinaryFrame,
        ).also { it.start() }
    }

    fun logout() {
        socket?.stop()
        socket = null
        _state.update { ControllerState(activity = it.activity.takeLast(20)) }
    }

    fun play(soundId: String) {
        val targetNodeId = selectedNodeIdOrLog() ?: return
        val sound = DefaultSoundOptions.firstOrNull { it.id == soundId }?.label ?: soundId
        val message = GuardianMessage(
            type = MessageType.PLAY,
            targetNodeId = targetNodeId,
            sound = soundId,
            volume = state.value.volume,
            loops = state.value.loops,
        )
        send(message, "Play requested: $sound (${state.value.loops} repeats)")
    }

    fun stop() {
        val targetNodeId = selectedNodeIdOrLog() ?: return
        send(GuardianMessage(type = MessageType.STOP, targetNodeId = targetNodeId), "Stop requested")
    }

    fun pause() {
        val targetNodeId = selectedNodeIdOrLog() ?: return
        send(GuardianMessage(type = MessageType.PAUSE, targetNodeId = targetNodeId), "Pause requested")
    }

    fun selectNode(nodeId: String) {
        _state.update { it.copy(selectedNodeId = nodeId) }
        state.value.nodes.firstOrNull { it.nodeId == nodeId }?.status?.let(::applyStatus)
        if (state.value.cameraEnabled) sendCameraConfig(state.value, nodeId, "Camera switched to $nodeId")
    }

    fun disconnectNode(nodeId: String) {
        send(
            GuardianMessage(type = MessageType.DISCONNECT_NODE, targetNodeId = nodeId),
            "Disconnect requested: $nodeId",
        )
    }

    fun setCameraEnabled(enabled: Boolean) {
        _state.update { it.copy(cameraEnabled = enabled) }
    }

    fun setCameraLens(lensFacing: CameraLensFacing) {
        updateCameraConfig("Camera lens changed") { it.copy(cameraLensFacing = lensFacing) }
    }

    fun setCameraFps(fps: Int) {
        updateCameraConfig("Camera FPS changed") { it.copy(cameraFps = fps.coerceIn(1, 8)) }
    }

    fun setCameraQuality(quality: Int) {
        updateCameraConfig("Camera quality changed") { it.copy(cameraQuality = quality.coerceIn(25, 75)) }
    }

    fun setCameraResolution(width: Int, height: Int) {
        updateCameraConfig("Camera resolution changed") { it.copy(cameraWidth = width, cameraHeight = height) }
    }

    fun setCameraTorch(torch: Boolean) {
        updateCameraConfig("Camera torch changed") { it.copy(cameraTorch = torch) }
    }

    fun applyCameraConfig(enabled: Boolean = state.value.cameraEnabled) {
        val targetNodeId = selectedNodeIdOrLog() ?: return
        val config = state.value.copy(cameraEnabled = enabled)
        _state.update { config }
        sendCameraConfig(config, targetNodeId, if (enabled) "Camera stream requested" else "Camera stream stopped")
    }

    fun setVolume(volume: Int) {
        _state.update { it.copy(volume = volume.coerceIn(0, 100)) }
    }

    fun setLoops(loops: Int) {
        _state.update { it.copy(loops = loops.coerceIn(0, 20)) }
    }

    fun setDefaultSound(soundId: String) {
        _state.update { it.copy(defaultSoundId = soundId) }
    }

    fun setAutoIntervalMinutes(minutes: Int) {
        _state.update { it.copy(autoIntervalMinutes = minutes.coerceIn(0, 1440)) }
    }

    fun autoIntervalUp() = setAutoIntervalMinutes(state.value.autoIntervalMinutes + 1)

    fun autoIntervalDown() = setAutoIntervalMinutes(state.value.autoIntervalMinutes - 1)

    fun applyAutoPlayConfig() {
        val sound = DefaultSoundOptions.firstOrNull { it.id == state.value.defaultSoundId } ?: DefaultSoundOptions.first()
        val intervalSeconds = state.value.autoIntervalMinutes * 60
        send(
            GuardianMessage(
                type = MessageType.AUTO_PLAY_CONFIG,
                targetNodeId = selectedNodeIdOrLog() ?: return,
                sound = sound.id,
                volume = state.value.volume,
                loops = state.value.loops,
                intervalSeconds = intervalSeconds,
            ),
            if (intervalSeconds > 0) {
                "Auto play set: ${sound.label} every ${state.value.autoIntervalMinutes} min"
            } else {
                "Auto play disabled"
            },
        )
    }

    fun mute() = setVolume(0)

    fun volumeUp() = setVolume(state.value.volume + 5)

    fun volumeDown() = setVolume(state.value.volume - 5)

    fun loopsUp() = setLoops(state.value.loops + 1)

    fun loopsDown() = setLoops(state.value.loops - 1)

    override fun onCleared() {
        socket?.stop()
        super.onCleared()
    }

    private fun send(message: GuardianMessage, activity: String) {
        val sent = socket?.send(message) == true
        log(if (sent) activity else "Unable to send: backend disconnected")
    }

    private fun updateCameraConfig(activity: String, transform: (ControllerState) -> ControllerState) {
        var updated = state.value
        _state.update {
            transform(it).also { next -> updated = next }
        }
        if (updated.cameraEnabled) {
            val targetNodeId = updated.selectedNodeId ?: run {
                log("Select an online node first")
                return
            }
            sendCameraConfig(updated, targetNodeId, activity)
        }
    }

    private fun sendCameraConfig(config: ControllerState, targetNodeId: String, activity: String) {
        send(
            GuardianMessage(
                type = MessageType.CAMERA_CONFIG,
                targetNodeId = targetNodeId,
                camera = CameraConfigPayload(
                    enabled = config.cameraEnabled,
                    lensFacing = config.cameraLensFacing,
                    fps = config.cameraFps,
                    quality = config.cameraQuality,
                    width = config.cameraWidth,
                    height = config.cameraHeight,
                    torch = config.cameraTorch,
                ),
            ),
            activity,
        )
    }

    private fun handleMessage(message: GuardianMessage) {
        if (message.type == MessageType.NODE_LIST) {
            applyNodeList(message.nodes)
        }

        val frame = message.frame
        if (message.type == MessageType.CAMERA_FRAME &&
            frame != null &&
            (message.nodeId == null || message.nodeId == state.value.selectedNodeId)
        ) {
            applyCameraFrame(frame.dataBase64)
        }

        val status = message.status
        if (status != null && (message.nodeId == null || message.nodeId == state.value.selectedNodeId)) {
            applyStatus(status)
        }

        val ack = message.ack
        if (ack != null) {
            val label = if (ack.status == AckStatus.SUCCESS) "Command acknowledged" else "Command failed: ${ack.reason ?: "Unknown"}"
            log(label)
        }
    }

    private fun applyNodeList(nodes: List<NodeSummary>) {
        _state.update { current ->
            val selected = current.selectedNodeId
                ?.takeIf { nodeId -> nodes.any { it.nodeId == nodeId } }
                ?: nodes.firstOrNull { it.online }?.nodeId
                ?: nodes.firstOrNull()?.nodeId
            current.copy(nodes = nodes, selectedNodeId = selected)
        }
        state.value.nodes.firstOrNull { it.nodeId == state.value.selectedNodeId }?.status?.let(::applyStatus)
    }

    private fun applyConnectionState(connectionState: ConnectionState) {
        _state.update {
            when (connectionState) {
                ConnectionState.Connecting -> it.copy(connecting = true, backendStatus = "Connecting")
                ConnectionState.Connected -> it.copy(connecting = false, backendStatus = "Connected")
                ConnectionState.Disconnected -> it.copy(connecting = true, backendStatus = "Reconnecting", nodeStatusLabel = "Offline")
            }
        }
    }

    private fun applyStatus(status: NodeStatusPayload) {
        _state.update {
            it.copy(
                nodeStatusLabel = if (status.online) "Online" else "Offline",
                batteryLabel = status.batteryPercent?.let { battery -> "$battery%" } ?: it.batteryLabel,
                chargingLabel = when (status.charging) {
                    true -> "Charging"
                    false -> "Not charging"
                    null -> it.chargingLabel
                },
                networkLabel = status.network ?: it.networkLabel,
                bluetoothLabel = if (status.bluetoothConnected == true) "Connected" else "Disconnected",
                speakerName = status.speakerName,
                playbackLabel = status.playbackState.name,
                currentSound = status.currentSound,
                durationSeconds = status.durationSeconds,
                remainingSeconds = status.remainingSeconds,
                temperatureLabel = status.phoneTemperatureCelsius?.let { temp -> "%.1f C".format(temp) } ?: it.temperatureLabel,
                lastSeenLabel = timeFormat.format(Date(status.lastSeen)),
            )
        }
    }

    private fun log(line: String) {
        _state.update { it.copy(activity = (it.activity + "${timeFormat.format(Date())}  $line").takeLast(20)) }
    }

    private fun applyCameraFrame(dataBase64: String) {
        val bytes = runCatching { Base64.decode(dataBase64, Base64.NO_WRAP) }.getOrNull() ?: run {
            log("Camera frame decode failed")
            return
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: run {
            log("Camera frame image invalid")
            return
        }
        _state.update {
            it.copy(
                cameraFrame = bitmap,
                cameraLastFrameLabel = timeFormat.format(Date()),
            )
        }
    }

    private fun handleBinaryFrame(bytes: ByteArray) {
        if (bytes.size < 3) return
        val nodeIdLength = ByteBuffer.wrap(bytes, 0, 2).short.toInt()
        if (nodeIdLength <= 0 || bytes.size <= 2 + nodeIdLength) return
        val nodeId = bytes.copyOfRange(2, 2 + nodeIdLength).decodeToString()
        if (nodeId != state.value.selectedNodeId) return
        val jpeg = bytes.copyOfRange(2 + nodeIdLength, bytes.size)
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: run {
            log("Camera binary frame invalid")
            return
        }
        _state.update {
            it.copy(
                cameraFrame = bitmap,
                cameraLastFrameLabel = timeFormat.format(Date()),
            )
        }
    }

    private fun selectedNodeIdOrLog(): String? {
        val nodeId = state.value.selectedNodeId
        if (nodeId == null) log("Select an online node first")
        return nodeId
    }
}

data class ControllerState(
    val loggedIn: Boolean = false,
    val username: String = "",
    val password: String = "",
    val connecting: Boolean = false,
    val backendStatus: String = "Disconnected",
    val nodeStatusLabel: String = "Offline",
    val batteryLabel: String = "Unknown",
    val chargingLabel: String = "Unknown",
    val networkLabel: String = "Unknown",
    val bluetoothLabel: String = "Unknown",
    val speakerName: String? = null,
    val playbackLabel: String = "Stopped",
    val currentSound: String? = null,
    val durationSeconds: Int? = null,
    val remainingSeconds: Int? = null,
    val temperatureLabel: String = "Unknown",
    val lastSeenLabel: String = "Never",
    val nodes: List<NodeSummary> = emptyList(),
    val selectedNodeId: String? = null,
    val cameraEnabled: Boolean = false,
    val cameraLensFacing: CameraLensFacing = CameraLensFacing.BACK,
    val cameraFps: Int = 4,
    val cameraQuality: Int = 35,
    val cameraWidth: Int = 320,
    val cameraHeight: Int = 240,
    val cameraTorch: Boolean = false,
    val cameraFrame: Bitmap? = null,
    val cameraLastFrameLabel: String = "Never",
    val volume: Int = 80,
    val loops: Int = 0,
    val defaultSoundId: String = DefaultSoundOptions.first().id,
    val autoIntervalMinutes: Int = 0,
    val activity: List<String> = emptyList(),
)
