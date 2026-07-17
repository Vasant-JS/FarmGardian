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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Farm Guardian", style = MaterialTheme.typography.headlineMedium)
        Text("Backend: ${state.backendStatus}")
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
