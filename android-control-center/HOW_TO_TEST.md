# How to Test Every Feature — Android Control Center Ultimate

> This guide tells you **exactly what to tap, what to expect, and what the real result is** for every feature across all 17 source repos. All features use real Shizuku/ADB commands — zero placeholders.

---

## ⚠️ Setup Requirements First

Before testing ANY feature:

| Requirement | How to enable |
|-------------|---------------|
| Shizuku running | Open app → Shizuku Center → Start via Wireless ADB or root |
| ADB debugging | Settings → Developer Options → USB/Wireless debugging ON |
| Android 10+ | minSdk is 29 — required |
| Developer Options | Settings → About phone → tap Build Number 7 times |

---

## 📋 Quick Test Table

| Feature | Where | What happens | Real result |
|---------|-------|-------------|-------------|
| Shell terminal | Shell tab | Type `pm list packages` | Lists ALL installed packages live |
| Freeze an app | App Manager → Freeze | Tap freeze on any app | App suspended via `pm suspend` — can't be launched |
| Grant permission | App Explorer → any app → Permissions | Toggle dangerous permission switch | `pm grant <pkg> <perm>` fires — switch turns green |
| Run shell cmd | App Explorer → any app → Commands | Tap ▶ on "Force Stop" | `am force-stop <pkg>` output shown in terminal |
| Debloat | App Manager → Debloat | Pick any system app, tap Remove | `pm uninstall --user 0 <pkg>` runs |
| Dark mode per app | Customization → Per-App Dark Mode | Toggle switch on any app | Overlay applied via Shizuku WRITE_SECURE_SETTINGS |
| Custom theme | Customization | Pick "Neon Matrix" preset → Apply | Entire app color palette changes |
| QS Tiles | Network Center | Tap "Add Wi-Fi Tile" | Tile added to Quick Settings panel |

---

## 1. 💻 Shell Terminal (aShellYou replica)

**Where:** Shell tab (bottom navigation)

### Test 1.1 — Basic command execution
1. Tap the Shell tab
2. Type: `pm list packages -3` (list user-installed apps only)
3. Tap Send ▶
4. **Expected:** Live scrollable list of all user package names

### Test 1.2 — Command history
1. Run any command
2. Tap the ↑ arrow (history button)
3. **Expected:** Previous command fills the input field

### Test 1.3 — Bookmarks / Favorites
1. Type any command → long-press Send → "Save as favorite"
2. Tap ★ (bookmarks button)
3. **Expected:** Saved command appears in list, tap to re-run

### Test 1.4 — Autocomplete
1. Start typing `pm ` (with space)
2. **Expected:** Suggestion chips appear (grant, revoke, list, etc.)
3. Tap a suggestion → command completes

### Test 1.5 — Output copy
1. Run any command
2. Long-press on the output text
3. **Expected:** System copy menu appears, output is selectable

### Test 1.6 — Clear terminal
1. Tap ✕ or "Clear" button in top bar
2. **Expected:** Output history cleared, input reset

---

## 2. 🗑️ Debloat / Canta

**Where:** App Manager tab → "Debloat" chip

### Test 2.1 — View system apps with safety ratings
1. Open Debloat screen
2. **Expected:** List of system apps with color badges (🟢 Safe / 🟡 Caution / 🔴 Dangerous)

### Test 2.2 — Remove a safe bloatware app
1. Find any app marked 🟢 Safe (e.g. "com.android.calculator2")
2. Tap the app → tap "Remove for user"
3. **Expected:** `pm uninstall --user 0 com.android.calculator2` runs, app disappears from launcher

### Test 2.3 — Restore a removed app
1. Tap the "Removed" filter chip
2. Find the app you removed
3. Tap "Restore"
4. **Expected:** `pm install-existing com.android.calculator2` runs, app returns

### Test 2.4 — Apply Preset (Canta community list)
1. Tap top-right menu → "Community Presets"
2. Select "Google Bloatware" preset
3. Tap "Apply Preset"
4. **Expected:** All selected apps queued for batch removal

### Test 2.5 — View operation logs
1. After any remove/restore action, tap "Logs" button
2. **Expected:** Timestamped list of every pm command that ran and its result

---

## 3. ❄️ Freeze Apps (Hail)

**Where:** App Manager tab → "Freeze" chip

### Test 3.1 — Freeze an app
1. Open Freeze Apps screen
2. Find any user app (e.g. a social media app)
3. Tap the ❄ Freeze button
4. **Expected:** `pm suspend --user 0 <pkg>` runs, app shows ❄ badge, is unlaunchable

