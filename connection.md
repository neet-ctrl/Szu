Here's the complete guide — every step, every command, nothing skipped:

📶 Wi-Fi ADB — Phone to Phone (Wireless, no cable)
What you need
Both phones on the same Wi-Fi network (or one is a hotspot the other joins)
TARGET phone must be Android 11 or newer
HOST phone: running ACCU
On the TARGET phone (phone you want to control):
Step 1 — Unlock Developer Options

Settings → About phone → Build number
Tap "Build number" 7 times rapidly
→ "You are now a developer!"
Samsung: Settings → About phone → Software information → Build number
MIUI/HyperOS: Settings → About phone → tap MIUI version 7×

Step 2 — Enable Wireless Debugging

Settings → Developer Options → Wireless debugging → toggle ON
Step 3 — Get the 6-digit pairing code

Settings → Developer Options → Wireless debugging
→ tap "Pair device with pairing code"
Screen shows:
  Wi-Fi pairing code:   1 2 3 4 5 6       ← write this down
  IP address & Port:    192.168.1.42:37839  ← pairing port (temporary)
Main Wireless debugging page also shows:
  IP address & Port:    192.168.1.42:41547  ← session port (for connecting)
ACCU discovers both ports automatically via mDNS.
You only ever need the 6-digit code.
On the HOST phone (the one running ACCU):
Step 4 — Open ACCU Center

ACCU → bottom navigation bar → ACCU Center (shield icon)
→ Status card shows: "Not Connected"
→ Three buttons visible: [Wireless ADB] [OTG / USB] [Use Root]
Step 5 — Tap "Wireless ADB"

Tap the "Wireless ADB" button
→ ACCU starts mDNS auto-discovery (_adb-tls-pairing._tcp + _adb-tls-connect._tcp)
→ When target is found, ACCU shows the pairing code input
   (or a notification: "Wireless Debugging Detected — Open ACCU")
Step 6 — Enter the 6-digit code

ACCU Center → type the 6-digit code → tap Confirm
ACCU runs automatically (no manual typing needed):
  adb pair 192.168.1.42:37839 123456
  → "Successfully paired to 192.168.1.42:37839 [guid=...]"
  adb connect 192.168.1.42:41547
  → "connected to 192.168.1.42:41547"
Status card turns green:
  "ACCU Connected · Wireless ADB (192.168.1.42) · uid=2000"
Done. Every screen in ACCU now runs on the target phone automatically.

Verify the connection in ACCU Shell:
id
# → uid=2000(shell) gid=2000(shell)
getprop ro.product.model
# → [Galaxy S24] or whatever the TARGET model is
pm list packages -3
# → lists all user-installed apps on TARGET phone
dumpsys battery
# → TARGET battery status
Reconnect after phone restart:
ACCU Center → tap "Restart"
→ ACCU runs: adb connect 192.168.1.42:41547
→ Reconnects in ~1 second
If session port changed (rare):
→ tap "Wireless ADB" again → mDNS rediscovers new port → enter code once more
🔌 OTG ADB — Phone to Phone via USB Cable (no Wi-Fi)
What you need
HOST phone (running ACCU): must support USB OTG host mode
TARGET phone (to be controlled): any Android phone
Cable (pick one):
USB-C OTG adapter + USB-C cable
USB-C to USB-C OTG cable (check it says "OTG/host")
Micro-USB OTG adapter + cable (older phones)
Test OTG support: plug a USB mouse into HOST — if cursor appears, OTG works ✓

On the TARGET phone:
Step 1 — Unlock Developer Options (same as above, tap Build number 7×)

Step 2 — Enable USB Debugging

Settings → Developer Options → USB debugging → toggle ON
Recommended extras:
  Install via USB → ON
  USB debugging (Security settings) → ON   [Samsung/MIUI extra]
Connect the cables:
Step 3 — Plug the phones together

HOST phone
  └─[USB-C OTG adapter]
       └─[USB-C cable]
            └─ TARGET phone
OR: direct USB-C to USB-C OTG cable between both phones
If using USB-A OTG adapter: OTG adapter goes in HOST, cable goes to TARGET

In ACCU on the HOST phone:
Step 4 — Open ACCU Center and tap "OTG / USB"

ACCU → ACCU Center (shield icon in bottom nav)
→ tap "OTG / USB" button
ACCU runs: adb devices
→ Checks for USB-connected device
Step 5 — Approve on the TARGET phone

[TARGET phone shows popup]:
  "Allow USB debugging?"
  RSA key fingerprint: AB:CD:EF:12:34...
  ☑ Always allow from this computer   ← tick this!
  → tap [Allow]
If the popup doesn't appear: swipe down notification shade on TARGET → look for "USB preference" → change from "Charging only" to "File Transfer" → go back to ACCU and tap "OTG / USB" again

Step 6 — Connected

ACCU Center status card:
  "ACCU Connected · OTG / USB ADB · uid=2000"
All 85+ screens now control the TARGET phone.
Verify in ACCU Shell:
id
# → uid=2000(shell) gid=2000(shell)
adb shell getprop ro.product.model
# → TARGET phone model
adb devices
# → shows: <serial_number>  device
adb shell pm list packages -3
# → all user apps on TARGET
adb shell screencap -p /sdcard/screen.png
adb pull /sdcard/screen.png
# → captures and pulls TARGET screenshot
Bonus: Switch from OTG to wireless (remove the cable)
Once OTG is working, run these commands in ACCU Shell:

# Step A: switch TARGET to TCP mode
adb tcpip 5555
# → "restarting in TCP mode port: 5555"
# Step B: get TARGET's IP
adb shell ip route | awk '{print $9}'
# → e.g. 192.168.1.42
# Step C: unplug the OTG cable
# Step D: connect wirelessly

adb connect 192.168.1.42:5555
# → "connected to 192.168.1.42:5555"

adb devices
# → 192.168.1.42:5555  device

Disconnect when done:
ACCU Center → tap "Stop"
→ session cleared, status card goes back to "Not Connected"

All of this is now written into every doc file (README.md, HOW_TO_TEST.md) and the in-app ADB Tutorial screen (ACCU → Shell → Tutorial tab) with the exact same steps, so users can reference it directly inside the app.