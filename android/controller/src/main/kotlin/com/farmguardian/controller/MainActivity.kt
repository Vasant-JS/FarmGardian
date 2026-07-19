package com.farmguardian.controller

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.farmguardian.shared.NodeSummary

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
    val selectedNode = state.nodes.firstOrNull { it.nodeId == state.selectedNodeId }
    val primary = Color(0xFF012D1D)
    val primaryPanel = Color(0xFF174E36)
    val gold = Color(0xFFFFB702)
    val page = Color(0xFFF8F9FA)
    val outline = Color(0xFFC1C8C2)
    val muted = Color(0xFF414844)
    val onlineCount = state.nodes.count { it.online }
    val durationSeconds = state.durationSeconds
    val remainingSeconds = state.remainingSeconds
    var showCameraScreen by remember { mutableStateOf(false) }
    var selectedNav by remember { mutableStateOf("Nodes") }
    val playbackProgress = when {
        durationSeconds != null && remainingSeconds != null && durationSeconds > 0 ->
            ((durationSeconds - remainingSeconds).toFloat() / durationSeconds).coerceIn(0f, 1f)
        state.playbackLabel == "Playing" -> 0.45f
        else -> 0f
    }

    if (!state.loggedIn) {
        LoginScreen(
            connectionStatus = state.backendStatus,
            activity = state.activity.takeLast(3),
            onLogin = viewModel::login,
        )
        return
    }

    if (showCameraScreen) {
        CameraRouteScreen(
            state = state,
            nodes = state.nodes,
            selectedNodeId = state.selectedNodeId,
            primary = primary,
            gold = gold,
            outline = outline,
            muted = muted,
            onSelectNode = viewModel::selectNode,
            onBack = {
                viewModel.applyCameraConfig(false)
                showCameraScreen = false
            },
            onStart = { viewModel.applyCameraConfig(true) },
            onStop = { viewModel.applyCameraConfig(false) },
            onLens = viewModel::setCameraLens,
            onTorch = { viewModel.setCameraTorch(!state.cameraTorch) },
            onFpsDown = { viewModel.setCameraFps(state.cameraFps - 1) },
            onFpsUp = { viewModel.setCameraFps(state.cameraFps + 1) },
            onQualityDown = { viewModel.setCameraQuality(state.cameraQuality - 10) },
            onQualityUp = { viewModel.setCameraQuality(state.cameraQuality + 10) },
            onResolution = viewModel::setCameraResolution,
            onPlay = viewModel::play,
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(page),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                backendStatus = state.backendStatus,
                primary = primary,
                gold = gold,
                onLogout = viewModel::logout,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SectionHeader("Connected Nodes", "$onlineCount Node${if (onlineCount == 1) "" else "s"} Active", primary, muted)

                if (state.nodes.isEmpty()) {
                    DashboardCard(outline = outline) {
                        Text("No nodes connected", color = muted, fontWeight = FontWeight.SemiBold)
                        Text("Login with the same account on a Node phone to pair it here.", color = muted, fontSize = 13.sp)
                    }
                } else {
                    selectedNode?.let { node ->
                        NodeStatusCard(
                            state = state,
                            nodeName = node.friendlyName,
                            nodeId = node.nodeId,
                            online = node.online,
                            progress = playbackProgress,
                            primary = primary,
                            gold = gold,
                            outline = outline,
                            muted = muted,
                        )
                    }
                    NodeSelector(
                        nodes = state.nodes,
                        selectedNodeId = state.selectedNodeId,
                        primary = primary,
                        outline = outline,
                        onSelect = viewModel::selectNode,
                        onDisconnect = viewModel::disconnectNode,
                    )
                }

                if (state.connecting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = gold)
                }

                if (state.nodes.isNotEmpty()) {
                    OpenCameraCard(
                        selectedNodeName = selectedNode?.friendlyName ?: "Main Farm",
                        selectedNodeId = state.selectedNodeId ?: "Node-01",
                        status = state.nodeStatusLabel,
                        primary = primary,
                        outline = outline,
                        onOpen = {
                            showCameraScreen = true
                            viewModel.applyCameraConfig(true)
                        },
                    )
                }

                SectionTitle(icon = Icons.Default.VolumeUp, title = "Quick Commands", color = primary)
                DefaultSoundOptions.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { sound ->
                            SoundCommandButton(
                                label = sound.label,
                                icon = soundIcon(sound.id),
                                active = sound.id == state.currentSound,
                                primary = primary,
                                gold = gold,
                                onClick = { viewModel.play(sound.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                PlaybackDeck(
                    volume = state.volume,
                    loops = state.loops,
                    playbackLabel = state.playbackLabel,
                    primaryPanel = primaryPanel,
                    gold = gold,
                    onVolume = viewModel::setVolume,
                    onVolumeDown = viewModel::volumeDown,
                    onVolumeUp = viewModel::volumeUp,
                    onStop = viewModel::stop,
                    onPause = viewModel::pause,
                    onLoopsDown = viewModel::loopsDown,
                    onLoopsUp = viewModel::loopsUp,
                )

                DashboardCard(outline = outline) {
                    SectionTitle(icon = Icons.Default.Schedule, title = "Timed Autoplay", color = primary)
                    Text("Default Sound", color = muted, fontSize = 12.sp)
                    Box {
                        OutlinedButton(
                            onClick = { defaultSoundMenuOpen = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(selectedDefaultSound.label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
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
                    }
                    Text("Interval (min)", color = muted, fontSize = 12.sp)
                    Stepper(
                        value = state.autoIntervalMinutes.toString(),
                        onMinus = viewModel::autoIntervalDown,
                        onPlus = viewModel::autoIntervalUp,
                    )
                    Button(
                        onClick = viewModel::applyAutoPlayConfig,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null)
                        Text("Apply Timer", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                    }
                }

                ActivityLog(activity = state.activity.takeLast(6), primary = primary, gold = gold, outline = outline, muted = muted)

                Spacer(modifier = Modifier.height(128.dp))
            }
        }

        EmergencyButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 84.dp),
            onClick = { viewModel.play("loud_buzzer") },
        )

        BottomNavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            selected = selectedNav,
            primary = primary,
            gold = gold,
            onSelect = { selectedNav = it },
        )
    }
}

@Composable
private fun OpenCameraCard(
    selectedNodeName: String,
    selectedNodeId: String,
    status: String,
    primary: Color,
    outline: Color,
    onOpen: () -> Unit,
) {
    DashboardCard(outline = outline) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(primary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Live Camera", color = primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("$selectedNodeName - $selectedNodeId - $status", color = Color(0xFF414844), fontSize = 12.sp)
            }
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Open")
            }
        }
    }
}

