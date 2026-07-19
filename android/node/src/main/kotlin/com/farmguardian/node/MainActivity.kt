package com.farmguardian.node

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        if (NodeStatusStore.readLogin(this) != null) {
            startForegroundService(Intent(this, NodeService::class.java))
        }
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
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
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
    var login by remember { mutableStateOf(NodeStatusStore.readLogin(context)) }
    var username by remember { mutableStateOf(login?.username.orEmpty()) }
    var password by remember { mutableStateOf(login?.password.orEmpty()) }
    var nodeId by remember { mutableStateOf(login?.nodeId ?: "Farm-01") }
    var friendlyName by remember { mutableStateOf(login?.friendlyName ?: "Main Farm") }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = NodeStatusStore.readSnapshot(context)
            login = NodeStatusStore.readLogin(context)
            delay(1_000)
        }
    }

    if (login == null) {
        NodeLoginScreen(
            username = username,
            password = password,
            nodeId = nodeId,
            friendlyName = friendlyName,
            onUsernameChange = { username = it },
            onPasswordChange = { password = it },
            onNodeIdChange = { nodeId = it },
            onFriendlyNameChange = { friendlyName = it },
            onSubmit = {
                NodeStatusStore.saveLogin(context, username.trim(), password, nodeId.trim(), friendlyName.trim())
                login = NodeStatusStore.readLogin(context)
                onStartService()
            },
        )
        return
    }

    NodeDashboardScreen(
        nodeId = login?.nodeId ?: "Unknown",
        snapshot = snapshot,
        onStartService = onStartService,
        onClearLogs = {
            NodeStatusStore.clearLogs(context)
            snapshot = NodeStatusStore.readSnapshot(context)
        },
        onLogout = {
            context.stopService(Intent(context, NodeService::class.java))
            NodeStatusStore.clearLogin(context)
            login = null
        },
    )
}

@Composable
private fun NodeDashboardScreen(
    nodeId: String,
    snapshot: NodeUiSnapshot,
    onStartService: () -> Unit,
    onClearLogs: () -> Unit,
    onLogout: () -> Unit,
) {
    val primary = Color(0xFF012D1D)
    val page = Color(0xFFF8F9FA)
    val outline = Color(0xFFC1C8C2)
    val muted = Color(0xFF414844)
    val gold = Color(0xFFFFB702)
    val connected = snapshot.connection.equals("Connected", ignoreCase = true)
    val playing = snapshot.playback.equals("Playing", ignoreCase = true)
    var selectedNav by remember { mutableStateOf("Nodes") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(page),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(Color.White)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Agriculture, contentDescription = null, tint = primary, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text("Farm Guardian", color = primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Node ID: $nodeId", color = Color.Black, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                ConnectedPill(if (connected) "Connected" else snapshot.connection, connected, primary)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
                    StatusTile(
                        icon = Icons.Default.Hub,
                        badge = if (connected) "Stable" else "Retrying",
                        label = "Connection",
                        value = snapshot.connection,
                        badgeColor = if (connected) Color(0xFFC1ECD4) else Color(0xFFFFDEA9),
                        iconColor = primary,
                        outline = outline,
                        modifier = Modifier.weight(1f),
                    )
                    StatusTile(
                        icon = Icons.Default.Terminal,
                        badge = "Active",
                        label = "Service",
                        value = "Running",
                        badgeColor = Color(0xFFC1ECD4),
                        iconColor = primary,
                        outline = outline,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
                    StatusTile(
                        icon = Icons.Default.BatteryChargingFull,
                        badge = snapshot.charging,
                        label = "Battery",
                        value = snapshot.battery,
                        badgeColor = if (snapshot.charging == "Charging") Color(0xFFFFDEA9) else Color(0xFFE1E3E4),
                        iconColor = Color(0xFF7D5800),
                        outline = outline,
                        modifier = Modifier.weight(1f),
                    )
                    StatusTile(
                        icon = Icons.Default.Bluetooth,
                        badge = if (snapshot.bluetooth == "Connected") "Paired" else "Phone",
                        label = "Audio Source",
                        value = snapshot.speaker,
                        badgeColor = if (snapshot.bluetooth == "Connected") Color(0xFFE1E3E4) else Color(0xFFC1ECD4),
                        iconColor = primary,
                        outline = outline,
                        modifier = Modifier.weight(1f),
                    )
                }

                DashboardCard(outline = outline) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Playback", color = primary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (playing) Icons.Default.PlayArrow else Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = muted,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (playing) "Playing" else "Idle / ${snapshot.playback}",
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(26.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .background(Color(0xFFEDEEEF), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF717973), modifier = Modifier.size(42.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (playing) "Deterrent stream active" else "No active deterrent stream",
                                color = muted,
                                fontSize = 15.sp,
                            )
                            Text(
                                text = if (playing) snapshot.currentSound else "System Standby",
                                color = primary,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(Color(0xFFE1E3E4), RoundedCornerShape(999.dp)),
                            ) {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth(if (playing) 0.62f else 0f)
                                        .height(4.dp)
                                        .background(primary, RoundedCornerShape(999.dp)),
                                )
                            }
                        }
                    }
                }

                DashboardCard(outline = outline) {
                    Text("NODE CONTROLS", color = muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = onStartService,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1E3E4), contentColor = muted),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("Start Service", modifier = Modifier.padding(start = 10.dp), fontSize = 18.sp)
                    }
                    Button(
                        onClick = onClearLogs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = primary),
                        border = BorderStroke(2.dp, Color(0xFF717973)),
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Text("Clear Logs", modifier = Modifier.padding(start = 10.dp), fontSize = 18.sp)
                    }
                    Button(
                        onClick = onLogout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFBA1A1A)),
                        border = BorderStroke(2.dp, Color(0xFFBA1A1A)),
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null)
                        Text("Logout Node", modifier = Modifier.padding(start = 10.dp), fontSize = 18.sp)
                    }
                }

                DashboardCard(outline = outline) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Activity Log", color = primary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("Last update: ${snapshot.lastSeen}", color = muted, fontSize = 12.sp)
                    }
                    val logs = snapshot.logs.takeLast(6).ifEmpty {
                        listOf("${snapshot.lastSeen.takeIf { it != "Never" } ?: "--:--:--"}  Service  Waiting for first event")
                    }
                    logs.reversed().forEach { line ->
                        LogRow(line = line, primary = primary, gold = gold, muted = muted)
                    }
                }

                Spacer(modifier = Modifier.height(128.dp))
            }
        }

        NodeBottomNav(
            modifier = Modifier.align(Alignment.BottomCenter),
            selected = selectedNav,
            primary = primary,
            gold = gold,
            onSelect = { selectedNav = it },
        )
    }
}

