package com.accu.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.accu.ui.theme.ACCTheme
import kotlinx.coroutines.*

private data class OverlayAppEntry(val pkg: String, val label: String, val volume: Float)

class SoundMasterOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private val overlayScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var autoHideJob: Job? = null
    private val lifecycleOwner = OverlayLifecycleOwner()

    private val _visible = mutableStateOf(false)
    private val _appVolumes = mutableStateOf<List<OverlayAppEntry>>(emptyList())

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        lifecycleOwner.handleCreate()
        buildOverlayView()
        hookVolumeChanged()
    }

    private fun hookVolumeChanged() {
        AccuSoundMasterService.onVolumeChanged = {
            overlayScope.launch(Dispatchers.Main) {
                refreshAppVolumes()
                if (_appVolumes.value.isNotEmpty()) {
                    showOverlay()
                    scheduleAutoHide()
                }
            }
        }
    }

    private fun refreshAppVolumes() {
        val pkgs = AccuSoundMasterService.getActivePackages()
        _appVolumes.value = pkgs.map { pkg ->
            val label = pkg.split(".").last().replaceFirstChar { it.uppercase() }
            OverlayAppEntry(
                pkg = pkg,
                label = label,
                volume = AccuSoundMasterService.getVolumeOf(pkg, -1),
            )
        }
    }

    private fun showOverlay() {
        _visible.value = true
        if (overlayView?.windowToken == null) {
            try { windowManager.addView(overlayView, buildLayoutParams()) } catch (_: Exception) {}
        }
    }

    private fun hideOverlay() {
        _visible.value = false
        overlayScope.launch(Dispatchers.Main) {
            delay(400)
            if (!_visible.value) {
                try { if (overlayView?.windowToken != null) windowManager.removeView(overlayView) } catch (_: Exception) {}
            }
        }
    }

    private fun scheduleAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = overlayScope.launch {
            delay(AUTO_HIDE_MS)
            hideOverlay()
        }
    }

    private fun buildLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = 0
    }

    private fun buildOverlayView() {
        overlayView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            this.setViewTreeLifecycleOwner(lifecycleOwner)
            this.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                ACCTheme {
                    val visible by _visible
                    val appVolumes by _appVolumes
                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = slideOutVertically { -it } + fadeOut(),
                    ) {
                        SoundMasterOverlayContent(
                            appVolumes = appVolumes,
                            onVolumeChange = { pkg, vol ->
                                AccuSoundMasterService.setVolumeOf(pkg, -1, vol)
                                // Update local state immediately
                                _appVolumes.value = _appVolumes.value.map {
                                    if (it.pkg == pkg) it.copy(volume = vol) else it
                                }
                                scheduleAutoHide()
                            },
                            onDismiss = ::hideOverlay,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        AccuSoundMasterService.onVolumeChanged = {}
        overlayScope.cancel()
        try { if (overlayView?.windowToken != null) windowManager.removeView(overlayView) } catch (_: Exception) {}
        lifecycleOwner.handleDestroy()
        super.onDestroy()
    }

    companion object {
        const val AUTO_HIDE_MS = 3500L

        fun start(context: Context) =
            context.startService(Intent(context, SoundMasterOverlayService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, SoundMasterOverlayService::class.java))
    }
}

// ─── Overlay UI ───────────────────────────────────────────────────────────────

@Composable
private fun SoundMasterOverlayContent(
    appVolumes: List<OverlayAppEntry>,
    onVolumeChange: (String, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp)) {
            // Header row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.VolumeUp,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Sound Master",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${appVolumes.size} app${if (appVolumes.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (appVolumes.isEmpty()) {
                Text(
                    "No apps currently controlled — add apps in Sound Master",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            } else {
                appVolumes.forEach { entry ->
                    var sliderVal by remember(entry.pkg) { mutableFloatStateOf(entry.volume) }
                    // Sync if external change
                    LaunchedEffect(entry.volume) { sliderVal = entry.volume }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            entry.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(86.dp),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Slider(
                            value = sliderVal,
                            onValueChange = { v ->
                                sliderVal = v
                                onVolumeChange(entry.pkg, v)
                            },
                            valueRange = 0f..200f,
                            modifier = Modifier.weight(1f).height(28.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = if (sliderVal > 100f)
                                    MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.primary,
                                activeTrackColor = if (sliderVal > 100f)
                                    MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.primary,
                            ),
                        )
                        Text(
                            "${"%.0f".format(sliderVal)}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(40.dp),
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            color = when {
                                sliderVal > 100f -> MaterialTheme.colorScheme.tertiary
                                sliderVal == 0f  -> MaterialTheme.colorScheme.error
                                else             -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}

// ─── Lifecycle Owner for WindowManager Compose ───────────────────────────────

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateCtrl = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateCtrl.savedStateRegistry

    fun handleCreate() {
        savedStateCtrl.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun handleDestroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
