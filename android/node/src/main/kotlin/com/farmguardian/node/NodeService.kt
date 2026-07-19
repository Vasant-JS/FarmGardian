package com.farmguardian.node

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.farmguardian.shared.CameraConfigPayload
import com.farmguardian.shared.CameraLensFacing
import com.farmguardian.shared.CameraFramePayload
import com.farmguardian.shared.AckPayload
import com.farmguardian.shared.AckStatus
import com.farmguardian.shared.ConnectionState
import com.farmguardian.shared.DeviceRole
import com.farmguardian.shared.GuardianConfig
import com.farmguardian.shared.GuardianMessage
import com.farmguardian.shared.GuardianSocketClient
import com.farmguardian.shared.MessageType
import com.farmguardian.shared.NodeStatusPayload
import com.farmguardian.shared.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NodeService : LifecycleService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var socket: GuardianSocketClient
    private var player: ExoPlayer? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService? = null
    private var cameraActive = false
    private var cameraQuality = 60
    private var cameraFrameIntervalMs = 500L
    @Volatile private var lastCameraFrameAt = 0L
    private var healthJob: Job? = null
    private var autoPlayJob: Job? = null
    private var lastCommand = "none"
    private var currentSound: String? = null
    private var playbackState = PlaybackState.Stopped
    private var durationSeconds: Int? = null
    private var remainingLoops = 0
    private var activeResId: Int? = null
    private var autoSoundId: String? = null
    private var autoIntervalSeconds = 0
    private var autoLoops = 0
    private var autoVolume = 80

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Connecting"))
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            exoPlayer.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackStateValue: Int) {
                        if (playbackStateValue == Player.STATE_ENDED) onPlaybackEnded()
                    }
                },
            )
        }
        val login = NodeStatusStore.readLogin(this)
        if (login == null) {
            NodeStatusStore.appendLog(this, "Login", "Missing credentials")
            stopSelf()
            return
        }
        NodeStatusStore.appendLog(this, "Service", "Started")
        socket = GuardianSocketClient(
            scope = serviceScope,
            role = DeviceRole.NODE,
            username = login.username,
            password = login.password,
            nodeId = login.nodeId,
            friendlyName = login.friendlyName,
            backendUrl = BuildConfig.BACKEND_WS_URL,
            onState = ::applyConnectionState,
            onMessage = ::handleMessage,
        )
        socket.start()
        startHealthReporting()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        NodeStatusStore.appendLog(this, "Service", "Stopped")
        healthJob?.cancel()
        autoPlayJob?.cancel()
        stopCameraStream()
        cameraExecutor?.shutdown()
        socket.stop()
        player?.release()
        player = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleMessage(message: GuardianMessage) {
        serviceScope.launch {
            when (message.type) {
                MessageType.PLAY -> play(message)
                MessageType.PAUSE -> pause(message.id)
                MessageType.STOP -> stop(message.id)
                MessageType.AUTO_PLAY_CONFIG -> configureAutoPlay(message)
                MessageType.CAMERA_CONFIG -> configureCamera(message)
                MessageType.DISCONNECT_NODE -> disconnectFromController()
                else -> Unit
            }
        }
    }

    private fun disconnectFromController() {
        NodeStatusStore.appendLog(this, "Disconnected", "Requested by controller")
        NodeStatusStore.clearLogin(this)
        stopCameraStream()
        socket.stop()
        stopSelf()
    }

    private fun applyConnectionState(connectionState: ConnectionState) {
        val label = when (connectionState) {
            ConnectionState.Connecting -> "Connecting"
            ConnectionState.Connected -> "Connected"
            ConnectionState.Disconnected -> "Reconnecting"
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(label))
        NodeStatusStore.saveConnection(this, label)
        if (connectionState == ConnectionState.Connected) sendStatus(MessageType.STATUS)
    }

    private fun play(message: GuardianMessage) {
        val sound = message.sound
        if (sound.isNullOrBlank()) {
            acknowledge(message.id, AckStatus.FAILED, "MissingSound")
            return
        }

        val resId = resources.getIdentifier(sound, "raw", packageName)
        if (resId == 0) {
            acknowledge(message.id, AckStatus.FAILED, "FileMissing")
            NodeStatusStore.appendLog(this, "Played $sound", "File missing")
            return
        }

        val resolvedPlayer = player ?: return acknowledge(message.id, AckStatus.FAILED, "PlayerUnavailable")
        val safeVolume = (message.volume ?: 80).coerceIn(0, 100)
        val loops = (message.loops ?: 0).coerceIn(0, 20)
        runCatching {
            activeResId = resId
            remainingLoops = loops
            resolvedPlayer.stop()
            resolvedPlayer.clearMediaItems()
            setDeviceVolume(safeVolume)
            resolvedPlayer.volume = safeVolume / 100f
            resolvedPlayer.setMediaItem(MediaItem.fromUri("android.resource://$packageName/$resId"))
            resolvedPlayer.prepare()
            resolvedPlayer.play()

            playbackState = PlaybackState.Playing
            currentSound = sound
            durationSeconds = resolvedPlayer.duration.takeIf { it > 0 }?.let { (it / 1000).toInt() }
            lastCommand = "PLAY $sound"
            NodeStatusStore.appendLog(this, "Played $sound", "Success, repeats $loops")
            acknowledge(message.id, AckStatus.SUCCESS)
            sendStatus(MessageType.STATUS)
        }.onFailure {
            NodeStatusStore.appendLog(this, "Played $sound", "Failed: ${it.message.orEmpty()}")
            acknowledge(message.id, AckStatus.FAILED, it.message ?: "PlaybackFailed")
        }
    }

    private fun configureAutoPlay(message: GuardianMessage) {
        val intervalSeconds = (message.intervalSeconds ?: 0).coerceIn(0, 86_400)
        val sound = message.sound

        if (intervalSeconds == 0) {
            autoPlayJob?.cancel()
            autoPlayJob = null
            autoSoundId = null
            autoIntervalSeconds = 0
            lastCommand = "AUTO OFF"
            NodeStatusStore.appendLog(this, "Auto play", "Disabled")
            acknowledge(message.id, AckStatus.SUCCESS)
            sendStatus(MessageType.STATUS)
            return
        }

        if (sound.isNullOrBlank() || resources.getIdentifier(sound, "raw", packageName) == 0) {
            acknowledge(message.id, AckStatus.FAILED, "FileMissing")
            NodeStatusStore.appendLog(this, "Auto play", "Failed: missing sound")
            return
        }

        autoSoundId = sound
        autoIntervalSeconds = intervalSeconds
        autoLoops = (message.loops ?: 0).coerceIn(0, 20)
        autoVolume = (message.volume ?: 80).coerceIn(0, 100)
        lastCommand = "AUTO $sound ${intervalSeconds}s"
        NodeStatusStore.appendLog(this, "Auto play", "Every ${intervalSeconds}s: $sound")
        startAutoPlayLoop()
        acknowledge(message.id, AckStatus.SUCCESS)
        sendStatus(MessageType.STATUS)
    }

    private fun startAutoPlayLoop() {
        autoPlayJob?.cancel()
        val sound = autoSoundId ?: return
        val intervalMs = autoIntervalSeconds * 1000L
        if (intervalMs <= 0) return

        autoPlayJob = serviceScope.launch {
            while (true) {
                delay(intervalMs)
                play(
                    GuardianMessage(
                        type = MessageType.PLAY,
                        sound = sound,
                        volume = autoVolume,
                        loops = autoLoops,
                    ),
                )
            }
        }
    }

    private fun pause(commandId: String) {
        val resolvedPlayer = player ?: return acknowledge(commandId, AckStatus.FAILED, "PlayerUnavailable")
        if (resolvedPlayer.isPlaying) {
            resolvedPlayer.pause()
            playbackState = PlaybackState.Paused
            lastCommand = "PAUSE"
            NodeStatusStore.appendLog(this, "Paused playback", "Success")
            acknowledge(commandId, AckStatus.SUCCESS)
            sendStatus(MessageType.STATUS)
        } else if (playbackState == PlaybackState.Paused) {
            resolvedPlayer.play()
            playbackState = PlaybackState.Playing
            lastCommand = "RESUME"
            NodeStatusStore.appendLog(this, "Resumed playback", "Success")
            acknowledge(commandId, AckStatus.SUCCESS)
            sendStatus(MessageType.STATUS)
        } else {
            acknowledge(commandId, AckStatus.FAILED, "NothingPlaying")
        }
    }

    private fun stop(commandId: String) {
        player?.stop()
        playbackState = PlaybackState.Stopped
        currentSound = null
        durationSeconds = null
        remainingLoops = 0
        activeResId = null
        lastCommand = "STOP"
        NodeStatusStore.appendLog(this, "Stopped playback", "Success")
        acknowledge(commandId, AckStatus.SUCCESS)
        sendStatus(MessageType.STATUS)
    }

    private fun onPlaybackEnded() {
        val resId = activeResId
        val resolvedPlayer = player
        if (remainingLoops > 0 && resId != null && resolvedPlayer != null) {
            remainingLoops -= 1
            resolvedPlayer.seekTo(0)
            resolvedPlayer.play()
            NodeStatusStore.appendLog(this, "Repeated ${currentSound ?: "sound"}", "Remaining $remainingLoops")
            sendStatus(MessageType.STATUS)
            return
        }

        playbackState = PlaybackState.Stopped
        currentSound = null
        durationSeconds = null
        activeResId = null
        lastCommand = "COMPLETED"
        sendStatus(MessageType.STATUS)
    }

    private fun acknowledge(commandId: String, status: AckStatus, reason: String? = null) {
        socket.send(
            GuardianMessage(
                type = MessageType.ACK,
                ack = AckPayload(commandId = commandId, status = status, reason = reason),
                status = buildStatus(),
            ),
        )
    }

    private fun startHealthReporting() {
        healthJob?.cancel()
        healthJob = serviceScope.launch {
            while (true) {
                sendStatus(MessageType.HEARTBEAT)
                delay(GuardianConfig.HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun sendStatus(type: MessageType) {
        val status = buildStatus()
        NodeStatusStore.saveSnapshot(this, status, lastCommand)
        socket.send(GuardianMessage(type = type, status = status))
    }

    private fun buildStatus(): NodeStatusPayload {
        val battery = batteryInfo()
        val audioOutput = audioOutputInfo()
        return NodeStatusPayload(
            online = true,
            batteryPercent = battery.percent,
            charging = battery.charging,
            network = networkLabel(),
            bluetoothConnected = audioOutput.bluetoothConnected,
            speakerName = audioOutput.label,
            appVersion = "0.1.0",
            playbackState = playbackState,
            currentSound = currentSound,
            durationSeconds = durationSeconds,
            remainingSeconds = remainingSeconds(),
            phoneTemperatureCelsius = battery.temperatureCelsius,
            cameraActive = cameraActive,
            lastSeen = System.currentTimeMillis(),
        )
    }

    private fun configureCamera(message: GuardianMessage) {
        val config = message.camera
        if (config == null) {
            acknowledge(message.id, AckStatus.FAILED, "MissingCameraConfig")
            return
        }

        if (!config.enabled) {
            stopCameraStream()
            NodeStatusStore.appendLog(this, "Camera", "Stopped")
            acknowledge(message.id, AckStatus.SUCCESS)
            sendStatus(MessageType.STATUS)
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            acknowledge(message.id, AckStatus.FAILED, "CameraPermissionNeeded")
            NodeStatusStore.appendLog(this, "Camera", "Permission needed")
            return
        }

        startCameraStream(config)
        acknowledge(message.id, AckStatus.SUCCESS)
    }

    @SuppressLint("MissingPermission")
    private fun startCameraStream(config: CameraConfigPayload) {
        stopCameraStream()
        cameraQuality = config.quality.coerceIn(20, 95)
        cameraFrameIntervalMs = 1000L / config.fps.coerceIn(1, 10)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener(
            {
                runCatching {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    val selector = selectCamera(provider, config.lensFacing)
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(config.width, config.height))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(cameraExecutor ?: Executors.newSingleThreadExecutor()) { image ->
                        handleCameraImage(image)
                    }

                    provider.unbindAll()
                    camera = provider.bindToLifecycle(this, selector, analysis)
                    camera?.cameraControl?.enableTorch(config.torch)
                    cameraActive = true
                    NodeStatusStore.appendLog(this, "Camera", "Started ${config.width}x${config.height} ${config.fps}fps ${config.lensFacing}")
                    sendStatus(MessageType.STATUS)
                }.onFailure {
                    cameraActive = false
                    NodeStatusStore.appendLog(this, "Camera", "Failed: ${it.message ?: "Unavailable"}")
                    sendStatus(MessageType.STATUS)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun selectCamera(provider: ProcessCameraProvider, requested: CameraLensFacing): CameraSelector {
        val requestedLens = if (requested == CameraLensFacing.FRONT) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        val requestedSelector = CameraSelector.Builder().requireLensFacing(requestedLens).build()
        if (provider.hasCamera(requestedSelector)) return requestedSelector

        val fallbackLens = if (requestedLens == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        val fallbackSelector = CameraSelector.Builder().requireLensFacing(fallbackLens).build()
        if (provider.hasCamera(fallbackSelector)) {
            NodeStatusStore.appendLog(this, "Camera", "Fallback lens used")
            return fallbackSelector
        }

        throw IllegalStateException("No camera available")
    }

    private fun stopCameraStream() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
        cameraActive = false
        cameraExecutor?.shutdown()
        cameraExecutor = null
    }

    private fun handleCameraImage(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastCameraFrameAt < cameraFrameIntervalMs) return
            lastCameraFrameAt = now
            val jpeg = image.toJpeg(cameraQuality)
            val base64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
            val sent = socket.send(
                GuardianMessage(
                    type = MessageType.CAMERA_FRAME,
                    frame = CameraFramePayload(
                        dataBase64 = base64,
                        width = image.width,
                        height = image.height,
                        timestamp = now,
                    ),
                ),
            )
            if (!sent) NodeStatusStore.appendLog(this, "Camera", "Frame send failed")
        } finally {
            image.close()
        }
    }

    private fun ImageProxy.toJpeg(quality: Int): ByteArray {
        val nv21 = toNv21()
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val output = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), quality, output)
        return output.toByteArray()
    }

    private fun ImageProxy.toNv21(): ByteArray {
        val nv21 = ByteArray(width * height * 3 / 2)
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer.duplicate()
        var yOffset = 0
        for (row in 0 until height) {
            val rowStart = row * yPlane.rowStride
            for (col in 0 until width) {
                nv21[yOffset++] = yBuffer.get(rowStart + col * yPlane.pixelStride)
            }
        }

        val uPlane = planes[1]
        val vPlane = planes[2]
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        var uvOffset = width * height
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                nv21[uvOffset++] = vBuffer.get(row * vPlane.rowStride + col * vPlane.pixelStride)
                nv21[uvOffset++] = uBuffer.get(row * uPlane.rowStride + col * uPlane.pixelStride)
            }
        }
        return nv21
    }

    private fun remainingSeconds(): Int? {
        val resolvedPlayer = player ?: return null
        if (playbackState != PlaybackState.Playing) return null
        val duration = resolvedPlayer.duration
        if (duration <= 0) return null
        return ((duration - resolvedPlayer.currentPosition).coerceAtLeast(0) / 1000).toInt()
    }

    private fun batteryInfo(): BatteryInfo {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temperature = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return BatteryInfo(
            percent = if (level >= 0 && scale > 0) (level * 100 / scale) else null,
            charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL,
            temperatureCelsius = if (temperature > 0) temperature / 10f else null,
        )
    }

    private fun audioOutputInfo(): AudioOutputInfo {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn) {
            return AudioOutputInfo(true, bluetoothDeviceName() ?: "Bluetooth audio")
        }
        if (audioManager.isWiredHeadsetOn) {
            return AudioOutputInfo(false, "Wired/AUX audio")
        }
        return AudioOutputInfo(false, "Phone speaker")
    }

    private fun bluetoothDeviceName(): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val manager = getSystemService(BluetoothManager::class.java)
        val adapter = manager.adapter ?: return null
        val connected = runCatching {
            adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED ||
                adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
        }.getOrDefault(false)
        if (!connected) return null
        return runCatching { adapter.bondedDevices.firstOrNull()?.name }.getOrNull()
    }

    private fun networkLabel(): String {
        val manager = getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return "Offline"
        val capabilities = manager.getNetworkCapabilities(network) ?: return "Offline"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Online"
        }
    }

    private fun setDeviceVolume(volume: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (max * volume.coerceIn(0, 100)) / 100, 0)
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Farm Guardian", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(status: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Farm Guardian")
            .setContentText(status)
            .setOngoing(true)
            .build()

    private data class BatteryInfo(
        val percent: Int?,
        val charging: Boolean,
        val temperatureCelsius: Float?,
    )

    private data class AudioOutputInfo(
        val bluetoothConnected: Boolean,
        val label: String,
    )

    companion object {
        private const val CHANNEL_ID = "farm_guardian_node"
        private const val NOTIFICATION_ID = 1001
    }
}