@Composable
private fun CameraRouteScreen(
    state: ControllerState,
    nodes: List<NodeSummary>,
    selectedNodeId: String?,
    primary: Color,
    gold: Color,
    outline: Color,
    muted: Color,
    onSelectNode: (String) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLens: (CameraLensFacing) -> Unit,
    onTorch: () -> Unit,
    onFpsDown: () -> Unit,
    onFpsUp: () -> Unit,
    onQualityDown: () -> Unit,
    onQualityUp: () -> Unit,
    onResolution: (Int, Int) -> Unit,
    onPlay: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(Color(0xFFF8F9FA)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            CameraPanel(
                state = state,
                nodes = nodes,
                selectedNodeId = selectedNodeId,
                primary = primary,
                gold = gold,
                outline = outline,
                muted = muted,
                onSelectNode = onSelectNode,
                onBack = onBack,
                onStart = onStart,
                onStop = onStop,
                onLens = onLens,
                onTorch = onTorch,
                onFpsDown = onFpsDown,
                onFpsUp = onFpsUp,
                onQualityDown = onQualityDown,
                onQualityUp = onQualityUp,
                onResolution = onResolution,
                onPlay = onPlay,
            )
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun TopAppBar(backendStatus: String, primary: Color, gold: Color, onLogout: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color.White)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Agriculture, contentDescription = null, tint = primary, modifier = Modifier.size(27.dp))
        Text(
            text = "Farm Guardian",
            color = primary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 10.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        StatusPill(
            label = if (backendStatus == "Connected") "CONNECTED" else backendStatus.uppercase(),
            color = if (backendStatus == "Connected") Color(0xFF0E8A45) else gold,
        )
        IconButton(onClick = onLogout) {
            Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color(0xFF414844))
        }
    }
}

