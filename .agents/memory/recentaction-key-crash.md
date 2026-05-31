---
name: RecentAction LazyRow duplicate key crash
description: Root cause and fix for the app-launch crash caused by duplicate LazyRow keys in DashboardViewModel.
---

## Rule
`RecentAction.id` has a default of `0L`. Every call to `buildRecentActions()` must explicitly set `id` — never rely on the default.

**Why:** The `RecentActionsList` LazyRow uses `key = { it.id }`. When all 8 items share the default `id = 0L`, Compose throws `IllegalArgumentException: Key "0" was already used` and the app crashes immediately on launch. This was the crash agents kept failing to fix.

**How to apply:** In `buildRecentActions()` use `mapIndexed` and set `id = pkg.lastUpdateTime.takeIf { it > 0 } ?: index.toLong()`. For fallback/empty-state items use distinct negative IDs (`-1L`, `-2L`). DB-backed entities with `@PrimaryKey(autoGenerate = true)` are safe because Room assigns real IDs before they reach the UI.
