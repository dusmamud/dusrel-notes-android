# UI Analysis — Original Notes app (com.dusrel.notes)

Functional analysis only. This file contains no reference-app strings,
layout XML, images, or decompiled code — only what screens do, how the
user moves between them, and where this app's own implementation differs.

## 1. Reference behaviour, summarised functionally

The reference app was analysed functionally (screens, navigation, states)
from an earlier UI analysis pass. This section restates that behaviour in
the app's own terms; it is not a copy of anything from the reference.

### Screens grouped by purpose

- **Launcher list (home shell).** A scrollable list of notes and
  checklists, with tabs/filters for active, archived, and trashed notes,
  plus a folders view. Each row shows a title, a short preview, and
  status marks (pinned, private, reminder). Tapping a row opens the
  editor; row-level actions cover pin, archive, trash/restore, moving to
  a folder, and sharing.
- **Editor.** A single-note editing screen for text notes, checklists,
  and doodles. It offers title/body editing, colour, pin/private
  switches, folder move, reminder setting, share, and export. A checklist
  note additionally shows its items with done toggles and an add-item row.
- **Folders.** Create/rename/delete folders; opening a folder filters
  the list to that folder's notes.
- **Private area.** A PIN-gated area holding private notes. Locked notes
  are hidden (not merely dimmed) until the PIN is entered.
- **Doodle / handwriting.** A freehand canvas inside a doodle note, with
  colour choice and a clear action. Strokes belong to the note and are
  saved with it.
- **Widgets.** A home-screen list widget showing recent notes (tap a row
  to open it) plus quick-entry buttons, and a small quick-note widget
  that creates a note straight from the home screen.
- **Share / export flows.** Share a note as plain text through the
  system chooser; export a single note (or a backup) as JSON.
- **Reminders / alerts.** A reminder set on a note fires a notification
  at the chosen time; reminders are listed on their own screen and can
  be cancelled there. Exact timing is used when the system allows it,
  with a best-effort fallback otherwise.
- **Settings / permissions.** Theme choice, private-notes PIN setup,
  backup import/export, and the notification / exact-alarm permission
  prompts the system requires for reminders to work.

### Navigation and intents (functional)

- Bottom tabs: Notes list, Folders, Reminders, Settings.
- List → editor on row tap; editor → back to list via the top-bar back
  button; private area and settings are reached from the list header.
- Widget row tap and reminder notification tap both deep-link to the
  editor for that note (locked private notes route to the private area
  instead); the quick-note widget creates a fresh text note in the editor.
- Share uses the system text-share chooser; export uses the system
  document picker. No custom account or cloud destination is involved.

### States a screen must handle

- Loading (brief, while the database first emits), empty list, empty
  search results, empty trash, empty archive, empty private area, empty
  reminders, empty folders.
- Locked private area (PIN entry / PIN setup / lockout after failures).
- Trashed vs archived vs active filtering, pinned ordering, folder
  filtering, and search filtering across titles, bodies, checklist
  items, and folder names.

### Explicitly out of scope (excluded from this app)

AI writing/assistant features, cloud sync, any account sign-in, and
audio recording / voice notes. The app is local-only: its sync provider
is a no-op by design, and none of these features were implemented.

## 2. Gap table — this app before this change

Evidence is the file that shows the gap. Status: PRESENT / PARTIAL /
MISSING, judged against the functional behaviour in §1.