@Composable
private fun StatusTile(
    icon: ImageVector,
    badge: String,
    label: String,
    value: String,
    badgeColor: Color,
    iconColor: Color,
    outline: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(192.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(30.dp))
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = badge,
                    color = Color(0xFF012D1D),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Column {
                Text(label, color = Color(0xFF414844), fontSize = 14.sp)
                Text(value, color = Color(0xFF012D1D), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DashboardCard(outline: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

@Composable
private fun ConnectedPill(label: String, connected: Boolean, primary: Color) {
    Row(
        modifier = Modifier
            .background(if (connected) Color(0xFFC1ECD4) else Color(0xFFFFDEA9), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (connected) primary else Color(0xFF7D5800), CircleShape),
        )
        Text(label, color = Color.Black, fontSize = 14.sp)
    }
}

@Composable
private fun LogRow(line: String, primary: Color, gold: Color, muted: Color) {
    val title = nodeLogTitle(line)
    val color = nodeLogColor(line, primary, gold)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(color.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(nodeLogIcon(line), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.Black, fontSize = 18.sp)
            Text(line.drop(10).trim(), color = muted, fontSize = 13.sp)
        }
        Text(line.take(8), color = Color(0xFF717973), fontSize = 13.sp)
    }
}

@Composable
private fun NodeBottomNav(
    modifier: Modifier,
    selected: String,
    primary: Color,
    gold: Color,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(Color.White, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NodeNavItem(Icons.Default.Hub, "Nodes", selected == "Nodes", primary, gold) { onSelect("Nodes") }
        NodeNavItem(Icons.Default.Notifications, "Alerts", selected == "Alerts", primary, gold) { onSelect("Alerts") }
        NodeNavItem(Icons.Default.Timer, "Schedules", selected == "Schedules", primary, gold) { onSelect("Schedules") }
        NodeNavItem(Icons.Default.Settings, "Settings", selected == "Settings", primary, gold) { onSelect("Settings") }
    }
}

@Composable
private fun NodeNavItem(
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) primary else Color.Black, modifier = Modifier.size(21.dp))
        Text(label, color = if (selected) primary else Color.Black, fontSize = 11.sp)
    }
}

