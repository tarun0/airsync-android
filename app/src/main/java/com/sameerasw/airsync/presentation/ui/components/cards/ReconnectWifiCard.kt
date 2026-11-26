package com.sameerasw.airsync.presentation.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.utils.HapticUtil
import com.sameerasw.airsync.utils.WifiGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lets the user restrict automatic reconnects to specific Wi-Fi networks.
 *
 * The list is matched case-insensitively against the current SSID (read through root). Empty
 * means "any network", which is the previous behaviour.
 */
@Composable
fun ReconnectWifiCard(
    ssids: Set<String>,
    onSave: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var text by remember(ssids) { mutableStateOf(ssids.sorted().joinToString(", ")) }
    var status by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                "Reconnect only on these Wi-Fi networks",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "Leave empty to retry on any network. When set, reconnects are skipped while you are " +
                        "on another network, so the phone stops probing while you are out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Wi-Fi names, comma separated") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        HapticUtil.performClick(haptics)
                        scope.launch {
                            val current = withContext(Dispatchers.IO) {
                                WifiGate.currentSsid(context, force = true)
                            }
                            if (current.isNullOrBlank()) {
                                status = "Could not read the current Wi-Fi (needs root)"
                            } else {
                                val entries = text.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                text = (entries + current).distinct().joinToString(", ")
                                status = "Added $current"
                            }
                        }
                    }
                ) {
                    Text("Use current")
                }
                Button(
                    onClick = {
                        HapticUtil.performClick(haptics)
                        val entries = text.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .toSet()
                        onSave(entries)
                        status = if (entries.isEmpty()) {
                            "Off: retrying on any network"
                        } else {
                            "Saved ${entries.joinToString(", ")}"
                        }
                    }
                ) {
                    Text("Save")
                }
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
