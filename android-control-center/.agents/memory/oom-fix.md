---
name: OOM Fix Pattern
description: Root causes and fixes for java.lang.OutOfMemoryError in ACCU — 1.5 GB allocation crash
---

## The Bug
`java.lang.OutOfMemoryError: Failed to allocate a 1552749016 byte allocation`

## Root Causes Fixed (June 2026)

1. **execPlainShell stdout/stderr unbounded** — `readText()` buffers all shell output into a String. Fixed: `readStreamCapped(limitMb=8)` helper added, caps at 8 MB with truncation notice.

2. **pushViaBase64 loads entire file** — `File.readBytes()` on any size file. Fixed: 50 MB guard added; callers (adb push / cp) handle large files.

3. **APK extraction via base64 exec** — `exec("base64 file")` → 700 MB base64 String + 500 MB decoded ByteArray = 1.2 GB peak. Fixed: New `pullFile(remotePath, localPath)` method added to AccuConnectionManager uses `adb pull` or `cp` (streaming, no ByteArray). Both AppDetailViewModel and AppManagerViewModel now use `pullApkToStream()` → temp file → stream to URI.

4. **TextEditorScreen unguarded readText()** — Fixed: 4 MB limit before reading; shows friendly error if exceeded.

5. **AdbFileBrowserScreen size check after readBytes()** — Fixed: SAF size queried via OpenableColumns.SIZE BEFORE calling readBytes().

## Rules Going Forward
- Never call `File.readBytes()` or `readText()` without a size guard.
- Never buffer entire shell output — use `readStreamCapped`.
- APK/file extraction: always use `connectionManager.pullFile()` → temp → stream to URI.
- Size checks must happen BEFORE the read, not after.
