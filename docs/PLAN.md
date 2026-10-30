# Architecture & Execution Plan — Notes (com.dusrel.notes)

## Ground-Truth Target App Architecture Breakdown
- **Reference Package:** `com.miui.notes` (`v1.3.2.7`, build `1327`, `arm64-v8a`)
- **Decompiled Location:** Orgo Cloud Linux VM (`/root/analysis/jadx_full/sources`, `/root/analysis/apktool_out`)

---

## Phase 1: Home Screen Parity (Notes & Tasks Tabs) — [COMPLETED & VERIFIED]
- [x] Status bar & navigation bar insets (`statusBarsPadding()`, `navigationBarsPadding()`).
- [x] Pull-down Private Vault unlock gesture with `LockAction` threshold & spring fling.
- [x] Horizontal page swipe spring fling with `dampingRatio = 0.95f, stiffness = Spring.StiffnessMediumLow`.
- [x] Default mobile notes cards to single column List View mode (`isGridView = false`).
- [x] Complete removal of synthetic bottom navigation bar (no bottom bar exists in reference app).
- [x] Side-by-side Top Dual Tabs (`Notes` & `Tasks`) on left when expanded, smoothly collapsing to **TOP CENTER** of the 56dp action bar on vertical scroll down.
- [x] Top-Right action bar icons: `action_folder` (`ic_data_folder_list`) on Notes tab, `action_setting` (`miui_action_bar_settings`) permanent on both tabs.
- [x] Bottom-right Floating Action Button (`content_add`) with zero artificial bottom offset.

---

## Phase 2: Note Editor & Toolbox Architecture — [NEXT]
1. **Dual-Mode Action Bar (`v12_phone_edit_header.xml`):**
   - View Mode: Share (`ic_action_share`), Theme Palette (`v12_ic_action_theme`), 3-dots Menu (`ic_action_more`).
   - Edit Mode: Undo (`ic_action_undo`), Redo (`ic_action_redo`), Done Checkmark (`ic_action_done`).
2. **Note Body Structure (`phone_note_edit.xml`):**
   - Folder selector pill chip (`note_edit_folder_layout.xml`) above Title.
   - Title (22sp bold, borderless).
   - Extra Info Line (Date/time, character count, lock badge, clock badge).
   - Note body with rich text and checklist spans.
3. **Docked Full-Width Bottom Toolbar (`small_screen_mode_tool_group.xml`):**
   - AI Assistant (`icon_ai`)
   - Voice note recorder (`ic_tool_voice` with `RecordingWaveView`)
   - Gallery image picker (`ic_tool_gallery`)
   - Doodle drawing canvas (`ic_tool_doodle` with 5 brushes, dual erasers, color palette)
   - Checklist toggle (`ic_tool_checkbox`)
   - Expandable Rich Text drawer (`ic_tool_richtext` with 14 formatting tools)
4. **Task Toolbox / Floating Sidebar (`FloatFullWindow`):**
   - Quick task edge panel for fast creation and check-off.
