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
  - `FolmeDamping = 0.95f`, `FolmeStiffness = Spring.StiffnessMediumLow` (response 0.6s per `PhysicBasedInterpolator.java`)
  - `TouchDownScaleButtons = 0.85f`, `TouchDownScaleCards = 0.95f`, `FabHideScale = 0.8f`

## Phase 2: Home Screen (Notes & Tasks) 1000% Mirror
- [x] Step 2.1: Action Bar 1:1 Parity
  - Dynamic Collapsible Action Bar: 28sp Bold Large Left Title collapses into 18sp Semi-Bold Title at Top Center of 56dp Action Bar on vertical scroll down (`CollapseTitle` with `setGravity(Gravity.CENTER_HORIZONTAL)`).
  - Top-Right Action Icons: Folder Manager (Notes-only: scales down & fades out on Tasks tab) + Settings gear (permanent on right).
  - Eliminated all synthetic buttons (no Search button, no Grid toggle, no 3-dots overflow in Action Bar).
  - Sticky folder pills filter bar (`data_folder_list_layout.xml` parity) below header on Notes tab.
- [x] Step 2.2: Status Bar & Navigation Bar Insets
  - Root column starts below status bar via `statusBarsPadding()`.
  - Bottom navigation bar and FAB padded via `navigationBarsPadding()`.
- [x] Step 2.3: Pull-Down Private Vault Indicator (`LockAction` & `miuix_sbl_simple_indicator` Parity)
  - Dragging down at top of Notes: Closed lock + `"Pull down to open private folder"`.
  - Over threshold (>= 150f): Open lock + gold tint + `"Release to open private folder"`.
  - Release fling triggers `onOpenPrivate()`.
- [x] Step 2.4: Horizontal Swipe Physics (`HomepageViewPager` / `PhysicBasedInterpolator` Parity)
  - `PagerDefaults.flingBehavior` with `dampingRatio = 0.95f` physical spring damping.
- [x] Step 2.5: Mobile Notes Cards Default to List View Mode
  - By user specification and verified `common_note_item_list.xml` structure, mobile cards default to single-column list cards (`isGridView = false`).
  - Title, 1-line snippet, date/time, status badges (pin, audio, doodle, reminder, lock).
- [x] Step 2.6: Tasks Screen UX Parity
  - Directly starts under Action Bar.
  - 18x18dp squircle checkbox with 5dp radius, strikethrough animation, completed accordion.
- [x] Step 2.7: Single 58dp Primary Gold FAB Motion & Bottom Navigation Dock
  - During horizontal swipe between tabs, FAB scales down to 0.8f and fades out; on settle it scales back to 1.0f.
  - Tapping FAB on Notes tab opens Note Editor.
  - Tapping FAB on Tasks tab opens `CreateTaskBottomSheet`.
  - Persistent 60dp Bottom Navigation Bar (`Notes` & `Tasks`).

---

## Phase 3: Note Editor Screen 1000% Mirror Blueprint (Grounded from Decompiled Target APK)

> [!NOTE]
> Detailed decompiled source breakdown from Orgo VM (`PhoneNoteEditFragment.java`, `v12_phone_edit_header.xml`, `phone_note_edit.xml`, `small_screen_mode_tool_group.xml`, `RichTextIconHorizontalView.java`, `normal_note_edit_options.xml`). To be approved before source edits.

- [ ] **Step 3.1: Dual-Mode Top Action Bar (`v12_phone_edit_header.xml` & `BaseActionBarController.java`)**
  - Left: Back arrow (`ic_action_back`).
  - Right: Mutually Exclusive Dual State:
    - **View Mode (Idle / Non-typing):**
      - Share button (`ic_action_share`)
      - Color / Theme palette picker (`v12_ic_action_theme`)
      - 3-dots More menu (`ic_action_more`)
    - **Edit Mode (Typing / Soft keyboard active):**
      - Undo button (`ic_action_undo`)
      - Redo button (`ic_action_redo`)
      - Done Checkmark button (`ic_action_done` — dismisses keyboard, saves note, transitions action bar back to View Mode).
- [ ] **Step 3.2: 3-Dots More Menu (`normal_note_edit_options.xml`)**
  - `Reminder` (`@id/set_alarm`) ➔ Date & Time picker
  - `Hide` / `Unhide` (`@id/hide` / `@id/unhide`) ➔ Private Vault toggle
  - `Place on Home screen` (`@id/send_to_desktop`)
  - `Move to` (`@id/move_to`) ➔ Folder picker
  - `Delete` (`@id/delete`) ➔ Trash
- [ ] **Step 3.3: Content Header Hierarchy (`note_edit_folder_layout.xml` & `note_extra_info_layout.xml`)**
  - Folder Pill Chip: Sits directly above note title (`ic_note_dir_indicator` + folder name). Clicking opens folder dialog.
  - Title: 22sp Bold borderless text input with hint "Title".
  - Extra Info Line: Date + Time + divider + Character count (e.g. `24 Oct 15:30 | 142 characters`).
  - Badges on Info Line: Private Lock icon (`ic_note_edit_info_lock`) + Clock Reminder icon (`ic_note_edit_info_clock`) + reminder time.
- [ ] **Step 3.4: Full-Width Docked Bottom Toolbar (`small_screen_mode_tool_group.xml`)**
  - Docked directly above soft keyboard (via `imePadding()`).
  - Left 81% (`toolbar_relative_layout`):
    - Audio Record (`ic_tool_voice`)
    - Image Insert (`ic_tool_gallery`)
    - Doodle / Sketch canvas (`ic_tool_doodle`)
    - Checklist item toggle (`ic_tool_checkbox`)
  - Right 19%:
    - Divider (`rich_text_divide`)
    - Rich text expander switch (`ic_tool_richtext`).
- [ ] **Step 3.5: Rich Text Expandable Palette (`RichTextIconHorizontalView.java`)**
  - Slides open above keyboard when rich text switch is toggled:
    - Bold, Italic, Underline, Strikethrough
    - Text Highlight marker (color background)
    - Bullet list (•) & Numbered list (1. 2. 3.)
    - Blockquote line
    - Horizontal divider line
    - Headings H1, H2, H3
    - Font size A+ / A-
    - Text alignment (Center, Right, Left)
    - Indent in / out
- [ ] **Step 3.6: Theme Chooser Group (`ThemeChooserGroup.java` & `v12_theme_chooser_item.xml`)**
  - Horizontal scrollable swatches for 14 paper themes.

---

## Phase 4: Remote CI Verification
- [x] CI Run #41106699088: Status bar insets, pull-down private vault indicator, spring pager physics — **PASSED 100% GREEN**.
- [x] CI Run #41110589635: Mobile notes cards default view set to list mode — **PASSED 100% GREEN**.
