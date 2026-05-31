package com.accu.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.accu.ui.dashboard.ShizukuStatus
import com.accu.ui.theme.*

// ─── Press-scale modifier ─────────────────────────────────────────────────────
// Pass the same MutableInteractionSource used by your clickable/Surface so the
// scale reacts to the real press, not a separate phantom interactionSource.
//
// Example:
//   val src = remember { MutableInteractionSource() }
//   Box(modifier = Modifier.pressScale(src).clickable(src, ...) { ... })
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.96f,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "press_scale",
    )
    this.scale(scale)
}

@Composable
fun StatusBadge(
    status: ShizukuStatus,
    onClick: () -> Unit,
    glowAlpha: Float = 1f,
) {
    val haptic = LocalHapticFeedback.current
    val (color, label, icon) = when (status) {
        ShizukuStatus.RUNNING       -> Triple(AccentGreen,  "Active",        Icons.Default.CheckCircle)
        ShizukuStatus.ROOT_MODE     -> Triple(AccentCyan,   "Root",          Icons.Default.AdminPanelSettings)
        ShizukuStatus.NOT_RUNNING   -> Triple(AccentOrange, "Stopped",       Icons.Default.Warning)
        ShizukuStatus.NOT_INSTALLED -> Triple(AccentRed,    "Not Installed", Icons.Default.Error)
        ShizukuStatus.UNKNOWN       -> Triple(Color.Gray,   "Unknown",       Icons.Default.Help)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "badge_scale",
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = glowAlpha * 0.8f)),
        modifier = Modifier
            .scale(scale)
            .drawBehind {
                if (status == ShizukuStatus.RUNNING || status == ShizukuStatus.ROOT_MODE) {
                    drawCircle(color.copy(alpha = 0.08f * glowAlpha), radius = size.maxDimension * 0.8f)
                }
            },
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ACCTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ACCLargeTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LargeTopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
fun FeatureRow(
    title: String,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "row_scale",
    )

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) ({
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }) else null,
        leadingContent = leadingIcon,
        trailingContent = trailingContent,
        modifier = if (onClick != null) {
            Modifier
                .scale(scale)
                .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
        } else {
            Modifier
        },
    )
}

@Composable
fun FeatureSwitch(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    FeatureRow(
        title = title,
        subtitle = subtitle,
        leadingIcon = leadingIcon,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { newVal ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCheckedChange(newVal)
                },
                enabled = enabled,
            )
        },
        onClick = {
            if (enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(!checked)
            }
        },
    )
}

@Composable
fun LoadingScreen(message: String = "Loading…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        action?.let {
            Spacer(Modifier.height(24.dp))
            it()
        }
    }
}
