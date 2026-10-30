# Project Status — Notes (com.dusrel.notes)

- **Date:** 2026-10-31
- **Current Milestone:** Top Action Bar Dual Tabs (Notes & Tasks) with Vertical Collapse to Center & Elimination of Bottom Nav Bar
- **Previous Remote CI Run:** [Run #41110589635](https://github.com/dusmamud/dusrel-notes-android/actions/runs/41110589635) — **PASSED 100% GREEN (Exit Code 0)**
  - `Unit tests`: completed -> success
  - `Lint`: completed -> success
  - `Assemble debug APK`: completed -> success (APK artifact generated)

---

## Verified Parity Milestones

1. **Top Action Bar Dual Tabs Architecture (`setupMIUIViewPager` & `ActionBar.Tab` Parity):**
   - **Zero Bottom Navigation Bar:** Target APK decompilation confirms no bottom navigation bar exists; eliminated artificial 60dp bottom nav bar completely.
   - **Expanded Mode (Top of list):** Side-by-side large tabs on the top left:
     - `Notes` Tab: 28sp Bold when active, 20sp Medium 45% alpha when inactive.
     - `Tasks` Tab: 28sp Bold when active, 20sp Medium 45% alpha when inactive.
     - Tapping either tab animates pager smoothly to that page.
   - **Collapsed Mode (Vertically scrolled down):**
     - Large left tabs collapse to zero height.
     - Compact dual tabs (`[ Notes ]  [ Tasks ]`, 17sp) smoothly fade in and anchor right at the **TOP CENTER** of the 56dp Action Bar.
     - Interactive tapping switches pages in collapsed state.
   - **Top-Right End Icons (`action_bar_menu.xml`):**
     - `action_folder` (`custom_folder_view.xml`): Visible on Notes tab, scales down & fades out on Tasks tab.
     - `action_setting` (`custom_setting_view.xml`): Permanent on far right on both Notes and Tasks tabs.

2. **Full-Immersion Screen & Floating Action Button (`content_add` Parity):**
   - Clean circular FAB (`+`) anchored at bottom right (`end = 20.dp, bottom = 24.dp` above `navigationBarsPadding()`).
   - Notes tab FAB tap: creates new Note -> launches Note Editor.
   - Tasks tab FAB tap: opens quick task creation sheet.

3. **Pull-Down to Enter Private Vault (`LockAction` & `miuix_sbl_simple_indicator` Parity):**
   - **Dragging (< 150f threshold):** Closed lock icon with muted tint + `"Pull down to open private folder"`.
   - **Activated (>= 150f threshold):** Open lock icon with gold tint + `"Release to open private folder"`.
   - **Release Fling (`onPreFling`):** Finger release when activated launches `onOpenPrivate()`.

4. **Horizontal Swipe Physics (`HomepageViewPager` / `PhysicBasedInterpolator` Parity):**
   - `HorizontalPager` with `PagerDefaults.flingBehavior(state = pagerState, snapAnimationSpec = spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessMediumLow))`.

5. **Notes Cards Default to Single Column List View:**
   - Default view set to single column List View (`isGridView = false`).
