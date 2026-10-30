# Implementation Plan — Modern Note App Parity

## Phase 1: Design Tokens & Color Palettes
- [x] Task 1.1: Update `presentation/theme/Theme.kt` with the 5-color note card palette (`NoteCardThemeColors`), background tokens, and text colors.
- [x] Task 1.2: Add note theme helper functions to map `theme_id` / `color` integer (0 to 4) to `Color` tokens in both Light and Dark mode.

## Phase 2: Home Screen Responsive Staggered Grid & List View Toggle
- [x] Task 2.1: Add `isGridView: Boolean` preference state to `PreferencesRepository.kt` and `NotesViewModel.kt`.
- [x] Task 2.2: Add top bar layout toggle icon (Staggered Grid ⇋ Linear List) in `NotesListScreen.kt`.
- [x] Task 2.3: Implement `LazyVerticalStaggeredGrid` with `StaggeredGridCells.Adaptive(minSize = 160.dp)` for staggered masonry layout.
- [x] Task 2.4: Upgrade Note Card composable (`NoteCard`) with:
  - Dynamic content-height calculation
  - Exact theme background color
  - 16dp rounded corners
  - Gold pin icon badge for pinned notes
  - Checklist item preview
  - Doodle stroke preview

## Phase 3: Editor Screen Enhancements & Card Color Selector
- [x] Task 3.1: Add 5-color theme selector horizontal swatches in `EditorScreen.kt`.
- [x] Task 3.2: Add top header folder chip indicator (tap to change note folder).
- [x] Task 3.3: Ensure editor background smoothly adopts the selected note card color.

## Phase 4: Advanced Doodle Canvas & Multi-Brush Tooling
- [x] Task 4.1: Expand `DoodleCanvas.kt` with 5 stroke width presets (`2dp`, `4dp`, `8dp`, `14dp`, `24dp`).
- [x] Task 4.2: Add tool selector: Pen, Marker, Highlighter, Eraser.
- [x] Task 4.3: Implement point drag and proximity erasing.

## Phase 5: CI & GitHub Actions Verification
- [x] Task 5.1: Configure `.github/workflows/android-ci.yml` with executable gradlew setup so local laptop stays at 0% load.