@Composable
private fun SectionHeader(title: String, meta: String, primary: Color, muted: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.weight(1f))
        Text(meta, color = muted, fontSize = 12.sp)
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
        Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DashboardCard(outline: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NodeStatusCard(
    state: ControllerState,
    nodeName: String,
    nodeId: String,
    online: Boolean,
    progress: Float,
    primary: Color,
    gold: Color,
    outline: Color,
    muted: Color,
) {
    DashboardCard(outline = outline) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(primary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Speaker, contentDescription = null, tint = Color.White)
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            ) {
                Text(nodeName, color = primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("($nodeId)", color = muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                    MiniMetric(Icons.Default.Sensors, if (online) "Online" else "Offline", if (online) Color(0xFF087F3E) else Color(0xFFBA1A1A))
                    MiniMetric(Icons.Default.BatteryChargingFull, state.batteryLabel, muted)
                    MiniMetric(Icons.Default.SignalCellular4Bar, state.networkLabel, muted)
                }
            }
            Icon(Icons.Default.Hub, contentDescription = null, tint = outline.copy(alpha = 0.7f), modifier = Modifier.size(54.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabelChip(state.speakerName ?: "Phone Speaker", muted)
            LabelChip("ACTIVE CONTROL", primary, background = gold)
        }
        Surface(
            color = primary.copy(alpha = 0.05f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, primary.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, tint = gold, modifier = Modifier.size(17.dp))
                    Text("Current Status", color = primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text("Playing: ${state.currentSound ?: state.playbackLabel}", color = muted, fontSize = 13.sp)
                Text("Remaining: ${state.remainingSeconds?.let { formatSeconds(it) } ?: "--:--"}", color = muted, fontSize = 13.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(outline, RoundedCornerShape(999.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(gold, RoundedCornerShape(999.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMetric(icon: ImageVector, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, color = color, fontSize = 11.sp)
    }
}

@Composable
private fun LabelChip(
    label: String,
    color: Color,
    background: Color = Color(0xFFEDEEEF),
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(background, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun NodeSelector(
    nodes: List<NodeSummary>,
    selectedNodeId: String?,
    primary: Color,
    outline: Color,
    onSelect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        nodes.forEach { node ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSelect(node.nodeId) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (node.nodeId == selectedNodeId) primary else outline),
                ) {
                    Text(
                        text = "${node.friendlyName} - ${if (node.online) "Online" else "Offline"}",
                        color = if (node.nodeId == selectedNodeId) primary else Color(0xFF414844),
                        fontSize = 12.sp,
                    )
                }
                TextButton(onClick = { onDisconnect(node.nodeId) }) {
                    Text("Disconnect", color = Color(0xFFBA1A1A), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SoundCommandButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    primary: Color,
    gold: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) gold else Color(0xFFE1E3E4),
            contentColor = if (active) primary else Color.Black,
        ),
        border = BorderStroke(1.dp, if (active) primary else Color(0xFFC1C8C2)),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (active) 4.dp else 1.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun PlaybackDeck(
    volume: Int,
    loops: Int,
    playbackLabel: String,
    primaryPanel: Color,
    gold: Color,
    onVolume: (Int) -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onLoopsDown: () -> Unit,
    onLoopsUp: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = primaryPanel,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 5.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("OUTPUT VOLUME", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("$volume%", color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoundIconButton(Icons.Default.Remove, onVolumeDown)
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { onVolume(it.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(Icons.Default.Add, onVolumeUp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.Default.Stop, onStop, filled = true)
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onPause,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color(0xFF4B3600)),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(if (playbackLabel == "Paused") Icons.Default.PlayCircle else Icons.Default.PauseCircle, contentDescription = "Play or pause", modifier = Modifier.size(44.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("REPEATS", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SmallSquareButton(Icons.Default.Remove, onLoopsDown)
                        Text("$loops", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        SmallSquareButton(Icons.Default.Add, onLoopsUp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, onClick: () -> Unit, filled: Boolean = false) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .background(Color.White.copy(alpha = if (filled) 0.12f else 0.04f), CircleShape),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun SmallSquareButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(32.dp)
            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(6.dp)),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onMinus, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
        }
        Box(
            modifier = Modifier
                .height(48.dp)
                .weight(1f)
                .background(Color(0xFFEDEEEF), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onPlus, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun CameraPanel(
    state: ControllerState,
    nodes: List<NodeSummary>,
    selectedNodeId: String?,
    primary: Color,
    gold: Color,
    outline: Color,
    muted: Color,
    onSelectNode: (String) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLens: (CameraLensFacing) -> Unit,
    onTorch: () -> Unit,
    onFpsDown: () -> Unit,
    onFpsUp: () -> Unit,
    onQualityDown: () -> Unit,
    onQualityUp: () -> Unit,
    onResolution: (Int, Int) -> Unit,
    onPlay: (String) -> Unit,
) {
    val selectedNode = nodes.firstOrNull { it.nodeId == selectedNodeId }

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Stop camera", tint = Color.Black)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live Camera - ${selectedNode?.friendlyName ?: "Main Farm"}",
                    color = primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(modifier = Modifier.size(9.dp).background(primary, CircleShape))
                    Text("${selectedNodeId ?: "Node-01"} - ${state.nodeStatusLabel}", color = muted, fontSize = 14.sp)
                }
            }
            IconButton(onClick = { onResolution(1280, 720) }) {
                Icon(Icons.Default.Tune, contentDescription = "High quality stream", tint = Color.Black)
            }
            Image(
                painter = painterResource(id = R.drawable.farm_guardian_logo),
                contentDescription = "Farm Guardian",
                modifier = Modifier.size(44.dp),
            )
        }

        CameraStreamCard(
            state = state,
            nodeId = selectedNodeId ?: "Node-01",
            primary = primary,
            onStart = onStart,
            onStop = onStop,
            onTorch = onTorch,
            onLens = onLens,
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Switch Camera", color = primary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("View All Nodes", color = Color(0xFF7D5800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF7D5800), modifier = Modifier.size(20.dp))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            nodes.ifEmpty {
                listOf(NodeSummary(nodeId = "Farm-01", friendlyName = "Main Farm Gate", online = false))
            }.forEach { node ->
                CameraThumb(
                    node = node,
                    selected = node.nodeId == selectedNodeId,
                    primary = primary,
                    outline = outline,
                    onClick = { onSelectNode(node.nodeId) },
                )
            }
        }

        SectionTitle(icon = Icons.Default.Campaign, title = "Instant Deterrents", color = primary)
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.fillMaxWidth()) {
            DeterrentTile(
                label = "Dog Barking",
                icon = Icons.Default.Pets,
                background = gold,
                iconBackground = Color(0xFF7D5800),
                textColor = Color(0xFF5E4100),
                onClick = { onPlay("dog_barking_1") },
                modifier = Modifier.weight(1f),
            )
            DeterrentTile(
                label = "Monkey Scream",
                icon = Icons.Default.Eco,
                background = gold,
                iconBackground = Color(0xFF7D5800),
                textColor = Color(0xFF5E4100),
                onClick = { onPlay("monkey_screem") },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.fillMaxWidth()) {
            DeterrentTile(
                label = "Loud Buzzer",
                icon = Icons.Default.NotificationImportant,
                background = gold,
                iconBackground = Color(0xFF7D5800),
                textColor = Color(0xFF5E4100),
                onClick = { onPlay("loud_buzzer") },
                modifier = Modifier.weight(1f),
            )
            DeterrentTile(
                label = "Gun Shot",
                icon = Icons.Default.PriorityHigh,
                background = Color(0xFFFFDAD6),
                iconBackground = Color(0xFF7E0009),
                textColor = Color(0xFF93000A),
                onClick = { onPlay("gun_shot") },
                modifier = Modifier.weight(1f),
            )
        }

        CameraStatsCard(
            icon = Icons.Default.Router,
            label = "Signal Strength",
            value = "${state.networkLabel} - ${if (state.backendStatus == "Connected") "Excellent" else "Reconnecting"}",
            iconTint = primary,
            iconBg = Color(0xFFE1E8E4),
            outline = outline,
        )
        CameraStatsCard(
            icon = Icons.Default.BatteryChargingFull,
            label = "Node Battery",
            value = "${state.batteryLabel} - ${state.chargingLabel}",
            iconTint = Color(0xFF7D5800),
            iconBg = Color(0xFFFFF5D6),
            outline = outline,
        )

        DashboardCard(outline = outline) {
            SectionTitle(icon = Icons.Default.Settings, title = "Stream Controls", color = primary)
            Text("Last frame: ${state.cameraLastFrameLabel}", color = muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStart, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = primary)) { Text("Start") }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Stop") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ControlChip("Back", state.cameraLensFacing == CameraLensFacing.BACK, primary) { onLens(CameraLensFacing.BACK) }
                ControlChip("Front", state.cameraLensFacing == CameraLensFacing.FRONT, primary) { onLens(CameraLensFacing.FRONT) }
                ControlChip("Torch", state.cameraTorch, primary, icon = Icons.Default.FlashlightOn, onClick = onTorch)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("FPS ${state.cameraFps}", color = muted, modifier = Modifier.weight(1f))
                SmallOutlineButton(Icons.Default.Remove, onFpsDown)
                SmallOutlineButton(Icons.Default.Add, onFpsUp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Quality ${state.cameraQuality}", color = muted, modifier = Modifier.weight(1f))
                SmallOutlineButton(Icons.Default.Remove, onQualityDown)
                SmallOutlineButton(Icons.Default.Add, onQualityUp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ControlChip("Low", state.cameraWidth == 320, primary, icon = Icons.Default.Cameraswitch) { onResolution(320, 240) }
                ControlChip("Med", state.cameraWidth == 640, primary) { onResolution(640, 480) }
                ControlChip("High", state.cameraWidth == 1280, primary) { onResolution(1280, 720) }
            }
        }
    }
}

@Composable
private fun CameraStreamCard(
    state: ControllerState,
    nodeId: String,
    primary: Color,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTorch: () -> Unit,
    onLens: (CameraLensFacing) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black, RoundedCornerShape(12.dp)),
    ) {
        val frame = state.cameraFrame
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = "Live camera frame",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(42.dp))
                    Text("Waiting for live stream", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Tap Start to request camera from $nodeId", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f)),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Top) {
                LabelChip("LIVE", Color.White, background = Color(0xFFBA1A1A))
                Spacer(modifier = Modifier.width(8.dp))
                LabelChip(nodeId, Color.White, background = Color.Black.copy(alpha = 0.48f))
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("Last: ${state.cameraLastFrameLabel}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("FPS: ${state.cameraFps} | Q${state.cameraQuality}", color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(12.dp))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CameraOverlayButton(Icons.Default.ZoomIn, "Zoom", onStart)
                    CameraOverlayButton(Icons.Default.Fullscreen, "Fullscreen", onStart)
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(12.dp))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CameraOverlayButton(Icons.Default.Screenshot, "Snapshot", onStart)
                    CameraOverlayButton(Icons.Default.Videocam, "Start or stop", if (state.cameraEnabled) onStop else onStart)
                    CameraOverlayButton(Icons.Default.FlashlightOn, "Torch", onTorch, active = state.cameraTorch, primary = primary)
                    CameraOverlayButton(Icons.Default.Cameraswitch, "Switch lens", onClick = {
                        onLens(if (state.cameraLensFacing == CameraLensFacing.BACK) CameraLensFacing.FRONT else CameraLensFacing.BACK)
                    })
                }
            }
        }
    }
}

@Composable
private fun CameraOverlayButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    primary: Color = Color(0xFF012D1D),
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .background(if (active) primary else Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun CameraThumb(
    node: NodeSummary,
    selected: Boolean,
    primary: Color,
    outline: Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(168.dp)
            .height(112.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        border = BorderStroke(1.dp, if (selected) primary else outline),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (selected) primary.copy(alpha = 0.16f) else Color(0xFFE1E3E4)),
            ) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = if (selected) primary else Color(0xFF717973), modifier = Modifier.align(Alignment.Center).size(30.dp))
                LabelChip(
                    label = node.nodeId,
                    color = Color.White,
                    background = if (selected) primary else Color(0xFF414844),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }
            Text(
                text = node.friendlyName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                color = if (selected) primary else Color.Black,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun DeterrentTile(
    label: String,
    icon: ImageVector,
    background: Color,
    iconBackground: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = textColor),
        contentPadding = PaddingValues(8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconBackground, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
            }
            Text(label, modifier = Modifier.padding(top = 10.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CameraStatsCard(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color,
    iconBg: Color,
    outline: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFEDEEEF),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, outline),
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .background(iconBg, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(30.dp))
            }
            Column {
                Text(label, color = Color(0xFF414844), fontSize = 15.sp)
                Text(value, color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ControlChip(label: String, active: Boolean, primary: Color, icon: ImageVector? = null, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (active) primary else Color.Transparent, contentColor = if (active) Color.White else primary),
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun SmallOutlineButton(icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.size(38.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(0.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ActivityLog(activity: List<String>, primary: Color, gold: Color, outline: Color, muted: Color) {
    DashboardCard(outline = outline) {
        SectionTitle(icon = Icons.Default.History, title = "Activity Log", color = primary)
        if (activity.isEmpty()) {
            Text("No activity yet", color = muted, fontSize = 13.sp)
        }
        activity.reversed().forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(activityColor(line, gold).copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(activityIcon(line), contentDescription = null, tint = activityColor(line, gold), modifier = Modifier.size(16.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(activityTitle(line), color = if (line.contains("failed", ignoreCase = true) || line.contains("offline", ignoreCase = true)) Color(0xFFBA1A1A) else Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(line.drop(10).trim(), color = muted, fontSize = 12.sp)
                    Text(line.take(8), color = outline, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun EmergencyButton(modifier: Modifier, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(60.dp)
            .background(Color(0xFFBA1A1A), CircleShape),
    ) {
        Icon(Icons.Default.Emergency, contentDescription = "Emergency buzzer", tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun BottomNavigationBar(
    modifier: Modifier,
    selected: String,
    primary: Color,
    gold: Color,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(Color.White, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomNavItem(Icons.Default.Hub, "Nodes", selected == "Nodes", primary, gold) { onSelect("Nodes") }
        BottomNavItem(Icons.Default.Notifications, "Alerts", selected == "Alerts", primary, gold) { onSelect("Alerts") }
        BottomNavItem(Icons.Default.AccessTime, "Schedules", selected == "Schedules", primary, gold) { onSelect("Schedules") }
        BottomNavItem(Icons.Default.Settings, "Settings", selected == "Settings", primary, gold) { onSelect("Settings") }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    primary: Color,
    gold: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(if (selected) gold else Color.Transparent, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) primary else Color.Black, modifier = Modifier.size(20.dp))
        Text(label, color = if (selected) primary else Color.Black, fontSize = 10.sp)
    }
}

private fun soundIcon(soundId: String): ImageVector = when (soundId) {
    "dog_barking_1", "dog_barking_2" -> Icons.Default.Pets
    "monkey_screem" -> Icons.Default.Person
    "scaring_monkey_sound" -> Icons.Default.Warning
    "scaring_monkey_sound_2" -> Icons.Default.Campaign
    "loud_buzzer" -> Icons.Default.NotificationImportant
    "loud_mixed_alarm" -> Icons.Default.Emergency
    "gun_shot" -> Icons.Default.Bolt
    else -> Icons.Default.VolumeUp
}

private fun activityIcon(line: String): ImageVector = when {
    line.contains("disconnect", ignoreCase = true) || line.contains("offline", ignoreCase = true) -> Icons.Default.LinkOff
    line.contains("charging", ignoreCase = true) -> Icons.Default.BatteryChargingFull
    line.contains("play", ignoreCase = true) || line.contains("command", ignoreCase = true) -> Icons.Default.Send
    else -> Icons.Default.Info
}

private fun activityColor(line: String, gold: Color): Color = when {
    line.contains("failed", ignoreCase = true) || line.contains("offline", ignoreCase = true) -> Color(0xFFBA1A1A)
    line.contains("charging", ignoreCase = true) -> Color(0xFF1B4332)
    line.contains("play", ignoreCase = true) || line.contains("command", ignoreCase = true) -> gold
    else -> Color(0xFF717973)
}

private fun activityTitle(line: String): String = when {
    line.contains("failed", ignoreCase = true) -> "Command failed"
    line.contains("offline", ignoreCase = true) || line.contains("disconnect", ignoreCase = true) -> "Node offline"
    line.contains("charging", ignoreCase = true) -> "Power status changed"
    line.contains("play", ignoreCase = true) || line.contains("command", ignoreCase = true) -> "Command sent"
    else -> "System update"
}

private fun formatSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val remaining = seconds % 60
    return "%02d:%02d".format(minutes, remaining)
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
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(bottom = 24.dp),
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
                text = "(C) 2024 Farm Guardian Security Systems",
                color = Color(0xFF8A918D),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
