# Technical Requirements Document (TRD) — Notes (com.dusrel.notes)

## 1. Architecture Overview
Clean Architecture with unidirectional data flow (UDF) and MVVM:
- **Presentation Layer:** 100% Jetpack Compose (Material 3), Navigation Compose, StateFlow observables.
- **Domain Layer:** Pure Kotlin business logic, `PrivateVault` (AES-GCM crypto), `RichText` span tokenizer, `DoodleCodec` stroke serializer.
- **Data Layer:** Room Database (`NoteDao`, `FolderDao`, `TodoDao`), DataStore Preferences, WorkManager / ExactAlarm.

## 2. Technical Standards & Design Tokens

### Color Roles (5 Theme Palettes):
```kotlin
enum class NoteTheme(val id: Int, val lightColor: Long, val darkColor: Long, val lightText: Long, val darkText: Long) {
    YELLOW(0, 0xFFFFFBF0, 0xFF2A2720, 0xFF2D261A, 0xFFF0E8D9),
    BLUE(1,   0xFFF0F6FF, 0xFF1A2433, 0xFF152945, 0xFFD8E6FC),
    WHITE(2,  0xFFFFFFFF, 0xFF202124, 0xFF1F1F1F, 0xFFE2E2E2),
    GREEN(3,  0xFFF0FAF3, 0xFF1D2A22, 0xFF183823, 0xFFD4F2DE),
    PINK(4,   0xFFFFF0F3, 0xFF301F24, 0xFF451622, 0xFFFCD8E1)
}
```

### Responsive & Adaptive Layout Architecture:
- `LazyVerticalStaggeredGrid` with `StaggeredGridCells.Adaptive(minSize = 160.dp)`:
  - Phone (< 600dp): 2 columns
  - Foldable / Small Tablet (600dp - 840dp): 3 columns
  - Large Tablet / Desktop (> 840dp): 4 columns
- Grid / List state persisted in DataStore (`is_grid_view_mode: Boolean`).

### Rich Text Span Format:
- Spans persisted as JSON in `richTextSpans` column.
- Marker syntax parsed and rendered with full WYSIWYG fidelity.
