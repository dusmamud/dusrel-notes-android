# BUILD_REPORT — Notes (com.dusrel.notes)

Built 2026-10-27 in `~/workspace/dusrel-notes/` only. No other repo was modified.
No git push, no public repo, no APK was created. The directory is not a git
repository, so nothing was committed or pushed anywhere.

## Originality boundary (honoured)

Nothing was read, copied, extracted, or reused from `~/workspace/downloads/old-apk.apk`.
All UI, strings, layouts, icons, and code here are original. The only material taken
from the ShrinkIt Android project is generic build scaffolding the task explicitly
allowed: the version-catalog structure (adapted, Coil/Hilt/Play/browser dropped),
the Gradle wrapper files (generic tooling, already pinned to 9.6.0), the proven
AGP 9 top-level `kotlin.compilerOptions` pattern, and the Poppins font-family XML
shape (pointing at this app's own bundled Poppins files). No Xiaomi assets, strings,
permissions, or code are present — a grep for `miui`/`xiaomi` over `app/src/main`
returns nothing.

## Files created

Build: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
`gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties` (+ jar),
`gradlew`, `gradlew.bat`, `app/build.gradle.kts`, `app/proguard-rules.pro`,
`.gitignore`, `.github/workflows/android-ci.yml`, `README.md`, this file.

Config (exact, as specified): package `com.dusrel.notes`, app name Notes,
minSdk 26, targetSdk 36, compileSdk 37, JDK 17, Gradle wrapper 9.6.0, AGP 9.4.0,
Kotlin 2.4.10, Compose BOM 2026.09.00, Navigation Compose 2.10.2, Room 2.8.5,
DataStore preferences 1.2.1, WorkManager 2.12.0, KSP 2.3.10, JUnit 4.13.2,
coroutines 1.11.0, lifecycle-runtime-ktx 2.10.0, activity-compose 1.9.3,
appcompat 1.8.0, core-ktx 1.19.1. AGP 9 built-in Kotlin only
(`kotlin.compilerOptions jvmTarget JVM_17`, no `kotlin.android` plugin,
no `android.kotlinOptions`).

Manifest: `app/src/main/AndroidManifest.xml` — MainActivity (MAIN/LAUNCHER,
exported), both widget receivers with `APPWIDGET_UPDATE`, boot receiver.
Permissions only: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
`RECEIVE_BOOT_COMPLETED`. No storage permissions (SAF only), no internet.

Kotlin (main): `MainActivity.kt` (edge-to-edge, NavHost, bottom tabs,
first-run start-destination decision from the first real DataStore value);
`domain/model/Models.kt` (+ `sortNotes`, `todoProgress`),
`domain/model/RichText.kt`, `domain/repository/NotesRepository.kt`,
`domain/search/Search.kt`, `domain/sync/Sync.kt` (`NoOpSyncProvider` only),
`domain/backup/BackupCodec.kt` (org.json), `domain/doodle/DoodleCodec.kt`,
`data/local/Entities.kt` (Room entities + DAOs + database),
`data/local/RoomNotesRepository.kt`, `data/local/PreferencesRepository.kt`
(DataStore theme + SHA-256 PIN hash + guide flag),
`data/work/ReminderWork.kt` (WorkManager worker, scheduler, boot re-scheduler),
`presentation/theme/Theme.kt` (original tokens, Poppins),
`presentation/components/SymbolIcon.kt` (BasicText + bundled fonts, filled param),
`presentation/viewmodel/NotesViewModel.kt` (StateFlow, debounced autosave),
`presentation/screens/EditorScreen.kt`, `ListScreens.kt` (list, folders, private,
reminders), `SettingsScreens.kt` (settings, about, guide), `DoodleCanvas.kt`,
`presentation/widgets/Widgets.kt`.

Resources: `strings.xml` (original English), `colors.xml`, `themes.xml`
(original tokens), `font/poppins.xml`, launcher foreground/background vectors
(original folded-page glyph) + adaptive-icon XML, notification icon,
`layout/widget_notes_list.xml`, `layout/widget_quick_note.xml`,
`xml/notes_list_widget_info.xml`, `xml/quick_note_widget_info.xml`.
Fonts were already present and were not re-downloaded.

Tests (7 files, 31 test methods): `FakeNotesRepository.kt`,
`search/NoteSearchTest.kt`, `todo/TodoProgressTest.kt`,
`repository/FolderMoveTest.kt`, `repository/TrashRestoreTest.kt`,
`backup/BackupCodecTest.kt`, `richtext/RichTextMarkerTest.kt`,
`richtext/DoodleCodecTest.kt`.

## Features vs the 12-item list

1. Notes list — done: search field, 3 sort orders, pin, archive, trash with
   restore + confirmed permanent delete, colour, tap-to-open.
2. Rich-text editor — done, with the honest caveat stated in-app: plain-text
   markdown markers rendered in a preview area, not real spans. Autosave is
   debounced 500 ms in the ViewModel (whose scope outlives the editor screen).
3. Todo notes — done: add, check-off, delete, progress count.
4. Folders — done: create, folder list with counts, move from editor, filter
   the list by folder, delete (notes return to "No folder").
5. Private notes — done: set-PIN first time, SHA-256 hash only in DataStore,
   session-only unlock flag, private notes hidden from the main list until unlocked.
6. Reminders — done via WorkManager one-time work + notification + reminders
   list + boot re-schedule. DEVIATION (honest): reminders use WorkManager only,
   not `setExactAndAllowWhileIdle` — the exact-alarm permission is declared but
   the WorkManager path is used unconditionally, so exact timing is best-effort
   rather than exact. If exact timing is required, that is a follow-up change.
7. Full-text search — done: pure `searchNotes` (title, content, todo text,
   folder name) over the list, plus DAO `LIKE` queries used for export fallback.
8. Doodle canvas — done: pointer-drag strokes, colour palette, clear, JSON in
   `doodleData`, reloaded on open.
9. Widgets — done: both providers, RemoteViews layouts, widget_info XMLs,
   manifest declarations. No Glance. Note: the list widget shows open/new-note
   buttons, not a live list of note titles (that would need a RemoteViewsService).
10. Export/share — done: `ACTION_SEND` share, single-note and full-library JSON
    export via `ACTION_CREATE_DOCUMENT`, JSON import via `OpenDocument`.
11. Settings — done: Light/Dark/System, language placeholder showing the device
    locale with an explicit "no separate translations shipped" note, About screen
    stating the app is original and local-first with no cloud, local backup
    export/import.
12. Empty states, loading state, first-run guide — done: empty states for list,
    trash, folders, reminders; a brief loading state on first list load (a plain
    empty database is indistinguishable from "still loading" in a Room Flow, so
    the loading text is time-boxed to 600 ms); a 3-page guide shown once.

Navigation routes (all defined in `MainActivity`): `list`, `editor/{noteId}`,
`folders`, `private`, `reminders`, `settings`, `about`, `guide`. Bottom tabs:
Notes, Folders, Reminders, Settings; Private/About/Editor use the top back bar.

## What was verified locally (exact scope)

- Every `R.*` reference in Kotlin was grepped against `res/` — all exist
  (fonts, drawables, layouts, widget ids, strings). All 12 resource XML files
  parse as valid XML. All Kotlin files pass a brace-balance scan.
- The pure-JVM domain code and all 7 test files were compiled with a
  downloaded Kotlin 2.2 compiler (the sandbox's system kotlinc is 1.3 and cannot
  parse modern Kotlin — trailing commas, `lowercase()`, etc. — so it was not
  usable) against JUnit 4.13.2, real org.json 20240303, and coroutines 1.11.0:
  **31 tests, all passing.** This covers search, todo progress, sorting,
  folder move, trash/restore/permanent delete, backup round-trip (including
  quoted/newline content and null folder/reminder), rich-text markers, and
  doodle encode/decode round-trips.
- Why `testImplementation(org.json:json)` exists: android.jar ships only stubs
  for org.json that throw at runtime in JVM unit tests, so the real org.json
  library is added for tests only; main code uses org.json as specified.
- Two real bugs found and fixed during review: (1) the first-run guide would
  have shown on every launch because the StateFlow's initial `false` is
  indistinguishable from "guide not done" — now the start destination waits for
  the first real DataStore value; (2) an editor dispose-save would have
  overwritten the note with stale initial state — removed in favour of the
  ViewModel debounced save, whose scope outlives the screen.

## What is NOT verified

- `./gradlew testDebugUnitTest`, `lintDebug`, and `assembleDebug` were **not**
  run here: Gradle cannot run in this sandbox (Java TCP blocked for the Gradle
  daemon, pinned 2026-10-05). No APK exists and none is claimed. The CI
  workflow file is the first place the full Android build (including Compose,
  Room/KSP, DataStore, WorkManager, navigation, and widget XML compilation) is
  proven — a laptop or CI run is required before any install.
- Compose screens, navigation at runtime, notifications firing, widget
  placement, and on-device behaviour are therefore unverified beyond the
  static checks above. The widget list-content limitation and the
  WorkManager-only reminder timing (item 6/9 notes above) are the two known
  partial areas.

## Update 2026-10-27 — the 4 partials fixed (reviewer-audited, defects fixed)

All four previous partials were replaced with real implementations, then
integrated and re-audited. The first independent audit returned FAIL for
private notes (spans JSON leaked body text; locked notes reachable via
`note_id`); both defects and all minor findings below were fixed before
this report was updated.

1. **Rich text — real spans.** `domain/model/RichText.kt` now holds a
   `RichDocument` (text + `TextSpan` + `ParagraphSpan`) with span JSON
   persistence (`toSpansJson` / `richDocumentFromSpansJson`), a one-way
   `migrateMarkersToSpans` for old notes, and `presentation/screens/
   RichTextEditor.kt` rendering spans in the editor via AnnotatedString.
   `EditorScreen` saves `content` + `richTextSpans` together; migration
   now also saves the migrated text (an earlier version saved spans only).
   Tests: 16 span tests + 8 migration tests.
2. **Private notes — real encryption + lockout.** `domain/crypto/
   PrivateVault.kt` (AES-GCM, PBKDF2-HmacSHA256 100k iterations, 32-byte
   random salt, per-encryption IV; wrong PIN fails via the GCM tag) and
   `Lockout.kt` (5 failures → 30 s persisted lockout). Title, content
   **and spans JSON** are encrypted (`encryptedRichTextSpans` column, DB
   v3 + migrations 1→2 and 2→3); the key lives in memory only and clears on
   lock; unlock re-encrypts private notes (healing any legacy plaintext
   spans). Locked notes are gated in the editor, in `MainActivity`'s
   `note_id` handling (redirects to Private), in Reminders, in folder
   counts/moves, and in export. Private list now reads decrypted notes so
   titles show when unlocked. Tests: 14 vault + 10 lockout tests.
   Remaining limitation, stated plainly: todo/checklist text for private
   notes is stored in plaintext in the todos table; it is never surfaced
   while locked (list/search/widget/export all gate on locked), but it is
   not encrypted at rest.
3. **Reminders — exact alarm path.** `domain/reminder/ReminderScheduler.kt`
   decides EXACT vs FALLBACK; `ExactAlarmScheduler` uses
   `setExactAndAllowWhileIdle`, `ExactAlarmReceiver` posts the notification,
   WorkManager stays as fallback, the editor shows "exact"/"best-effort"
   with an exact-alarm settings button, the reminders list now shows the
   persisted path per reminder (`reminderExact` column), boot re-schedule
   uses the `ReminderCandidate` overload, and trashing/deleting a note
   cancels both paths. Tests: 11 scheduler tests.
4. **Widgets — live list.** `NotesWidgetService`/`RemoteViewsFactory`
   reads Room and renders title + snippet rows; the list layout now has a
   real `ListView` (`widget_notes_list_view`); the provider sets the
   adapter + click template; `MainActivity` handles `note_id`; widget data
   refreshes on stored-note changes and on resume. Quick-note widget
   unchanged. Tests: 5 widget tests.
5. **Small fix.** Single-note JSON export now includes the note's folder
   (was `emptyList`), and `BackupCodec` round-trips `richTextSpans`,
   `encryptedTitle`/`encryptedContent`/`encryptedRichTextSpans`,
   `isEncrypted`, and `reminderExact` (new test covers non-empty values).

**Verification for this update (exact scope):** pure-JVM suite compiled
with a standalone Kotlin 2.2 compiler + Temurin JDK 21 (Gradle not used):
**90/90 tests pass** (original 31 replaced/expanded: 24 rich-text, 24
crypto/lockout, 11 reminder, 5 widget, plus search/todo/folder/trash/
backup/doodle). Static re-check after fixes: `miui|xiaomi` 0 matches,
no deleted marker/PIN-hash APIs referenced, all XML parses, all widget
and manifest ids cross-checked, no `.git`, no `.apk` in the tree.
**Still NOT verified:** Gradle test/lint/assemble, APK, Compose runtime,
widget placement (including the weighted `ListView` height on launchers),
exact-alarm firing, boot re-schedule, and notification taps on a real
device — all need a laptop/CI build and on-device testing.