### Test 3.2 — Unfreeze an app
1. Tap any frozen app (shows ❄ badge)
2. Tap "Unfreeze"
3. **Expected:** `pm unsuspend --user 0 <pkg>` runs, app launches normally again

### Test 3.3 — Auto-freeze on screen off
1. Go to Work Profile Settings
2. Enable "Auto-freeze on screen off"
3. Press power button
4. **Expected:** All tagged apps freeze within 1 second of screen off (ScreenOffReceiver fires)

### Test 3.4 — Freeze All Quick Settings tile
1. Pull down notification shade
2. Find "Freeze All" tile (add via QS edit if not there)
3. Tap tile
4. **Expected:** All apps in freeze list suspended instantly

### Test 3.5 — Freeze groups/tags
1. Long-press an app → "Add to group"
2. Create group "Games"
3. Tap group → "Freeze Group"
4. **Expected:** All apps in group frozen with one tap

---

## 4. 🔒 Permission Manager (Inure/Blocker)

**Where:** App Manager tab → "Permissions" chip

### Test 4.1 — View all permissions across all apps
1. Open Permission Manager
2. **Expected:** All apps listed with their dangerous permission count

### Test 4.2 — Revoke a dangerous permission
1. Find an app with location permission
2. Tap app → tap location permission row
3. Toggle switch OFF
4. **Expected:** `pm revoke <pkg> android.permission.ACCESS_FINE_LOCATION` runs — switch turns red

### Test 4.3 — Grant a permission
1. Find app with a revoked permission
2. Toggle the switch ON
3. **Expected:** `pm grant <pkg> <permission>` runs — switch turns green immediately

---

## 5. 🧩 App Explorer (New Flagship Feature)

**Where:** App Manager tab → "Explorer" chip

### Test 5.1 — Filter user vs system apps
1. Open App Explorer
2. Tap "User" filter chip
3. **Expected:** Only user-installed apps shown (no system)
4. Tap "System"
5. **Expected:** Only system apps shown

### Test 5.2 — Expand an app card
1. Tap any app card
2. **Expected:** 3 collapsible sections appear: Permissions, Shell Commands, Technical Details

### Test 5.3 — Toggle a dangerous permission directly
1. Expand any app card
2. Tap "Permissions" section
3. Find any DANGEROUS permission with a red switch
4. Toggle the switch
5. **Expected:** Switch state changes instantly, snackbar shows "Granted: CAMERA" or "Revoked: CAMERA"

### Test 5.4 — Run a shell command per app
1. Expand any app → tap "Shell Commands"
2. Tap ▶ on "Memory Stats"
3. **Expected:** Dark green terminal output shows dumpsys meminfo for that app

### Test 5.5 — Copy command output
1. After running a command (see 5.4)
2. Tap 📋 copy icon next to the output
3. **Expected:** Output copied to clipboard (paste anywhere to verify)

### Test 5.6 — View technical details
1. Expand any app → "Technical Details" section
2. **Expected:** Package name, version code, target SDK, APK path, data dir all shown
3. Tap copy icon next to Package Name
4. **Expected:** Package name copied to clipboard

### Test 5.7 — Dangerous commands
1. Expand any app → Shell Commands
2. Enable the "Dangerous" toggle
3. **Expected:** 7 additional dangerous commands appear (Clear Data, Disable, Uninstall, etc.)
4. Tap ▶ on "Clear Cache"
5. **Expected:** `pm clear --cache-only <pkg>` runs, output: "Success"

---

## 6. 📦 Component Manager (Blocker)

**Where:** App Manager tab → Components

### Test 6.1 — View activities/services/receivers
1. Open Component Manager
2. Tap any app
3. **Expected:** Tabbed list — Activities, Services, Receivers, Providers

### Test 6.2 — Block a tracker component
1. Find any app with "analytics" or "tracking" in a component name
2. Tap the component → "Block via IFW"
3. **Expected:** Intent Firewall rule written via Shizuku, component blocked

### Test 6.3 — Online rules (Blocker signatures)
1. Go to Online Rules screen
2. **Expected:** 8000+ tracker signatures listed with categories
3. Tap "Block All Trackers"
4. **Expected:** IFW rules applied for all matched components

---

## 7. 🎵 Audio DSP (RootlessJamesDSP)

**Where:** Audio Center tab

