package com.accu.ui.appmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.ui.components.ACCTopBar

data class UsageStat(val appName: String, val pkg: String, val todayMins: Int, val weekMins: Int, val launchCount: Int, val lastUsed: String)

@Composable
fun InureUsageStatsScreen(onBack: () -> Unit = {}) {
    var period by remember { mutableStateOf("Today") }
    val stats = remember {
        listOf(
            UsageStat("YouTube", "com.google.android.youtube", 142, 680, 18, "Now"),
            UsageStat("Chrome", "com.android.chrome", 87, 420, 34, "2 min ago"),
            UsageStat("WhatsApp", "com.whatsapp", 71, 390, 58, "5 min ago"),
            UsageStat("Gmail", "com.google.android.gm", 43, 210, 12, "1 hr ago"),
            UsageStat("Spotify", "com.spotify.music", 38, 180, 7, "2 hr ago"),
            UsageStat("Maps", "com.google.android.apps.maps", 31, 145, 5, "Yesterday"),
            UsageStat("Slack", "com.slack", 28, 390, 44, "30 min ago"),
            UsageStat("Instagram", "com.instagram.android", 22, 180, 8, "1 hr ago"),
            UsageStat("Telegram", "org.telegram.messenger", 18, 120, 22, "45 min ago"),
            UsageStat("Settings", "com.android.settings", 12, 40, 9, "3 hr ago"),
        )
    }
    val sorted = stats.sortedByDescending { if (period == "Today") it.todayMins else it.weekMins }
    val totalToday = stats.sumOf { it.todayMins }

    Scaffold(topBar = { ACCTopBar(title = "Usage Statistics", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Period selector
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Today", "This Week").forEach { p ->
                    FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p) })
                }
            }

            // Total card
            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (period == "Today") "Screen Time Today" else "Screen Time This Week", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    val totalMins = if (period == "Today") totalToday else stats.sumOf { it.weekMins }
                    val hrs = totalMins / 60; val mins = totalMins % 60
                    Text("${hrs}h ${mins}m", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    // Mini bar chart
                    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        val days = listOf("M" to 340, "T" to 280, "W" to 420, "T" to 510, "F" to 390, "S" to 680, "S" to totalToday)
                        val maxVal = days.maxOf { it.second }.toFloat()
                        days.forEach { (label, value) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth().fillMaxHeight(value / maxVal).background(MaterialTheme.colorScheme.primary.copy(alpha = if (label == "S") 1f else 0.4f)))
                                Text(label, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(sorted.take(10)) { stat ->
                    val mins = if (period == "Today") stat.todayMins else stat.weekMins
                    val maxMins = if (period == "Today") 200 else 700
                    ListItem(
                        headlineContent = { Text(stat.appName) },
                        supportingContent = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${mins}m used · ${stat.launchCount}x launched · last: ${stat.lastUsed}", fontSize = 11.sp)
                                }
                                Spacer(Modifier.height(2.dp))
                                LinearProgressIndicator(
                                    progress = { (mins / maxMins.toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                )
                            }
                        },
                        leadingContent = { Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Text("${mins / 60}h${if (mins % 60 > 0) " ${mins % 60}m" else ""}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
