# File Organizer

An Android app that scans your device's storage and organizes, cleans, and analyzes files —
built with Kotlin and Jetpack Compose. Includes an offline bilingual (English/Swahili) command
parser and an optional AI chat assistant.

This is a full Kotlin/Compose rewrite of an earlier Sketchware-generated Java/View version of the
same app. See [MIGRATION.md](MIGRATION.md) for exactly what changed and why.

## Features

- **35 file operations** across Organization, Move & Relocate, Clean Up, Social Media, Developer
  Tools, Storage Analysis, and Automation
- **Full SD card support** — scanning, cleanup, and organizing all operate across every detected
  storage volume (internal + SD card/USB-OTG), not just internal storage. A scope selector on the
  Commands tab lets you restrict a run to "Internal Only" or "SD Card Only" when an SD card is
  present; "Move to SD Card" commands remain a deliberate exception for pulling files onto it
- **Folder-scoped actions** — pick a specific folder via an in-app browser and every command
  (built-in or custom) runs inside that folder only, instead of sweeping the whole device
- **Protected folders** — source-code repos and firmware/ROM dumps are auto-detected (by marker
  files like `build.gradle`, `.git`, or a `system`/`vendor` layout) and excluded from bulk
  organize/move/delete commands by default, so they can't be shredded file-by-file. You can also
  mark any folder as protected by hand. Explicitly scoping a command into a protected folder via
  the folder picker overrides the automatic skip for that one run
- **AI-built custom commands** — describe a specific operation in the AI Chat tab ("move all .mkv
  files from Downloads to the SD card Movies folder") and it's parsed into a concrete plan —
  matched files, source, destination — shown for review before anything changes, the same way a
  code-review tool shows a diff before you commit it. Works fully offline via keyword heuristics;
  an optional Gemini key makes the parsing more flexible
- **Settings tab** — add or remove your own Gemini API key at runtime (encrypted on-device, no
  rebuild needed), plus toggles for automation notifications, scan behavior, confirmation
  dialogs, auto-protection, and a one-tap way to clear the on-device scan index
- **Automation notifications** — Daily Auto-Organize and Nightly Cleanup now post a summary
  notification when they finish running in the background
- **AI Chat tab** — describe what you want in plain English or Swahili ("panga picha za skrini",
  "clean up my whatsapp junk") and it detects the matching command; works fully offline, with an
  optional Gemini-powered mode for more natural replies
- **Live preview** — see which files a command will touch before running it
- **Undo** — move operations (not deletes) can be reversed from the Log tab
- **Background automation** — Daily Auto-Organize and Nightly Cleanup run on a schedule via
  WorkManager, no need to open the app
- **Duplicate detection** verified by SHA-256 hash, size-bucketed first for speed
- **Persisted preferences** — your last command selection and automation toggles survive an app
  restart

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android 8.0 (API 26) or higher on-device

## Setup

1. Clone the repo and open it in Android Studio, or build from the command line:
   ```
   ./gradlew assembleDebug
   ```
2. (Optional) Enable the online AI Chat mode one of two ways:
   - **In-app (recommended)** — open the **Settings** tab → *AI Integration* → paste your key and
     tap **Save Key**. It's stored encrypted on-device (`EncryptedSharedPreferences`, Android
     Keystore-backed) and takes effect immediately, no rebuild required. Tap **Test Connection**
     to verify it works, or **Remove Key** to go back to offline-only.
   - **At build time** — copy `local.properties.example` to `local.properties` and add:
     ```
     GEMINI_API_KEY=your-key-here
     ```
     `local.properties` is gitignored — **never commit a real key**. A key entered in-app always
     takes priority over this one while it's set.

   Get a free key from [Google AI Studio](https://aistudio.google.com/apikey) — there's also a
   shortcut button for this right in the Settings screen.

   Without any key, the app runs entirely offline and every command still works; only chat
   replies and custom-command parsing fall back to simpler, locally-computed logic.

## Permissions

The app requests "All files access" (`MANAGE_EXTERNAL_STORAGE`) on Android 11+, since organizing
and cleaning up files across the whole device is the core feature. On Android 10 and below it
falls back to the classic `READ/WRITE_EXTERNAL_STORAGE` runtime permissions.

## CI/CD

`.github/workflows/android-ci.yml` runs on every push/PR:

- **build** — assembles a debug APK and runs unit tests, uploaded as a build artifact
- **release** — on a `v*` tag, builds a signed release APK + AAB and publishes them to a GitHub
  Release

To enable signed releases, add these secrets under **Settings → Secrets and variables → Actions**:

| Secret              | Value                                              |
|---------------------|-----------------------------------------------------|
| `KEYSTORE_BASE64`   | Your release keystore, base64-encoded (`base64 -w0 your.keystore`) |
| `KEYSTORE_PASSWORD` | Keystore password                                  |
| `KEY_ALIAS`         | Key alias inside the keystore                       |
| `KEY_PASSWORD`      | Key password                                        |

The workflow verifies all four are present before attempting a signed build, and deletes the
decoded keystore file immediately afterward regardless of outcome.

## Architecture

```
data/           Models (FileMetadata, ExecutionResult, CommandType, CustomAction),
                FileTypeResolver, MetadataManager (JSON persistence),
                PreferencesManager (DataStore), ApiKeyManager (encrypted key storage)
domain/         StorageScanner, StorageVolumeManager, CommandMatcher, CommandExecutor,
                CommandParser, CustomCommandParser, GeminiClient, ProtectionRules
automation/     WorkManager worker + scheduler for background automation, NotificationHelper
permissions/    Storage + notification permission helpers (scoped storage + legacy)
ui/             MainViewModel (StateFlow), MainScreen, screens/ (Commands, Chat, Log, Settings),
                components/ (glass-morphism UI, folder picker), theme/
```

`CommandMatcher` is the single source of truth for "does this file match this command" — used by
both the live preview panel and the executor, so they can never drift out of sync.

## License

MIT — see [LICENSE](LICENSE).
