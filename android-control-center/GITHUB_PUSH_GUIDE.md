# How to Push Android Control Center Ultimate to GitHub

This guide explains how to push this project directory to GitHub so the debug build workflow starts automatically on every push.

---

## Prerequisites

- [Git](https://git-scm.com/downloads) installed on your PC/Mac
- A [GitHub account](https://github.com)
- This project folder (`android-control-center/`) on your computer (unzip `AndroidControlCenterUltimate.zip`)

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
git commit -m "Initial commit: Android Control Center Ultimate"
```

---

## Step 3 — Connect to GitHub and Push

Replace `YourUsername` and `AndroidControlCenterUltimate` with your actual GitHub username and repo name:

```bash
git remote add origin https://github.com/YourUsername/AndroidControlCenterUltimate.git
git branch -M main
git push -u origin main
```

Enter your GitHub username and password (or personal access token) when prompted.

> **Note**: GitHub no longer accepts passwords for HTTPS. Use a **Personal Access Token** instead.
> Create one at: GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → New token
> Grant `repo` scope. Use the token as your password.

---

## Step 4 — Watch the Debug Build Run Automatically

1. Go to your GitHub repo: `https://github.com/YourUsername/AndroidControlCenterUltimate`
2. Click the **Actions** tab
3. You will see **"Build Debug APK"** workflow running automatically
4. Wait ~10–15 minutes for the build to complete
5. Click the completed run → scroll to **Artifacts** section → download **ACC-Ultimate-debug-N.apk**

---

## What Triggers the Build?

The workflow (`.github/workflows/build.yml`) triggers on:

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
git commit -m "Your description of changes"
git push
```

The GitHub Actions workflow will automatically start a new debug build within seconds of the push.

---

## Downloading the Debug APK

After a successful build:

1. Go to **Actions** tab on GitHub
2. Click the latest **"Build Debug APK"** workflow run
3. Scroll to the bottom — find **Artifacts** section
4. Click **ACC-Ultimate-debug-N** to download the `.zip` containing the APK
5. Unzip and install the `.apk` on your Android device (enable "Install from unknown sources")

---

## Troubleshooting

### Build fails with "Gradle not found"
Make sure `gradlew` has execute permissions. The workflow runs `chmod +x gradlew` automatically.

### "Permission denied" on push
Use a Personal Access Token instead of your GitHub password. See Step 3 note above.

### Build takes too long
Gradle caching is enabled. The first build takes ~15 min. Subsequent builds with no dependency changes take ~5–8 min.

### APK won't install on device
Enable "Install from unknown sources" / "Install unknown apps" in your device's security settings.

---

## Project Structure (for reference)

```
android-control-center/
├── .github/
│   └── workflows/
│       └── build.yml          ← GitHub Actions CI (debug-only)
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   └── java/com/accu/
│   │       ├── navigation/    ← App navigation
│   │       ├── ui/            ← All screens
│   │       ├── services/      ← Background services + QS tiles
│   │       ├── data/          ← Room DB, repositories
│   │       └── di/            ← Hilt dependency injection
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
└── README.md
```
