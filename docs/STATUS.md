# Project Status — Notes (com.dusrel.notes)

- **Date:** 2026-10-31
- **Current Milestone:** Task Toolbox Integration (`FloatFullWindow` / `todo_float_full_view.xml`), Editor Toolbar 18-Tool In-Place Switching (`small_screen_mode_tool_group.xml`), List View Default (`PhoneAppSettingsFragment`), and Settings Screen Parity
- **Previous Remote CI Run:** [Run #41124739575](https://github.com/dusmamud/dusrel-notes-android/actions/runs/41124739575) — **PASSED 100% GREEN (Exit Code 0)**

---

## Verified Parity Milestones

1. **Task Toolbox & Floating Edge Indicator (`FloatFullWindow` & `todo_float_full_view.xml` Parity):**
   - **Floating Edge Handle:** Docked on screen edge (`pref_title_todo_float_mode`). Swipe inward or tap to reveal.
   - **Sliding Drawer Window:** Modern rounded overlay with frosted background scrim.
   - **Active Task Checklist:** Live interactive squircle checkboxes for immediate task completion without leaving the screen.
   - **Inline Quick Task Creator:** Type and press Enter/Check to immediately add a task.
   - **Quick Note Jotter:** Bottom text field to immediately save a thought to Notes with one tap.

2. **Note Editor Single Docked Toolbar (`small_screen_mode_tool_group.xml` & `SmallScreenNoteEditToolbarController.java` Parity):**
   - **Single 52dp Docked Bar:** Docked directly above software keyboard (`imePadding()`), hidden in View Mode.
   - **Left 81% Animated In-Place Switch:**
     - **Normal State:** AI Assistant (`@id/ai`), Voice Note (`@id/audio`), Gallery (`@id/gallery`), Doodle (`@id/edit_image`), Checklist (`@id/check`).
     - **Rich Text State:** 18 horizontally scrollable formatting tools in the SAME 52dp bar (H1, H2, H3, A+, A-, Bold, Italic, Underline, Strikethrough, Highlight Marker, Bullet List, Numbered List, Horizontal Divider Rule, Blockquote, Align Center, Align Right, Indent, Outdent).
   - **Vertical Divider Line:** 0.75dp divider (`rich_text_divide`).
   - **Right 19% Toggle:** Switch icon (`rich_text_switch`) smoothly toggles between normal tools and rich text tools in place.
   - **Audio Recording Panel:** Expands above toolbar with 27-bar wave animation visualizer, MM:SS live timer, and save checkmark.
   - **Dual-Mode Header:** View Mode (Share, Palette, 3-dots Menu) vs Edit Mode (Undo, Redo, Done Checkmark). Done saves, dismisses keyboard, clears focus, and exits edit mode.

3. **Home Screen Top Header & Tabs Parity (`setupMIUIViewPager` & `PhoneNotePageFragment.java`):**
   - **Default List View on Mobile:** `isGridView` initialized to `false` (List View), matching `PhoneAppSettingsFragment` default `"0"`.
   - **Dual Large Tabs:** Side-by-side on left (`[ Notes ]  [ Tasks ]`), smoothly collapsing to **TOP CENTER** of 56dp action bar on scroll down.
   - **Pull-Down Private Vault:** Pulling down on Notes list triggers private vault unlock with lock indicator and threshold spring fling.
   - **Zero Synthetic Bottom Bar:** Reference app has zero bottom navigation bar.

4. **Settings Screen Parity (`phone_app_preferences.xml`):**
   - View mode switcher: List view vs Grid view.
   - Quick functions: "Use task toolbox" (`pref_title_todo_float_mode`) toggle.
   - Appearance: Light, Dark, System.
   - Private vault management, JSON Backup & Restore.
