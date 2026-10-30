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

## Phase 2: Note Editor & Toolbar Architecture — [COMPLETED & VERIFYING]
- [x] Docked full-width bottom toolbar (`small_screen_mode_tool_group.xml`): AI assistant, voice recorder with wave animation, gallery photo picker, doodle canvas mode, checklist toggle.
- [x] Vertical gradient divider (`rich_text_divide`) and expandable rich text toggle (`rich_text_switch`).
- [x] Horizontally scrollable rich text formatting drawer (`RichTextIconHorizontalView.java`): Bold, Italic, Underline, Bullet/Numbered lists, H1, H2, Font size A+/A-.
- [x] Dual-mode top action bar (`v12_phone_edit_header.xml`): View Mode (Share, 14-Theme Palette, 3-dots Menu) vs Edit Mode (Undo, Redo, Done Checkmark).
- [x] Done checkmark saves note, dismisses keyboard, clears focus, and restores View Mode.
- [x] Folder selector pill chip (`note_edit_folder_layout.xml`) positioned above the title.
- [x] Extra info line with timestamp, character count, lock badge, and alarm badge.
