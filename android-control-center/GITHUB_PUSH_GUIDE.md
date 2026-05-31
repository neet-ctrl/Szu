# How to Push Android Control Center Ultimate to GitHub

Supports **two push methods** — both automatically trigger the debug build:

| Method | When to use |
|--------|------------|
| **Method A** — Push the whole workspace (recommended) | You want to keep everything together |
| **Method B** — Push only android-control-center | You only want the Android project |

Both methods build the same debug APK. **No signed APK is ever produced.**

---

## Prerequisites

- [Git](https://git-scm.com/downloads) installed on your PC/Mac
- A [GitHub account](https://github.com)
- The project files on your computer

---

## Step 1 — Create a New GitHub Repository

1. Go to [https://github.com/new](https://github.com/new)
2. Fill in:
   - **Repository name**: `AndroidControlCenterUltimate`
   - **Visibility**: Public or Private (both work on free tier)
   - **DO NOT** check "Add README", "Add .gitignore", or "Add license"
3. Click **Create repository**
4. Copy the HTTPS URL (e.g. `https://github.com/YourName/AndroidControlCenterUltimate.git`)

---

## Method A — Push the Entire Workspace (Recommended)

This lets you push from the workspace root (`cd workspaces`) and the workflow **auto-triggers** because `.github/workflows/build.yml` is at the workspace root.

```bash
# Navigate to the workspace ROOT (parent of android-control-center)
cd /path/to/workspaces

# Initialize git at the workspace root
git init
git add .
git commit -m "feat: Android Control Center Ultimate — complete workspace"

# Connect to GitHub and push
git remote add origin https://github.com/YourName/AndroidControlCenterUltimate.git
git branch -M main
git push -u origin main
```

The workflow at `.github/workflows/build.yml` watches for changes in `android-control-center/**` and triggers automatically. Every future push from the workspace root works the same way:

```bash
# Future updates — from workspace root:
git add .
git commit -m "feat: your change description"
git push
```

---

## Method B — Push Only the android-control-center Folder

```bash
# Navigate INTO the android-control-center folder
cd /path/to/workspaces/android-control-center

# Initialize git inside android-control-center
git init
git add .
git commit -m "feat: Android Control Center Ultimate — 116 files, all 17 repos"

# Connect to GitHub and push
git remote add origin https://github.com/YourName/AndroidControlCenterUltimate.git
git branch -M main
git push -u origin main
```

The workflow at `android-control-center/.github/workflows/build.yml` will trigger automatically on every push.

---

## Authentication

GitHub no longer accepts passwords for HTTPS pushes. Use a **Personal Access Token**:

1. GitHub → Settings → Developer settings → Personal access tokens → **Tokens (classic)**
2. Click "New token" → grant `repo` scope → Generate
3. Copy the token — use it as your password when prompted

---

## Step 4 — Watch the Build Run Automatically

1. Go to `https://github.com/YourName/AndroidControlCenterUltimate`
2. Click the **Actions** tab
3. You will see **"Build Debug APK"** running
4. Wait ~10–15 minutes for completion (faster on 2nd+ build due to Gradle cache)
5. Click the completed run → scroll to **Artifacts**
6. Download **ACCU-debug-buildN**
7. Unzip → install the `.apk` on your Android device

---

## What Triggers the Build?

| Event | Condition |
|-------|-----------|
| Push to any branch | Changes in `android-control-center/**` |
| Pull request | Changes in `android-control-center/**` |
| Manual dispatch | Actions tab → "Run workflow" button |

---

## Future Updates

```bash
# Method A (workspace root):
git add .
git commit -m "feat: your change"
git push

# Method B (android-control-center subfolder):
git add .
git commit -m "feat: your change"
git push
```

Both trigger a new debug build automatically within seconds.

---

## Downloading the Debug APK

1. Go to **Actions** tab on GitHub
2. Click the latest **"Build Debug APK"** run (green checkmark = success)
3. Scroll to **Artifacts** section
4. Click **ACCU-debug-buildN** to download
5. Unzip → install `.apk` on Android device
6. Enable "Install from unknown sources" if asked (Settings → Security → Install unknown apps)

---

## Troubleshooting

### Build doesn't trigger when pushing workspace root
Make sure `.github/workflows/build.yml` is at the **root of the repository** (not inside android-control-center). If you pushed Method A correctly, this file is at `workspaces/.github/workflows/build.yml` which becomes the root.

### Build fails: "Gradle not found"
```bash
git add gradlew gradle/
git commit -m "fix: add gradle wrapper"
git push
```

### "Permission denied" on push
Use Personal Access Token as your password (see Authentication above).

### Build takes too long
First build ~15 min. With Gradle caching enabled, subsequent builds take ~5–8 min.

### APK won't install on device
- Enable "Install from unknown sources" in Settings → Security
- If "App not installed": uninstall any previous version first
- Device must run Android 10+ (minSdk 29)

### Trigger build manually
Actions tab → "Build Debug APK (Workspace Root)" → "Run workflow"

---

## Build Configuration Summary

| Setting | Value |
|---------|-------|
| Build type | Debug ONLY (no signed release APK) |
| Java version | 17 (Temurin) |
| Min Android | API 29 (Android 10) |
| Target Android | API 36 |
| APK artifact name | `ACCU-debug-buildN` |
| APK retention | 30 days |
| Lint | Optional only |
| Gradle heap | 6 GB max |
| Gradle cache | Enabled |
| Parallel build | Enabled |

---

## Project Structure (after Method A push)

```
[GitHub repo root] = workspaces/
├── .github/
│   └── workflows/
│       └── build.yml              ← Workspace-root trigger (Method A)
├── android-control-center/
│   ├── .github/
│   │   └── workflows/
│   │       └── build.yml          ← Subfolder trigger (Method B)
│   ├── app/src/main/
│   │   ├── AndroidManifest.xml
│   │   └── java/com/accu/
│   │       ├── navigation/        ← NavRoutes.kt + AppNavigation.kt (60 routes)
│   │       ├── ui/                ← 43+ screens (Jetpack Compose)
│   │       ├── services/          ← 13 background services + QS tiles
│   │       ├── receivers/         ← 5 broadcast receivers
│   │       ├── data/              ← Room DB, DAOs, repositories
│   │       └── di/                ← Hilt DI modules
│   ├── FEATURE_PROMISE.md
│   └── GITHUB_PUSH_GUIDE.md       ← This file
└── artifacts/                     ← Web artifacts (not part of Android build)
```
