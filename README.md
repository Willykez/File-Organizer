# File Organizer

An Android app that scans your device's storage and organizes, cleans, and analyzes files —
built with Kotlin and Jetpack Compose. Includes an offline bilingual (English/Swahili) command
parser and an optional AI chat assistant.

This is a full Kotlin/Compose rewrite of an earlier Sketchware-generated Java/View version of the
same app. See [MIGRATION.md](MIGRATION.md) for exactly what changed and why.

## Features

- **35 file operations** across Organization, Move & Relocate, Clean Up, Social Media, Developer
  Tools, Storage Analysis, and Automation
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
2. (Optional) To enable the online AI Chat mode, copy `local.properties.example` to
   `local.properties` and add your own key:
   ```
   GEMINI_API_KEY=your-key-here
   ```
   `local.properties` is gitignored — **never commit a real key**. Without one, the app runs
   entirely offline and every command still works; only chat replies fall back to a simpler,
   locally-computed response.

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
data/           Models (FileMetadata, ExecutionResult, CommandType), FileTypeResolver,
                MetadataManager (JSON persistence), PreferencesManager (DataStore)
domain/         StorageScanner, CommandMatcher, CommandExecutor, CommandParser, GeminiClient
automation/     WorkManager worker + scheduler for background automation
permissions/    Storage permission helpers (scoped storage + legacy)
ui/             MainViewModel (StateFlow), MainScreen, screens/ (Commands, Chat, Log),
                components/ (glass-morphism UI), theme/
```

`CommandMatcher` is the single source of truth for "does this file match this command" — used by
both the live preview panel and the executor, so they can never drift out of sync.

## License

MIT — see [LICENSE](LICENSE).
