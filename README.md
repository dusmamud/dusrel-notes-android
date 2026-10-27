# Notes

An original, local-first native Android notes app by dusrel.

Package `com.dusrel.notes`, app name **Notes**. Kotlin + Jetpack Compose (Material 3),
Clean Architecture + MVVM, Room for storage, DataStore for settings and vault
metadata, WorkManager + AlarmManager for reminders. No backend, no account, no cloud,
no internet permission.

## Features

1. Notes list — search, sort (pinned first / recent / title), pin, archive, trash with
   restore and permanent delete, note colour, tap to open.
2. Rich-text editor — title + body with **real spans rendered in the editor itself**
   (bold / italic / underline on the selection, heading sizes, bullet and numbered
   paragraphs). Spans are stored as JSON alongside the plain text (`richTextSpans`
   column); old marker notes migrate to spans once on load. No marker preview.
3. Todo/checklist notes — add items, check off, delete items, progress count.
4. Folders — create folders, move notes into them from the editor, filter the list
   by folder, delete a folder (its notes return to "No folder"). Locked private
   notes are excluded from folder counts and folder-delete moves.
5. Private notes — **real encryption, not just hiding.** Private note title,
   content AND spans JSON are encrypted at rest with AES-GCM; the key is derived
   from the user's PIN via PBKDF2-HmacSHA256 (100,000 iterations, random 32-byte
   per-install salt). The PIN itself is never stored. A wrong PIN fails via the
   GCM auth tag. After 5 consecutive wrong attempts the vault locks for 30 seconds
   (persisted, so it survives process death). Private notes stay hidden from the
   main list, search, folders, reminders, widgets and export while locked, and a
   locked note cannot be opened from a widget or notification tap. If you forget
   the PIN, private notes cannot be recovered. Limitation, stated plainly:
   checklist (todo) text for private notes is stored in plaintext in the todos
   table — it is never surfaced while locked, but it is not encrypted at rest.
6. Reminders — pick a date/time on a note. When the device grants exact alarms,
   the reminder is scheduled with `AlarmManager.setExactAndAllowWhileIdle` and
   shown as "exact"; otherwise it falls back to WorkManager and is shown as
   "best-effort", with a button to open the exact-alarm settings. The reminders
   list shows the path per reminder. After reboot, a boot receiver re-schedules
   future reminders on the correct path. Trashing or permanently deleting a note
   cancels its alarm on both paths.
7. Full-text search — across titles, note content, todo item text, and folder names
   (pure `domain/search/searchNotes` plus Room `LIKE` queries). Locked private
   notes are excluded from search.
8. Doodle/handwriting canvas — original pointer-drag canvas with colour choice and
   clear; strokes serialise to JSON in the note's `doodleData` field and reload.
9. Widgets — `NotesListWidgetProvider` is a **live list**: a `RemoteViewsService` /
   `RemoteViewsFactory` reads Room (non-private, non-trashed, non-archived notes)
   and renders title + snippet rows; tapping a row opens that note (locked private
   notes go to the Private screen instead). The list refreshes when stored notes
   change. `QuickNoteWidgetProvider` is a single quick-note button. RemoteViews
   only, no Compose Glance.
10. Export/share — share a note as text via `ACTION_SEND`; export a single note
    (now including its folder) or the whole library as JSON via the Storage Access
    Framework; import a JSON backup. Private notes are excluded from library
    export unless unlocked; an unlocked explicit export carries decrypted content
    with stale ciphertext stripped.
11. Settings — Light/Dark/System theme, language placeholder (shows the device
    locale; no separate translations shipped), About screen, local backup export/import.
12. Empty states (list, trash, folders, reminders), a brief loading state on first
    list load, and a 3-page first-run guide shown once (DataStore flag).

## The no-backend seam

`domain/sync/SyncProvider` is the seam where a backend could be added later.
It ships only as `NoOpSyncProvider`: it never uploads or downloads anything.

## Build (on a laptop — not in this sandbox)

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Requires JDK 17. Gradle wrapper 9.6.0, AGP 9.4.0, Kotlin 2.4.10,
Compose BOM 2026.09.00, Room 2.8.5, DataStore 1.2.1, WorkManager 2.12.0.

## Honest sandbox limit

The Gradle build and APK were **not** run in the sandbox where this code was
written: Gradle cannot run there because Java TCP to the Gradle daemon is blocked
(pinned 2026-10-05). CI (`.github/workflows/android-ci.yml`) is the first place
the build is proven. See `BUILD_REPORT.md` for exactly what was and was not verified.
