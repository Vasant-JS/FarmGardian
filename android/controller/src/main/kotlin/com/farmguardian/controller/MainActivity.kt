package com.farmguardian.controller

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val selectedDefaultSound = DefaultSoundOptions.firstOrNull { it.id == state.defaultSoundId } ?: DefaultSoundOptions.first()

    if (!state.loggedIn) {
        LoginScreen(
            connectionStatus = state.backendStatus,
            activity = state.activity.takeLast(3),
            onLogin = viewModel::login,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Farm Guardian", style = MaterialTheme.typography.headlineMedium)

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

@Composable
private fun LoginScreen(
    connectionStatus: String,
    activity: List<String>,
    onLogin: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val primary = Color(0xFF012D1D)
    val page = Color(0xFFF8F9FA)
    val muted = Color(0xFF414844)
    val outline = Color(0xFFC1C8C2)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(page)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Card(
                modifier = Modifier.size(104.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.farm_guardian_logo),
                        contentDescription = "Farm Guardian logo",
                        modifier = Modifier.size(86.dp),
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Farm Guardian",
                    color = primary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Remote farm sound control",
                    color = muted,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE3E6E4)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text("Login / Username", color = muted, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = { Text("e.g. farm_manager_01") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Password", color = muted, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Button(
                        onClick = { onLogin(username, password) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = Color.White),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    ) {
                        Text("Login", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }

                    Text(
                        text = "Trouble accessing your account?",
                        color = primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 14.sp,
                    )

                    activity.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .background(Color(0xFFEDEEEF), RoundedCornerShape(999.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFFFFC629), RoundedCornerShape(999.dp)),
                )
                Text(
                    text = "CONNECTION STATUS: ${if (connectionStatus == "Disconnected") "READY" else connectionStatus.uppercase()}",
                    color = Color(0xFF717973),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "© 2024 Farm Guardian Security Systems",
                color = Color(0xFF8A918D),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