| Area | Status | File evidence |
|---|---|---|
| List card: updated date | MISSING | `presentation/screens/ListScreens.kt` — `NoteCard` showed title, type icon, status icons, and a one-line preview only; `Note.updatedAt` was never rendered. |
| List card: checklist progress | MISSING | `ListScreens.kt` — `todoProgress()` existed in `domain/model/Models.kt` but the list screen never read todos per note, so no progress text appeared. |
| List card: doodle snippet fallback | MISSING | `ListScreens.kt` — preview was the first content line or blank; a doodle note with no text showed a blank line. |
| Long-press / swipe on list card | MISSING | `ListScreens.kt` — cards had only tap-to-open plus small icon buttons; no long-press gesture or actions sheet existed. |
| Editor: archive / trash actions | MISSING | `presentation/screens/EditorScreen.kt` — the editor had share and export buttons but no archive or trash action; archiving/trashing was list-only. |
| Editor: created/updated date line | MISSING | `EditorScreen.kt` — `Note.createdAt`/`updatedAt` were never rendered. |
| Editor: duplicate back affordance | PRESENT (bug) | `EditorScreen.kt` had an in-screen back row while `MainActivity.kt` already provides a Scaffold TopAppBar back button on non-tab routes. |
| Search no-results copy | PARTIAL | `ListScreens.kt` — an empty list always showed the generic "Nothing here yet" copy, even when a search query was active. |
| Private empty state | PARTIAL | `ListScreens.kt` — `PrivateScreen` showed a single plain text line, unlike the icon + title + body pattern of the other empty states. |
| Widget rows: date + pin | MISSING | `presentation/widgets/NotesWidgetService.kt` + `res/layout/widget_note_item.xml` — rows set title + snippet only. |
| Widget list: empty view | MISSING | `presentation/widgets/Widgets.kt` — no `setEmptyView` call; an empty list showed a blank widget. |
| Quick-note widget | PRESENT | `Widgets.kt` + `res/layout/widget_quick_note.xml` — single quick-note button, unchanged by this change. |
| Doodle: undo | MISSING | `presentation/screens/DoodleCanvas.kt` — only colour choice and Clear existed. |
| Doodle: stroke width | MISSING | `DoodleCanvas.kt` — every stroke drew at a fixed width; `domain/doodle/DoodleCodec.kt` stored no width. |
| Doodle: canvas follows theme | MISSING | `DoodleCanvas.kt` — canvas background was hardcoded white while the rest of the app follows `NotesTheme`. |
| Doodle: eraser tool | MISSING (deliberately not added) | See §4. |
| Rich-text toolbar: text colour, strikethrough, links, images, alignment | MISSING | `presentation/screens/RichTextEditor.kt` — toolbar covers bold/italic/underline/lists only. |
| Rich-text editor: undo/redo | MISSING | `RichTextEditor.kt` — no undo/redo stack. |
| Inline checklist toggle inside text notes | MISSING | Text notes render spans only; toggling only exists on the checklist section of checklist notes. |
| PDF / image export | MISSING | Export is JSON-only (`domain/backup/BackupCodec.kt`). |
| Recurring reminders | MISSING | `domain/reminder/ReminderScheduler.kt` — one-shot only. |
| Biometric unlock for private notes | MISSING | Private unlock is PIN-only (`presentation/screens/ListScreens.kt` `PrivateScreen`). |
| Search lowercasing locale | PARTIAL (bug) | `domain/search/Search.kt` used default-locale `lowercase()`, which mis-lowercases (e.g. Turkish "I") and can break matching. |

## 3. What this change fixes

All fixes are in the app's own Material 3 + Poppins + Material Symbols
Rounded design; no new permissions, no package/name change, no backend
change, no new actions beyond reusing existing ones.

- **List card** (`ListScreens.kt`, new `domain/list/ListCard.kt`):
  updated date under the snippet (formatted with the observable locale
  from `LocalConfiguration`, never `Locale.getDefault()` in a
  composable), checklist progress text for checklist notes reusing
  `todoProgress()` (the ViewModel now exposes its per-note todos map
  read-only as `todosByNote`), and a "Doodle" snippet fallback for
  doodle notes with empty content. Card style is unchanged.
- **Long-press** (`ListScreens.kt`): long-pressing a card
  (`combinedClickable`) opens an actions dialog reusing existing
  actions only — pin/unpin, archive/unarchive, move to trash/restore,
  move to folder (folder picker dialog), share (system chooser) — plus
  permanent delete with the existing confirmation dialog on the Trash
  tab. No new permissions or actions were invented.
