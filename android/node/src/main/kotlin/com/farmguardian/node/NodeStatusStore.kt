package com.farmguardian.node

import android.content.Context
import com.farmguardian.shared.NodeStatusPayload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NodeStatusStore {
    private const val PREFS = "farm_guardian_node_status"
    private const val MAX_LOGS = 100
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun saveConnection(context: Context, connection: String) {
        prefs(context).edit().putString("connection", connection).apply()
    }

    fun saveSnapshot(context: Context, status: NodeStatusPayload, lastCommand: String) {
        prefs(context).edit()
            .putString("connection", if (status.online) "Connected" else "Offline")
            .putInt("battery", status.batteryPercent ?: -1)
            .putBoolean("charging", status.charging == true)
            .putString("network", status.network ?: "Unknown")
            .putBoolean("bluetooth", status.bluetoothConnected == true)
            .putString("speaker", status.speakerName ?: "Phone speaker")
            .putString("playback", status.playbackState.name)
            .putString("currentSound", status.currentSound ?: "None")
            .putString("lastCommand", lastCommand)
            .putLong("lastSeen", status.lastSeen)
            .apply()
    }

    fun appendLog(context: Context, action: String, result: String) {
        val prefs = prefs(context)
        val logs = readLogs(context).toMutableList()
        logs += "${timeFormat.format(Date())}  $action  $result"
        prefs.edit().putString("logs", logs.takeLast(MAX_LOGS).joinToString("\n")).apply()
    }

    fun readSnapshot(context: Context): NodeUiSnapshot {
        val prefs = prefs(context)
        return NodeUiSnapshot(
            connection = prefs.getString("connection", "Starting") ?: "Starting",
            battery = prefs.getInt("battery", -1).takeIf { it >= 0 }?.let { "$it%" } ?: "Unknown",
            charging = if (prefs.getBoolean("charging", false)) "Charging" else "Not charging",
            network = prefs.getString("network", "Unknown") ?: "Unknown",
            bluetooth = if (prefs.getBoolean("bluetooth", false)) "Connected" else "Disconnected",
            speaker = prefs.getString("speaker", "Unknown") ?: "Unknown",
            playback = prefs.getString("playback", "Stopped") ?: "Stopped",
            currentSound = prefs.getString("currentSound", "None") ?: "None",
            lastCommand = prefs.getString("lastCommand", "none") ?: "none",
            lastSeen = prefs.getLong("lastSeen", 0L).takeIf { it > 0 }?.let { timeFormat.format(Date(it)) } ?: "Never",
            logs = readLogs(context),
        )
    }

    private fun readLogs(context: Context): List<String> =
        prefs(context).getString("logs", null)
            ?.lines()
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

data class NodeUiSnapshot(
    val connection: String,
    val battery: String,
    val charging: String,
    val network: String,
    val bluetooth: String,
    val speaker: String,
    val playback: String,
    val currentSound: String,
    val lastCommand: String,
    val lastSeen: String,
    val logs: List<String>,
)
