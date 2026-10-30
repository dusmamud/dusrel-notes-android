# Project Status — Notes (com.dusrel.notes)

- **Date:** 2026-10-30
- **Current Milestone:** Status Bar Insets, Pull-Down Private Vault Indicator, Spring Pager Physics & 2-Column Grid Parity Verified & Passing CI
- **Remote CI Run:** [Run #41106699088](https://github.com/dusmamud/dusrel-notes-android/actions/runs/41106699088) — **PASSED 100% GREEN (Exit Code 0)**
  - `Unit tests`: completed -> success
  - `Lint`: completed -> success
  - `Assemble debug APK`: completed -> success (APK artifact generated)

---

## Verified Parity Milestones

1. **Edge-to-Edge System Status Bar & Navigation Bar Insets:**
   - **Status Bar Padding (`statusBarsPadding()`):** Root column starts cleanly below the device status bar, completely eliminating content collision with clock, battery, and camera punch-hole while the background seamlessly fills the edge-to-edge window.
   - **Navigation Bar Padding (`navigationBarsPadding()`):** Applied to the bottom navigation bar row and the floating action button, ensuring zero clipping by the system gesture pill or 3-button navigation.

2. **Pull-Down to Enter Private Vault (`LockAction` & `miuix_sbl_simple_indicator` Parity):**
   - Verified decompiled source: `NotePageFragment.java:LockAction` attaching `DefaultTrigger` to `SpringBackLayout`.
   - **Dragging (< 150f threshold):** Closed lock icon with muted gray tint + label `"Pull down to open private folder"` (`R.string.private_entry_default`).
   - **Activated (>= 150f threshold):** Open lock icon with theme gold tint + label `"Release to open private folder"` (`R.string.private_entry_available`).
   - **Release Fling (`onPreFling`):** Finger release when activated triggers `onOpenPrivate()` immediately and resets spring distance; releasing before threshold springs back safely.

3. **Horizontal Swipe Physics (`HomepageViewPager` / `PhysicBasedInterpolator` Parity):**
   - Verified decompiled source: `HomepageViewPager.java:initScroller` with `FolmeVpScroller` and `PhysicBasedInterpolator` (`mDamping = 0.95f`, `mResponse = 0.6f`).
   - Implemented in `HorizontalPager` via `PagerDefaults.flingBehavior(state = pagerState, snapAnimationSpec = spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessMediumLow))`.
   - Smooth physical spring snap when swiping horizontally between Notes and Tasks tabs.

4. **Note Cards Default to 2-Column Staggered Grid on Phone:**
   - Grounded verification from decompiled `PreferenceUtils.java:getGridMode`:
     ```java
     public static boolean getGridMode(Context context) {
         if (RomUtils.isPadMode()) {
             return false;
         }
         return getBooleanPreference(context, PREFERENCE_GRID_MODE, true);
     }
     ```
   - On mobile/phone, default is `true` (2-column staggered grid with `common_note_item_grid.xml` parity).

5. **Dynamic Collapsible Action Bar & Top-Center Title Transition:**
   - **Expanded Mode (Top of list):** Left-aligned Large Title ("Notes" on tab 0, "Tasks" on tab 1) at **28sp Bold**. Center title is invisible (`alpha = 0.0f`). Top bar has zero elevation.
   - **Vertical Scroll Collapse (Scrolling down list):** Large title smoothly translates upwards and fades out; small compact title (**18sp Semi-Bold**) smoothly fades in right at the **TOP CENTER** of the 56dp Action Bar (`CollapseTitle` with `setGravity(Gravity.CENTER_HORIZONTAL)`). Top bar gains subtle surface tint & hairline divider (`shadowElevation = 2.dp`).
   - **Scroll-up Restoration:** Scrolling back to top smoothly fades out the center title and restores the prominent large left title.
   - **Top-Right Icons (`action_bar_menu.xml` Parity):**
     - Folder Manager (`action_folder`): Visible on Notes tab, smoothly scales down (1.0f ➔ 0.0f) and fades out on Tasks tab.
     - Settings Gear (`action_setting`): Permanently anchored on the far right on both Notes and Tasks tabs.
     - Zero synthetic buttons (no search button, no grid toggle, no 3-dots overflow in action bar).

6. **CI Remote Verification:**
   - Zero local laptop build load: All Gradle compilation, unit tests, lint checks, and APK packaging executed on remote GitHub Actions runners.
   - Verified 100% green on commit `d32dde8` (CI Run #41106699088).