private fun nodeLogIcon(line: String): ImageVector = when {
    line.contains("service", ignoreCase = true) -> Icons.Default.PowerSettingsNew
    line.contains("auto", ignoreCase = true) || line.contains("config", ignoreCase = true) -> Icons.Default.SettingsSuggest
    line.contains("connect", ignoreCase = true) -> Icons.Default.Link
    line.contains("battery", ignoreCase = true) || line.contains("charging", ignoreCase = true) -> Icons.Default.BatteryChargingFull
    else -> Icons.Default.Info
}

private fun nodeLogColor(line: String, primary: Color, gold: Color): Color = when {
    line.contains("auto", ignoreCase = true) || line.contains("config", ignoreCase = true) -> gold
    line.contains("fail", ignoreCase = true) || line.contains("error", ignoreCase = true) -> Color(0xFFBA1A1A)
    line.contains("connect", ignoreCase = true) -> Color(0xFF717973)
    else -> primary
}

private fun nodeLogTitle(line: String): String = when {
    line.contains("service", ignoreCase = true) -> "Service Started"
    line.contains("auto", ignoreCase = true) -> "Auto play enabled"
    line.contains("connect", ignoreCase = true) -> "Connected by Controller"
    line.contains("battery", ignoreCase = true) || line.contains("charging", ignoreCase = true) -> "Charging detected"
    line.contains("play", ignoreCase = true) -> "Playback command"
    else -> "Node event"
}

@Composable
private fun NodeLoginScreen(
    username: String,
    password: String,
    nodeId: String,
    friendlyName: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onNodeIdChange: (String) -> Unit,
    onFriendlyNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val primary = Color(0xFF012D1D)
    val page = Color(0xFFF8F9FA)
    val muted = Color(0xFF414844)
    val outline = Color(0xFF717973)
    val outlineVariant = Color(0xFFC1C8C2)
    val gold = Color(0xFFFFB702)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 144.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                modifier = Modifier.size(112.dp),
                shape = RoundedCornerShape(2.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.farm_guardian_logo),
                        contentDescription = "Farm Guardian logo",
                        modifier = Modifier.size(88.dp),
                    )
                }
            }

            Text(
                text = "Farm Guardian Node",
                color = primary,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 36.dp, bottom = 62.dp),
            )

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Configure Node", color = Color.Black, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
                    Text("Use the same login as the Controller app", color = muted, fontSize = 24.sp)
                }

                Spacer(modifier = Modifier.height(18.dp))
                FieldLabel("Username", muted)
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    placeholder = { Text("Enter username", fontSize = 26.sp) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(28.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                FieldLabel("Password", muted)
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    placeholder = { Text("........", fontSize = 26.sp) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(28.dp)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    Spacer(modifier = Modifier.weight(1f).height(1.dp).background(outlineVariant))
                    Text("HARDWARE CONFIG", color = outline, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.weight(1f).height(1.dp).background(outlineVariant))
                }

                FieldLabel("Node ID", muted)
                OutlinedTextField(
                    value = nodeId,
                    onValueChange = onNodeIdChange,
                    placeholder = { Text("Farm-01", fontSize = 26.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                FieldLabel("Node Name", muted)
                OutlinedTextField(
                    value = friendlyName,
                    onValueChange = onFriendlyNameChange,
                    placeholder = { Text("Main Farm", fontSize = 26.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp),
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(32.dp))
                    Text("Login and Start Node", modifier = Modifier.padding(start = 16.dp), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(22.dp))
                Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(outlineVariant))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = primary, modifier = Modifier.size(26.dp))
                    Text("Trouble connecting?", color = primary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.SettingsEthernet, contentDescription = null, tint = muted, modifier = Modifier.size(26.dp))
                    Text("Advanced Network Settings", color = muted, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color(0xE6E1E3E4), RoundedCornerShape(999.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(gold, RoundedCornerShape(999.dp)),
                )
                Text("DHCP: Connected", color = muted, fontSize = 18.sp)
            }
            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(outlineVariant),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Sensors, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
                Text("Hardware OK", color = muted, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun FieldLabel(label: String, color: Color) {
    Text(label, color = color, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
}
