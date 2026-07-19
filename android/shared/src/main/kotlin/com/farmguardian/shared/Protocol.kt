package com.farmguardian.shared

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class GuardianMessage(
    val id: String = UUID.randomUUID().toString(),
    val type: MessageType,
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
    val playbackState: PlaybackState = PlaybackState.Stopped,
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
    val lensFacing: CameraLensFacing = CameraLensFacing.BACK,
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
    val lensFacing: CameraLensFacing? = null,
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
enum class CameraLensFacing {
    FRONT,
    BACK,
}

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
enum class MessageType {
    LOGIN,
    HELLO,
    PING,
    PONG,
    PLAY,
    STOP,
    PAUSE,
    AUTO_PLAY_CONFIG,
    CAMERA_CONFIG,
    CAMERA_FRAME,
    AUDIO_CONFIG,
    NODE_LIST,
    DISCONNECT_NODE,
    STATUS,
    HEARTBEAT,
    ACK,
}

object BinaryFrameKind {
    const val CAMERA_JPEG: Byte = 1
    const val MIC_PCM_16: Byte = 2
}

@Serializable
enum class AckStatus {
    SUCCESS,
    FAILED,
}

@Serializable
enum class PlaybackState {
    Playing,
    Paused,
    Stopped,
}

data class SoundOption(
    val id: String,
    val label: String,
    val category: String,
)

val DefaultSoundOptions = listOf(
    SoundOption("dog_barking_1", "Dog Barking 1", "Dogs"),
    SoundOption("dog_barking_2", "Dog Barking 2", "Dogs"),
    SoundOption("monkey_screem", "Monkey Scream", "Monkeys"),
    SoundOption("scaring_monkey_sound", "Scaring Monkey", "Monkeys"),
    SoundOption("scaring_monkey_sound_2", "Scaring Monkey 2", "Monkeys"),
    SoundOption("loud_buzzer", "Loud Buzzer", "Sirens"),
    SoundOption("loud_mixed_alarm", "Mixed Alarm", "Sirens"),
    SoundOption("gun_shot", "Gun Shot", "Custom"),
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
