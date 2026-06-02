package com.accu.ui.shell

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.accu.connection.AccuConnectionManager
import com.accu.ui.components.ACCTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ═══════════════════════════════════════════════════════════════
//  DATA MODELS
// ═══════════════════════════════════════════════════════════════

enum class TileCategory(
    val label: String,
    val color: Color,
    val icon: ImageVector,
) {
    MEDIA_CAPTURE("Media Capture",  Color(0xFF7C3AED), Icons.Default.Screenshot),
    SCREEN_CTRL  ("Screen Control", Color(0xFF1D4ED8), Icons.Default.BrightnessHigh),
    SYSTEM       ("System",         Color(0xFF065F46), Icons.Default.Settings),
    AUDIO        ("Audio",          Color(0xFF9A3412), Icons.Default.VolumeUp),
    NETWORK      ("Network",        Color(0xFF0284C7), Icons.Default.Wifi),
    DIAGNOSTICS  ("Diagnostics",    Color(0xFF374151), Icons.Default.Analytics),
    PERFORMANCE  ("Performance",    Color(0xFFB45309), Icons.Default.Speed),
    INPUT        ("Input & UI",     Color(0xFF0F766E), Icons.Default.TouchApp),
    CUSTOM       ("Custom",         Color(0xFF6B7280), Icons.Default.Terminal),
}

data class ShellQsTile(
    val id: String,
    val label: String,
    val command: String,
    val description: String = "",
    val iconName: String = "terminal",
    val category: TileCategory = TileCategory.CUSTOM,
    val isBuiltIn: Boolean = false,
    val isEnabled: Boolean = true,
    val confirmBeforeRun: Boolean = false,
    val executionCount: Int = 0,
    val lastRun: String = "Never",
    val lastOutput: String = "",
    val isRunning: Boolean = false,
)

data class TileLog(
    val tileId: String,
    val tileLabel: String,
    val category: TileCategory,
    val timestamp: String,
    val command: String,
    val output: String,
    val exitCode: Int,
    val durationMs: Long = 0L,
)

data class TileResult(
    val tileId: String,
    val tileLabel: String,
    val category: TileCategory,
    val command: String,
    val output: String,
    val exitCode: Int,
    val timestamp: String,
    val durationMs: Long,
)

// ═══════════════════════════════════════════════════════════════
//  50 BUILT-IN TILES
// ═══════════════════════════════════════════════════════════════

