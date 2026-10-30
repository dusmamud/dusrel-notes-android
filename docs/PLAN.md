# 1000% Exact Mirror Implementation Plan — Modern OEM Notes & Tasks Parity

> [!IMPORTANT]
> **Source Target Binary:** Reference Productivity Application (`v1.3.2.7`, build `1327`, `arm64-v8a`)  
> **Extraction Location:** Disassembled & Reverse-Engineered on Orgo Linux Cloud VM (`/root/analysis/target.apk`)  
> **Invariants:** 100% Ground truth from decompiled layouts and source code. Zero guesswork. Zero synthetic Material3 styling. Strict brand embargo (no OEM brand names). Zero local Gradle load (remote CI verification only).

---

## Phase 1: Tokens, Dimensions & Physics Constants
- [x] Step 1.1: Document exact metrics from `dimens.xml` in `Theme.kt`:
  - `ActionBarHeight = 56.dp`
  - `BottomNavHeight = 60.dp`
  - `CardCornerRadius = 16.dp`, `CardPadding = 16.dp`
  - `TaskItemRadius = 16.dp`, `TaskStartMargin = 18.dp`, `TaskTopMargin = 4.dp`, `TaskBottomMargin = 6.dp`
  - `SquircleCheckboxSize = 18.dp`, `SquircleCheckboxRadius = 5.dp`
  - `FabDiameter = 58.dp`, `FabElevation = 6.dp`
  - `SheetCornerRadius = 24.dp`
- [x] Step 1.2: Define Folme spring physics constants:
  - `FolmeDamping = 0.9f`, `FolmeStiffness = Spring.StiffnessMediumLow` (response 0.85s)
  - `TouchDownScaleButtons = 0.85f`, `TouchDownScaleCards = 0.95f`, `FabHideScale = 0.8f`

## Phase 2: Home Screen (Notes & Tasks) 1000% Mirror
- [ ] Step 2.1: Action Bar 1:1 Parity
  - Single dynamic title ("Notes" vs "Tasks") with 56dp height.
  - Notes tab shows: Search, Grid toggle, Folder manager, 3-dots menu.
  - Tasks tab automatically hides: Folder manager and Grid toggle.
- [ ] Step 2.2: Collapsible Search Capsule (`headerClose="true"`)
  - By default collapsed; revealed on search icon tap or pull-down overscroll.
- [ ] Step 2.3: Notes Masonry Staggered Grid
  - 2-column staggered grid, 16dp squircle radius, 16dp internal padding, 5 color presets.
  - Card footer with date/folder tag and status badges (pin, doodle, reminder, lock).
- [ ] Step 2.4: Tasks Screen UX Parity
  - Directly starts under Action Bar (no inline quick-task box, no permanent search bar).
  - 18x18dp squircle checkbox with 5dp radius, strikethrough animation, completed accordion.
- [ ] Step 2.5: Single 58dp Primary Gold FAB Motion & Bottom Navigation Dock
  - During horizontal swipe between tabs, FAB scales down to 0.8f and fades out; on settle it scales back to 1.0f.
  - Tapping FAB on Notes tab opens Note Editor.
  - Tapping FAB on Tasks tab opens `CreateTaskBottomSheet`.
  - Persistent 60dp Bottom Navigation Bar (`Notes` & `Tasks`).

## Phase 3: Note Editor Screen 1000% Mirror
- [ ] Step 3.1: Top Action Bar (56dp)
  - Back arrow (auto-saves and exits).
  - Live Undo / Redo buttons with document history stack.
  - Folder chip selector pill, Pin toggle, Color palette picker, 3-dots More menu.
- [ ] Step 3.2: Borderless Paper Canvas
  - Title: 22sp Bold, borderless.
  - Subtitle meta line: Date/time, character & word count.
  - Document body with rich text formatting (H1, H2, Bold, Italic, Bullet lists, Numbered lists, Checklists).
- [ ] Step 3.3: Bottom Formatting Toolbar (Docked above Keyboard)
  - Permanently anchored above IME keyboard (`adjustResize` mode).
  - Exact tool icons: Todo Checkbox, Font styling (`Aa`), Image attachment, Doodle canvas, Voice memo indicator.

## Phase 4: Modals & Sheets
- [ ] Step 4.1: `CreateTaskBottomSheet` (Slide-up modal with 24dp squircle top corners, input, date reminder, save checkmark).
- [ ] Step 4.2: `NoteOptionBottomSheet` (Long-press on card opens Pin, Move to folder, Move to Private Vault, Delete).

## Phase 5: CI Remote Verification
- [ ] Step 5.1: Git commit & push.
- [ ] Step 5.2: Verify GitHub Actions CI run passes 100% green with exit code 0 (zero local laptop load).
