# Product Requirements Document (PRD) — Notes (com.dusrel.notes)

## 1. Vision & Overview
An original, local-first, privacy-focused native Android notes app built with modern Jetpack Compose (Material 3). It delivers complete visual and functional excellence with masonry note cards, warm paper palettes, doodle studio, checklists, and private vault, with zero tracking, zero cloud dependencies, and 100% clean-room engineering.

## 2. Target Audience & Problem Statement
- **Audience:** Android users who love clean, fast, aesthetic note-taking UX (masonry cards, warm paper colors, doodle handwriting, checklists), and demand strict privacy, local-first storage, no telemetry, and universal compatibility across all Android devices (Samsung, Pixel, Motorola, OnePlus, etc.) from phones to foldables and tablets.
- **Problem:** Stock OEM notes apps are tied to vendor lock-in, require mandatory cloud accounts, embed telemetry SDKs, and are incompatible with non-OEM devices.

## 3. Core Feature Matrix & Parity Goals

| Feature | Reference Design | Dusrel Notes Target |
| :--- | :--- | :--- |
| **Home Layout** | 2-column Staggered Grid ⇋ 1-column Linear List toggle | `LazyVerticalStaggeredGrid` adaptive (2 cols phone, 3-4 cols tablet) ⇋ Linear List |
| **Theme Presets** | 5 color cards (Yellow, Blue, White, Green, Pink) | Exact 5 card background themes with dark mode contrast tokens |
| **Pinned Notes** | Pinned notes pinned to top with visual pin mark | Top Pinned Staggered Grid section with gold pin badge |
| **Rich Editor** | In-editor inline formatting & heading levels | Jetpack Compose `BasicTextField` with inline formatting spans |
| **Doodle / Ink** | Multi-tool handwriting canvas (5 brush widths) | Full `DoodleCanvas` with 5 stroke widths (2dp-24dp), Pen/Marker/Highlighter/Eraser |
| **Checklists** | Draggable todo items with progress & strike-through | Integrated Todo items with checked animation & progress meter |
| **Vault / Privacy** | PIN / Biometric protected notes | AES-256-GCM + PBKDF2 100k iterations with biometric unlock |
| **Adaptive Layout**| Separate OEM tablet XML layouts | Unified responsive Compose layout across Phone, Foldable & Tablet |

## 4. User Journeys
1. **Quick Capture:** Tap (+) FAB -> Opens Editor with auto-focus -> Select card color -> Type title & thoughts -> Back tap automatically debounces and auto-saves to Room.
2. **Organize & Browse:** Toggle between Masonry Grid and Linear List with one tap -> Pin important notes -> Filter by custom folders.
3. **Sketch / Doodle:** Open Doodle tab -> Pick Pen or Highlighter -> Draw with pressure/width -> Embed vector doodle directly into note card preview.
4. **Lock Private Note:** Move sensitive note to Vault -> AES-GCM encryption occurs at rest -> Note vanishes from search and widgets until unlocked.
