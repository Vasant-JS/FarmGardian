package com.farmguardian.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farmguardian.shared.AckStatus
import com.farmguardian.shared.ConnectionState
import com.farmguardian.shared.DefaultSoundOptions
import com.farmguardian.shared.DeviceRole
import com.farmguardian.shared.GuardianMessage
import com.farmguardian.shared.GuardianSocketClient
import com.farmguardian.shared.MessageType
import com.farmguardian.shared.NodeStatusPayload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class ControllerViewModel : ViewModel() {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val socket = GuardianSocketClient(
        scope = viewModelScope,
        role = DeviceRole.CONTROLLER,
        friendlyName = "Home Phone",
        backendUrl = BuildConfig.BACKEND_WS_URL,
        onState = ::applyConnectionState,
        onMessage = ::handleMessage,
    )

    private val _state = MutableStateFlow(ControllerState(connecting = true))
    val state: StateFlow<ControllerState> = _state

    init {
        socket.start()
    }

    fun play(soundId: String) {
        val sound = DefaultSoundOptions.firstOrNull { it.id == soundId }?.label ?: soundId
        val message = GuardianMessage(
            type = MessageType.PLAY,
            sound = soundId,
            volume = state.value.volume,
            loops = state.value.loops,
        )
        send(message, "Play requested: $sound (${state.value.loops} repeats)")
    }

    fun stop() {
        send(GuardianMessage(type = MessageType.STOP), "Stop requested")
    }

    fun pause() {
        send(GuardianMessage(type = MessageType.PAUSE), "Pause requested")
    }

    fun setVolume(volume: Int) {
        _state.update { it.copy(volume = volume.coerceIn(0, 100)) }
    }

    fun setLoops(loops: Int) {
        _state.update { it.copy(loops = loops.coerceIn(0, 20)) }
    }

    fun mute() = setVolume(0)

    fun volumeUp() = setVolume(state.value.volume + 5)

    fun volumeDown() = setVolume(state.value.volume - 5)

    fun loopsUp() = setLoops(state.value.loops + 1)

    fun loopsDown() = setLoops(state.value.loops - 1)

    override fun onCleared() {
        socket.stop()
        super.onCleared()
    }

    private fun send(message: GuardianMessage, activity: String) {
        val sent = socket.send(message)
        log(if (sent) activity else "Unable to send: backend disconnected")
    }

    private fun handleMessage(message: GuardianMessage) {
        val status = message.status
        if (status != null) applyStatus(status)

        val ack = message.ack
        if (ack != null) {
            val label = if (ack.status == AckStatus.SUCCESS) "Command acknowledged" else "Command failed: ${ack.reason ?: "Unknown"}"
            log(label)
        }
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
}

data class ControllerState(
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
    val volume: Int = 80,
    val loops: Int = 0,
    val activity: List<String> = emptyList(),
)
