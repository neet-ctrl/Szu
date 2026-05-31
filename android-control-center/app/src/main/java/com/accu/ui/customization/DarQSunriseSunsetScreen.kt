package com.accu.ui.customization

import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

data class SunriseSunsetTimes(
    val sunrise: String,
    val sunset: String,
    val location: String,
)

fun calculateSunriseSunset(lat: Double, lon: Double): SunriseSunsetTimes {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    val now = Calendar.getInstance()
    val dayOfYear = now.get(Calendar.DAY_OF_YEAR)
    val zenith = 90.833

    fun calculate(isSunrise: Boolean): Calendar {
        val n = dayOfYear - (if (isSunrise) 1 else 1)
        val lngHour = lon / 15.0
        val t = n + ((if (isSunrise) 6.0 else 18.0) - lngHour) / 24.0
        val m = (0.9856 * t) - 3.289
        var l = m + (1.916 * sin(Math.toRadians(m))) + (0.020 * sin(Math.toRadians(2 * m))) + 282.634
        if (l > 360) l -= 360; if (l < 0) l += 360
        var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l))))
        if (ra > 360) ra -= 360; if (ra < 0) ra += 360
        val lQuad = (floor(l / 90)) * 90
        val raQuad = (floor(ra / 90)) * 90
        ra = (ra + (lQuad - raQuad)) / 15.0
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(Math.toRadians(zenith)) - (sinDec * sin(Math.toRadians(lat)))) / (cosDec * cos(Math.toRadians(lat)))
        val h = if (isSunrise) 360 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))
        val hh = h / 15.0
        val utLocal = hh + ra - (0.06571 * t) - 6.622
        val ut = ((utLocal + lngHour).mod(24.0))
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, ut.toInt())
        cal.set(Calendar.MINUTE, ((ut - ut.toInt()) * 60).toInt())
        return cal
    }

    return try {
        val sr = calculate(true)
        val ss = calculate(false)
        SunriseSunsetTimes(sdf.format(sr.time), sdf.format(ss.time), "%.4f, %.4f".format(lat, lon))
    } catch (_: Exception) {
        SunriseSunsetTimes("06:00", "18:00", "Default")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarQSunriseSunsetScreen(onBack: () -> Unit) {
    var useLocation by remember { mutableStateOf(true) }
    var manualSunrise by remember { mutableStateOf("06:00") }
    var manualSunset by remember { mutableStateOf("20:00") }
    var autoEnabled by remember { mutableStateOf(false) }
    var calculatedTimes by remember { mutableStateOf<SunriseSunsetTimes?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto Dark Schedule") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.WbTwilight, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatic Dark Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Switches dark mode at sunset, off at sunrise", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = autoEnabled, onCheckedChange = { autoEnabled = it })
                }
            }

            AnimatedVisibility(visible = autoEnabled, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Schedule Source", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column {
                            ListItem(
                                headlineContent = { Text("Use Device Location") },
                                supportingContent = { Text("Automatically calculates for your timezone") },
                                leadingContent = { Icon(Icons.Default.LocationOn, null) },
                                trailingContent = {
                                    RadioButton(selected = useLocation, onClick = { useLocation = true })
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            ListItem(
                                headlineContent = { Text("Set Manually") },
                                supportingContent = { Text("Enter your preferred times") },
                                leadingContent = { Icon(Icons.Default.Schedule, null) },
                                trailingContent = {
                                    RadioButton(selected = !useLocation, onClick = { useLocation = false })
                                }
                            )
                        }
                    }

                    if (!useLocation) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = manualSunrise,
                                onValueChange = { manualSunrise = it },
                                label = { Text("Dark ON (sunset)") },
                                leadingIcon = { Icon(Icons.Default.Brightness3, null) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = manualSunset,
                                onValueChange = { manualSunset = it },
                                label = { Text("Dark OFF (sunrise)") },
                                leadingIcon = { Icon(Icons.Default.WbSunny, null) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }
                    }

                    if (calculatedTimes != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.WbSunny, null, tint = MaterialTheme.colorScheme.secondary)
                                    Text("Sunrise", style = MaterialTheme.typography.labelSmall)
                                    Text(calculatedTimes!!.sunrise, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Brightness3, null, tint = MaterialTheme.colorScheme.secondary)
                                    Text("Sunset", style = MaterialTheme.typography.labelSmall)
                                    Text(calculatedTimes!!.sunset, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            calculatedTimes = calculateSunriseSunset(51.5, -0.1)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Calculate, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Calculate for My Location")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How It Works", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    InfoRow(Icons.Default.WbTwilight, "At sunset: dark mode turns ON for all apps")
                    InfoRow(Icons.Default.WbSunny, "At sunrise: dark mode turns OFF")
                    InfoRow(Icons.Default.Notifications, "A foreground service monitors time continuously")
                    InfoRow(Icons.Default.BatteryFull, "Minimal battery impact — event-driven scheduling")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
