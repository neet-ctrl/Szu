# ADB Connection Guide — Android Control Center Ultimate (ACCU)

> **What is ADB?**  
> Android Debug Bridge (ADB) is a command-line tool that lets one device control another Android device
> over USB or Wi-Fi. ACCU uses ADB to provide every feature in this list — from shell commands and file
> management to package control, logcat, and screen recording — without requiring a PC.

---

## Table of Contents

1. [Wi-Fi ADB (Android 11+ Wireless Debugging)](#wi-fi-adb-android-11)
2. [Wi-Fi ADB (Legacy — adb tcpip)](#wi-fi-adb-legacy)
3. [OTG ADB (Phone-to-Phone via USB Cable)](#otg-adb-phone-to-phone)
4. [Local ADB via Shizuku (no cable needed)](#local-adb-via-shizuku)
5. [All ADB Features in ACCU](#all-adb-features-in-accu)
6. [Troubleshooting](#troubleshooting)
7. [Complete ADB Command Reference](#complete-adb-command-reference)

---

## Wi-Fi ADB (Android 11+)

Wireless Debugging is a built-in Android 11+ feature that lets you pair and connect over Wi-Fi
without a USB cable.

### Requirements
- Both phones on the **same Wi-Fi network**
- Target device: Android 11 (API 30) or higher
- Host device: ACCU installed

### Step-by-step

**1. Enable Developer Options on target device**
```
Settings → About phone → Build number
Tap 7 times → "You are now a developer!"

Then:
Settings → Developer Options
```

**2. Enable Wireless Debugging**
```
Settings → Developer Options → Wireless debugging → ON
```
> Note the IP address and port shown on this screen — you'll need them.

**3. Pair with pairing code**
```
On target: Wireless debugging → "Pair device with pairing code"
Note the 6-digit code and the PAIRING port

In ACCU Shell (Wi-Fi tab):
  adb pair <IP>:<PAIRING_PORT> <6digit_code>

Example:
  adb pair 192.168.1.42:37839 123456
  → Successfully paired to 192.168.1.42:37839
```

> ⚠️ The **pairing port** is different from the **connection port**. Use the pairing port only during
> the `adb pair` step. Use the connection port (shown on the Wireless Debugging main page) for `adb connect`.

**4. Connect**
```
adb connect <IP>:<CONNECTION_PORT>

Example:
  adb connect 192.168.1.42:41547
  → connected to 192.168.1.42:41547

Verify:
  adb devices
  → 192.168.1.42:41547   device
```

**5. Pair with QR code (alternative)**
```
On target: Wireless debugging → "Pair device with QR code"
In ACCU: Shizuku → ADB Pairing → QR mode → Scan QR
```

**6. Disconnect**
```
adb disconnect 192.168.1.42:41547   # Specific device
adb disconnect                       # All devices
```

---

## Wi-Fi ADB (Legacy)

For Android 10 and below — requires a USB connection first.

```bash
# Step 1: USB debug the target device (via OTG or PC)
adb devices
→ <serial>   device

# Step 2: Switch to TCP mode
adb tcpip 5555

# Step 3: Get target IP
adb shell ip route | awk '{print $9}'

# Step 4: Disconnect USB, then connect wirelessly
adb connect <TARGET_IP>:5555

# Step 5: Verify
adb shell getprop ro.product.model
```

> 💡 Port 5555 is the default. You can use any port above 1024, but 5555 is universally understood.

---

## OTG ADB (Phone-to-Phone)

Connect one Android phone (HOST running ACCU) to another (TARGET) via a USB OTG cable.

### Hardware Requirements

| Item | What you need |
|------|--------------|
| HOST phone | Supports USB OTG **host** mode |
| TARGET phone | Any Android phone with USB debugging enabled |
| Cable option A | USB-C OTG adapter on HOST + USB-C cable to TARGET |
| Cable option B | USB-A OTG adapter on HOST + USB-A to C cable to TARGET |
| Cable option C | USB-C to USB-C OTG cable (check labeling) |

> ⚠️ A standard USB-C charge cable will NOT work. You need one with data + OTG host capability.

### Step-by-step

**1. Enable USB Debugging on TARGET**
```
Settings → About phone → Build number   (tap 7 times)
Settings → Developer Options → USB debugging → ON
```

**2. Connect phones via OTG cable**
```
HOST ←[ OTG adapter ]←[ USB cable ]→ TARGET
```

**3. Approve debug connection on TARGET**
```
[Prompt appears on TARGET screen]
"Allow USB debugging? RSA fingerprint: AB:CD:..."
☑ Always allow from this computer
→ Tap [Allow]

If no prompt appears:
  • Pull down target notification shade
  • Change USB mode from "Charging" to "File Transfer"
  • Disconnect and reconnect
```

**4. Open ACCU Shell in OTG mode**
```
ACCU → Shell → OTG ADB tab

Verify:
  adb devices
  → <serial>   device
```

**5. Run commands**
```bash
adb shell getprop ro.product.model     # Target model
adb shell pm list packages -3          # User-installed apps
adb shell screencap -p /sdcard/ss.png  # Screenshot
adb pull /sdcard/ss.png               # Pull to host
adb install app.apk                    # Install on target
adb shell pm uninstall --user 0 com.bloat.app  # Debloat
```

**6. Bridge to Wireless (remove the cable)**
```bash
# While OTG connected:
adb tcpip 5555
adb shell ip route | awk '{print $9}'   # Get target IP

# Unplug cable, then:
adb connect <TARGET_IP>:5555
adb devices
→ <TARGET_IP>:5555   device
```

---

## Local ADB via Shizuku

Run ADB-level commands **without any cable or second device** using Shizuku's local ADB bridge.

```
ACCU → Shizuku Center → Start Shizuku
Then:
ACCU → Shell → Local ADB tab
```

Shizuku grants the same permissions as `adb shell` by running an ADB daemon locally. All shell
commands work identically.

---

## All ADB Features in ACCU

### ADB Shell — Interactive Terminal

**Screen:** ACCU → Shell

```
Features:
  ✅ Local ADB (Shizuku), Wi-Fi ADB, OTG ADB modes
  ✅ Interactive command input with send button
  ✅ Command history (↑/↓ navigation)
  ✅ Bookmarks (save frequent commands)
  ✅ AI-powered command suggestions
  ✅ Output search (search within terminal output)
  ✅ Save output to file
  ✅ Copy entire output to clipboard
  ✅ Tab completion
  ✅ Ctrl+C interrupt signal
  ✅ 200+ preloaded command examples
  ✅ Script editor & runner
```

### App Management

**Screen:** ACCU → App Manager

```bash
adb shell pm list packages -3               # View user apps
adb shell pm list packages -s               # View system apps
adb shell pm uninstall --user 0 <pkg>       # Uninstall/remove
adb shell pm disable-user --user 0 <pkg>    # Disable app
adb shell pm enable <pkg>                   # Re-enable
adb shell pm clear <pkg>                    # Clear data + cache
adb shell am force-stop <pkg>               # Force stop
adb shell monkey -p <pkg> -c android.intent.category.LAUNCHER 1  # Launch
adb shell pm path <pkg>                     # Get APK path
adb pull <apk_path>                         # Extract APK
```

### Package Information

**Screen:** ACCU → App Manager → App Detail

```bash
adb shell dumpsys package <pkg>             # Full package dump
adb shell pm dump <pkg> | grep versionName  # Version
adb shell pm dump <pkg> | grep firstInstallTime  # Install date
adb shell pm dump <pkg> | grep lastUpdateTime    # Update date
adb shell pm list permissions -g <pkg>      # Permissions
adb shell dumpsys package <pkg> | grep -A5 "Activities:"  # Components
adb shell getprop | grep <pkg>              # Related props
```

### File Management

**Screen:** ACCU → Shell → File Browser (folder icon when connected)

```bash
adb shell ls -la /sdcard/                   # Browse files
adb shell ls -la /sdcard/Android/data/      # App private files
adb push local_file.txt /sdcard/            # Upload to device
adb pull /sdcard/remote.txt                 # Download from device
adb shell rm /sdcard/file.txt               # Delete
adb shell mkdir /sdcard/new_folder          # Create folder
adb shell mv /sdcard/a.txt /sdcard/b.txt    # Rename/move
adb shell cp /sdcard/src.txt /sdcard/dst.txt  # Copy
```

### Process Manager

**Screen:** ACCU → Shell → Processes

```bash
adb shell ps -A                             # All processes
adb shell ps -A | grep <name>              # Find by name
adb shell top -b -n1                        # CPU/RAM usage snapshot
adb shell kill <PID>                        # Kill by PID
adb shell am kill <pkg>                     # Kill app process
adb shell dumpsys activity services         # Running services
adb shell dumpsys activity running          # Active processes
```

### Logcat

**Screen:** ACCU → Shell → Logcat

```bash
adb logcat                                  # All logs (streaming)
adb logcat -v time                          # With timestamps
adb logcat -v threadtime                    # Thread + timestamps
adb logcat ActivityManager:I *:S            # Filter: tag:level
adb logcat | grep -i "error"               # Filter by keyword
adb logcat *:E                              # Errors only
adb logcat -d > log.txt                     # Dump & save
adb logcat -c                               # Clear buffer
adb logcat --pid=<PID>                      # Specific process
```

Log levels: **V**erbose · **D**ebug · **I**nfo · **W**arning · **E**rror · **F**atal

### Screenshots

**Screen:** ACCU → Shell → Screen Capture

```bash
# Capture
adb shell screencap -p /sdcard/screenshot.png

# Pull to host
adb pull /sdcard/screenshot.png

# Clean up
adb shell rm /sdcard/screenshot.png

# One-liner
adb shell screencap -p /sdcard/ss.png && adb pull /sdcard/ss.png && adb shell rm /sdcard/ss.png
```

### Screen Recording

**Screen:** ACCU → Shell → Screen Capture → Recording tab

```bash
# Basic recording (Ctrl+C to stop, max 180s)
adb shell screenrecord /sdcard/record.mp4

# Custom options
adb shell screenrecord \
  --size 1280x720 \
  --bit-rate 4000000 \
  --time-limit 60 \
  /sdcard/record.mp4

# Pull after stopping
adb pull /sdcard/record.mp4

# Clean up
adb shell rm /sdcard/record.mp4
```

> ℹ️ `screenrecord` saves H.264 video in MP4 container. Max duration: 180 seconds.

### Device Information

**Screen:** ACCU → Shell → Device Info

```bash
# Identity
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.product.brand
adb shell getprop ro.serialno

# Android version
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.id
adb shell getprop ro.build.fingerprint

# CPU
adb shell cat /proc/cpuinfo
adb shell getprop ro.product.cpu.abi

# Memory
adb shell cat /proc/meminfo
adb shell free -h

# Display
adb shell wm size
adb shell wm density

# Battery
adb shell dumpsys battery

# Serial
adb get-serialno
```

### Wireless ADB

**Screen:** ACCU → Shell (Wi-Fi tab) / Shizuku → ADB Pairing / Wi-Fi ADB mDNS

```bash
# Android 11+ — pair
adb pair <IP>:<PAIR_PORT> <6DIGIT_CODE>

# Connect
adb connect <IP>:<CONN_PORT>

# Legacy (adb tcpip)
adb tcpip 5555
adb connect <IP>:5555

# Disconnect
adb disconnect <IP>:<PORT>

# List connected
adb devices
```

### Fastboot

**Screen:** ACCU → Shell → Fastboot

```bash
# Reboot commands
adb reboot                     # Normal reboot
adb reboot bootloader          # Enter fastboot/bootloader
adb reboot recovery            # Enter recovery
adb reboot download            # Samsung Odin / download mode
adb reboot sideload            # Stock recovery sideload

# In fastboot mode
fastboot devices               # List connected devices
fastboot reboot                # Exit fastboot
fastboot getvar all            # All fastboot variables
fastboot oem device-info       # OEM info (lock state, etc.)

# Flash operations (⚠️ dangerous)
fastboot flash recovery twrp.img
fastboot flash boot boot.img
fastboot erase cache
fastboot flashing unlock       # ⚠️ Wipes device!
fastboot flashing lock

# Sideload OTA
adb sideload update.zip
```

### Developer Tools

**Screen:** ACCU → Shell

```bash
# System properties
adb shell getprop               # All props
adb shell setprop <key> <value> # Set prop (needs root)
adb shell getprop | grep <key>  # Search props

# Settings database
adb shell settings list global
adb shell settings list secure
adb shell settings list system
adb shell settings get global <key>
adb shell settings put global <key> <value>

# Debug apps
adb shell am set-debug-app <pkg>
adb shell am clear-debug-app
adb logcat --pid=$(adb shell pidof -s <pkg>)  # App-specific logcat

# Execute shell scripts
adb shell < script.sh
adb push script.sh /sdcard/ && adb shell sh /sdcard/script.sh
```

### Settings Database Access

```bash
# Read settings
adb shell settings get global airplane_mode_on
adb shell settings get secure install_non_market_apps
adb shell settings get system screen_off_timeout

# Modify settings
adb shell settings put global airplane_mode_on 0
adb shell settings put secure install_non_market_apps 1
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
```

### Permission Operations

**Screen:** ACCU → Permission Manager / ACCU System Service

```bash
# Grant permission
adb shell pm grant <pkg> android.permission.READ_CONTACTS
adb shell pm grant <pkg> android.permission.CAMERA

# Revoke permission
adb shell pm revoke <pkg> android.permission.CAMERA

# Check permissions
adb shell dumpsys package <pkg> | grep "permission"

# Grant INSTALL_PACKAGES to ACCU
adb shell pm grant com.accu.controlcenter android.permission.INSTALL_PACKAGES
```

### Activity Manager Commands

```bash
# Start activities
adb shell am start -n com.pkg/.MainActivity
adb shell am start -a android.intent.action.VIEW -d "https://example.com"
adb shell am start -n com.pkg/.HiddenActivity   # Hidden/internal activities

# Broadcast intents
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE

# Send intents
adb shell am start -a android.intent.action.SEND -t text/plain --es "android.intent.extra.TEXT" "Hello"

# Force stop
adb shell am force-stop <pkg>

# Kill background
adb shell am kill-all

# Start service
adb shell am startservice -n com.pkg/.MyService
```

### Package Manager Commands

```bash
# Install
adb install app.apk
adb install -r app.apk           # Reinstall (keep data)
adb install -d app.apk           # Allow downgrade
adb install-multiple base.apk split.apk  # Split APKs

# Uninstall
adb uninstall <pkg>
adb shell pm uninstall --user 0 <pkg>  # Remove for user (keeps system)

# List
adb shell pm list packages
adb shell pm list packages -3    # User apps only
adb shell pm list packages -s    # System apps only
adb shell pm list packages -e    # Enabled apps
adb shell pm list packages -d    # Disabled apps

# Inspect
adb shell pm dump <pkg>          # Full package info
adb shell dumpsys package <pkg>  # Alternative dump
```

### Device Control Commands

```bash
# Reboot
adb reboot
adb reboot bootloader
adb reboot recovery

# Screen
adb shell input keyevent 26      # Power (lock/wake toggle)
adb shell input keyevent 224     # KEYCODE_WAKEUP (wake)
adb shell input keyevent 223     # KEYCODE_SLEEP

# Key simulation
adb shell input keyevent 3       # HOME
adb shell input keyevent 4       # BACK
adb shell input keyevent 187     # RECENTS
adb shell input keyevent 24      # VOLUME_UP
adb shell input keyevent 25      # VOLUME_DOWN

# Touch simulation
adb shell input tap 540 960      # Tap at x=540, y=960
adb shell input swipe 200 800 200 300 300  # Swipe (start_x start_y end_x end_y ms)
adb shell input text "hello"     # Type text
adb shell input keyevent --longpress 3  # Long-press HOME
```

### Networking

```bash
# Network info
adb shell ip addr show           # All interfaces + IPs
adb shell ip route               # Routing table
adb shell ifconfig wlan0         # Wi-Fi interface details
adb shell netstat -tuln          # Open ports
adb shell dumpsys wifi           # Wi-Fi details

# ADB over TCP/IP
adb tcpip 5555                   # Enable TCP mode
adb connect <IP>:5555            # Connect via TCP

# DNS / connectivity
adb shell nslookup example.com   # DNS lookup
adb shell ping 8.8.8.8 -c 4     # Ping test
adb shell curl https://example.com  # HTTP request (if curl available)
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `no devices/emulators found` | USB debugging ON? Charge-only cable? Try different cable |
| `unauthorized` | Check target screen for RSA dialog — tap Allow |
| `Connection refused` (Wi-Fi) | Same Wi-Fi network? VPN disabled? Check port number |
| `adb tcpip 5555` fails | Device must be detected via USB first |
| Pairing code doesn't work | Code expires in ~60s — generate new one. Use PAIRING port, not connection port |
| Recording stops immediately | Reduce resolution or bitrate. Storage full? |
| Permission denied | Some commands need Shizuku / root. Use ACCU System Service for elevated access |
| OTG not detected | Host phone must support USB OTG host mode. Use OTG adapter, not regular cable |

---

## Power-User Tricks

```bash
# Disable all animations (makes phone feel faster)
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

# Remove bloatware permanently (for current user)
adb shell pm uninstall --user 0 com.facebook.appmanager
adb shell pm uninstall --user 0 com.google.android.apps.tachyon

# Enable hidden developer features
adb shell settings put global development_settings_enabled 1

# Allow sideloading
adb shell settings put secure install_non_market_apps 1

# Enable USB debugging programmatically
adb shell settings put global adb_enabled 1

# Dump all installed package names and versions
adb shell pm list packages -3 | cut -d: -f2 | while read pkg; do
  version=$(adb shell dumpsys package "$pkg" | grep versionName | head -1 | tr -d ' ')
  echo "$pkg  $version"
done

# Backup app (ADB backup — limited on Android 12+)
adb backup -apk -noshared -all -f backup.ab

# Access Android/data files (requires ADB, not available in file manager)
adb pull /sdcard/Android/data/com.example.app/files/

# Run automation script on device
adb push myscript.sh /sdcard/
adb shell chmod +x /sdcard/myscript.sh
adb shell sh /sdcard/myscript.sh
```

---

## Connection Mode Summary

| Mode | Requirements | Best For |
|------|-------------|----------|
| **Local (Shizuku)** | Shizuku running | Daily use, no cables |
| **Wi-Fi ADB (11+)** | Same Wi-Fi, Android 11+ | Wireless remote control |
| **Wi-Fi ADB (legacy)** | USB first to set TCP, then wireless | Older devices |
| **OTG ADB** | USB OTG cable, OTG host support | Maximum speed, air-gapped |

---

*Generated by ACCU — Android Control Center Ultimate*  
*All features accessible from ACCU → Shell and its sub-screens*