### Test 7.1 — Enable DSP
1. Open Audio Center
2. Toggle "Enable Audio DSP" ON
3. Play any audio
4. **Expected:** Audio passes through the JamesDSP engine (slight volume change may be audible)

### Test 7.2 — Parametric EQ
1. Tap "Parametric EQ"
2. **Expected:** 5-band EQ sliders appear
3. Drag 100Hz band up by +6dB
4. **Expected:** Bass noticeably louder in audio playback

### Test 7.3 — AutoEQ headphone profile
1. Tap "AutoEQ Profiles"
2. Search your headphone model
3. Tap "Apply"
4. **Expected:** All 10 EQ bands set to the headphone compensation curve

### Test 7.4 — Liveprog EEL script
1. Tap "Liveprog Editor"
2. **Expected:** Code editor opens with EEL syntax
3. Tap "Load Sample" → "Compressor"
4. Tap "Apply"
5. **Expected:** Dynamic range compression applied to audio

### Test 7.5 — Bass Boost
1. In Audio Center, slide Bass Boost to +50%
2. Play music
3. **Expected:** Bass frequencies noticeably boosted

### Test 7.6 — App audio blocklist
1. Tap "App Audio Blocklist"
2. Toggle any app to "Block DSP"
3. **Expected:** That app's audio bypasses all DSP effects

---

## 8. 📞 Call Recorder (ShizuCallRecorder)

**Where:** Call Recorder tab

### Test 8.1 — Enable recording
1. Open Call Recorder screen
2. Toggle "Enable Call Recording" ON
3. **Expected:** Service starts, recording icon appears in status bar during calls

### Test 8.2 — Make a test call
1. Call any number
2. Hang up
3. **Expected:** New recording appears in the recordings list

### Test 8.3 — Change codec
1. Go to Recording Settings
2. Select "FLAC (Lossless)"
3. **Expected:** Next recording saved as .flac file

### Test 8.4 — Filename format
1. Recording Settings → Filename Format
2. Change to `{date}_{contact}_{direction}`
3. **Expected:** New recordings use that naming pattern

---

## 9. 🌐 Better Internet Tiles

**Where:** Network Center tab → Tiles section

### Test 9.1 — Add Wi-Fi Quick Settings tile
1. Open Network Center → Tiles Settings
2. Tap "Add Wi-Fi Tile"
3. **Expected:** QS edit notification appears, tile shows in Quick Settings

### Test 9.2 — Toggle Wi-Fi from QS without popup
1. Pull down notification shade
2. Tap the Wi-Fi tile
3. **Expected:** Wi-Fi toggles ON/OFF directly (no picker dialog like stock Android)

### Test 9.3 — Mobile Data tile
1. Add Mobile Data tile (same steps as 9.1)
2. Tap it
3. **Expected:** Mobile data toggles directly via `svc data enable/disable` through Shizuku

### Test 9.4 — Hotspot tile
1. Add Hotspot tile
2. Tap it
3. **Expected:** Hotspot enabled/disabled directly

---

## 10. ⌨️ Key Mapper

**Where:** Automation tab → Key Mapper

### Test 10.1 — Remap volume down to screenshot
1. Open Key Mapper
2. Tap "+" → choose trigger "Volume Down (double tap)"
3. Choose action "Take Screenshot"
4. Tap Save
5. **Expected:** Double-tapping volume down takes a screenshot

### Test 10.2 — Shell command on key press
1. New mapping → trigger: "Volume Up long press"
2. Action: "Shell Command" → enter `am start -a android.intent.action.POWER_USAGE_SUMMARY`
3. Save
4. **Expected:** Long-pressing volume up opens battery usage screen

### Test 10.3 — Floating button trigger
1. New mapping → trigger: "Floating Button"
2. Action: "Toggle Freeze All"
3. **Expected:** Floating overlay button appears, tap freezes all apps in list

---

## 11. 🌍 Language Selector

**Where:** Language Center tab

### Test 11.1 — Change language for a single app
1. Open Language Center
2. Find any app (e.g. Chrome)
3. Tap it → select "Français" from the list
4. **Expected:** `am broadcast --user 0 -a android.intent.action.LOCALE_CHANGED` fires, Chrome opens in French

### Test 11.2 — Language QS tile
1. Add the Language QS tile
2. Tap it while in any app
3. **Expected:** Quick language picker for the current foreground app

---

## 12. 📁 File Manager (Material Files)

**Where:** File Manager tab

