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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Farm Guardian Node", style = MaterialTheme.typography.headlineMedium)
        Image(
            painter = painterResource(id = R.drawable.farm_guardian_logo),
            contentDescription = "Farm Guardian logo",
            modifier = Modifier.size(120.dp),
        )

        Text("Node ID: ${login?.nodeId ?: "Unknown"}")
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
        Button(
            onClick = {
                context.stopService(Intent(context, NodeService::class.java))
                NodeStatusStore.clearLogin(context)
                login = null
            },
        ) {
            Text("Logout Node")
        }
        Button(
            onClick = {
                NodeStatusStore.clearLogs(context)
                snapshot = NodeStatusStore.readSnapshot(context)
            },
        ) {
            Text("Clear Logs")
        }
        Text("Logs", style = MaterialTheme.typography.titleMedium)
        snapshot.logs.takeLast(20).forEach {
            Text(it)
        }
    }
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
    val fieldBackground = Color(0xFFF3F4F5)
    val gold = Color(0xFFFFB702)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(page)
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .align(Alignment.TopCenter),
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
