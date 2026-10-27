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
