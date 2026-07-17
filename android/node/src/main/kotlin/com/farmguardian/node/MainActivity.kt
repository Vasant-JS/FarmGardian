package com.farmguardian.node

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        startForegroundService(Intent(this, NodeService::class.java))
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NodeScreen { startForegroundService(Intent(this, NodeService::class.java)) }
                }
            }
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
private fun NodeScreen(onStartService: () -> Unit) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(NodeStatusStore.readSnapshot(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = NodeStatusStore.readSnapshot(context)
            delay(1_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Farm Guardian Node", style = MaterialTheme.typography.headlineMedium)
        Text("Node ID: Farm-01")
        Text("Connected: ${snapshot.connection}")
        Text("Bluetooth Connected: ${snapshot.bluetooth}")
        Text("Audio Output: ${snapshot.speaker}")
        Text("Foreground Service Running")
        Text("Last Command: ${snapshot.lastCommand}")
        Text("Last Playback: ${snapshot.currentSound}")
        Text("Playback: ${snapshot.playback}")
        Text("Battery: ${snapshot.battery} - ${snapshot.charging}")
        Text("Network: ${snapshot.network}")
        Text("Last Seen: ${snapshot.lastSeen}")
        Button(onClick = onStartService) {
            Text("Start Service")
        }
        Text("Logs", style = MaterialTheme.typography.titleMedium)
        snapshot.logs.takeLast(20).forEach {
            Text(it)
        }
    }
}
