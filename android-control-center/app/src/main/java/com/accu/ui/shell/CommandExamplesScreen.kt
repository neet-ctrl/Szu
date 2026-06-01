package com.accu.ui.shell

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accu.ui.components.ACCTopBar
import com.accu.ui.components.InfoTooltipIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class CommandExample(
    val id: String,
    val title: String,
    val command: String,
    val description: String,
    val labels: List<String>,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
)

// ──────────────────────────────────────────────────────────────────
//  ViewModel — persists custom commands + favorites in SharedPrefs
// ──────────────────────────────────────────────────────────────────

private const val PREF_COMMANDS = "command_examples_prefs"
private const val KEY_CUSTOMS   = "custom_commands"
private const val KEY_FAVORITES = "favorite_ids"

data class CommandExamplesUiState(
    val customCommands: List<CommandExample> = emptyList(),
    val favorites:      Set<String>          = emptySet(),
)

@HiltViewModel
class CommandExamplesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREF_COMMANDS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(CommandExamplesUiState())
    val state: StateFlow<CommandExamplesUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        val customs = prefs.getString(KEY_CUSTOMS, "[]")?.let { parseCustoms(it) } ?: emptyList()
        val favSet  = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        _state.update { it.copy(customCommands = customs, favorites = favSet) }
    }

    fun addCustom(cmd: CommandExample) {
        _state.update { it.copy(customCommands = it.customCommands + cmd) }
        persist()
    }

    fun updateCustom(updated: CommandExample) {
        _state.update { it.copy(customCommands = it.customCommands.map { c -> if (c.id == updated.id) updated else c }) }
        persist()
    }

    fun deleteCustom(id: String) {
        _state.update { it.copy(customCommands = it.customCommands.filter { c -> c.id != id }, favorites = it.favorites - id) }
        persist()
    }

    fun toggleFavorite(id: String) {
        _state.update { s ->
            val fav = if (id in s.favorites) s.favorites - id else s.favorites + id
            s.copy(favorites = fav)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, _state.value.favorites).apply()
    }

    private fun persist() {
        val json = JSONArray().apply {
            _state.value.customCommands.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("title", c.title); put("command", c.command)
                    put("description", c.description)
                    put("labels", JSONArray(c.labels))
                })
            }
        }.toString()
        prefs.edit()
            .putString(KEY_CUSTOMS, json)
            .putStringSet(KEY_FAVORITES, _state.value.favorites)
            .apply()
    }

    private fun parseCustoms(json: String): List<CommandExample> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val labelsArr = o.optJSONArray("labels")
            val labels = if (labelsArr != null) (0 until labelsArr.length()).map { labelsArr.getString(it) }
                         else listOf("custom")
            CommandExample(
                id = o.getString("id"), title = o.getString("title"),
                command = o.getString("command"), description = o.optString("description"),
                labels = labels, isCustom = true,
            )
        }
    } catch (_: Exception) { emptyList() }
}

