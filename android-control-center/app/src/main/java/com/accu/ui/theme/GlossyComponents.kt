package com.accu.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.*

// ─────────────────────────────────────────────────────────
//  Glossy / Glass-morphism Surface composable
//  Drop this anywhere to replace a plain Surface with glass
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
    Box(
        modifier = modifier
            .drawBehind {
                // outer glow
                drawRoundRect(
                    color = glowColor,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    size = size.copy(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()),
                    topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
                )
            }
            .clip(shape)
            .background(glassColor)
            .border(1.dp, borderColor, shape),
        content = content,
    )
}

// ─── Animated gradient background ───
@Composable
fun AnimatedGradientBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "grad_bg")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(8000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "grad_offset",
    )
    Box(
        modifier = modifier.drawBehind {
            drawRect(
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(size.width * offset, 0f),
                    end = Offset(size.width * (1f - offset), size.height),
                )
            )
        },
        content = content,
    )
}

// ─── Neon glow text ───
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
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = EaseInOut), repeatMode = RepeatMode.Reverse),
        label = "glow_alpha",
    )
    androidx.compose.material3.Text(
        text = text,
        color = color.copy(alpha = alpha),
        style = style,
        modifier = modifier,
    )
}

// ─── Pulse ring animation (for status indicators) ───
@Composable
fun PulseRing(
    color: Color,
    size: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = EaseOut), repeatMode = RepeatMode.Restart),
        label = "pulse_scale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = EaseOut), repeatMode = RepeatMode.Restart),
        label = "pulse_alpha",
    )
    Box(modifier = modifier.size(size * 2), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = color.copy(alpha = alpha), radius = (size.toPx() / 2f) * scale)
            drawCircle(color = color, radius = size.toPx() / 2f)
        }
    }
}

// ─── Glossy card modifier ───
fun Modifier.glossyCard(
    primaryColor: Color,
    cornerRadius: Float = 16f,
    alpha: Float = 0.08f,
): Modifier = this
    .drawBehind {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = alpha * 1.5f), primaryColor.copy(alpha = alpha * 0.3f)),
            ),
            cornerRadius = CornerRadius(cornerRadius.dp.toPx()),
        )
    }
    .border(1.dp, Brush.verticalGradient(
        colors = listOf(primaryColor.copy(alpha = 0.3f), primaryColor.copy(alpha = 0.08f)),
    ), RoundedCornerShape(cornerRadius.dp))

// ─── Status dot ───
@Composable
fun StatusDot(active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) Color(0xFF00FF88) else Color(0xFFFF6B6B)
    if (active) PulseRing(color = color, size = 5.dp, modifier = modifier)
    else Box(modifier = modifier.size(10.dp).clip(CircleShape).background(color))
}

// ─── Shimmer loading effect ───
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing)),
        label = "shimmer_x",
    )
    Box(modifier = modifier.clip(shape).drawBehind {
        drawRect(brush = Brush.horizontalGradient(
            colors = listOf(Color(0x1AFFFFFF), Color(0x33FFFFFF), Color(0x1AFFFFFF)),
            startX = shimmerOffset * size.width,
            endX = (shimmerOffset + 1f) * size.width,
        ))
    })
}
