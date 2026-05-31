package com.accu.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

// ─────────────────────────────────────────────────────────
//  Glossy / Glass-morphism Surface
//  Contrast-guard: auto-boosts border opacity when the
//  background is bright (luminance > 0.5) so glass edges
//  stay visible on light wallpapers.
// ─────────────────────────────────────────────────────────

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    glassColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
    glowColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
    content: @Composable BoxScope.() -> Unit,
) {
    // Contrast guard — bright backgrounds get a stronger, darker border
    val background = MaterialTheme.colorScheme.background
    val isLight = background.luminance() > 0.5f
    val safeBorderAlpha = if (isLight) (borderColor.alpha * 2.5f).coerceAtMost(0.75f) else borderColor.alpha
    val safeGlowAlpha  = if (isLight) (glowColor.alpha  * 2.0f).coerceAtMost(0.25f) else glowColor.alpha
    val safeBorder = borderColor.copy(alpha = safeBorderAlpha)
    val safeGlow   = glowColor.copy(alpha = safeGlowAlpha)
    val safeGlass  = if (isLight) glassColor.copy(alpha = (glassColor.alpha * 2f).coerceAtMost(0.18f)) else glassColor

    Box(
        modifier = modifier
            .drawBehind {
                drawRoundRect(
                    color = safeGlow,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    size = size.copy(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()),
                    topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
                )
            }
            .clip(shape)
            .background(safeGlass)
            .border(1.dp, safeBorder, shape),
        content = content,
    )
}

// ─── Animated gradient background ───────────────────────────────────────────
@Composable
fun AnimatedGradientBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "grad_bg")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "grad_offset",
    )
    Box(
        modifier = modifier.drawBehind {
            drawRect(
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(size.width * offset, 0f),
                    end   = Offset(size.width * (1f - offset), size.height),
                )
            )
        },
        content = content,
    )
}

// ─── Neon glow text ──────────────────────────────────────────────────────────
@Composable
fun GlowText(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow_text")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_alpha",
    )
    Text(
        text = text,
        color = color.copy(alpha = alpha),
        style = style,
        modifier = modifier,
    )
}

// ─── Pulse ring animation ─────────────────────────────────────────────────────
@Composable
fun PulseRing(
    color: Color,
    size: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_scale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_alpha",
    )
    Box(modifier = modifier.size(size * 2), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = color.copy(alpha = alpha), radius = (size.toPx() / 2f) * scale)
            drawCircle(color = color, radius = size.toPx() / 2f)
        }
    }
}

// ─── Glossy card modifier ─────────────────────────────────────────────────────
fun Modifier.glossyCard(
    primaryColor: Color,
    cornerRadius: Float = 16f,
    alpha: Float = 0.08f,
): Modifier = this
    .drawBehind {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    primaryColor.copy(alpha = alpha * 1.5f),
                    primaryColor.copy(alpha = alpha * 0.3f),
                ),
            ),
            cornerRadius = CornerRadius(cornerRadius.dp.toPx()),
        )
    }
    .border(
        1.dp,
        Brush.verticalGradient(
            colors = listOf(primaryColor.copy(alpha = 0.3f), primaryColor.copy(alpha = 0.08f)),
        ),
        RoundedCornerShape(cornerRadius.dp),
    )

// ─── Status dot ──────────────────────────────────────────────────────────────
@Composable
fun StatusDot(active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) Color(0xFF00FF88) else Color(0xFFFF6B6B)
    if (active) PulseRing(color = color, size = 5.dp, modifier = modifier)
    else Box(modifier = modifier.size(10.dp).clip(CircleShape).background(color))
}

// ─── Shimmer loading effect ───────────────────────────────────────────────────
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val shimmerHighColor = if (isLight) Color(0x33000000) else Color(0x33FFFFFF)
    val shimmerBaseColor = if (isLight) Color(0x1A000000) else Color(0x1AFFFFFF)

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing)),
        label = "shimmer_x",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(shimmerBaseColor, shimmerHighColor, shimmerBaseColor),
                        startX = shimmerOffset * size.width,
                        endX   = (shimmerOffset + 1f) * size.width,
                    )
                )
            },
    )
}

// ─── Skeleton list (shimmer rows for loading states) ─────────────────────────
@Composable
fun SkeletonList(
    rows: Int = 5,
    modifier: Modifier = Modifier,
    showLeadingCircle: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { i ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showLeadingCircle) {
                    ShimmerBox(modifier = Modifier.size(40.dp), shape = CircleShape)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val widthFraction = (0.5f + (i % 3) * 0.12f).coerceAtMost(0.88f)
                    ShimmerBox(modifier = Modifier.fillMaxWidth(widthFraction).height(14.dp))
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.38f).height(10.dp))
                }
            }
        }
    }
}

// ─── Full-screen skeleton (drop-in for loading state) ────────────────────────
@Composable
fun SkeletonScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        // fake top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerBox(modifier = Modifier.size(24.dp), shape = CircleShape)
            ShimmerBox(modifier = Modifier.width(160.dp).height(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        SkeletonList(rows = 8)
    }
}

// ─── Expandable section card ──────────────────────────────────────────────────
//  Drop-in collapsible card for progressive disclosure on dense screens.
//  Uses rememberSaveable so expand state survives rotation.
@Composable
fun ExpandableSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    badge: String? = null,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chevron_rot",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val headerScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "header_scale",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column {
            ListItem(
                headlineContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        if (badge != null) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                },
                leadingContent = if (icon != null) ({
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                }) else null,
                trailingContent = {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(chevronRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier
                    .scale(headerScale)
                    .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        expanded = !expanded
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Top,
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(tween(150)),
            ) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    content()
                }
            }
        }
    }
}