private val PRELOADED_COMMANDS = listOf(
    // Activity Manager
    CommandExample("am01","Force-stop app","am force-stop <package>","Force stops a specific package, terminating its processes.",listOf("am","package","kill")),
    CommandExample("am02","Start activity","am start -n <package>/<activity>","Starts a specific activity of an application.",listOf("am","activity","launch")),
    CommandExample("am03","Start via VIEW","am start -a android.intent.action.VIEW -d <uri>","Opens a URI using the VIEW intent action.",listOf("am","intent","launch")),
    CommandExample("am04","Start service","am startservice <package>/<service>","Starts a specific service of an application.",listOf("am","service","launch")),
    CommandExample("am05","Stop service","am stopservice <package>/<service>","Stops a specific running service.",listOf("am","service","stop")),
    CommandExample("am06","Kill background","am kill <package>","Kills the background processes of a specific package.",listOf("am","process","kill")),
    CommandExample("am07","Kill all background","am kill-all","Kills all background processes.",listOf("am","process","kill")),
    CommandExample("am08","Send broadcast","am broadcast -a <action>","Sends a broadcast intent with the specified action.",listOf("am","intent","broadcast")),
    // AppOps
    CommandExample("ao01","Get app ops","appops get <package>","Gets the app operations (permissions state) for a package.",listOf("appops","package","permission")),
    CommandExample("ao02","Set app op","appops set <package> <operation> <mode>","Sets an app operation mode (allow/deny/ignore).",listOf("appops","package","permission")),
    CommandExample("ao03","Reset app ops","appops reset <package>","Resets all app operations for a package to defaults.",listOf("appops","package","permission")),
    // cat / cd / clear
    CommandExample("fs01","View file","cat <file_path>","Displays the contents of a file.",listOf("file","read")),
    CommandExample("fs02","Change directory","cd <directory_path>","Changes the current directory.",listOf("directory","navigation")),
    CommandExample("fs03","Go to root","cd /","Changes the current directory to the root directory.",listOf("directory","navigation")),
    CommandExample("fs04","Go home","cd ~","Changes the current directory to the home directory.",listOf("directory","navigation")),
    CommandExample("fs05","Go up","cd ..","Moves up one directory level.",listOf("directory","navigation")),
    CommandExample("fs06","Previous dir","cd -","Changes to the previous directory.",listOf("directory","navigation")),
    CommandExample("fs07","Clear terminal","clear","Clears the terminal screen.",listOf("terminal","utility")),
    // cmd
    CommandExample("cmd01","Activity service","cmd activity","Interacts with the activity manager service.",listOf("cmd","system","activity")),
    CommandExample("cmd02","Set app op (cmd)","cmd appops set <package> <op> <mode>","Sets an app operation using the cmd interface.",listOf("cmd","package","permission","appops")),
    CommandExample("cmd03","Bluetooth enable","cmd bluetooth_manager enable","Enables Bluetooth.",listOf("cmd","system","bluetooth","connectivity")),
    CommandExample("cmd04","Bluetooth disable","cmd bluetooth_manager disable","Disables Bluetooth.",listOf("cmd","system","bluetooth","connectivity")),
    CommandExample("cmd05","Notification service","cmd notification","Interacts with the notification manager service.",listOf("cmd","system","notification")),
    CommandExample("cmd06","Compile package","cmd package compile -m speed -f <package>","Force compiles a package with speed optimization profile.",listOf("cmd","package","optimize","compile")),
    CommandExample("cmd07","Expand notifications","cmd statusbar expand-notifications","Expands the notification shade.",listOf("cmd","system","ui","notification")),
    CommandExample("cmd08","Expand QS","cmd statusbar expand-settings","Expands the quick settings panel.",listOf("cmd","system","ui","settings")),
    CommandExample("cmd09","Collapse status bar","cmd statusbar collapse","Collapses the status bar.",listOf("cmd","system","ui")),
    CommandExample("cmd10","Night mode off","cmd uimode night no","Disables night mode in the system UI.",listOf("cmd","system","display","ui")),
    CommandExample("cmd11","Night mode on","cmd uimode night yes","Enables night mode in the system UI.",listOf("cmd","system","display","ui")),
    // content
    CommandExample("co01","Query system settings","content query --uri content://settings/system","Queries system settings using the content provider.",listOf("content","system","settings")),
    CommandExample("co02","Insert content","content insert --uri <uri> --bind <key>:<type>:<value>","Inserts a value into a content provider.",listOf("content","system","database")),
    CommandExample("co03","Delete content","content delete --uri <uri>","Deletes entries from a content provider.",listOf("content","system","database")),
    // cp/date/device_config/df/dmesg/du
    CommandExample("fs08","Copy file","cp <from> <to>","Copies a file or directory from one location to another.",listOf("file","copy")),
    CommandExample("fs09","Copy recursive","cp -r <from> <to>","Recursively copies a directory and its contents.",listOf("directory","copy","recursive")),
    CommandExample("sy01","Date/time","date","Displays the current date and time.",listOf("system","time")),
    CommandExample("sy02","List device config","device_config list <namespace>","Lists all device configuration flags in a namespace.",listOf("system","config","flags")),
    CommandExample("sy03","Get device config","device_config get <namespace> <key>","Gets a specific device configuration flag value.",listOf("system","config","flags")),
    CommandExample("sy04","Set device config","device_config put <namespace> <key> <value>","Sets a device configuration flag value.",listOf("system","config","flags")),
    CommandExample("sy05","Disk usage /system","df -h /system","Displays disk usage for /system partition in human-readable format.",listOf("system","disk")),
    CommandExample("sy06","Kernel messages","dmesg","Displays kernel messages.",listOf("system","kernel","log")),
    CommandExample("sy07","File disk usage","du -h","Displays disk usage of files and directories.",listOf("file","disk")),
    CommandExample("sy08","System dir usage","du -sh /system/*","Displays a summary of disk usage for /system.",listOf("system","disk")),
    // dumpsys
    CommandExample("ds01","Activities","dumpsys activity","Displays information about activities and their states.",listOf("dumpsys","system","activity","process")),
    CommandExample("ds02","Top activity","dumpsys activity top","Displays the currently running top activity.",listOf("dumpsys","system","activity","process")),
    CommandExample("ds03","Alarms","dumpsys alarm","Displays information about all pending alarms.",listOf("dumpsys","system","alarm")),
    CommandExample("ds04","Battery status","dumpsys battery","Displays battery status and information.",listOf("dumpsys","battery")),
    CommandExample("ds05","Set battery level","dumpsys battery set level <n>","Sets the battery level to a specific value (for testing).",listOf("dumpsys","battery","testing")),
    CommandExample("ds06","Set battery status","dumpsys battery set status <n>","Sets the battery status to a specific value (for testing).",listOf("dumpsys","battery","testing")),
    CommandExample("ds07","Reset battery","dumpsys battery reset","Resets the battery statistics.",listOf("dumpsys","battery")),
    CommandExample("ds08","Connectivity","dumpsys connectivity","Displays information about network connectivity.",listOf("dumpsys","system","network","connectivity")),
    CommandExample("ds09","CPU info","dumpsys cpuinfo","Displays CPU usage information.",listOf("dumpsys","system","cpu","performance")),
    CommandExample("ds10","Display info","dumpsys display","Displays information about the display system.",listOf("dumpsys","system","display")),
    CommandExample("ds11","Input devices","dumpsys input","Displays information about input devices and events.",listOf("dumpsys","system","input","device")),
    CommandExample("ds12","Memory all","dumpsys meminfo","Displays memory usage information for all processes.",listOf("dumpsys","system","memory","performance")),
    CommandExample("ds13","Memory for package","dumpsys meminfo <package>","Displays detailed memory usage for a specific package.",listOf("dumpsys","system","memory","package")),
    CommandExample("ds14","Network stats","dumpsys netstats","Displays network usage statistics.",listOf("dumpsys","system","network","stats")),
    CommandExample("ds15","Notifications","dumpsys notification","Displays information about notifications.",listOf("dumpsys","system","notification")),
    CommandExample("ds16","Package info","dumpsys package <package>","Displays detailed information about a specific package.",listOf("dumpsys","system","package","info")),
    CommandExample("ds17","Power management","dumpsys power","Displays power management information and wake locks.",listOf("dumpsys","system","power","battery")),
    CommandExample("ds18","Usage stats","dumpsys usagestats","Displays app usage statistics.",listOf("dumpsys","system","usage","stats")),
    CommandExample("ds19","Wi-Fi info","dumpsys wifi","Displays Wi-Fi status and information.",listOf("dumpsys","system","wifi","network")),
    CommandExample("ds20","Window manager","dumpsys window","Displays information about the window manager.",listOf("dumpsys","system","window","display")),
    // echo/exit/file/find/getenforce/getprop
    CommandExample("ut01","Echo","echo <message>","Prints a message to the terminal.",listOf("terminal","utility","text")),
    CommandExample("ut02","Exit shell","exit","Exits the current shell session.",listOf("terminal","session")),
    CommandExample("ut03","File type","file <file_path>","Determines the type of a file.",listOf("file","utility")),
    CommandExample("ut04","Find files","find <path> -name <pattern>","Searches for files matching a name pattern.",listOf("file","search","utility")),
    CommandExample("sy09","SELinux mode","getenforce","Displays the current SELinux mode.",listOf("system","security")),
    CommandExample("sy10","List all props","getprop","Lists all system properties.",listOf("system","property","info")),
    CommandExample("sy11","Get prop","getprop <property>","Gets the value of a specific system property.",listOf("system","property","info")),
    CommandExample("sy12","SDK version","getprop ro.build.version.sdk","Displays the Android SDK version number.",listOf("system","property","version")),
    CommandExample("sy13","Build ID","getprop ro.build.display.id","Displays the build display ID (firmware version).",listOf("system","property","version")),
    CommandExample("sy14","Device model","getprop ro.product.model","Displays the device model name.",listOf("system","property","device")),
    CommandExample("sy15","Manufacturer","getprop ro.product.manufacturer","Displays the device manufacturer.",listOf("system","property","device")),
    CommandExample("sy16","Serial number","getprop ro.serialno","Displays the device serial number.",listOf("system","property","device")),
    // grep/id/ifconfig/input
    CommandExample("ut05","Grep","grep <pattern> <file>","Searches for a pattern in files or input.",listOf("file","search","utility")),
    CommandExample("sy17","User ID","id","Displays the current user ID, group ID, and security context.",listOf("system","user","security")),
    CommandExample("nt01","Network interfaces","ifconfig","Displays network interface configurations.",listOf("network","interface","info")),
    CommandExample("in01","Key event","input keyevent <keycode>","Simulates pressing a hardware key.",listOf("input","key","simulate")),
    CommandExample("in02","Power button","input keyevent 26","Simulates pressing the Power button.",listOf("input","key","power")),
    CommandExample("in03","Home button","input keyevent 3","Simulates pressing the Home button.",listOf("input","key","navigation")),
    CommandExample("in04","Back button","input keyevent 4","Simulates pressing the Back button.",listOf("input","key","navigation")),
    CommandExample("in05","Volume Up","input keyevent 24","Simulates pressing Volume Up.",listOf("input","key","volume")),
    CommandExample("in06","Volume Down","input keyevent 25","Simulates pressing Volume Down.",listOf("input","key","volume")),
    CommandExample("in07","Recents button","input keyevent 187","Simulates pressing the Recents button.",listOf("input","key","navigation")),
    CommandExample("in08","Sleep device","input keyevent 223","Puts the device to sleep.",listOf("input","key","power")),
    CommandExample("in09","Wake device","input keyevent 224","Wakes the device up.",listOf("input","key","power")),
    CommandExample("in10","Tap screen","input tap <x> <y>","Simulates a screen tap at the specified coordinates.",listOf("input","touch","simulate")),
    CommandExample("in11","Swipe gesture","input swipe <x1> <y1> <x2> <y2>","Simulates a swipe gesture from one point to another.",listOf("input","touch","simulate")),
    CommandExample("in12","Swipe timed","input swipe <x1> <y1> <x2> <y2> <duration_ms>","Simulates a swipe gesture with a specified duration.",listOf("input","touch","simulate")),
    CommandExample("in13","Type text","input text <text>","Types the specified text on the focused input field.",listOf("input","text","simulate")),
    // ip/iptables/kill/logcat
    CommandExample("nt02","IP addresses","ip addr","Displays IP addresses assigned to all network interfaces.",listOf("network","ip","info")),
    CommandExample("nt03","Routing table","ip route","Displays the routing table.",listOf("network","route","info")),
    CommandExample("nt04","Routing rules","ip rule","Displays the routing policy rules.",listOf("network","route","info")),
    CommandExample("nt05","iptables rules","iptables -L","Lists all iptables rules.",listOf("network","firewall","system")),
    CommandExample("sy18","Kill process","kill <pid>","Terminates a process with the specified process ID.",listOf("process","kill")),
    CommandExample("lo01","View logcat","logcat","Displays system logs.",listOf("logcat","system","log")),
    CommandExample("lo02","Log buffer size","logcat -g","Displays the size of the log buffer.",listOf("logcat","system","log")),
    CommandExample("lo03","Set log buffer","logcat -G <size>","Sets the size of the log buffer.",listOf("logcat","system","log")),
    CommandExample("lo04","Clear logcat","logcat -c","Clears the log buffer.",listOf("logcat","system","log","utility")),
    CommandExample("lo05","Last 100 lines","logcat -d -t 100","Last 100 log lines then exit.",listOf("logcat","system","log")),
    CommandExample("lo06","Filter by tag","logcat -s <Tag>","Show only logs for specific tag.",listOf("logcat","system","log")),
    // ls/md5/mkdir/monkey/mv/netstat/ping
    CommandExample("fs10","List files","ls","Lists files and directories in the current path.",listOf("file","directory")),
    CommandExample("fs11","List all with details","ls -la","Lists all files including hidden ones with detailed information.",listOf("file","directory","detail")),
    CommandExample("fs12","List recursive","ls -R","Recursively lists files and directories.",listOf("file","directory","recursive")),
    CommandExample("fs13","List with sizes","ls -s","Lists files and directories with their sizes.",listOf("file","directory","size")),
    CommandExample("fs14","MD5 checksum","md5sum <file_path>","Computes the MD5 hash of a file.",listOf("file","hash","utility")),
    CommandExample("fs15","Make directory","mkdir <file_path>","Creates a new directory.",listOf("directory","create")),
    CommandExample("fs16","Make parent dirs","mkdir -p <path>","Creates a directory along with any necessary parent directories.",listOf("directory","create")),
    CommandExample("te01","Stress test app","monkey -p <package> -v <count>","Generates pseudo-random user events for stress testing an app.",listOf("testing","package","stress")),
    CommandExample("fs17","Move file","mv <from> <to>","Moves or renames a file or directory.",listOf("file","directory","move")),
    CommandExample("nt06","Network stats","netstat","Displays network connections and statistics.",listOf("network","utility")),
    CommandExample("nt07","Ping host","ping -c 4 <host>","Tests network connectivity to a host.",listOf("network","utility","test")),
    // pm
    CommandExample("pm01","Clear app data","pm clear <package>","Clears the data and cache of a specific package.",listOf("pm","package","data","cache")),
    CommandExample("pm02","Disable for user","pm disable-user --user 0 <package>","Disables a package for the current user without uninstalling.",listOf("pm","package","disable","user")),
    CommandExample("pm03","Disable component","pm disable <package/component>","Disables a specific component of a package.",listOf("pm","package","disable")),
    CommandExample("pm04","Enable component","pm enable <package/component>","Enables a specific component of a package.",listOf("pm","package","enable")),
    CommandExample("pm05","Grant permission","pm grant <package> <Permission>","Grants a specific permission to a package.",listOf("pm","package","permission")),
    CommandExample("pm06","Hide package","pm hide <package>","Hides a package from the launcher.",listOf("pm","package","hide")),
    CommandExample("pm07","Unhide package","pm unhide <package>","Unhides a previously hidden package.",listOf("pm","package","hide")),
    CommandExample("pm08","Install APK","pm install <apk_path>","Installs an APK from the specified path.",listOf("pm","package","install")),
    CommandExample("pm09","Reinstall APK","pm install -r <apk_path>","Reinstalls an existing app, keeping its data.",listOf("pm","package","install","update")),
    CommandExample("pm10","Downgrade APK","pm install -d <apk_path>","Allows version code downgrade when installing an APK.",listOf("pm","package","install","downgrade")),
    CommandExample("pm11","Install + grant perms","pm install -g <apk_path>","Installs an APK and grants all runtime permissions.",listOf("pm","package","install","permission")),
    CommandExample("pm12","List device features","pm list features","Lists all hardware and software features.",listOf("pm","system","feature","list")),
    CommandExample("pm13","List libraries","pm list libraries","Lists all shared libraries on the device.",listOf("pm","system","library","list")),
    CommandExample("pm14","List all packages","pm list packages","Lists all installed packages.",listOf("pm","package","list")),
    CommandExample("pm15","List user packages","pm list packages -3","Lists only third-party (user-installed) packages.",listOf("pm","package","list","user")),
    CommandExample("pm16","List system packages","pm list packages -s","Lists only system packages.",listOf("pm","package","list","system")),
    CommandExample("pm17","List disabled","pm list packages -d","Lists only disabled packages.",listOf("pm","package","list","disable")),
    CommandExample("pm18","List enabled","pm list packages -e","Lists only enabled packages.",listOf("pm","package","list","enable")),
    CommandExample("pm19","List with paths","pm list packages -f","Lists all packages with their APK file paths.",listOf("pm","package","list","file")),
    CommandExample("pm20","List permissions","pm list permissions","Lists all permissions defined on the device.",listOf("pm","permission","list")),
    CommandExample("pm21","List users","pm list users","Lists all user profiles on the device.",listOf("pm","user","list")),
    CommandExample("pm22","Package APK path","pm path <package>","Displays the APK file path of a package.",listOf("pm","package","file","path")),
    CommandExample("pm23","Revoke permission","pm revoke <package> <Permission>","Revokes a specific permission from a package.",listOf("pm","package","permission")),
    CommandExample("pm24","Set install location","pm set-install-location <location>","Sets the default install location (0=auto, 1=internal, 2=external).",listOf("pm","package","install","storage")),
    CommandExample("pm25","Suspend package","pm suspend <package>","Suspends a package, making it unusable.",listOf("pm","package","suspend")),
    CommandExample("pm26","Unsuspend package","pm unsuspend <package>","Unsuspends a previously suspended package.",listOf("pm","package","suspend")),
    CommandExample("pm27","Trim caches","pm trim-caches <desired_free_space>","Trims cache files to reach the desired free space.",listOf("pm","package","cache","storage")),
    CommandExample("pm28","Uninstall package","pm uninstall <package>","Fully uninstalls a package from the device.",listOf("pm","package","uninstall")),
    CommandExample("pm29","Uninstall keep data","pm uninstall -k <package>","Uninstalls a package but keeps its data and cache.",listOf("pm","package","uninstall","data")),
    CommandExample("pm30","Uninstall for user","pm uninstall --user 0 <package>","Uninstalls a package for the current user (debloat without root).",listOf("pm","package","uninstall","user","bloatware")),
    CommandExample("pm31","Uninstall user+data","pm uninstall -k --user 0 <package>","Uninstalls package for current user while keeping its data.",listOf("pm","package","uninstall","user","data")),
    // ps/pwd/reboot/rm
    CommandExample("sy19","Running processes","ps -A","Lists all running processes.",listOf("process","system")),
    CommandExample("sy20","Print working dir","pwd","Displays the current working directory.",listOf("directory","system")),
    CommandExample("sy21","Reboot","reboot","Reboots the device.",listOf("system","reboot","power")),
    CommandExample("sy22","Reboot bootloader","reboot bootloader","Reboots the device into bootloader (fastboot) mode.",listOf("system","reboot","bootloader")),
    CommandExample("sy23","Reboot recovery","reboot recovery","Reboots the device into recovery mode.",listOf("system","reboot","recovery")),
    CommandExample("sy24","Power off","reboot -p","Powers off the device.",listOf("system","power","shutdown")),
    CommandExample("fs18","Delete file","rm <file_path>","Deletes a file.",listOf("file","delete")),
    CommandExample("fs19","Force delete","rm -rf <path>","Recursively and forcefully deletes a file or directory.",listOf("file","directory","delete")),
    CommandExample("fs20","Remove empty dir","rmdir <directory_path>","Deletes an empty directory.",listOf("directory","delete")),
    // screen/service/setprop/settings
    CommandExample("sc01","Take screenshot","screencap -p /sdcard/screenshot.png","Captures a screenshot in PNG format and saves to sdcard.",listOf("screen","capture","utility")),
    CommandExample("sc02","Record screen","screenrecord /sdcard/recording.mp4","Records the device screen and saves as MP4 video.",listOf("screen","record","video")),
    CommandExample("sc03","Record timed","screenrecord --time-limit <seconds> /sdcard/recording.mp4","Records the screen for a specified duration in seconds.",listOf("screen","record","video")),
    CommandExample("sc04","Record custom size","screenrecord --size <width>x<height> /sdcard/recording.mp4","Records the screen at a specific resolution.",listOf("screen","record","video")),
    CommandExample("sy25","List services","service list","Lists all running system services.",listOf("system","service","list")),
    CommandExample("sy26","Set system prop","setprop <property> <value>","Sets a system property to the specified value.",listOf("system","property","config")),
    CommandExample("se01","Get setting","settings get <namespace> <key>","Gets the value of a specific system setting.",listOf("settings","system")),
    CommandExample("se02","List settings","settings list <namespace>","Lists all settings in a namespace (system/secure/global).",listOf("settings","system","list")),
    CommandExample("se03","Set setting","settings put <namespace> <key> <value>","Sets the value of a specific system setting.",listOf("settings","system")),
    CommandExample("se04","Animator scale","settings put global animator_duration_scale <value>","Sets the animator duration scale (0=off, 0.5x, 1x, etc.).",listOf("settings","system","animation","ui")),
    CommandExample("se05","Transition scale","settings put global transition_animation_scale <value>","Sets the transition animation scale.",listOf("settings","system","animation","ui")),
    CommandExample("se06","Window scale","settings put global window_animation_scale <value>","Sets the window animation scale.",listOf("settings","system","animation","ui")),
    CommandExample("se07","Screen brightness","settings put system screen_brightness <0-255>","Sets the screen brightness (0=darkest, 255=brightest).",listOf("settings","system","display")),
    CommandExample("se08","Screen timeout","settings put system screen_off_timeout <ms>","Sets the screen timeout in milliseconds.",listOf("settings","system","display")),
    CommandExample("se09","Accessibility service","settings put secure enabled_accessibility_services <service>","Enables a specific accessibility service.",listOf("settings","system","accessibility")),
    CommandExample("se10","Enable ADB","settings put global adb_enabled <0|1>","Enables or disables ADB.",listOf("settings","system","adb")),
    CommandExample("se11","Developer options","settings put global development_settings_enabled <0|1>","Enables or disables developer options.",listOf("settings","system","developer")),
    CommandExample("se12","Dark mode","settings put secure ui_night_mode 2","Force dark mode on.",listOf("settings","system","display","ui")),
    CommandExample("se13","Light mode","settings put secure ui_night_mode 1","Force light mode on.",listOf("settings","system","display","ui")),
    CommandExample("se14","Delete setting","settings delete <namespace> <key>","Deletes a specific system setting.",listOf("settings","system")),
    // sleep/stat/su/svc
    CommandExample("ut06","Sleep","sleep <seconds>","Pauses execution for a specified number of seconds.",listOf("utility","delay")),
    CommandExample("fs21","File stat","stat <file_path>","Displays detailed status information about a file.",listOf("file","utility")),
    CommandExample("sy27","Switch to root","su","Switches to superuser mode (root).",listOf("system","root")),
    CommandExample("sv01","BT enable","svc bluetooth enable","Enables Bluetooth.",listOf("svc","system","bluetooth","connectivity")),
    CommandExample("sv02","BT disable","svc bluetooth disable","Disables Bluetooth.",listOf("svc","system","bluetooth","connectivity")),
    CommandExample("sv03","Mobile data on","svc data enable","Enables mobile data.",listOf("svc","system","data","connectivity")),
    CommandExample("sv04","Mobile data off","svc data disable","Disables mobile data.",listOf("svc","system","data","connectivity")),
    CommandExample("sv05","NFC enable","svc nfc enable","Enables NFC.",listOf("svc","system","nfc","connectivity")),
    CommandExample("sv06","NFC disable","svc nfc disable","Disables NFC.",listOf("svc","system","nfc","connectivity")),
    CommandExample("sv07","Screen stay on","svc power stayon true","Keeps the screen always on while connected.",listOf("svc","system","power","display")),
    CommandExample("sv08","Screen auto off","svc power stayon false","Restores normal screen timeout behavior.",listOf("svc","system","power","display")),
    CommandExample("sv09","Reboot (svc)","svc power reboot","Reboots the device via the power service.",listOf("svc","system","power","reboot")),
    CommandExample("sv10","Shutdown (svc)","svc power shutdown","Shuts down the device.",listOf("svc","system","power","shutdown")),
    CommandExample("sv11","Set USB function","svc usb setFunctions <function>","Sets USB mode (mtp, ptp, rndis, midi, etc.).",listOf("svc","system","usb","connectivity")),
    CommandExample("sv12","Wi-Fi enable","svc wifi enable","Enables Wi-Fi.",listOf("svc","system","wifi","connectivity")),
    CommandExample("sv13","Wi-Fi disable","svc wifi disable","Disables Wi-Fi.",listOf("svc","system","wifi","connectivity")),
    // top/touch/uname/uptime/wc/whoami/wm
    CommandExample("sy28","Top processes","top","Displays running processes in real-time.",listOf("process","system","monitor")),
    CommandExample("sy29","Top snapshot","top -n 1","Displays a single snapshot of running processes.",listOf("process","system","monitor")),
    CommandExample("fs22","Create empty file","touch <file_path>","Creates a new empty file or updates the modification timestamp.",listOf("file","create","update")),
    CommandExample("sy30","Unmount filesystem","umount <mount_point>","Unmounts a filesystem.",listOf("system","filesystem")),
    CommandExample("sy31","System info","uname -a","Displays system information.",listOf("system","info")),
    CommandExample("sy32","Uptime","uptime","Displays how long the device has been running since last reboot.",listOf("system","info","time")),
    CommandExample("ut07","Line count","wc -l <file_path>","Counts the number of lines in a file.",listOf("file","utility","count")),
    CommandExample("sy33","Current user","whoami","Displays the current user.",listOf("system","user")),
    CommandExample("wm01","Screen density","wm density <dpi>","Sets the screen density.",listOf("wm","system","display","ui")),
    CommandExample("wm02","Reset density","wm density reset","Resets the screen density to default.",listOf("wm","system","display","ui")),
    CommandExample("wm03","Set screen size","wm size <width>x<height>","Sets the screen resolution.",listOf("wm","system","display","ui")),
    CommandExample("wm04","Reset screen size","wm size reset","Resets the screen resolution to default.",listOf("wm","system","display","ui")),
    CommandExample("wm05","Get screen size","wm size","Gets current display size.",listOf("wm","system","display")),
    CommandExample("wm06","Set overscan","wm overscan <left>,<top>,<right>,<bottom>","Sets overscan for the display.",listOf("wm","system","display","ui")),
    CommandExample("wm07","Reset overscan","wm overscan reset","Resets overscan to default.",listOf("wm","system","display","ui")),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommandExamplesScreen(
    onBack: () -> Unit = {},
    onCommandSelected: (String) -> Unit = {},
    viewModel: CommandExamplesViewModel = hiltViewModel(),
) {
    val vmState by viewModel.state.collectAsStateWithLifecycle()
    val customCommands = vmState.customCommands
    val favorites      = vmState.favorites
    val allCommands = remember(customCommands) { PRELOADED_COMMANDS + customCommands }
    val commands = remember(allCommands, favorites) {
        allCommands.map { if (it.id in favorites) it.copy(isFavorite = true) else it }
    }

    var search by remember { mutableStateOf("") }
    var selectedLabels by remember { mutableStateOf(setOf<String>()) }
    var sortMode by remember { mutableStateOf("Default") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<CommandExample?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<CommandExample?>(null) }
    var showLoadDefaultsDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newCommand by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }
    var newLabels by remember { mutableStateOf("") }

    val allLabels = remember(allCommands) { allCommands.flatMap { it.labels }.toSet().sorted() }

    val filtered = remember(commands, search, selectedLabels, sortMode) {
        commands.filter { cmd ->
            (selectedLabels.isEmpty() || selectedLabels.any { it in cmd.labels }) &&
            (search.isBlank() || cmd.title.contains(search, ignoreCase = true) ||
                cmd.command.contains(search, ignoreCase = true) ||
                cmd.description.contains(search, ignoreCase = true) ||
                cmd.labels.any { it.contains(search, ignoreCase = true) })
        }.let { list ->
            when (sortMode) {
                "A–Z" -> list.sortedBy { it.title }
                "Z–A" -> list.sortedByDescending { it.title }
                "Favorites first" -> list.sortedWith(compareByDescending { it.isFavorite })
                "Custom first" -> list.sortedWith(compareByDescending { it.isCustom })
                else -> list
            }
        }
    }

    // Add command dialog
    if (showAddDialog || showEditDialog != null) {
        val editing = showEditDialog
        LaunchedEffect(editing) {
            if (editing != null) {
                newTitle = editing.title; newCommand = editing.command
                newDescription = editing.description; newLabels = editing.labels.joinToString(", ")
            }
        }
        AlertDialog(
            onDismissRequest = { showAddDialog = false; showEditDialog = null; newTitle = ""; newCommand = ""; newDescription = ""; newLabels = "" },
            title = { Text(if (editing != null) "Edit Command" else "Add Custom Command") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newTitle, { newTitle = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(
                        newCommand, { newCommand = it },
                        label = { Text("Command *") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
                    )
                    OutlinedTextField(newDescription, { newDescription = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(newLabels, { newLabels = it }, label = { Text("Labels (comma separated)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("e.g. network, wifi, svc") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTitle.isNotBlank() && newCommand.isNotBlank()) {
                        val labelList = newLabels.split(",").map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf("custom") }
                        if (editing != null) {
                            viewModel.updateCustom(editing.copy(title = newTitle, command = newCommand, description = newDescription, labels = labelList))
                        } else {
                            viewModel.addCustom(CommandExample("custom_${System.currentTimeMillis()}", newTitle, newCommand, newDescription, labelList, isCustom = true))
                        }
                        newTitle = ""; newCommand = ""; newDescription = ""; newLabels = ""
                        showAddDialog = false; showEditDialog = null
                    }
                }) { Text(if (editing != null) "Save" else "Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false; showEditDialog = null; newTitle = ""; newCommand = ""; newDescription = ""; newLabels = "" }) { Text("Cancel") } }
        )
    }

    // Delete confirm dialog
    showDeleteConfirm?.let { cmd ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text("Delete Command?") },
            text = { Text("\"${cmd.title}\" will be permanently removed from your custom commands.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCustom(cmd.id)
                    showDeleteConfirm = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") } }
        )
    }

    // Load defaults dialog
    if (showLoadDefaultsDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDefaultsDialog = false },
            icon = { Icon(Icons.Default.Download, null) },
            title = { Text("Load Predefined Commands") },
            text = { Text("This will reload all ${PRELOADED_COMMANDS.size} built-in commands. Your custom commands will not be affected.") },
            confirmButton = { TextButton(onClick = { showLoadDefaultsDialog = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showLoadDefaultsDialog = false }) { Text("Cancel") } }
        )
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filter by Label", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (selectedLabels.isNotEmpty()) {
                        TextButton(onClick = { selectedLabels = emptySet() }) { Text("Clear") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allLabels.forEach { label ->
                        FilterChip(
                            selected = label in selectedLabels,
                            onClick = {
                                selectedLabels = if (label in selectedLabels) selectedLabels - label else selectedLabels + label
                            },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            ACCTopBar(
                title = "Command Library",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        BadgedBox(badge = { if (selectedLabels.isNotEmpty()) Badge { Text("${selectedLabels.size}") } }) {
                            Icon(Icons.Default.FilterList, "Filter by label")
                        }
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
                        DropdownMenu(showSortMenu, { showSortMenu = false }) {
                            listOf("Default", "A–Z", "Z–A", "Favorites first", "Custom first").forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    leadingIcon = { if (sortMode == m) Icon(Icons.Default.Check, null) },
                                    onClick = { sortMode = m; showSortMenu = false }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showLoadDefaultsDialog = true }) { Icon(Icons.Default.Refresh, "Load predefined commands") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { newTitle = ""; newCommand = ""; newDescription = ""; newLabels = ""; showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add custom command")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                search, { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search ${commands.size} commands…") },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp)) }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            // Active filter chips
            AnimatedVisibility(visible = selectedLabels.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    items(selectedLabels.toList()) { label ->
                        InputChip(
                            selected = true, onClick = { selectedLabels = selectedLabels - label },
                            label = { Text(label, fontSize = 11.sp) },
                            trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
            }

            // Result count + sort indicator
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${filtered.size} of ${commands.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (sortMode != "Default") {
                    Text("Sorted: $sortMode", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Empty state
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No commands found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (search.isNotEmpty() || selectedLabels.isNotEmpty()) {
                            TextButton(onClick = { search = ""; selectedLabels = emptySet() }) { Text("Clear filters") }
                        }
                    }
                }
                return@Scaffold
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp, top = 4.dp)) {
                items(filtered, key = { it.id }) { cmd ->
                    CommandExampleCard(
                        cmd = cmd,
                        onSelect = { onCommandSelected(cmd.command); onBack() },
                        onFavoriteToggle = { viewModel.toggleFavorite(cmd.id) },
                        onEdit = if (cmd.isCustom) ({ showEditDialog = cmd; newTitle = cmd.title; newCommand = cmd.command; newDescription = cmd.description; newLabels = cmd.labels.joinToString(", ") }) else null,
                        onDelete = if (cmd.isCustom) ({ showDeleteConfirm = cmd }) else null,
                        onLabelClick = { label -> selectedLabels = if (label in selectedLabels) selectedLabels - label else selectedLabels + label }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandExampleCard(
    cmd: CommandExample,
    onSelect: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onLabelClick: (String) -> Unit,
) {
    var showCardMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    ElevatedCard(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clickable { onSelect() }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(cmd.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (cmd.isCustom) Text("CUSTOM", fontSize = 8.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                    if (cmd.description.isNotBlank()) {
                        Text(cmd.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                // Copy command to clipboard
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(cmd.command)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy command", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (cmd.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        null, Modifier.size(18.dp),
                        tint = if (cmd.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onEdit != null || onDelete != null) {
                    Box {
                        IconButton(onClick = { showCardMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp))
                        }
                        DropdownMenu(showCardMenu, { showCardMenu = false }) {
                            onEdit?.let { DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { showCardMenu = false; it() }) }
                            onDelete?.let { DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showCardMenu = false; it() }) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // Command text block — tap card to paste into shell, copy icon to clipboard
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    cmd.command,
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (cmd.labels.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(cmd.labels) { label ->
                        SuggestionChip(onClick = { onLabelClick(label) }, label = { Text(label, fontSize = 9.sp) }, modifier = Modifier.height(22.dp))
                    }
                }
            }
        }
    }
}
