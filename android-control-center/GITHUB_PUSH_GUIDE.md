# How to Push Android Control Center Ultimate to GitHub

This guide explains how to push this project directory to GitHub so the debug build workflow starts automatically on every push.

---

## Prerequisites

- [Git](https://git-scm.com/downloads) installed on your PC/Mac
- A [GitHub account](https://github.com)
- This project folder (`android-control-center/`) on your computer

---

## Step 1 — Create a New GitHub Repository

1. Go to [https://github.com/new](https://github.com/new)
2. Fill in:
   - **Repository name**: `AndroidControlCenterUltimate` (or any name you prefer)
   - **Visibility**: Public or Private (both work with GitHub Actions on free tier)
   - **DO NOT** check "Add a README", "Add .gitignore", or "Add a license" — the project already has these
3. Click **Create repository**
4. Copy the HTTPS URL shown (e.g. `https://github.com/YourUsername/AndroidControlCenterUltimate.git`)

---

## Step 2 — Initialize Git in the Project Folder

Open a terminal (Command Prompt / PowerShell on Windows, Terminal on Mac/Linux).

Navigate into the unzipped project folder:

```bash
cd path/to/android-control-center
```

Initialize git and make the first commit:

```bash
git init
git add .
git commit -m "feat: Android Control Center Ultimate — 17 repos merged, 109 files, debug APK"
```

---

## Step 3 — Connect to GitHub and Push

Replace `YourUsername` and `AndroidControlCenterUltimate` with your actual GitHub username and repo name:

```bash
git remote add origin https://github.com/YourUsername/AndroidControlCenterUltimate.git
git branch -M main
git push -u origin main
```

> **Authentication note**: GitHub no longer accepts passwords for HTTPS.
> Use a **Personal Access Token** instead:
> GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → New token
> Grant `repo` scope. Use the token as your password when prompted.

---

## Step 4 — Watch the Debug Build Run Automatically ✅

1. Go to your GitHub repo: `https://github.com/YourUsername/AndroidControlCenterUltimate`
2. Click the **Actions** tab
3. You will see **"Build Debug APK"** workflow running automatically
4. Wait ~10–15 minutes for the build to complete
5. Click the completed run → scroll to **Artifacts** section
6. Download **ACCU-debug-buildN** (a `.zip` file)
7. Unzip → install the `.apk` on your Android device

---

## What Triggers the Build?

The workflow (`.github/workflows/build.yml`) is **Debug Only — No Signed APK**:

| Event | When |
|-------|------|
| `push` to `main` | Every time you push new commits |
| `push` to `master` | Every time you push to master |
| `push` to `develop` | Every time you push to develop |
| `pull_request` | When a PR is opened/updated against main/master |
| `workflow_dispatch` | Manually via Actions tab → "Run workflow" button |

---

## Making Future Updates

Whenever you change files and want to rebuild:

```bash
# From inside the android-control-center/ folder:
git add .
git commit -m "feat: describe your change here"
git push
```

GitHub Actions will automatically start a new debug build within seconds.

---

## Downloading the Debug APK

After a successful build:

1. Go to **Actions** tab on GitHub
2. Click the latest **"Build Debug APK"** workflow run (green checkmark = success)
3. Scroll to the bottom — find **Artifacts** section
4. Click **ACCU-debug-buildN** to download the `.zip` containing the APK
5. Unzip → install the `.apk` on your Android device
6. Enable "Install from unknown sources" if asked (Settings → Security → Install unknown apps)

---

## Troubleshooting

### Build fails with "Gradle not found"
Make sure `gradlew` has execute permissions. The workflow runs `chmod +x gradlew` automatically. If the file is missing:
```bash
git add gradlew gradle/
git commit -m "fix: add gradle wrapper"
git push
```

### "Permission denied" on push
Use a Personal Access Token instead of your GitHub password. See Step 3 note above.

### Build takes too long
Gradle caching is enabled. The first build takes ~15 min. Subsequent builds with no dependency changes take ~5–8 min.

### APK won't install on device
- Enable "Install from unknown sources" / "Install unknown apps" in Settings → Security
- If "App not installed" error: uninstall any previous version first
- Make sure your device runs Android 10+ (minSdk 29)

### Want to trigger a build manually
1. Go to Actions tab → "Build Debug APK"
2. Click "Run workflow" button → select branch → click green "Run workflow"

---

## Build Configuration Summary

| Setting | Value |
|---------|-------|
| Build type | **Debug ONLY** (no signed release APK ever) |
| Java version | 17 (Temurin) |
| Min Android | API 29 (Android 10) |
| Target Android | API 36 |
| APK artifact name | `ACCU-debug-buildN` |
| APK retention | 30 days |
| Lint | Optional (manual trigger or on PRs) |
| Gradle heap | 6 GB max |
| Parallel build | Enabled |
| Gradle caching | Enabled (faster 2nd+ builds) |

---

## Project Structure

```
android-control-center/
├── .github/
│   └── workflows/
│       └── build.yml              ← GitHub Actions CI (debug ONLY)
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml    ← Services, tiles, receivers declared
│   │   ├── res/xml/
│   │   │   └── device_admin.xml   ← Hail device admin policies
│   │   └── java/com/accu/
│   │       ├── navigation/        ← NavRoutes.kt + AppNavigation.kt (57 routes)
│   │       ├── ui/                ← All 40+ screens (Compose)
│   │       ├── services/          ← 13 background services + QS tiles
│   │       ├── receivers/         ← 5 broadcast receivers
│   │       ├── data/              ← Room DB, DAOs, repositories
│   │       └── di/                ← Hilt dependency injection modules
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── FEATURE_PROMISE.md             ← 100% feature coverage table
└── GITHUB_PUSH_GUIDE.md           ← This file
```