val BUILT_IN_TILES: List<ShellQsTile> = listOf(

    // ── Media Capture (12) ──────────────────────────────────
    ShellQsTile("bi_ss", "Screenshot",
        "mkdir -p /sdcard/DCIM/ACCU && screencap /sdcard/DCIM/ACCU/screenshot_\$(date +%Y%m%d_%H%M%S).png && echo 'Screenshot saved to /sdcard/DCIM/ACCU/'",
        "Capture screen to DCIM/ACCU/", "screenshot", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_ss_dl", "Screenshot → Downloads",
        "screencap /sdcard/Download/screenshot_\$(date +%Y%m%d_%H%M%S).png && echo 'Screenshot saved to Downloads'",
        "Capture screen to Downloads folder", "screenshot", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_rec15", "Screen Record 15s",
        "mkdir -p /sdcard/DCIM/ACCU && screenrecord --time-limit 15 /sdcard/DCIM/ACCU/rec_\$(date +%Y%m%d_%H%M%S).mp4 && echo 'Recording (15s) saved'",
        "Record 15 seconds of screen to DCIM/ACCU/", "videocam", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_rec30", "Screen Record 30s",
        "mkdir -p /sdcard/DCIM/ACCU && screenrecord --time-limit 30 /sdcard/DCIM/ACCU/rec_\$(date +%Y%m%d_%H%M%S).mp4 && echo 'Recording (30s) saved'",
        "Record 30 seconds of screen", "videocam", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_rec60", "Screen Record 60s",
        "mkdir -p /sdcard/DCIM/ACCU && screenrecord --time-limit 60 /sdcard/DCIM/ACCU/rec_\$(date +%Y%m%d_%H%M%S).mp4 && echo 'Recording (60s) saved'",
        "Record 60 seconds of screen", "videocam", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_rec3m", "Screen Record 3min",
        "mkdir -p /sdcard/DCIM/ACCU && screenrecord --time-limit 180 /sdcard/DCIM/ACCU/rec_\$(date +%Y%m%d_%H%M%S).mp4 && echo 'Recording (3min) saved'",
        "Record 3 minutes of screen", "videocam", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_rec_hd", "Screen Record HD",
        "mkdir -p /sdcard/DCIM/ACCU && screenrecord --bit-rate 8000000 --time-limit 30 /sdcard/DCIM/ACCU/rechd_\$(date +%Y%m%d_%H%M%S).mp4 && echo 'HD Recording (30s) saved'",
        "High-bitrate 30s recording (8Mbps)", "videocam", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_rec_720", "Screen Record 720p",
        "mkdir -p /sdcard/DCIM/ACCU && screenrecord --size 1280x720 --time-limit 30 /sdcard/DCIM/ACCU/rec720_\$(date +%Y%m%d_%H%M%S).mp4 && echo '720p Recording (30s) saved'",
        "720p resolution 30s recording", "videocam", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_ss_scan", "Screenshot + Media Scan",
        "mkdir -p /sdcard/DCIM/ACCU && screencap /sdcard/DCIM/ACCU/ss.png && am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/DCIM/ACCU/ss.png && echo 'Screenshot taken and media-scanned'",
        "Screenshot then trigger gallery scan", "screenshot", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_list_ss", "List Screenshots",
        "ls -lh /sdcard/DCIM/ACCU/*.png 2>/dev/null | tail -15 || echo 'No screenshots in /sdcard/DCIM/ACCU/'",
        "List recent screenshots in ACCU folder", "screenshot", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_list_rec", "List Recordings",
        "ls -lh /sdcard/DCIM/ACCU/*.mp4 2>/dev/null | tail -10 || echo 'No recordings in /sdcard/DCIM/ACCU/'",
        "List recent screen recordings", "videocam", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_media_size", "ACCU Media Folder Size",
        "du -sh /sdcard/DCIM/ACCU/ 2>/dev/null && ls /sdcard/DCIM/ACCU/ | wc -l | xargs -I{} echo '{} files total' || echo 'ACCU folder not created yet'",
        "Show total size and file count of ACCU media folder", "folder", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    // ── Screen Control (11) ─────────────────────────────────
    ShellQsTile("bi_screen_off", "Screen Off",
        "input keyevent 26 && echo 'Screen turned off'",
        "Turn the screen off (power keyevent)", "lock", TileCategory.SCREEN_CTRL, isBuiltIn = true),

    ShellQsTile("bi_screen_on", "Wake Screen",
        "input keyevent 224 && echo 'Screen woken up'",
        "Wake the screen (KEYCODE_WAKEUP)", "brightness_high", TileCategory.SCREEN_CTRL, isBuiltIn = true),

    ShellQsTile("bi_brightness_0", "Brightness Min",
        "settings put system screen_brightness_mode 0 && settings put system screen_brightness 1 && echo 'Brightness set to minimum'",
        "Set brightness to near-zero", "brightness_low", TileCategory.SCREEN_CTRL, isBuiltIn = true),

    ShellQsTile("bi_brightness_50", "Brightness 50%",
        "settings put system screen_brightness_mode 0 && settings put system screen_brightness 128 && echo 'Brightness set to 50%'",
        "Set brightness to 50%", "brightness_medium", TileCategory.SCREEN_CTRL, isBuiltIn = true),

    ShellQsTile("bi_brightness_100", "Brightness Max",
        "settings put system screen_brightness_mode 0 && settings put system screen_brightness 255 && echo 'Brightness set to maximum'",
        "Set brightness to maximum", "brightness_high", TileCategory.SCREEN_CTRL, isBuiltIn = true),

    ShellQsTile("bi_auto_bright_on", "Auto Brightness On",
        "settings put system screen_brightness_mode 1 && echo 'Auto brightness enabled'",
        "Enable automatic brightness", "brightness_auto", TileCategory.SCREEN_CTRL, isBuiltIn = true),

    ShellQsTile("bi_auto_bright_off", "Auto Brightness Off",
        "settings put system screen_brightness_mode 0 && echo 'Auto brightness disabled'",
        "Disable automatic brightness", "brightness_low", TileCategory.SCREEN_CTRL, isBuiltIn = true),

    ShellQsTile("bi_rotation_lock", "Rotation Lock",
        "settings put system accelerometer_rotation 0 && settings put system user_rotation 0 && echo 'Rotation locked (portrait)'",
        "Lock screen rotation to portrait", "screen_lock_rotation", TileCategory.SCREEN_CTRL, isBuiltIn = true),

    ShellQsTile("bi_rotation_auto", "Rotation Auto",
        "settings put system accelerometer_rotation 1 && echo 'Auto rotation enabled'",
        "Enable automatic screen rotation", "screen_rotation", TileCategory.SCREEN_CTRL, isBuiltIn = true),

    ShellQsTile("bi_timeout_5m", "Screen Timeout 5min",
        "settings put system screen_off_timeout 300000 && echo 'Screen timeout set to 5 minutes'",
        "Set screen timeout to 5 minutes", "timer", TileCategory.SCREEN_CTRL, isBuiltIn = true),

    ShellQsTile("bi_timeout_never", "Screen Always On",
        "settings put system screen_off_timeout 2147483647 && echo 'Screen timeout disabled (always on)'",
        "Disable screen auto-off", "timer_off", TileCategory.SCREEN_CTRL, isBuiltIn = true),

    // ── System (10) ─────────────────────────────────────────
    ShellQsTile("bi_airplane_on", "Airplane Mode On",
        "settings put global airplane_mode_on 1 && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true && echo 'Airplane mode ON'",
        "Enable airplane mode", "airplanemode_active", TileCategory.SYSTEM, isBuiltIn = true),

    ShellQsTile("bi_airplane_off", "Airplane Mode Off",
        "settings put global airplane_mode_on 0 && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false && echo 'Airplane mode OFF'",
        "Disable airplane mode", "airplanemode_inactive", TileCategory.SYSTEM, isBuiltIn = true),

    ShellQsTile("bi_lock_screen", "Lock Screen",
        "input keyevent 26",
        "Lock the device screen immediately", "lock", TileCategory.SYSTEM, isBuiltIn = true),

    ShellQsTile("bi_kill_bg", "Kill Background Apps",
        "am kill-all && echo 'All background apps killed'",
        "Force-stop all cached background processes", "cancel", TileCategory.SYSTEM, isBuiltIn = true),

    ShellQsTile("bi_force_gc", "Force GC All Apps",
        "am send-trim-memory all COMPLETE && echo 'Garbage collection triggered on all apps'",
        "Trigger GC on every running app", "memory", TileCategory.SYSTEM, isBuiltIn = true),

    ShellQsTile("bi_battery_saver_on", "Battery Saver On",
        "settings put global low_power 1 && echo 'Battery saver ENABLED'",
        "Enable battery saver mode", "battery_saver", TileCategory.SYSTEM, isBuiltIn = true),

    ShellQsTile("bi_battery_saver_off", "Battery Saver Off",
        "settings put global low_power 0 && echo 'Battery saver DISABLED'",
        "Disable battery saver mode", "battery_full", TileCategory.SYSTEM, isBuiltIn = true),

    ShellQsTile("bi_dnd_on", "DND On",
        "cmd notification set_dnd_mode on && echo 'Do Not Disturb ENABLED'",
        "Enable Do Not Disturb mode", "do_not_disturb", TileCategory.SYSTEM, isBuiltIn = true),

    ShellQsTile("bi_dnd_off", "DND Off",
        "cmd notification set_dnd_mode off && echo 'Do Not Disturb DISABLED'",
        "Disable Do Not Disturb mode", "do_not_disturb_off", TileCategory.SYSTEM, isBuiltIn = true),

    ShellQsTile("bi_reboot", "Reboot Device",
        "reboot",
        "Immediately reboot the device", "restart_alt", TileCategory.SYSTEM, isBuiltIn = true, confirmBeforeRun = true),

    // ── Audio (8) ───────────────────────────────────────────
    ShellQsTile("bi_vol_max", "Media Volume Max",
        "media volume --set 15 --stream 3 && echo 'Media volume → MAX (15)'",
        "Set media volume to maximum", "volume_up", TileCategory.AUDIO, isBuiltIn = true),

    ShellQsTile("bi_vol_50", "Media Volume 50%",
        "media volume --set 8 --stream 3 && echo 'Media volume → 50% (8)'",
        "Set media volume to 50%", "volume_down", TileCategory.AUDIO, isBuiltIn = true),

    ShellQsTile("bi_vol_mute", "Media Mute",
        "media volume --set 0 --stream 3 && echo 'Media volume → MUTED'",
        "Mute media/music volume", "volume_off", TileCategory.AUDIO, isBuiltIn = true),

    ShellQsTile("bi_ringer_silent", "Ringer Silent",
        "cmd audio set-ringer-mode 0 && echo 'Ringer mode → SILENT'",
        "Set ringer to silent mode", "volume_off", TileCategory.AUDIO, isBuiltIn = true),

    ShellQsTile("bi_ringer_vibrate", "Ringer Vibrate",
        "cmd audio set-ringer-mode 1 && echo 'Ringer mode → VIBRATE'",
        "Set ringer to vibrate only mode", "vibration", TileCategory.AUDIO, isBuiltIn = true),

    ShellQsTile("bi_ringer_normal", "Ringer Normal",
        "cmd audio set-ringer-mode 2 && echo 'Ringer mode → NORMAL'",
        "Set ringer to normal ring mode", "notifications_active", TileCategory.AUDIO, isBuiltIn = true),

    ShellQsTile("bi_notif_mute", "Mute Notifications",
        "media volume --set 0 --stream 5 && echo 'Notification volume → MUTED'",
        "Mute notification alert volume", "notifications_off", TileCategory.AUDIO, isBuiltIn = true),

    ShellQsTile("bi_vol_status", "Volume Status",
        "media volume --get --stream 3 && media volume --get --stream 5 && media volume --get --stream 2",
        "Show current volume levels for all streams", "volume_up", TileCategory.AUDIO, isBuiltIn = true),

    // ── Network (9) ─────────────────────────────────────────
    ShellQsTile("bi_wifi_on", "Wi-Fi On",
        "svc wifi enable && echo 'Wi-Fi ENABLED'",
        "Enable Wi-Fi radio", "wifi", TileCategory.NETWORK, isBuiltIn = true),

    ShellQsTile("bi_wifi_off", "Wi-Fi Off",
        "svc wifi disable && echo 'Wi-Fi DISABLED'",
        "Disable Wi-Fi radio", "wifi_off", TileCategory.NETWORK, isBuiltIn = true),

    ShellQsTile("bi_data_on", "Mobile Data On",
        "svc data enable && echo 'Mobile data ENABLED'",
        "Enable mobile data connection", "cell_tower", TileCategory.NETWORK, isBuiltIn = true),

    ShellQsTile("bi_data_off", "Mobile Data Off",
        "svc data disable && echo 'Mobile data DISABLED'",
        "Disable mobile data connection", "cell_tower", TileCategory.NETWORK, isBuiltIn = true),

    ShellQsTile("bi_bt_on", "Bluetooth On",
        "svc bluetooth enable && echo 'Bluetooth ENABLED'",
        "Enable Bluetooth radio", "bluetooth", TileCategory.NETWORK, isBuiltIn = true),

    ShellQsTile("bi_bt_off", "Bluetooth Off",
        "svc bluetooth disable && echo 'Bluetooth DISABLED'",
        "Disable Bluetooth radio", "bluetooth_disabled", TileCategory.NETWORK, isBuiltIn = true),

    ShellQsTile("bi_hotspot_on", "Hotspot On",
        "svc wifi startTethering 0 && echo 'Wi-Fi hotspot ENABLED'",
        "Enable Wi-Fi hotspot/tethering", "wifi_tethering", TileCategory.NETWORK, isBuiltIn = true),

    ShellQsTile("bi_hotspot_off", "Hotspot Off",
        "svc wifi stopTethering 0 && echo 'Wi-Fi hotspot DISABLED'",
        "Disable Wi-Fi hotspot/tethering", "wifi_tethering_off", TileCategory.NETWORK, isBuiltIn = true),

    ShellQsTile("bi_net_info", "Network Info",
        "echo '=== Wi-Fi ===' && dumpsys wifi | grep -E 'mNetworkInfo|SSID|ipAddress|linkSpeed' | head -6 && echo '=== IP ===' && ip addr show wlan0 2>/dev/null | grep 'inet '",
        "Show Wi-Fi connection and IP info", "info", TileCategory.NETWORK, isBuiltIn = true),

    // ── Diagnostics (10) ────────────────────────────────────
    ShellQsTile("bi_battery", "Battery Status",
        "dumpsys battery | grep -E 'level|status|temperature|voltage|plugged|health'",
        "Full battery level, temp, and health info", "battery_full", TileCategory.DIAGNOSTICS, isBuiltIn = true),

    ShellQsTile("bi_memory", "RAM Usage",
        "echo 'Total / Available:' && cat /proc/meminfo | grep -E 'MemTotal|MemAvailable|MemFree|Cached' | head -5",
        "Show RAM total and available memory", "memory", TileCategory.DIAGNOSTICS, isBuiltIn = true),

    ShellQsTile("bi_cpu", "CPU Frequency",
        "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq; do echo \"\$(basename \$(dirname \$(dirname \$cpu))): \$(awk '{printf \"%.0f MHz\", \$1/1000}' \$cpu 2>/dev/null)\"; done 2>/dev/null || cat /proc/cpuinfo | grep 'cpu MHz' | head -4",
        "Show current frequency for each CPU core", "speed", TileCategory.DIAGNOSTICS, isBuiltIn = true),

    ShellQsTile("bi_uptime", "Uptime",
        "uptime && cat /proc/uptime | awk '{s=\$1; h=int(s/3600); m=int((s%3600)/60); printf \"Uptime: %d hours %d minutes\\n\", h, m}'",
        "Show how long device has been running", "timer", TileCategory.DIAGNOSTICS, isBuiltIn = true),

    ShellQsTile("bi_disk", "Storage Usage",
        "echo '=== Internal ===' && df -h /data | tail -1 && echo '=== SD Card ===' && df -h /sdcard | tail -1",
        "Show internal and SD card storage usage", "storage", TileCategory.DIAGNOSTICS, isBuiltIn = true),

    ShellQsTile("bi_apps_count", "App Count",
        "echo 'User apps:' && pm list packages -3 | wc -l && echo 'System apps:' && pm list packages -s | wc -l && echo 'Disabled apps:' && pm list packages -d | wc -l",
        "Count user, system and disabled apps", "apps", TileCategory.DIAGNOSTICS, isBuiltIn = true),

    ShellQsTile("bi_temp", "Device Temperature",
        "echo '=== Thermal Zones ===' && for f in /sys/class/thermal/thermal_zone*/temp; do zone=\$(echo \$f | grep -o 'thermal_zone[0-9]*'); temp=\$(cat \$f 2>/dev/null); echo \"\$zone: \$(awk \"BEGIN{printf \\\"%.1f°C\\\", \$temp/1000}\")\" 2>/dev/null; done | head -8",
        "Read all thermal sensor temperatures", "thermostat", TileCategory.DIAGNOSTICS, isBuiltIn = true),

    ShellQsTile("bi_procs", "Process Count",
        "echo 'Total processes:' && ps -A | wc -l && echo 'Foreground:' && ps -A | grep -c ' S ' && echo 'Running:' && ps -A | grep -c ' R '",
        "Count total and running processes", "list", TileCategory.DIAGNOSTICS, isBuiltIn = true),

    ShellQsTile("bi_drop_cache", "Drop Memory Cache",
        "sync && echo 3 > /proc/sys/vm/drop_caches 2>/dev/null && echo 'Page/slab/inode caches dropped' || echo 'Root required for drop_caches'",
        "Free page, slab, and inode caches", "memory", TileCategory.DIAGNOSTICS, isBuiltIn = true),

    ShellQsTile("bi_battery_reset", "Reset Battery Stats",
        "dumpsys batterystats --reset && echo 'Battery statistics history cleared'",
        "Reset accumulated battery statistics", "battery_unknown", TileCategory.DIAGNOSTICS, isBuiltIn = true),

    // ── Performance (12) ────────────────────────────────────
    ShellQsTile("bi_perf_max", "Max Performance Mode",
        "settings put global restricted_networking_mode 0 && settings put system screen_brightness_mode 0 && settings put system screen_brightness 255 && am send-trim-memory all COMPLETE && echo 'Max performance mode applied'",
        "Max brightness, kill memory pressure, minimal restrictions", "speed", TileCategory.PERFORMANCE, isBuiltIn = true),

    ShellQsTile("bi_perf_anim_off", "Disable All Animations",
        "settings put global window_animation_scale 0 && settings put global transition_animation_scale 0 && settings put global animator_duration_scale 0 && echo 'All animations disabled — UI is instant'",
        "Set all animation scales to 0 for fastest UI", "speed", TileCategory.PERFORMANCE, isBuiltIn = true),

    ShellQsTile("bi_perf_anim_on", "Restore Animations",
        "settings put global window_animation_scale 1 && settings put global transition_animation_scale 1 && settings put global animator_duration_scale 1 && echo 'Animations restored to 1.0×'",
        "Restore animation scales to default 1.0", "speed", TileCategory.PERFORMANCE, isBuiltIn = true),

    ShellQsTile("bi_perf_anim_half", "Half-Speed Animations",
        "settings put global window_animation_scale 0.5 && settings put global transition_animation_scale 0.5 && settings put global animator_duration_scale 0.5 && echo 'Animations set to 0.5× (snappy)'",
        "Set animations to 0.5× for snappy feel", "speed", TileCategory.PERFORMANCE, isBuiltIn = true),

    ShellQsTile("bi_perf_gpu", "Force GPU Rendering",
        "settings put global hardware_accelerated_rendering 1 && settings put global force_hw_ui 1 && echo 'Force GPU rendering enabled'",
        "Enable hardware accelerated rendering globally", "speed", TileCategory.PERFORMANCE, isBuiltIn = true),

    ShellQsTile("bi_perf_bg_limit", "Limit Background Processes",
        "settings put global activity_manager_constants override_max_cached_processes=4 && echo 'Background process limit set to 4'",
        "Cap background processes at 4 to free RAM", "memory", TileCategory.PERFORMANCE, isBuiltIn = true),

    ShellQsTile("bi_perf_bg_unlimited", "Unlimited Background Processes",
        "settings put global activity_manager_constants '' && echo 'Background process limit removed'",
        "Remove any background process cap", "memory", TileCategory.PERFORMANCE, isBuiltIn = true),

    ShellQsTile("bi_perf_usb_high", "USB High Performance",
        "setprop sys.usb.config mtp,adb && echo 'USB configured for high performance MTP+ADB'",
        "Set USB to high-performance MTP+ADB mode", "storage", TileCategory.PERFORMANCE, isBuiltIn = true),

    ShellQsTile("bi_perf_trim", "Compact All Memory",
        "am send-trim-memory all RUNNING_CRITICAL && am send-trim-memory all COMPLETE && echo 'Memory compacted across all apps'",
        "Send critical trim-memory to all running apps", "memory", TileCategory.PERFORMANCE, isBuiltIn = true),

    ShellQsTile("bi_perf_doze_off", "Disable Doze Mode",
        "dumpsys deviceidle disable && echo 'Doze mode DISABLED — no battery optimization'",
        "Disable Android doze/idle battery optimization", "battery_saver", TileCategory.PERFORMANCE, isBuiltIn = true),

    ShellQsTile("bi_perf_doze_on", "Enable Doze Mode",
        "dumpsys deviceidle enable && echo 'Doze mode ENABLED'",
        "Re-enable Android doze mode", "battery_full", TileCategory.PERFORMANCE, isBuiltIn = true),

    ShellQsTile("bi_perf_stats", "Performance Stats",
        "echo '=== CPU ===' && cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null && echo '=== Anim Scales ===' && settings get global window_animation_scale && settings get global transition_animation_scale && echo '=== RAM ===' && cat /proc/meminfo | grep -E 'MemTotal|MemAvailable' | head -2",
        "Show governor, animation scales, and RAM overview", "analytics", TileCategory.PERFORMANCE, isBuiltIn = true),

    // ── Input & UI (10) ─────────────────────────────────────
    ShellQsTile("bi_input_tap", "Tap Screen Center",
        "input tap \$(wm size | grep -o '[0-9]*x[0-9]*' | awk -Fx '{print int(\$1/2), int(\$2/2)}') && echo 'Tapped screen center'",
        "Simulate a tap at center of screen", "touch_app", TileCategory.INPUT, isBuiltIn = true),

    ShellQsTile("bi_input_swipe_up", "Swipe Up (Home)",
        "sz=\$(wm size | grep -o '[0-9]*x[0-9]*'); w=\$(echo \$sz | awk -Fx '{print \$1}'); h=\$(echo \$sz | awk -Fx '{print \$2}'); cx=\$((\$w/2)); input swipe \$cx \$((\$h*3/4)) \$cx \$((\$h/4)) 300 && echo 'Swipe-up gesture sent'",
        "Send upward swipe gesture (navigate home)", "touch_app", TileCategory.INPUT, isBuiltIn = true),

    ShellQsTile("bi_input_back", "Send Back Button",
        "input keyevent 4 && echo 'Back key sent'",
        "Send KEYCODE_BACK (back navigation)", "arrow_back", TileCategory.INPUT, isBuiltIn = true),

    ShellQsTile("bi_input_home", "Send Home Button",
        "input keyevent 3 && echo 'Home key sent'",
        "Send KEYCODE_HOME (go to launcher)", "home", TileCategory.INPUT, isBuiltIn = true),

    ShellQsTile("bi_input_recents", "Open Recents",
        "input keyevent 187 && echo 'Recents opened'",
        "Send KEYCODE_APP_SWITCH (recent apps)", "apps", TileCategory.INPUT, isBuiltIn = true),

    ShellQsTile("bi_density_get", "Get Screen Density",
        "echo 'Physical:' && wm density && echo 'Override:' && wm density",
        "Show current display density (DPI)", "info", TileCategory.INPUT, isBuiltIn = true),

    ShellQsTile("bi_density_reset", "Reset Density",
        "wm density reset && echo 'Display density reset to default'",
        "Reset display density to device default", "settings", TileCategory.INPUT, isBuiltIn = true),

    ShellQsTile("bi_font_large", "Font Size Large",
        "settings put system font_scale 1.15 && echo 'Font scale → 1.15 (large)'",
        "Set system font size to large (1.15×)", "text_fields", TileCategory.INPUT, isBuiltIn = true),

    ShellQsTile("bi_font_normal", "Font Size Normal",
        "settings put system font_scale 1.0 && echo 'Font scale → 1.0 (normal)'",
        "Reset font size to normal (1.0×)", "text_fields", TileCategory.INPUT, isBuiltIn = true),

    ShellQsTile("bi_dark_mode_on", "Dark Mode On",
        "cmd uimode night yes && echo 'Dark mode ENABLED'",
        "Force system-wide dark mode", "dark_mode", TileCategory.INPUT, isBuiltIn = true),

    ShellQsTile("bi_dark_mode_off", "Dark Mode Off",
        "cmd uimode night no && echo 'Dark mode DISABLED (light theme)'",
        "Force system-wide light mode", "light_mode", TileCategory.INPUT, isBuiltIn = true),

    // ── Additional Media Capture (6) ──────────────────────
    ShellQsTile("bi_ss_portrait", "Screenshot Portrait Path",
        "screencap /sdcard/DCIM/Screenshots/\$(date +%Y%m%d_%H%M%S).png && echo 'Screenshot saved to Screenshots folder'",
        "Capture to default Screenshots folder", "screenshot", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_rec_1080", "Screen Record 1080p",
        "mkdir -p /sdcard/DCIM/ACCU && screenrecord --size 1920x1080 --time-limit 30 /sdcard/DCIM/ACCU/rec1080_\$(date +%Y%m%d_%H%M%S).mp4 && echo '1080p Recording (30s) saved'",
        "Full HD 1080p 30s screen recording", "videocam", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_rec_low", "Screen Record Low Bitrate",
        "mkdir -p /sdcard/DCIM/ACCU && screenrecord --bit-rate 2000000 --time-limit 60 /sdcard/DCIM/ACCU/reclq_\$(date +%Y%m%d_%H%M%S).mp4 && echo 'Low-bitrate Recording (60s) saved'",
        "2Mbps 60s recording for small file size", "videocam", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_launch_camera", "Open Camera App",
        "am start -a android.media.action.IMAGE_CAPTURE && echo 'Camera app launched'",
        "Launch device camera app for photo capture", "camera_alt", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_launch_video", "Open Video Camera",
        "am start -a android.media.action.VIDEO_CAPTURE && echo 'Video camera launched'",
        "Launch camera app in video recording mode", "videocam", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    ShellQsTile("bi_media_scan_all", "Scan Media Library",
        "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE --receiver-foreground -d file:///sdcard/DCIM/ACCU && echo 'Media scan triggered for ACCU folder'",
        "Force gallery to re-index ACCU media folder", "refresh", TileCategory.MEDIA_CAPTURE, isBuiltIn = true),

    // ── Additional Audio (4) ─────────────────────────────
    ShellQsTile("bi_vol_ring_max", "Ringtone Volume Max",
        "media volume --set 15 --stream 2 && echo 'Ringtone volume → MAX'",
        "Set ringtone volume to maximum", "notifications_active", TileCategory.AUDIO, isBuiltIn = true),

    ShellQsTile("bi_vol_alarm_max", "Alarm Volume Max",
        "media volume --set 15 --stream 4 && echo 'Alarm volume → MAX'",
        "Set alarm volume to maximum", "alarm", TileCategory.AUDIO, isBuiltIn = true),

    ShellQsTile("bi_vol_all_mute", "Mute All Streams",
        "media volume --set 0 --stream 2 && media volume --set 0 --stream 3 && media volume --set 0 --stream 4 && media volume --set 0 --stream 5 && echo 'All audio streams muted'",
        "Mute ringtone, media, alarm, and notification streams", "volume_off", TileCategory.AUDIO, isBuiltIn = true),

    ShellQsTile("bi_bluetooth_media", "Connect Bluetooth Media",
        "am start -a android.bluetooth.adapter.action.REQUEST_ENABLE && echo 'Bluetooth media connection prompt sent'",
        "Open Bluetooth settings to connect audio device", "bluetooth_audio", TileCategory.AUDIO, isBuiltIn = true),
)

// ═══════════════════════════════════════════════════════════════
//  VIEW MODEL
// ═══════════════════════════════════════════════════════════════

private const val PREF_TILES            = "qs_tiles_prefs"
private const val KEY_TILES             = "tiles_json"
private const val KEY_LOGS              = "logs_json"
private const val KEY_RESULTS           = "results_json"
private const val KEY_MEDIA_FOLDER      = "media_output_folder"
private const val KEY_MEDIA_FOLDER_URI  = "media_output_folder_uri"
private const val MAX_LOGS              = 500

data class QsTilesUiState(
    val customTiles:    List<ShellQsTile> = emptyList(),
    val logs:           List<TileLog>     = emptyList(),
    val results:        List<TileResult>  = emptyList(),
    val mediaFolder:    String            = "/sdcard/DCIM/ACCU",
    val mediaFolderUri: String            = "",
)

@HiltViewModel
class ShellQsTileDashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: AccuConnectionManager,
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREF_TILES, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(QsTilesUiState())
    val state: StateFlow<QsTilesUiState> = _state.asStateFlow()

    /** One-shot events for tile completion toasts — consumed by the screen's LaunchedEffect. */
    private val _completionEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val completionEvent: SharedFlow<String> = _completionEvent.asSharedFlow()

    init { load() }

    private fun load() {
        val tiles     = parseTiles(prefs.getString(KEY_TILES,   "[]") ?: "[]")
        val logs      = parseLogs(prefs.getString(KEY_LOGS,     "[]") ?: "[]")
        val results   = parseResults(prefs.getString(KEY_RESULTS,"[]") ?: "[]")
        val folder    = prefs.getString(KEY_MEDIA_FOLDER, "/sdcard/DCIM/ACCU") ?: "/sdcard/DCIM/ACCU"
        val folderUri = prefs.getString(KEY_MEDIA_FOLDER_URI, "") ?: ""
        _state.update { it.copy(customTiles = tiles, logs = logs, results = results, mediaFolder = folder, mediaFolderUri = folderUri) }
    }

    fun setMediaFolder(path: String) {
        prefs.edit().putString(KEY_MEDIA_FOLDER, path).apply()
        _state.update { it.copy(mediaFolder = path) }
    }

    fun setMediaFolderUri(uri: android.net.Uri, displayName: String) {
        val uriStr = uri.toString()
        // Decode the real filesystem path from the SAF tree URI so shell commands can use it.
        // SAF tree URIs encode paths as "primary:DCIM/ACCU" or "<volume>:<path>".
        val fsPath = tryDecodeFilesystemPath(uri) ?: "/sdcard/$displayName"
        prefs.edit()
            .putString(KEY_MEDIA_FOLDER_URI, uriStr)
            .putString(KEY_MEDIA_FOLDER, fsPath)
            .apply()
        _state.update { it.copy(mediaFolder = fsPath, mediaFolderUri = uriStr) }
    }

    /** Convert a SAF tree URI → absolute filesystem path, e.g. "primary:DCIM/ACCU" → "/sdcard/DCIM/ACCU". */
    private fun tryDecodeFilesystemPath(uri: android.net.Uri): String? = try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri) ?: return null
        when {
            docId.startsWith("primary:") -> "/sdcard/${docId.removePrefix("primary:")}"
            docId.contains(':') -> {
                val sep    = docId.indexOf(':')
                val volume = docId.substring(0, sep)
                val path   = docId.substring(sep + 1)
                "/storage/$volume/$path"
            }
            else -> null
        }
    } catch (_: Exception) { null }

    fun getMediaFolderUri(): android.net.Uri? {
        val str = _state.value.mediaFolderUri
        return if (str.isNotBlank()) android.net.Uri.parse(str) else null
    }

    private fun saveResultToFolder(filename: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val uriStr = _state.value.mediaFolderUri
            if (uriStr.isBlank()) return@launch
            try {
                val treeUri = android.net.Uri.parse(uriStr)
                val docDir  = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                    ?: return@launch
                docDir.findFile(filename)?.delete()
                val mime = when {
                    filename.endsWith(".png",  ignoreCase = true) -> "image/png"
                    filename.endsWith(".jpg",  ignoreCase = true) -> "image/jpeg"
                    filename.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                    filename.endsWith(".mp4",  ignoreCase = true) -> "video/mp4"
                    filename.endsWith(".webm", ignoreCase = true) -> "video/webm"
                    else -> "text/plain"
                }
                val docFile = docDir.createFile(mime, filename) ?: return@launch
                context.contentResolver.openOutputStream(docFile.uri)?.use { out ->
                    out.write(content.toByteArray())
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Copy a media file from the device filesystem to the user's SAF-chosen folder.
     * Called from within a Dispatchers.IO coroutine (runTile).
     *
     * Strategy:
     *  1. Try java.io.File.inputStream() — instant, works when app has storage permission.
     *  2. Fallback: base64 via root shell — always works, capped at 100 MB.
     */
    private suspend fun pullMediaFileToSaf(srcPath: String, destName: String, mimeType: String) {
        val uriStr = _state.value.mediaFolderUri
        if (uriStr.isBlank()) return
        try {
            val treeUri = android.net.Uri.parse(uriStr)
            val docDir  = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                ?: return
            docDir.findFile(destName)?.delete()
            val docFile = docDir.createFile(mimeType, destName) ?: return

            // Attempt 1: direct file read (works if MANAGE_EXTERNAL_STORAGE / legacy storage)
            val srcFile = java.io.File(srcPath)
            if (srcFile.exists() && srcFile.length() > 0L) {
                try {
                    context.contentResolver.openOutputStream(docFile.uri)?.use { out ->
                        srcFile.inputStream().use { inp -> inp.copyTo(out, bufferSize = 65_536) }
                    }
                    _completionEvent.tryEmit("Saved $destName to folder ✓")
                    return
                } catch (_: SecurityException) {
                    // No direct read permission — fall through to base64
                    docFile.delete()
                    val docFile2 = docDir.createFile(mimeType, destName) ?: return
                    pullViaBase64(srcPath, destName, docFile2)
                }
            } else {
                // File not yet visible to java.io.File — go straight to base64
                pullViaBase64(srcPath, destName, docFile)
            }
        } catch (_: Exception) {}
    }

    /** Map a lowercase file extension to the correct MIME type. */
    private fun mimeForExt(ext: String): String = when (ext) {
        "png"                      -> "image/png"
        "jpg", "jpeg"              -> "image/jpeg"
        "webp"                     -> "image/webp"
        "gif"                      -> "image/gif"
        "bmp"                      -> "image/bmp"
        "heic", "heif"             -> "image/heic"
        "mp4"                      -> "video/mp4"
        "mkv"                      -> "video/x-matroska"
        "webm"                     -> "video/webm"
        "avi"                      -> "video/x-msvideo"
        "3gp"                      -> "video/3gpp"
        "mp3"                      -> "audio/mpeg"
        "wav"                      -> "audio/wav"
        "m4a"                      -> "audio/mp4"
        "ogg"                      -> "audio/ogg"
        "flac"                     -> "audio/flac"
        "aac"                      -> "audio/aac"
        "pdf"                      -> "application/pdf"
        "apk"                      -> "application/vnd.android.package-archive"
        "zip"                      -> "application/zip"
        "tar"                      -> "application/x-tar"
        "gz", "tgz"                -> "application/gzip"
        "bz2"                      -> "application/x-bzip2"
        "7z"                       -> "application/x-7z-compressed"
        "rar"                      -> "application/x-rar-compressed"
        "json"                     -> "application/json"
        "xml"                      -> "application/xml"
        "csv"                      -> "text/csv"
        "html", "htm"              -> "text/html"
        "sh", "bash", "zsh"        -> "application/x-sh"
        "so"                       -> "application/octet-stream"
        else                       -> "text/plain"
    }

    private suspend fun pullViaBase64(
        srcPath: String,
        destName: String,
        docFile: androidx.documentfile.provider.DocumentFile,
    ) {
        // Guard: skip files larger than 100 MB to avoid OOM
        val sizeStr = connectionManager.exec("stat -c %s \"$srcPath\" 2>/dev/null").output.trim()
        val size    = sizeStr.toLongOrNull() ?: 0L
        if (size > 100_000_000L) {
            _completionEvent.tryEmit(
                "File too large to auto-copy (${size / 1_000_000}MB) — find it at $srcPath"
            )
            return
        }
        val b64 = connectionManager.exec("base64 \"$srcPath\" 2>/dev/null").output
        if (b64.isBlank()) return
        val bytes = try {
            Base64.decode(b64.replace("\n", "").replace(" ", ""), Base64.DEFAULT)
        } catch (_: Exception) { return }
        context.contentResolver.openOutputStream(docFile.uri)?.use { it.write(bytes) }
        _completionEvent.tryEmit("Saved $destName to folder ✓")
    }

    fun runTile(tile: ShellQsTile) {
        if (!tile.isEnabled) return
        viewModelScope.launch(Dispatchers.IO) {
            // Mark running
            updateRunning(tile.id, tile.isBuiltIn, true)
            val startMs = System.currentTimeMillis()
            // Substitute the user's chosen media folder in place of the hardcoded default.
            // This makes screenshot/recording/media tiles respect the SAF-picked folder.
            val mediaFolder = _state.value.mediaFolder
            val resolvedCommand = tile.command.replace("/sdcard/DCIM/ACCU", mediaFolder)
            // For MEDIA_CAPTURE tiles, stamp a reference file so we can detect EVERY
            // new file the command creates (PNG, MP4, APK, ZIP, PDF, …) by timestamp.
            val refFile = "/tmp/accu_pull_ref_$startMs"
            if (tile.category == TileCategory.MEDIA_CAPTURE) {
                connectionManager.exec("touch \"$refFile\" ; mkdir -p \"$mediaFolder\" 2>/dev/null")
            }
            val result = try {
                connectionManager.exec(resolvedCommand)
            } catch (e: Exception) {
                com.accu.connection.ShellResult(output = "", error = e.message ?: "Error", exitCode = 1)
            }
            val durationMs = System.currentTimeMillis() - startMs
            val ts = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date())
            val outputText = result.output.trim().take(800).ifBlank { result.error.take(400) }
            val log = TileLog(
                tileId    = tile.id,
                tileLabel = tile.label,
                category  = tile.category,
                timestamp = ts,
                command   = resolvedCommand,
                output    = outputText,
                exitCode  = if (result.isSuccess) 0 else 1,
                durationMs = durationMs,
            )
            val tileResult = TileResult(
                tileId    = tile.id,
                tileLabel = tile.label,
                category  = tile.category,
                command   = resolvedCommand,
                output    = outputText,
                exitCode  = if (result.isSuccess) 0 else 1,
                timestamp = ts,
                durationMs = durationMs,
            )
            _state.update { s ->
                val newLogs    = (listOf(log) + s.logs).take(MAX_LOGS)
                val newResults = (listOf(tileResult) + s.results.filter { it.tileId != tile.id }).take(200)
                s.copy(logs = newLogs, results = newResults)
            }
            updateRunning(tile.id, tile.isBuiltIn, false, outputText.take(100), ts)
            persistAll()
            // Emit completion toast event to the screen
            val toastMsg = if (result.isSuccess)
                "✓ ${tile.label} done"
            else
                "✗ ${tile.label}: ${outputText.take(60).ifBlank { "failed (exit ${result.exitCode})" }}"
            _completionEvent.tryEmit(toastMsg)
            if (tile.category == TileCategory.MEDIA_CAPTURE) {
                // Pull ALL new files created in the media folder since the command started.
                // This covers screenshots (.png), recordings (.mp4), extracted APKs (.apk),
                // archives (.zip / .tar / .gz), and any custom media-tile output.
                if (_state.value.mediaFolderUri.isNotBlank()) {
                    val newFiles = connectionManager.exec(
                        "find \"$mediaFolder\" -maxdepth 3 -type f -newer \"$refFile\" 2>/dev/null"
                    ).output.lines().map { it.trim() }.filter { it.isNotBlank() }
                    for (srcPath in newFiles) {
                        val fileName = srcPath.substringAfterLast("/")
                        val ext      = fileName.substringAfterLast(".", "").lowercase()
                        pullMediaFileToSaf(srcPath, fileName, mimeForExt(ext))
                    }
                }
                connectionManager.exec("rm -f \"$refFile\" 2>/dev/null")
            } else {
                val safeLabel = tile.label.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val safeTs    = ts.replace(" ", "_").replace(":", "-")
                saveResultToFolder("${safeLabel}_${safeTs}.txt", "$ ${resolvedCommand}\n\n${outputText}")
            }
        }
    }

    private fun updateRunning(id: String, isBuiltIn: Boolean, running: Boolean, lastOutput: String = "", lastRun: String = "") {
        if (!isBuiltIn) {
            _state.update { s ->
                s.copy(customTiles = s.customTiles.map { t ->
                    if (t.id == id) t.copy(
                        isRunning      = running,
                        executionCount = if (!running) t.executionCount + 1 else t.executionCount,
                        lastRun        = if (!running && lastRun.isNotEmpty()) lastRun else t.lastRun,
                        lastOutput     = if (!running && lastOutput.isNotEmpty()) lastOutput else t.lastOutput,
                    ) else t
                })
            }
        }
    }

    fun addTile(tile: ShellQsTile) {
        _state.update { it.copy(customTiles = it.customTiles + tile) }
        persistAll()
    }

    fun updateTile(tile: ShellQsTile) {
        _state.update { it.copy(customTiles = it.customTiles.map { t -> if (t.id == tile.id) tile else t }) }
        persistAll()
    }

    fun deleteTile(id: String) {
        _state.update { it.copy(customTiles = it.customTiles.filter { t -> t.id != id }) }
        persistAll()
    }

    fun enableTiles(ids: Set<String>) {
        _state.update { it.copy(customTiles = it.customTiles.map { t -> if (t.id in ids) t.copy(isEnabled = true) else t }) }
        persistAll()
    }

    fun disableTiles(ids: Set<String>) {
        _state.update { it.copy(customTiles = it.customTiles.map { t -> if (t.id in ids) t.copy(isEnabled = false) else t }) }
        persistAll()
    }

    fun toggleEnabled(id: String) {
        _state.update { it.copy(customTiles = it.customTiles.map { t -> if (t.id == id) t.copy(isEnabled = !t.isEnabled) else t }) }
        persistAll()
    }

    fun clearLogs() {
        _state.update { it.copy(logs = emptyList()) }
        prefs.edit().putString(KEY_LOGS, "[]").apply()
    }

    fun clearResults() {
        _state.update { it.copy(results = emptyList()) }
        prefs.edit().putString(KEY_RESULTS, "[]").apply()
    }

    fun deleteResult(tileId: String) {
        _state.update { it.copy(results = it.results.filter { r -> r.tileId != tileId }) }
        persistAll()
    }

    private fun persistAll() {
        prefs.edit()
            .putString(KEY_TILES,   serializeTiles(_state.value.customTiles))
            .putString(KEY_LOGS,    serializeLogs(_state.value.logs))
            .putString(KEY_RESULTS, serializeResults(_state.value.results))
            .apply()
    }

    // ── Serialization ──────────────────────────────────────
    private fun serializeTiles(tiles: List<ShellQsTile>) = JSONArray().apply {
        tiles.filter { !it.isBuiltIn }.forEach { t ->
            put(JSONObject().apply {
                put("id", t.id); put("label", t.label); put("command", t.command)
                put("description", t.description); put("iconName", t.iconName)
                put("category", t.category.name); put("isEnabled", t.isEnabled)
                put("confirmBeforeRun", t.confirmBeforeRun)
                put("executionCount", t.executionCount); put("lastRun", t.lastRun)
                put("lastOutput", t.lastOutput)
            })
        }
    }.toString()

    private fun serializeLogs(logs: List<TileLog>) = JSONArray().apply {
        logs.forEach { l ->
            put(JSONObject().apply {
                put("tileId", l.tileId); put("tileLabel", l.tileLabel)
                put("category", l.category.name); put("timestamp", l.timestamp)
                put("command", l.command); put("output", l.output)
                put("exitCode", l.exitCode); put("durationMs", l.durationMs)
            })
        }
    }.toString()

    private fun serializeResults(results: List<TileResult>) = JSONArray().apply {
        results.forEach { r ->
            put(JSONObject().apply {
                put("tileId", r.tileId); put("tileLabel", r.tileLabel)
                put("category", r.category.name); put("command", r.command)
                put("output", r.output); put("exitCode", r.exitCode)
                put("timestamp", r.timestamp); put("durationMs", r.durationMs)
            })
        }
    }.toString()

    private fun parseTiles(json: String): List<ShellQsTile> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ShellQsTile(
                id = o.getString("id"), label = o.getString("label"), command = o.getString("command"),
                description = o.optString("description"), iconName = o.optString("iconName", "terminal"),
                category = runCatching { TileCategory.valueOf(o.optString("category", "CUSTOM")) }.getOrDefault(TileCategory.CUSTOM),
                isEnabled = o.optBoolean("isEnabled", true),
                confirmBeforeRun = o.optBoolean("confirmBeforeRun", false),
                executionCount = o.optInt("executionCount", 0), lastRun = o.optString("lastRun", "Never"),
                lastOutput = o.optString("lastOutput"),
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun parseLogs(json: String): List<TileLog> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TileLog(
                tileId    = o.getString("tileId"), tileLabel = o.getString("tileLabel"),
                category  = runCatching { TileCategory.valueOf(o.optString("category", "CUSTOM")) }.getOrDefault(TileCategory.CUSTOM),
                timestamp = o.getString("timestamp"), command = o.getString("command"),
                output    = o.optString("output"), exitCode = o.optInt("exitCode", 0),
                durationMs = o.optLong("durationMs", 0L),
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun parseResults(json: String): List<TileResult> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TileResult(
                tileId    = o.getString("tileId"), tileLabel = o.getString("tileLabel"),
                category  = runCatching { TileCategory.valueOf(o.optString("category", "CUSTOM")) }.getOrDefault(TileCategory.CUSTOM),
                command   = o.getString("command"), output = o.optString("output"),
                exitCode  = o.optInt("exitCode", 0), timestamp = o.getString("timestamp"),
                durationMs = o.optLong("durationMs", 0L),
            )
        }
    } catch (_: Exception) { emptyList() }
}

// ═══════════════════════════════════════════════════════════════
//  SCREEN
// ═══════════════════════════════════════════════════════════════

private val TILE_ICON_OPTIONS = listOf(
    "terminal" to Icons.Default.Terminal,
    "screenshot" to Icons.Default.Screenshot,
    "videocam" to Icons.Default.Videocam,
    "wifi" to Icons.Default.Wifi,
    "volume_up" to Icons.Default.VolumeUp,
    "battery_full" to Icons.Default.BatteryFull,
    "settings" to Icons.Default.Settings,
    "lock" to Icons.Default.Lock,
    "bluetooth" to Icons.Default.Bluetooth,
    "brightness_high" to Icons.Default.BrightnessHigh,
    "memory" to Icons.Default.Memory,
    "speed" to Icons.Default.Speed,
    "timer" to Icons.Default.Timer,
    "restart_alt" to Icons.Default.RestartAlt,
    "volume_off" to Icons.Default.VolumeOff,
    "network_cell" to Icons.Default.NetworkCell,
    "storage" to Icons.Default.Storage,
    "info" to Icons.Default.Info,
    "analytics" to Icons.Default.Analytics,
    "notifications" to Icons.Default.Notifications,
    // New icons for Performance + Input categories
    "touch_app" to Icons.Default.TouchApp,
    "dark_mode" to Icons.Default.DarkMode,
    "light_mode" to Icons.Default.LightMode,
    "alarm" to Icons.Default.Alarm,
    "bluetooth_audio" to Icons.Default.BluetoothAudio,
    "text_fields" to Icons.Default.TextFields,
    "home" to Icons.Default.Home,
    "apps" to Icons.Default.Apps,
    "camera_alt" to Icons.Default.CameraAlt,
    "refresh" to Icons.Default.Refresh,
    "notifications_active" to Icons.Default.NotificationsActive,
    "battery_saver" to Icons.Default.BatterySaver,
    "vibration" to Icons.Default.Vibration,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShellQsTileDashboardScreen(
    onBack: () -> Unit = {},
    viewModel: ShellQsTileDashboardViewModel = hiltViewModel(),
) {
    val vmState     by viewModel.state.collectAsStateWithLifecycle()
    val customTiles = vmState.customTiles
    val allTiles    = BUILT_IN_TILES + customTiles
    val logs        = vmState.logs
    val results     = vmState.results
    val mediaFolder = vmState.mediaFolder
    val clipboard   = LocalClipboardManager.current
    val context     = LocalContext.current

    var showCreateSheet  by remember { mutableStateOf(false) }
    var editingTile      by remember { mutableStateOf<ShellQsTile?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<ShellQsTile?>(null) }
    var selectedTab      by remember { mutableIntStateOf(0) }
    var selectedIds      by remember { mutableStateOf(setOf<String>()) }
    var snackbar         by remember { mutableStateOf<String?>(null) }
    var catFilter        by remember { mutableStateOf<TileCategory?>(null) }
    var builtInSearch    by remember { mutableStateOf("") }
    val snackbarHost     = remember { SnackbarHostState() }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val displayName = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, it)?.name
                ?: it.lastPathSegment
                ?: "Custom Folder"
            viewModel.setMediaFolderUri(it, displayName)
            snackbar = "Save folder → $displayName ✓"
        }
    }

    LaunchedEffect(snackbar) { snackbar?.let { snackbarHost.showSnackbar(it); snackbar = null } }

    // Show a toast whenever a tile finishes running (success or failure)
    LaunchedEffect(Unit) {
        viewModel.completionEvent.collect { msg ->
            snackbarHost.showSnackbar(msg)
        }
    }

    val isSelecting = selectedIds.isNotEmpty()

    // Editor state
    var editLabel       by remember { mutableStateOf("") }
    var editCommand     by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editIconName    by remember { mutableStateOf("terminal") }
    var editCategory    by remember { mutableStateOf(TileCategory.CUSTOM) }
    var editConfirm     by remember { mutableStateOf(false) }
    var editEnabled     by remember { mutableStateOf(true) }

    fun openEditor(tile: ShellQsTile? = null) {
        editLabel = tile?.label ?: ""; editCommand = tile?.command ?: ""
        editDescription = tile?.description ?: ""; editIconName = tile?.iconName ?: "terminal"
        editCategory = tile?.category ?: TileCategory.CUSTOM
        editConfirm = tile?.confirmBeforeRun ?: false; editEnabled = tile?.isEnabled ?: true
        editingTile = tile; showCreateSheet = true
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = if (isSelecting) "${selectedIds.size} selected" else "Shell QS Tiles",
                onBack = { if (isSelecting) selectedIds = emptySet() else onBack() },
                actions = {
                    if (isSelecting) {
                        IconButton(onClick = { viewModel.enableTiles(selectedIds); snackbar = "Enabled"; selectedIds = emptySet() }) {
                            Icon(Icons.Default.PlayArrow, "Enable")
                        }
                        IconButton(onClick = { viewModel.disableTiles(selectedIds); snackbar = "Disabled"; selectedIds = emptySet() }) {
                            Icon(Icons.Default.Pause, "Disable")
                        }
                        IconButton(onClick = {
                            val cnt = selectedIds.size; selectedIds.forEach { viewModel.deleteTile(it) }
                            snackbar = "Deleted $cnt"; selectedIds = emptySet()
                        }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                        IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Default.Close, "Cancel") }
                    } else {
                        IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                            Icon(Icons.Default.FolderOpen, "Pick save folder")
                        }
                        if (selectedTab == 1) {
                            IconButton(onClick = { openEditor(null) }) { Icon(Icons.Default.Add, "New Tile") }
                        }
                        if (selectedTab == 2 && logs.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearLogs(); snackbar = "Logs cleared" }) {
                                Icon(Icons.Default.DeleteSweep, "Clear logs")
                            }
                        }
                        if (selectedTab == 3 && results.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearResults(); snackbar = "Results cleared" }) {
                                Icon(Icons.Default.DeleteSweep, "Clear results")
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!isSelecting && selectedTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { openEditor(null) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("New Tile") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Media folder banner ─────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        "Media → $mediaFolder",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) { Text("Change", style = MaterialTheme.typography.labelSmall) }
                }
            }

            // ── Tabs ────────────────────────────────────────
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 8.dp,
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Built-in (${BUILT_IN_TILES.size})") },
                    icon = { Icon(Icons.Default.ViewModule, null, Modifier.size(16.dp)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Custom (${customTiles.size})") },
                    icon = { Icon(Icons.Default.ViewDay, null, Modifier.size(16.dp)) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    text = { Text("Logs (${logs.size})") },
                    icon = { Icon(Icons.Default.Article, null, Modifier.size(16.dp)) })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 },
                    text = { Text("Results (${results.size})") },
                    icon = { Icon(Icons.Default.Assessment, null, Modifier.size(16.dp)) })
            }

            when (selectedTab) {
                0 -> BuiltInTilesTab(
                    tiles = BUILT_IN_TILES,
                    catFilter = catFilter,
                    searchQuery = builtInSearch,
                    onCatFilter = { catFilter = if (catFilter == it) null else it },
                    onSearchChange = { builtInSearch = it },
                    onRun = { tile ->
                        if (tile.confirmBeforeRun) showDeleteConfirm = tile
                        else { snackbar = "Running: ${tile.label}…"; viewModel.runTile(tile) }
                    },
                    onCopyCommand = { tile ->
                        clipboard.setText(AnnotatedString(tile.command))
                        snackbar = "Command copied"
                    },
                    onCloneToCustom = { tile ->
                        openEditor(tile.copy(id = "${System.currentTimeMillis()}", isBuiltIn = false, category = TileCategory.CUSTOM))
                        snackbar = "Cloning '${tile.label}' to Custom…"
                    },
                )

                1 -> CustomTilesTab(
                    tiles = customTiles,
                    selectedIds = selectedIds,
                    isSelecting = isSelecting,
                    onToggle = { viewModel.toggleEnabled(it.id) },
                    onRun = { tile ->
                        if (tile.confirmBeforeRun) showDeleteConfirm = tile
                        else { snackbar = "Running: ${tile.label}…"; viewModel.runTile(tile) }
                    },
                    onEdit = { openEditor(it) },
                    onDelete = { showDeleteConfirm = it },
                    onSelectToggle = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
                    onLongPress = { id -> selectedIds = selectedIds + id },
                )

                2 -> LogsTab(
                    logs = logs,
                    onClear = { viewModel.clearLogs(); snackbar = "Logs cleared" },
                    onCopyLog = { log ->
                        clipboard.setText(AnnotatedString("$ ${log.command}\n${log.output}"))
                        snackbar = "Log copied"
                    },
                )

                3 -> ResultsTab(
                    results = results,
                    onDelete = { viewModel.deleteResult(it) },
                    onClear = { viewModel.clearResults(); snackbar = "All results cleared" },
                    onCopy = { r ->
                        clipboard.setText(AnnotatedString("${r.tileLabel}\n$ ${r.command}\n${r.output}"))
                        snackbar = "Result copied"
                    },
                )
            }
        }
    }

    // ── Create/Edit bottom sheet ──────────────────────────
    if (showCreateSheet) {
        AlertDialog(
            onDismissRequest = { showCreateSheet = false; editingTile = null },
            title = { Text(if (editingTile == null) "New Custom Tile" else "Edit: ${editingTile!!.label}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = editLabel, onValueChange = { editLabel = it }, label = { Text("Label*") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(
                        value = editCommand, onValueChange = { editCommand = it },
                        label = { Text("Shell Command*") }, modifier = Modifier.fillMaxWidth().height(100.dp),
                        placeholder = { Text("e.g. svc wifi disable && svc wifi enable") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    OutlinedTextField(value = editDescription, onValueChange = { editDescription = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                    // Category
                    Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(TileCategory.entries) { cat ->
                            FilterChip(
                                selected = editCategory == cat,
                                onClick = { editCategory = cat },
                                label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(cat.icon, null, Modifier.size(12.dp)) },
                            )
                        }
                    }

                    // Icon
                    Text("Icon", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(TILE_ICON_OPTIONS) { (name, icon) ->
                            FilterChip(
                                selected = editIconName == name,
                                onClick = { editIconName = name },
                                label = { Icon(icon, name, Modifier.size(16.dp)) },
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Confirm before run", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("Show confirmation dialog first", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = editConfirm, onCheckedChange = { editConfirm = it })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Enabled", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Switch(checked = editEnabled, onCheckedChange = { editEnabled = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editLabel.isBlank() || editCommand.isBlank()) { snackbar = "Label and command required"; return@Button }
                    if (editingTile == null) {
                        viewModel.addTile(ShellQsTile(
                            id = "${System.currentTimeMillis()}", label = editLabel, command = editCommand,
                            description = editDescription, iconName = editIconName, category = editCategory,
                            isEnabled = editEnabled, confirmBeforeRun = editConfirm,
                        ))
                        snackbar = "Tile '$editLabel' created ✓"
                    } else {
                        viewModel.updateTile(editingTile!!.copy(
                            label = editLabel, command = editCommand, description = editDescription,
                            iconName = editIconName, category = editCategory,
                            confirmBeforeRun = editConfirm, isEnabled = editEnabled,
                        ))
                        snackbar = "Tile '$editLabel' updated ✓"
                    }
                    showCreateSheet = false; editingTile = null
                }) { Text(if (editingTile == null) "Create" else "Save") }
            },
            dismissButton = { TextButton(onClick = { showCreateSheet = false; editingTile = null }) { Text("Cancel") } },
        )
    }

    // ── Confirm run dialog ────────────────────────────────
    val toConfirm = showDeleteConfirm
    if (toConfirm != null) {
        val isDelete = !toConfirm.confirmBeforeRun
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            icon = {
                Icon(if (isDelete) Icons.Default.Delete else Icons.Default.Warning, null,
                    tint = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
            },
            title = { Text(if (isDelete) "Delete '${toConfirm.label}'?" else "Run '${toConfirm.label}'?") },
            text = {
                if (isDelete) Text("This custom tile and all its data will be permanently removed.")
                else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This action requires confirmation before running:", style = MaterialTheme.typography.bodySmall)
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                        Text(toConfirm.command, Modifier.padding(10.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isDelete) { viewModel.deleteTile(toConfirm.id); snackbar = "Tile deleted" }
                        else { snackbar = "Running '${toConfirm.label}'…"; viewModel.runTile(toConfirm) }
                        showDeleteConfirm = null
                    },
                    colors = if (isDelete) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                             else ButtonDefaults.buttonColors(),
                ) { Text(if (isDelete) "Delete" else "Run") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") } },
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  TAB: BUILT-IN TILES
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BuiltInTilesTab(
    tiles: List<ShellQsTile>,
    catFilter: TileCategory?,
    searchQuery: String,
    onCatFilter: (TileCategory) -> Unit,
    onSearchChange: (String) -> Unit,
    onRun: (ShellQsTile) -> Unit,
    onCopyCommand: (ShellQsTile) -> Unit,
    onCloneToCustom: (ShellQsTile) -> Unit,
) {
    val filtered = tiles.filter { t ->
        (catFilter == null || t.category == catFilter) &&
        (searchQuery.isBlank() || t.label.contains(searchQuery, ignoreCase = true) || t.description.contains(searchQuery, ignoreCase = true))
    }
    val grouped = filtered.groupBy { it.category }

    LazyColumn(contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Search bar
        item {
            OutlinedTextField(
                value = searchQuery, onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search built-in tiles…") },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchChange("") }) { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) }
                },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }

        // Category filter chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TileCategory.entries.filter { it != TileCategory.CUSTOM }) { cat ->
                    val count = tiles.count { it.category == cat }
                    FilterChip(
                        selected = catFilter == cat,
                        onClick = { onCatFilter(cat) },
                        label = { Text("${cat.label} ($count)", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(cat.icon, null, Modifier.size(12.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = cat.color.copy(alpha = 0.2f),
                            selectedLabelColor = cat.color,
                        ),
                    )
                }
            }
        }

        // Tiles per category
        grouped.forEach { (cat, catTiles) ->
            item(key = "header_${cat.name}") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(cat.color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(cat.icon, null, Modifier.size(14.dp), tint = cat.color) }
                    Text(cat.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cat.color)
                    Surface(shape = CircleShape, color = cat.color.copy(alpha = 0.12f)) {
                        Text("${catTiles.size}", style = MaterialTheme.typography.labelSmall, color = cat.color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }

            items(catTiles, key = { it.id }) { tile ->
                BuiltInTileCard(
                    tile = tile,
                    onRun = { onRun(tile) },
                    onCopy = { onCopyCommand(tile) },
                    onClone = { onCloneToCustom(tile) },
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No tiles match your search", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BuiltInTileCard(tile: ShellQsTile, onRun: () -> Unit, onCopy: () -> Unit, onClone: () -> Unit) {
    val icon = TILE_ICON_OPTIONS.find { it.first == tile.iconName }?.second ?: tile.category.icon
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.horizontalGradient(listOf(tile.category.color.copy(alpha = 0.08f), Color.Transparent))
            )
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Category accent
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(tile.category.color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, Modifier.size(22.dp), tint = tile.category.color) }

                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tile.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(tile.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (tile.confirmBeforeRun) {
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text("confirm", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Command preview
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        "$ ${tile.command.take(120)}${if (tile.command.length > 120) "…" else ""}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth(),
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(4.dp))

                // Actions
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCopy, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onClone, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Default.AddCircleOutline, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clone", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onRun,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = tile.category.color),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Run", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TAB: CUSTOM TILES
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomTilesTab(
    tiles: List<ShellQsTile>,
    selectedIds: Set<String>,
    isSelecting: Boolean,
    onToggle: (ShellQsTile) -> Unit,
    onRun: (ShellQsTile) -> Unit,
    onEdit: (ShellQsTile) -> Unit,
    onDelete: (ShellQsTile) -> Unit,
    onSelectToggle: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    if (tiles.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ViewDay, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No custom tiles yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tap + or clone a built-in tile to get started", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tiles, key = { it.id }) { tile ->
            val isSelected = tile.id in selectedIds
            val icon = TILE_ICON_OPTIONS.find { it.first == tile.iconName }?.second ?: Icons.Default.Terminal
            Card(
                modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = { if (isSelecting) onSelectToggle(tile.id) else onRun(tile) },
                    onLongClick = { onLongPress(tile.id) },
                ),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        !tile.isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    },
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth().background(
                        if (!isSelected) Brush.horizontalGradient(listOf(tile.category.color.copy(alpha = 0.07f), Color.Transparent))
                        else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (isSelecting) { Checkbox(checked = isSelected, onCheckedChange = { onSelectToggle(tile.id) }); Spacer(Modifier.width(4.dp)) }
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (tile.isEnabled) tile.category.color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(icon, null, Modifier.size(22.dp), tint = if (tile.isEnabled) tile.category.color else MaterialTheme.colorScheme.outline) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(tile.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    Surface(shape = RoundedCornerShape(4.dp), color = tile.category.color.copy(alpha = 0.12f)) {
                                        Text(tile.category.label, style = MaterialTheme.typography.labelSmall, color = tile.category.color, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                    if (!tile.isEnabled) {
                                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.outline.copy(0.2f)) {
                                            Text("off", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Text(tile.command, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                            Switch(checked = tile.isEnabled, onCheckedChange = { onToggle(tile) })
                        }

                        if (tile.lastOutput.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp)) {
                                Text("→ ${tile.lastOutput.take(80)}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
                        Spacer(Modifier.height(4.dp))

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${tile.executionCount}× · Last: ${tile.lastRun}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            if (!isSelecting) {
                                IconButton(onClick = { onEdit(tile) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onDelete(tile) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                }
                                if (tile.isEnabled) {
                                    IconButton(onClick = { onRun(tile) }, modifier = Modifier.size(32.dp)) {
                                        if (tile.isRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        else Icon(Icons.Default.PlayArrow, "Run", modifier = Modifier.size(18.dp), tint = tile.category.color)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TAB: LOGS
// ═══════════════════════════════════════════════════════════════

@Composable
private fun LogsTab(
    logs: List<TileLog>,
    onClear: () -> Unit,
    onCopyLog: (TileLog) -> Unit,
) {
    if (logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Article, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("No logs yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Run a tile to see its output log here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${logs.size} log entries (newest first)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.DeleteSweep, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear All", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        items(logs, key = { "${it.tileId}_${it.timestamp}_${it.durationMs}" }) { log ->
            LogCard(log = log, onCopy = { onCopyLog(log) })
        }
    }
}

@Composable
private fun LogCard(log: TileLog, onCopy: () -> Unit) {
    val successColor = Color(0xFF16A34A)
    val errorColor   = MaterialTheme.colorScheme.error
    val isSuccess    = log.exitCode == 0

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            // Header bar
            Row(
                Modifier.fillMaxWidth()
                    .background(if (isSuccess) successColor.copy(alpha = 0.12f) else errorColor.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .background(if (isSuccess) successColor.copy(alpha = 0.2f) else errorColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isSuccess) Icons.Default.Check else Icons.Default.Close,
                        null, Modifier.size(11.dp),
                        tint = if (isSuccess) successColor else errorColor,
                    )
                }
                Text(log.tileLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(4.dp), color = log.category.color.copy(alpha = 0.12f)) {
                    Text(log.category.label, style = MaterialTheme.typography.labelSmall, color = log.category.color, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
                Text(log.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (log.durationMs > 0) {
                    Text("${log.durationMs}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Command
            Text(
                "$ ${log.command}",
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp).fillMaxWidth(),
                fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )

            // Output
            if (log.output.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
                    color = if (isSuccess) successColor.copy(alpha = 0.06f) else errorColor.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        log.output,
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = if (isSuccess) MaterialTheme.colorScheme.onSurface else errorColor.copy(alpha = 0.85f),
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TAB: RESULTS (persistent per-tile last result)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ResultsTab(
    results: List<TileResult>,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    onCopy: (TileResult) -> Unit,
) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Assessment, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No results stored", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Last result from each tile is saved here permanently", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Results persist until you delete them", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${results.size} stored results", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("One result per tile · Persists until deleted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.DeleteSweep, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear All", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Stats row
        item {
            val successCount = results.count { it.exitCode == 0 }
            val errorCount   = results.count { it.exitCode != 0 }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultStatPill("$successCount Success", Color(0xFF16A34A), Modifier.weight(1f))
                ResultStatPill("$errorCount Failed", MaterialTheme.colorScheme.error, Modifier.weight(1f))
                ResultStatPill("${results.size} Total", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
        }

        // Category grouping
        val grouped = results.groupBy { it.category }
        grouped.forEach { (cat, catResults) ->
            item(key = "res_header_${cat.name}") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(cat.icon, null, Modifier.size(14.dp), tint = cat.color)
                    Text(cat.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cat.color)
                    Surface(shape = CircleShape, color = cat.color.copy(alpha = 0.12f)) {
                        Text("${catResults.size}", style = MaterialTheme.typography.labelSmall, color = cat.color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
            }
            items(catResults, key = { "result_${it.tileId}" }) { result ->
                ResultCard(
                    result = result,
                    onDelete = { onDelete(result.tileId) },
                    onCopy = { onCopy(result) },
                )
            }
        }
    }
}

@Composable
private fun ResultCard(result: TileResult, onDelete: () -> Unit, onCopy: () -> Unit) {
    val isSuccess  = result.exitCode == 0
    val accentColor = if (isSuccess) Color(0xFF16A34A) else MaterialTheme.colorScheme.error

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            // Left-accent header
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().background(result.category.color.copy(alpha = 0.1f)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(accentColor),
                    )
                    Text(result.tileLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(result.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Command
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Terminal, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(result.command, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }

                // Output
                Surface(
                    color = accentColor.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        result.output.ifBlank { "(no output)" },
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = if (isSuccess) MaterialTheme.colorScheme.onSurface else accentColor.copy(alpha = 0.9f),
                    )
                }

                // Footer
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        Surface(shape = RoundedCornerShape(4.dp), color = accentColor.copy(alpha = 0.12f)) {
                            Text(
                                if (isSuccess) "✓ exit 0" else "✗ exit ${result.exitCode}",
                                style = MaterialTheme.typography.labelSmall, color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        if (result.durationMs > 0) {
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(
                                    "${result.durationMs}ms",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, "Delete", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultStatPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
