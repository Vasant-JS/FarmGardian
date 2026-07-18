package com.farmguardian.controller

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farmguardian.shared.CameraLensFacing
import com.farmguardian.shared.DefaultSoundOptions

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ControllerScreen()
                }
            }
        }
    }
}

@Composable
private fun ControllerScreen(viewModel: ControllerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var defaultSoundMenuOpen by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf(state.username) }
    var password by remember { mutableStateOf(state.password) }
    val selectedDefaultSound = DefaultSoundOptions.firstOrNull { it.id == state.defaultSoundId } ?: DefaultSoundOptions.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Farm Guardian", style = MaterialTheme.typography.headlineMedium)

        if (!state.loggedIn) {
            TextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Login") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { viewModel.login(username, password) }) {
                Text("Login")
            }
            state.activity.takeLast(3).forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }

        Text("Backend: ${state.backendStatus}")
        Button(onClick = viewModel::logout) { Text("Logout") }

        Text("Connected Nodes", style = MaterialTheme.typography.titleMedium)
        if (state.nodes.isEmpty()) {
            Text("No nodes connected")
        }
        state.nodes.forEach { node ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.selectNode(node.nodeId) },
                ) {
                    Text("${if (node.nodeId == state.selectedNodeId) "* " else ""}${node.friendlyName} - ${if (node.online) "Online" else "Offline"}")
                }
                Button(onClick = { viewModel.disconnectNode(node.nodeId) }) {
                    Text("Disconnect")
                }
            }
        }

        Text("Node: ${state.nodeStatusLabel}")
        Text("Battery: ${state.batteryLabel} - ${state.chargingLabel}")
        Text("Network: ${state.networkLabel}")
        Text("Bluetooth: ${state.bluetoothLabel}")
        Text("Speaker: ${state.speakerName ?: "Unknown"}")
        Text("Playback: ${state.playbackLabel}")
        Text("Current Sound: ${state.currentSound ?: "None"}")
        Text("Remaining: ${state.remainingSeconds?.let { "$it sec" } ?: "Unknown"}")
        Text("Temperature: ${state.temperatureLabel}")
        Text("Last Seen: ${state.lastSeenLabel}")

        Text("Live Camera", style = MaterialTheme.typography.titleMedium)
        val frame = state.cameraFrame
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = "Live camera frame",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(Color(0xFF202124))
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No camera frame", color = Color.White)
            }
        }
        Text("Last frame: ${state.cameraLastFrameLabel}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.applyCameraConfig(true) }) { Text("Start Camera") }
            Button(onClick = { viewModel.applyCameraConfig(false) }) { Text("Stop Camera") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.setCameraLens(CameraLensFacing.BACK) }) {
                Text(if (state.cameraLensFacing == CameraLensFacing.BACK) "* Back" else "Back")
            }
            Button(onClick = { viewModel.setCameraLens(CameraLensFacing.FRONT) }) {
                Text(if (state.cameraLensFacing == CameraLensFacing.FRONT) "* Front" else "Front")
            }
            Button(onClick = { viewModel.setCameraTorch(!state.cameraTorch) }) {
                Text(if (state.cameraTorch) "Torch On" else "Torch Off")
            }
        }
        Text("Camera FPS ${state.cameraFps}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.setCameraFps(state.cameraFps - 1) }) { Text("-") }
            Button(onClick = { viewModel.setCameraFps(2) }) { Text("2") }
            Button(onClick = { viewModel.setCameraFps(state.cameraFps + 1) }) { Text("+") }
        }
        Text("Camera Quality ${state.cameraQuality}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.setCameraQuality(state.cameraQuality - 10) }) { Text("-") }
            Button(onClick = { viewModel.setCameraQuality(60) }) { Text("60") }
            Button(onClick = { viewModel.setCameraQuality(state.cameraQuality + 10) }) { Text("+") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.setCameraResolution(320, 240) }) { Text("Low") }
            Button(onClick = { viewModel.setCameraResolution(640, 480) }) { Text("Med") }
            Button(onClick = { viewModel.setCameraResolution(1280, 720) }) { Text("High") }
        }

        if (state.connecting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        DefaultSoundOptions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { sound ->
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.play(sound.id) },
                    ) {
                        Text(sound.label)
                    }
                }
            }
        }

        Text("Volume ${state.volume}%")
        Slider(
            value = state.volume.toFloat(),
            onValueChange = { viewModel.setVolume(it.toInt()) },
            valueRange = 0f..100f,
        )

        Text("Repeats ${state.loops}")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::loopsDown) { Text("-") }
            Button(onClick = { viewModel.setLoops(0) }) { Text("0") }
            Button(onClick = viewModel::loopsUp) { Text("+") }
        }

        Text("Default Timed Sound")
        OutlinedButton(onClick = { defaultSoundMenuOpen = true }) {
            Text(selectedDefaultSound.label)
        }
        DropdownMenu(
            expanded = defaultSoundMenuOpen,
            onDismissRequest = { defaultSoundMenuOpen = false },
        ) {
            DefaultSoundOptions.forEach { sound ->
                DropdownMenuItem(
                    text = { Text(sound.label) },
                    onClick = {
                        viewModel.setDefaultSound(sound.id)
                        defaultSoundMenuOpen = false
                    },
                )
            }
        }

        Text("Timed Interval ${state.autoIntervalMinutes} min")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::autoIntervalDown) { Text("-") }
            Button(onClick = { viewModel.setAutoIntervalMinutes(0) }) { Text("Off") }
            Button(onClick = viewModel::autoIntervalUp) { Text("+") }
            Button(onClick = viewModel::applyAutoPlayConfig) { Text("Apply Timer") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::volumeDown) { Text("-") }
            Button(onClick = viewModel::volumeUp) { Text("+") }
            Button(onClick = viewModel::mute) { Text("Mute") }
            Button(onClick = viewModel::pause) { Text("Pause") }
            Button(onClick = viewModel::stop) { Text("Stop") }
        }

        Text("Recent Activity", style = MaterialTheme.typography.titleMedium)
        state.activity.takeLast(6).forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
