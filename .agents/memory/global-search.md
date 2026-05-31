---
name: Global Search Architecture
description: How the global search / command palette works across all ACC screens
---

## Key files
- `SearchIndex.kt` — single source of truth for all 91 searchable screens. Every screen entry has `title`, `subtitle`, `route`, `icon`, `category`, `tags: List<String>`. Add new screens here.
- `DashboardViewModel.kt` — `allSearchableItems = SearchIndex.entries`; scored search (title exact=100, startsWith=85, contains=70, tag exact=60, subtitle=50, tag contains=35, multi-word=25). Returns top 30 sorted by score.
- `DashboardScreen.kt` — `CommandPaletteOverlay` renders quick-launch tiles (8 pinned) when empty, grouped sticky-header results by category when query is active. `iconForName()` maps string → `Icons.Default.*`.
- `SearchIndex.quickLaunch` — the 8 pinned quick-launch items (shizuku_center, shell, app_manager, privacy, audio_center, storage, notification_center, settings).

## Categories + accent colors
Apps=0xFF7C4DFF, Shell & ADB=0xFF00D4FF, Storage=0xFF00E676, Audio=0xFFE91E63, Privacy=0xFFFF6D00, Customization=0xFFD500F9, Network=0xFF2196F3, Automation=0xFF9C27B0, System=0xFF607D8B

## Rules
- `SearchResult.category` must exactly match one of the 9 `categoryOrder` strings or it falls to the end.
- `SearchResult.icon` must be a key in `iconForName()` or it renders `Icons.Default.Circle`.
- `stickyHeader` in `CommandPaletteOverlay` requires `@OptIn(ExperimentalFoundationApi::class)`.
- `focusRequester` auto-focuses the search field on open via `LaunchedEffect(Unit)`.

**Why:** Keeping all screen metadata in one place (`SearchIndex.kt`) means future screens only need one file touched to become searchable — not the ViewModel.
