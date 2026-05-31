package com.accu.sdkdemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accu.sdkdemo.data.TestResult
import com.accu.sdkdemo.data.TestStatus
import com.accu.sdkdemo.ui.components.SectionHeader
import com.accu.sdkdemo.ui.components.StatusColor
import com.accu.sdkdemo.viewmodel.MainViewModel

@Composable
fun AutomatedTestScreen(vm: MainViewModel) {
    val testResults by vm.testResults.collectAsState()
    val isRunning   by vm.isTestRunning.collectAsState()
    val progress    by vm.testProgress.collectAsState()

    val passCount = testResults.count { it.status == TestStatus.PASS }
    val failCount = testResults.count { it.status == TestStatus.FAIL }
    val warnCount = testResults.count { it.status == TestStatus.WARNING }
    val pendCount = testResults.count { it.status == TestStatus.PENDING }

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Automated Validation Suite — ${testResults.size} Tests")

                // Stats row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TestStatChip("PASS",    passCount, TestStatus.PASS,    Modifier.weight(1f))
                    TestStatChip("FAIL",    failCount, TestStatus.FAIL,    Modifier.weight(1f))
                    TestStatChip("WARN",    warnCount, TestStatus.WARNING, Modifier.weight(1f))
                    TestStatChip("PENDING", pendCount, TestStatus.PENDING, Modifier.weight(1f))
                }

                // Progress bar
                if (isRunning || progress > 0f) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        Text("${(progress * 100).toInt()}%  •  ${testResults.count { it.status != TestStatus.PENDING && it.status != TestStatus.RUNNING }} / ${testResults.size} done",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Run button
                Button(
                    onClick = { vm.runFullValidation() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isRunning && failCount == 0 && passCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                    ),
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Running tests…")
                    } else {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (passCount > 0 || failCount > 0) "Re-Run Full Validation" else "Run Full Validation Suite")
                    }
                }

                // Overall result
                if (!isRunning && (passCount + failCount + warnCount) > 0) {
                    Surface(
                        color = if (failCount == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (failCount == 0) Icons.Default.CheckCircle else Icons.Default.Cancel, null,
                                tint = if (failCount == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Text(
                                if (failCount == 0) "All tests passing — ACCU SDK integration is healthy!"
                                else "$failCount test(s) failed — see details below",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(testResults, key = { it.id }) { test ->
                TestResultCard(test)
            }
        }
    }
}

@Composable
private fun TestStatChip(label: String, count: Int, status: TestStatus, modifier: Modifier) {
    val (bg, contentColor) = when (status) {
        TestStatus.PASS    -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        TestStatus.FAIL    -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        TestStatus.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        else               -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = bg)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = contentColor))
            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

@Composable
private fun TestResultCard(test: TestResult) {
    val (icon, color) = when (test.status) {
        TestStatus.PASS    -> Icons.Default.CheckCircle to androidx.compose.ui.graphics.Color(0xFF2ECC71)
        TestStatus.FAIL    -> Icons.Default.Cancel to androidx.compose.ui.graphics.Color(0xFFE74C3C)
        TestStatus.WARNING -> Icons.Default.Warning to androidx.compose.ui.graphics.Color(0xFFF39C12)
        TestStatus.RUNNING -> Icons.Default.HourglassTop to androidx.compose.ui.graphics.Color(0xFF3498DB)
        TestStatus.PENDING -> Icons.Default.RadioButtonUnchecked to androidx.compose.ui.graphics.Color(0xFF9E9E9E)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (test.status) {
                TestStatus.FAIL -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                TestStatus.PASS -> MaterialTheme.colorScheme.surface
                else            -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (test.status == TestStatus.RUNNING) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(icon, null, Modifier.size(20.dp), tint = color)
            }
            Column(Modifier.weight(1f)) {
                Text("#${test.id + 1} — ${test.name}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                Text(test.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                if (test.detail.isNotBlank()) {
                    Text(test.detail, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = color, fontSize = 11.sp)
                }
            }
        }
    }
}