- **Editor** (`EditorScreen.kt`): archive/unarchive and move-to-trash
  buttons beside share/export; trashing returns to the list and cancels
  the note's reminder; a trashed note opened in the editor offers
  permanent delete behind the same confirmation pattern as the list.
  A "Created … • Updated …" line (observable locale) sits under the
  action row. The duplicate in-screen back row is removed; the Scaffold
  TopAppBar back button in `MainActivity.kt` is now the single back
  affordance.
- **Search no-results** (`ListScreens.kt`): when a query is active and
  nothing matches, the empty state names the query ("No results for
  …") with a Clear-search action, instead of the generic copy.
- **Private empty state** (`ListScreens.kt`): now uses the same icon +
  title + body pattern as the other empty states.
- **Widget list rows** (`domain/widget/WidgetNotes.kt`,
  `NotesWidgetService.kt`, `res/layout/widget_note_item.xml`): rows now
  carry a meta line (pin indicator + updated date, RemoteViews text
  only, no new assets). The list widget also gets an empty view
  (`setEmptyView` in `Widgets.kt`, `res/layout/widget_notes_list.xml`,
  new `widget_no_notes` string). The quick-note widget is untouched.
- **Doodle** (`DoodleCanvas.kt`, `DoodleCodec.kt`): Undo-last-stroke
  button, Thin/Medium/Wide stroke-width choice (3/6/12 px, persisted
  per stroke in the codec; old stored data without a width decodes at
  the historic 6 px), and the canvas background now follows the app
  theme (`colorScheme.surface`) instead of hardcoded white.
- **Search matching** (`domain/search/Search.kt`): all lowercasing is
  now `Locale.ROOT` — logic only, no composable change.

## 4. Eraser: omitted, and why

A separate eraser tool was deliberately **not** added. Erasing part of
a stroke would require splitting stored strokes (and their persisted
data) at the erase point; a wrong split silently destroys drawing data
with no way back. Undo-last-stroke plus Clear covers the safe scope:
both remove whole strokes, so stored stroke data is never partially
rewritten. If an eraser is wanted later, it should be built as
stroke-splitting with its own round-trip tests, not as a quick overlay.

## 5. What remains out of scope (not implemented in this pass)

- Rich-text toolbar: text colour, strikethrough, links, images,
  alignment.
- Undo/redo inside the rich-text editor.
- Inline checklist toggle inside text notes.
- PDF / image export (JSON export only).
- Recurring reminders.
- Biometric unlock for private notes.
- AI features, cloud sync, account sign-in, audio recording (excluded
  by scope, see §1).
- No Gradle build or APK is claimed: Gradle cannot run in this sandbox,
  so compile/device proof is the next CI run (needs a separate push
  approval) plus on-device checking.

## 6. Verification scope for this change (honest limits)

- Static: every new/changed resource ID exists in a layout or
  `strings.xml`; no new navigation route was added (all routes
  referenced already exist in `MainActivity.kt`); no
  `Locale.getDefault()` inside any composable (the one
  `Locale.getDefault()` use is in `NotesWidgetService.kt`, which is not
  a composable); a case-insensitive search for the reference vendor's
  name over `app/src/main` returns 0 matches;
  manifest permissions unchanged (POST_NOTIFICATIONS,
  SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED only); all 13 XML files
  under `app/src/main/res` parse.
- Pure-JVM tests (standalone Kotlin 1.9 compiler + JUnit 4, not
  Gradle): 98 tests run, 93 pass. The 5 failures are all in the
  untouched `BackupCodecTest`, which fails only because the harness
  links `org.json` from the Android SDK stub jar
  (`RuntimeException: Stub!` on `JSONObject` init) — a test-harness
  limitation, not a code failure. New/changed tests all pass:
  `ListCardTest` (5), the locale-explicit search test, the widget
  meta-line test, and the doodle width round-trip + old-format test.
- Not verified here: Compose compilation, Gradle/CI build, APK, and
  on-device behaviour (long-press gesture, widget empty view, doodle
  undo/widths, theme canvas). Those need the CI run + device check.

## 7. Main-screen + bottom-navigation redesign (this change)

Functional summary only: no reference-app strings, layouts, colours,
measurements, or code are reproduced here. The analysis below is the
functional behaviour supplied for this pass (launcher hosts a notes list
page and a todo page; folders are a filter inside the notes page plus
management dialogs; trash is a view/dialog, not a tab; archive has no
class-name evidence in the reference) restated in this app's own terms.
Anything static analysis could NOT prove (bottom-menu item count,
labels, order, any centre create tab, the exact default destination,
whether tabs and pager are one control or two linked controls, any
notes-page sort control) is treated as unknown and is NOT asserted here.

### 7.1 Main-screen functional analysis used

- **Destinations.** Two first-class pages in the launcher shell: a notes
  list page and a todo page. Bottom destinations functionally = Notes +
  Todo. Folders are a filter strip inside the notes page plus management
  dialogs, not a tab in the reference. Trash is a dialog/view, not a tab.
  Archive has zero class-name evidence in the reference, so it is kept
  here as an original feature of this app, not as a parity item.
- **Todo page (reference, functional).** A full first-class page:
  categories, inline create, its own search, done/not-done handling,
  drag ordering. Drag ordering is NOT implemented here (see §7.4).
- **Notes page (reference, functional).** Folder strip/filter, per-page
  search (notes + folders scope), empty states, grid/list view toggle,
  multi-select action mode, row indicators (pin, colour, reminder,
  private, folder label, thumbnail/handwriting/voice). Multi-select is
  NOT implemented here (see §7.4); thumbnail/handwriting/voice rows are
  out of scope (audio/handwriting activities excluded).
- **Header / top bar (reference, functional).** Options menu + settings
  entry + private entry + search entry; create affordances live on the
  pages. Create flow in the reference is separated: text -> editor,
  todo -> todo page, doodle -> its own activity. This app keeps its
  single create dialog -> single editor as a simplification, but a
  direct doodle path remains (the dialog's Doodle entry, and the Todo
  destination's New checklist entry).
- **Navigation (reference, functional).** Editor/doodle return by
  finish/popBackStack; tab switches swap pages without a back-stack
  push. This app implements exactly that: switching tabs does not add
  to the back stack, and back from a tab exits (or returns to the start
  destination), never walks through tab history.
- **Excluded items.** Account, cloud sync, AI, audio/voice notes, mind
  notes, handwriting activities: none added, none imitated. The backend
  seam stays local-only (no-op sync provider) and the manifest
  permissions are unchanged.

### 7.2 Gap table — main screen before this change

| Area | Status | File evidence |
|---|---|---|
| Todo as a first-class destination | MISSING | `MainActivity.kt` (before: tabs Notes/Folders/Reminders/Settings, no todo route) — todo existed only as `NoteType.TODO` inside the notes list (`presentation/screens/ListScreens.kt` `NotesListScreen`). |
| Todo destination: own search | MISSING | No todo route existed, so no todo-scoped search existed; the only search was the notes list's global `query` (`presentation/viewmodel/NotesViewModel.kt`). |
| Todo destination: inline item done-toggle / add-item | MISSING | Done toggles and add-item existed only inside the checklist editor (`presentation/screens/EditorScreen.kt`), not on any list page. |
| Bottom navigation: Todo tab | MISSING | `MainActivity.kt` `mainTabs` had 4 entries, none for todo. |
| Bottom navigation: state preservation on tab switch | PARTIAL | `MainActivity.kt` already used `popUpTo(LIST)` + `launchSingleTop` (no stack growth), but without `saveState`/`restoreState`, so a tab's scroll/filter state was not restored on return. |
| Notes page: folder filter strip | MISSING | `ListScreens.kt` had only a single "Folder: X ✕" clear button when a filter was active; no strip listing all folders. Folder management itself was PRESENT on the Folders tab. |
| Notes page: grid/list view toggle | MISSING | `ListScreens.kt` rendered a `LazyColumn` only; no layout switch existed. |
| Note row: folder name label | MISSING | `ListScreens.kt` `NoteCard` rendered date + progress only; the folder name was in ViewModel state (`folders`) but never shown on a row. |
| Notes page: Archived filter | PRESENT (kept) | `ListScreens.kt` All/Pinned/Archived/Trash tabs — Archived is retained unchanged; it is an original feature, not a parity item. |
| Notes page: sort dialog | PRESENT (kept) | `ListScreens.kt` sort dialog (Recent / Title / Pinned first) — retained; sort is shared with the Todo destination. |
| Create: text / checklist / doodle from one entry | PRESENT (kept, simplified) | `ListScreens.kt` create dialog offers all three types; the Todo destination adds a direct "New checklist" entry reusing the same editor route. |
| Multi-select action mode on the notes page | MISSING (deliberately not added) | See §7.4. |
| Todo page: drag ordering | MISSING (deliberately not added) | See §7.4. |

### 7.3 What this change redesigns (before / after, in words)

- **Bottom navigation — before:** 4 tabs: Notes, Folders, Reminders,
  Settings. **After:** 5 tabs: Notes, Todo, Folders, Reminders,
  Settings (decision: keep-all, option (a)). Justification: a 5-item
  Material 3 `NavigationBar` is the standard layout and nothing in the
  existing code breaks at 5 items, so folding Folders away would have
  deleted a working management destination for no proven benefit.
  Folders stays the management destination AND the notes page gains a
  folder filter strip (below), which matches the reference's
  filter-inside-notes-page function without deleting anything. The Todo
  tab uses the existing `CHECKLIST` symbol, filled when selected, per
  the app's icon rule. Notes remains the start destination; the default
  destination is not asserted beyond that (the reference default was
  not proven).
- **Todo destination — before:** none; checklists were rows in the
  notes list. **After:** new route `todo` (`MainActivity.kt`
  `Routes.TODO`) rendering `TodoScreen` (`ListScreens.kt`). It lists
  only checklist notes (new pure filter `domain/list/TodoList.kt`
  `todoNotes()` / `filterTodosByCompletion()` — no new database, no new
  table, same `NoteType.TODO` + `TodoItem` model). It supports: opening
  a checklist in the existing editor (row tap / "Open in editor"),
  creating a new checklist from within the destination ("New
  checklist" -> existing create/checklist editor route), marking items
  done/undone inline (checkbox per item, existing `toggleTodo`), adding
  items inline (per-card "New item" + Add, existing `addTodo`),
  deleting items inline (existing `deleteTodo`), moving a checklist to
  trash from its card, its own search (checklist-scoped, reusing the
  existing `searchNotes` logic after the todo-only filter), and
  All / To do / Done completion filters. Archived and trashed
  checklists are deliberately excluded here — they stay visible in the
  Archived / Trash tabs of the Notes list, so no checklist's lifecycle
  state is hidden by the new destination. Locked private checklists are
  hidden until unlocked, same as the notes list.
- **Tab-switch state preservation — before:** `popUpTo(LIST)` +
  `launchSingleTop` only. **After:** `popUpTo(Routes.LIST) {
  saveState = true }` + `launchSingleTop = true` + `restoreState =
  true` on every bottom-tab tap. `Routes.LIST` (not the graph's start
  destination, which is the Guide on first run before it is popped) is
  used as the anchor because LIST is always in the stack whenever the
  bottom bar is shown. Editor back behaviour is unchanged
  (`popBackStack`): editor opened from Todo returns to Todo, editor
  opened from Notes returns to Notes. The Folders -> Notes jump now
  also restores Notes state (`restoreState = true`).
- **Notes list header + filters — before:** title row (Notes +
  private + settings icons), search field, Notes/Archived/Trash tab
  buttons, sort button, single "Folder: X ✕" clear button, bottom "New
  note" button. **After:** the same structure, made clearer in the
  app's own style — title row unchanged; search entry unchanged;
  Notes/Archived/Trash tabs unchanged (Archived NOT removed); a
  horizontally scrollable folder filter strip is added above the sort
  row (All folders + one chip per folder, reusing the existing
  `folderFilterId` ViewModel state — no new data model, and the
  Folders tab remains the place to create/delete folders); the sort row
  gains a grid/list toggle icon beside the sort button; the "Folder:
  X ✕" clear button is kept. The primary create entry stays the single
  bottom "New note" button offering Text note / Checklist / Doodle
  through the existing create dialog and routes — one clear entry, not
  a FAB plus a header action plus a button.
- **Small polish — before:** list/grid was list-only; rows showed date
  + progress only. **After:** a grid/list layout switch on the notes
  page (`LazyVerticalGrid`, 2 columns, same `NoteCard` in both layouts)
  held in in-memory `remember` state only — deliberately NOT persisted
  (no DataStore key added; it resets on process death, stated here so
  it is not mistaken for a persisted setting). Note rows now show the
  folder name label (with folder icon) when the note is in a folder,
  in both the notes list and the Todo cards, using the folder name
  already available in ViewModel state.
- **Theme / icons / package / name:** unchanged. Light/Dark/System
  theme, Poppins, Material Symbols Rounded filled-when-selected, package
  `com.dusrel.notes`, app name Notes — all untouched.

### 7.4 Deliberately NOT changed / NOT implemented

- **Multi-select action mode:** skipped in this pass (large change:
  selection state, action-mode bar, batch actions across tabs). Listed
  here, not silently dropped. Long-press actions dialog from §3 remains
  the per-note action path.
- **Todo drag ordering:** not implemented. Checklists keep their
  existing item order (insertion position); no reorder UI or model
  change was added.
- **Folders destination:** not deleted, not folded into a tab-less
  strip-only design. The strip is additive; management stays on the
  Folders tab.
- **Reminders, Archived, private notes, widgets:** untouched except
  that the Todo destination reuses the existing private-lock hiding
  rule. No new permission, no account, no sync, no AI, no audio.
- **Create flow:** not split into separate activities. The single
  create dialog -> single editor simplification is kept, with the Todo
  destination's direct checklist entry as the only new create path.
- **Grid/list persistence:** not persisted (see §7.3). A DataStore key
  for it would be trivial to add later if wanted.

### 7.5 Verification scope for this change (honest limits)

- Static: route `todo` exists in `MainActivity.kt` and every bottom
  destination (Notes, Todo, Folders, Reminders, Settings) has a
  composable — no dead ends; tab taps use
  `popUpTo(LIST){saveState}` + `launchSingleTop` + `restoreState`,
  editor back uses `popBackStack`; no `Locale.getDefault()` inside any
  composable (the one pre-existing `Locale.getDefault()` use is in
  `NotesWidgetService.kt`, which is not a composable, and is untouched
  here); a case-insensitive search for the reference vendor's name
  over `app/src/main` returns 0 matches; manifest permissions unchanged
  (POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED
  only); package `com.dusrel.notes` and app name Notes unchanged; new
  screen labels are hardcoded literals, following the existing screens'
  pattern (existing screens do not use `strings.xml` for labels;
  `strings.xml` itself is untouched).
- Pure-JVM tests: new `TodoListTest` (3 tests: todo-only/active/
  private-lock filtering, completion filter incl. empty-checklist-is-
  OPEN rule) covers the new `domain/list/TodoList.kt` logic. Run under
  a real JUnit 4.13.2 runner (jar from the local Gradle distribution,
  plus hamcrest-core 1.3): **OK (3 tests)**. Gradle itself cannot run
  in this sandbox per the pinned limitation, and the system `kotlinc`
  is 1.3-era and cannot parse trailing commas that the untouched
  existing `Models.kt` also uses, so `/tmp` copies with only trailing
  commas stripped were compiled for the run. The repo files themselves
  were not modified for the harness.
- Not verified here: Compose compilation, Gradle/CI build, APK, and
  on-device behaviour (5-tab bar layout at small widths, tab-state
  restoration, Todo inline toggles, grid layout, folder strip scroll).
  Those need the CI run (separate push approval) plus on-device
  checking. Nothing was committed or pushed in this pass.
