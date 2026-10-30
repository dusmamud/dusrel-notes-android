# Project Status — Notes (com.dusrel.notes)

- **Date:** 2026-10-30
- **Current Milestone:** Dynamic Collapsible Action Bar & Vertical Scroll Top-Center Transition Verified & Passing CI
- **Remote CI Run:** [Run #41089253125](https://github.com/dusmamud/dusrel-notes-android/actions/runs/41089253125) — **PASSED 100% GREEN (Exit Code 0)**
  - `Unit tests`: completed -> success
  - `Lint`: completed -> success
  - `Assemble debug APK`: completed -> success (APK artifact generated)

---

## Verified Parity Milestones

1. **Dynamic Collapsible Action Bar & Top-Center Title Transition:**
   - **Expanded Mode (Top of list):** Left-aligned Large Title ("Notes" on tab 0, "Tasks" on tab 1) at **28sp Bold**. Center title is invisible (`alpha = 0.0f`). Top bar has zero elevation.
   - **Vertical Scroll Collapse (Scrolling down list):** Large title smoothly translates upwards and fades out; small compact title (**18sp Semi-Bold**) smoothly fades in right at the **TOP CENTER** of the 56dp Action Bar. Top bar gains subtle surface tint & hairline divider (`shadowElevation = 2.dp`).
   - **Scroll-up Restoration:** Scrolling back to top smoothly fades out the center title and restores the prominent large left title.
   - **Top-Right Icons (`action_bar_menu.xml` Parity):**
     - Folder Manager (`action_folder`): Visible on Notes tab, smoothly scales down (1.0f ➔ 0.0f) and fades out on Tasks tab.
     - Settings Gear (`action_setting`): Permanently anchored on the far right on both Notes and Tasks tabs.
     - Eliminated all synthetic buttons (no search button, no grid toggle, no 3-dots overflow in action bar).

2. **Collapsible Search Capsule & Gesture Triggers (`NestedHeaderLayout` Parity):**
   - By default completely collapsed (`headerClose="true"`). Zero permanent input box clutter.
   - Elastic pull-down overscroll gesture (`NestedScrollConnection`) smoothly reveals the rounded search capsule.
   - Deep overscroll pull-down on Notes tab triggers Private Vault unlock challenge (`miuix.springback.trigger.BaseTrigger.Action` parity).

3. **Sticky Folder Filter Pill Bar (`data_folder_list_layout.xml` Parity):**
   - Horizontal scrolling folder chips ("All" + individual user folders) positioned directly below header on Notes tab.
   - Completely hidden on Tasks tab.

4. **Tasks Screen UX:**
   - Starts directly under Action Bar.
   - 18x18dp squircle checkbox with 5dp radius.
   - Animated strike-through text upon task completion.
   - Collapsible accordion for Completed items with chevron switch icon.

5. **Single 58dp Primary Gold FAB Motion & Bottom Navigation Dock:**
   - Scales down and fades out during horizontal tab swipe (`!pagerState.isScrollInProgress`), springs back to 1.0f on page settle.
   - Tapping on Notes tab creates note / opens Note Editor.
   - Tapping on Tasks tab opens `CreateTaskBottomSheet` (24dp squircle corners).
   - Persistent 60dp Bottom Navigation Dock with gold active indicators.

6. **CI Remote Verification:**
   - Zero local laptop build load: All Gradle compilation, unit tests, lint checks, and APK packaging executed on remote GitHub Actions runners.
   - Verified 100% green on commit `4bbbe65`.
