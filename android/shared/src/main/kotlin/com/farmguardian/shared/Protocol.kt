package com.farmguardian.shared

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class GuardianMessage(
    val id: String = UUID.randomUUID().toString(),
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis(),
    val hello: HelloPayload? = null,
    val sound: String? = null,
    val volume: Int? = null,
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
    val playbackState: PlaybackState = PlaybackState.Stopped,
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
enum class MessageType {
    HELLO,
    PING,
    PONG,
    PLAY,
    STOP,
    STATUS,
    HEARTBEAT,
    ACK,
}

@Serializable
enum class AckStatus {
    SUCCESS,
    FAILED,
}

@Serializable
enum class PlaybackState {
    Playing,
    Stopped,
}

data class SoundOption(
    val id: String,
    val label: String,
    val category: String,
)

val DefaultSoundOptions = listOf(
    SoundOption("dog_01", "Dog Bark", "Dogs"),
    SoundOption("dog_02", "Dog Bark 2", "Dogs"),
    SoundOption("dog_03", "Dog Bark 3", "Dogs"),
    SoundOption("human_01", "Human Shout", "Humans"),
    SoundOption("human_02", "Human Shout 2", "Humans"),
    SoundOption("tiger", "Tiger", "Animals"),
    SoundOption("alarm", "Siren", "Sirens"),
)

object GuardianConfig {
    const val DEFAULT_NODE_ID = "Farm-01"
    const val DEFAULT_SECRET = "secret"
    const val DEFAULT_BACKEND_URL = "ws://10.0.2.2:8080/ws"
    const val HEARTBEAT_INTERVAL_MS = 30_000L
    const val BLUETOOTH_RETRY_INTERVAL_MS = 20_000L
    const val RECONNECT_INITIAL_DELAY_MS = 1_000L
    const val RECONNECT_MAX_DELAY_MS = 10_000L
}