### Test 12.1 — Browse device storage
1. Open File Manager
2. **Expected:** Root folders shown (Download, DCIM, Android, etc.)
3. Navigate into Downloads
4. **Expected:** Files listed with name, size, date

### Test 12.2 — FTP server
1. Tap "Advanced" → "Start FTP Server"
2. **Expected:** FTP URL shown (e.g. `ftp://192.168.1.x:2121`)
3. Open any FTP client app and connect
4. **Expected:** Device files accessible over local network

### Test 12.3 — Connect to remote SMB share
1. Tap "Remote" → "Add SMB / Windows Share"
2. Enter server address, username, password
3. **Expected:** Network files appear alongside local files

### Test 12.4 — Create ZIP archive
1. Long-press any file/folder → "Compress to ZIP"
2. **Expected:** .zip file created in the same directory

---

## 13. 🎨 Customization / Theme Engine

**Where:** Customization tab (paintbrush icon)

### Test 13.1 — Apply a theme preset
1. Open Customization
2. Scroll theme preset cards
3. Tap "Neon Matrix" 💚
4. Tap "Apply Theme"
5. **Expected:** Entire app switches to green-on-black color scheme

### Test 13.2 — AMOLED mode
1. Tap "AMOLED" display mode button
2. Tap Apply
3. **Expected:** All backgrounds turn pure black (#000000) — OLED pixels off = no power drain

### Test 13.3 — Glass Morphism
1. Toggle "Glass Morphism" ON
2. **Expected:** Card backgrounds become semi-transparent with colored tint and border

### Test 13.4 — Corner radius
1. Move the Corner Radius slider to the far right
2. **Expected:** Shape preview boxes become pill-shaped; throughout app all cards become pill-shaped

### Test 13.5 — Font scale
1. Move Font Scale to 1.4×
2. **Expected:** Preview text in the settings itself grows larger live

### Test 13.6 — Animation speed
1. Set Animation Speed to "Turbo Fast"
2. Navigate between screens
3. **Expected:** Transitions visibly snappier (shorter animation duration)

### Test 13.7 — Card style
1. Change Card Style to "Outlined Border"
2. Apply
3. **Expected:** Cards throughout app show clean outlined border instead of filled background

### Test 13.8 — Seed color
1. Tap any color circle in Seed Color section
2. Tap Apply
3. **Expected:** Primary/accent colors throughout app shift to the chosen seed

### Test 13.9 — Material You style
1. Select "Vibrant" from Monet Style chips
2. Apply
3. **Expected:** Palette uses bolder, more saturated colors

---

## 14. 🧹 SD Maid SE — Storage Cleaning

**Where:** Storage tab

### Test 14.1 — Scan app cache
1. Storage tab → "App Cleaner"
2. Tap "Scan"
3. **Expected:** List of apps with cache sizes shown (e.g. "YouTube — 847 MB cache")

### Test 14.2 — Clean selected apps
1. After scan, select 2-3 apps
2. Tap "Clean Selected"
3. **Expected:** `pm clear --cache-only <pkg>` runs for each, sizes drop to 0

### Test 14.3 — System junk cleaner
1. Storage tab → "System Cleaner"
2. Tap "Scan for junk"
3. **Expected:** Temp files, log files, empty folders detected

### Test 14.4 — Find duplicate files
1. Storage tab → "Deduplicator"
2. Tap "Scan Downloads"
3. **Expected:** Identical files grouped (by content hash), duplicates highlighted

### Test 14.5 — Corpse finder
1. Storage tab → "Corpse Finder"
2. Tap "Scan"
3. **Expected:** Orphaned data dirs (apps uninstalled but data left behind) listed

---

## 15. 🌙 Per-App Dark Mode (DarQ)

**Where:** Customization → Per-App Dark Mode (or Customization tab → DarQ)

### Test 15.1 — Force dark on a specific app
1. Open Dark Mode screen
2. Toggle any app (e.g. "Instagram") to Force Dark
3. **Expected:** `cmd overlay enable-exclusive --category <overlay>` fires via Shizuku, Instagram opens with dark background even without native support

### Test 15.2 — Schedule dark mode
1. Tap "Schedule"
2. Set "Enable at Sunset, Disable at Sunrise"
3. **Expected:** Dark mode activates at sunset based on GPS location

### Test 15.3 — View FAQ
1. Tap "FAQ" button
2. **Expected:** Expandable FAQ cards explaining how DarQ works and common issues

---

## 16. 📱 Installer with Flags (InstallWithOptions)

**Where:** Installer tab

### Test 16.1 — Install APK with options
1. Open Installer
2. Tap "Browse APK"
3. Select any APK from storage
4. **Expected:** APK details shown (package name, version, permissions it requests)

### Test 16.2 — Downgrade an app
1. Select APK with lower version than installed
2. Enable flag "Allow Version Downgrade"
3. Tap Install
4. **Expected:** App installed successfully at lower version (normally blocked by Android)

### Test 16.3 — Grant all permissions on install
1. Enable flag "Grant All Declared Permissions"
2. Install any APK
3. **Expected:** All permissions auto-granted without user dialogs

---

## 17. 🔍 VirusTotal Scanner

**Where:** App Manager → VirusTotal (or search "virus" in All Features)

### Test 17.1 — Scan a single app
1. Open VirusTotal screen
2. Enter your VirusTotal API key (free at virustotal.com)
3. Tap any app → "Scan with VirusTotal"
4. **Expected:** APK hash sent to VT API, detection results returned (e.g. "0/72 engines detected")

### Test 17.2 — Batch scan
1. Tap "Scan All User Apps"
2. **Expected:** Progress bar, each app's result shown one by one

---

## 18. 📡 Wireless ADB & mDNS

**Where:** Shell tab → "Wireless ADB" button, or Network Center → Wireless ADB

### Test 18.1 — Connect via mDNS (same Wi-Fi)
1. Open Wireless ADB / mDNS screen
2. Tap "Scan for ADB devices"
3. **Expected:** Nearby devices with ADB enabled shown (requires Android 11+)

### Test 18.2 — Manual ADB pairing
1. On device, go to Developer Options → Wireless Debugging → Pair via code
2. In ACCU, enter the IP:port and pairing code
3. **Expected:** Device paired, "Connected" status shown

---

## 19. 🔵 Shizuku Center

**Where:** Shizuku tab (shield icon)

### Test 19.1 — Check Shizuku status
1. Open Shizuku Center
2. **Expected:** Green "Running" status if Shizuku is active, red "Stopped" if not

### Test 19.2 — Start Shizuku via Wireless ADB
1. Enable Wireless ADB in Developer Options
2. In Shizuku Center → tap "Start via Wireless ADB"
3. **Expected:** Shizuku service starts, status turns green

### Test 19.3 — Verify app permission
1. In Shizuku Center → "App Permissions"
2. ACCU (com.accu.controlcenter) should show "Authorized"
3. **Expected:** If not, tap "Request Permission" and accept the Shizuku dialog

---

## 📊 All Features Screen

**Where:** Main menu → "All Features" (grid icon)

1. Open All Features screen
2. **Expected:** 90+ features from all 17 repos listed with icons
3. Tap search icon → type "freeze"
4. **Expected:** Only freeze-related features shown
5. Tap any feature card
6. **Expected:** Navigates directly to that feature's screen

---

## 🎓 Learning Center / Tutorial

**Where:** Learning Center tab (school icon)

1. Open Learning Center
2. **Expected:** Step-by-step guides for: Shizuku setup, freezing apps, using shell, setting up DSP
3. Tap any guide
4. **Expected:** Expandable step cards with screenshots and tips
5. Tap ℹ️ icon on any screen (throughout the app)
6. **Expected:** Context-aware tooltip explaining that specific feature

---

## ✅ How to confirm everything is real (not placeholder)

| Proof | How to check |
|-------|-------------|
| Commands actually execute | Run `pm list packages` in Shell — you'll see real packages |
| Permissions actually toggle | Before/after: check Settings → Apps → [app] → Permissions |
| Apps actually freeze | After freeze: try to open the app — it won't launch |
| Theme actually changes | Pick "Neon Matrix" — entire app goes green immediately |
| Recording actually works | Make a call → hang up → check file in /sdcard/ACCU/recordings/ |
| Cache actually cleared | Check Storage in Settings before/after running "Clean Cache" |

---

## 🚨 Known Requirements

| Feature group | Requirement |
|---------------|-------------|
| Shizuku features | Shizuku app installed + authorized |
| Root features (cache paths, storage stats) | Root access OR root-capable shell |
| Call recording | Works on most devices; MIUI/OneUI may need additional setup |
| Per-app dark mode | Android 10+ |
| Freeze (suspend) | Works without root via Shizuku on Android 9+ |
| QS tiles | Android 10+ for programmatic tile add |
| Wireless ADB pairing | Android 11+ for mDNS auto-discovery |
| Device admin features | User must manually enable ACCU as Device Administrator |
