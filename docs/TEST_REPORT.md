# Test report - development checkpoint

Environment (2026-10-02): JDK 21 and Kotlin compiler available; no Android SDK, Gradle runtime, Maven network or device/emulator in this sandbox. GitHub app repository write operations returned HTTP 403. Hence **no Android build, APK, AAB, lint, unit instrumentation, screenshot audit or device performance test can honestly be marked passed here**.

Local code-only checks: separately compile the pure Kotlin model using installed kotlinc, run deterministic stand-alone assertions, and use static XML/JSON/project integrity scans. Record the exact command and result when run. `app/src/test/` contains JUnit model tests pending Android Gradle execution. GitHub Actions is configured for Gradle, Android lint, unit tests, debug APK, unsigned release AAB; it has not run in this repository.

Required pending gates: complete vehicle/device/apartment flow; process death rotation; Room migration after v1 change; CameraX 1.6.2 real devices; full PDF Arabic/English 100-image runs and actual font embedding; accessibility, permission denial, restored backup (including PDFs), zip-bomb checks, no network traffic, leaks and disk full, store screenshots from app, separate authorized signing key. Do not release based only on a successful compile.

## Continuation checkpoint (2026-10-02)
- Added the missing Android `app_name` resource referenced by the manifest.
- Eliminated a Kotlin expression-body return issue in EXIF parsing.
- Bounded imported file streaming to 100 MB **while copying**, rather than checking only after filling the disk.
- Added pure-JVM tested, overflow-safe power-of-two bitmap sampling for list previews, thumbnails and reports; list preview decode now runs off the Compose main thread.
- Deleting an operation now removes its stored PDFs as well as originals and derived thumbnails.
- Backup manifest verification rejects duplicate or undeclared entries.
- Comparison edits validate linked original/return assets and increment the local revision log; completing an already-completed session is idempotent.
- PDF creation checks the stored SHA-256 of each source photo and fails rather than silently exporting a modified original.
- Added an explicitly synthetic and read-only first-run example, separate from user data.
- These code changes have **not** received Android compilation/device verification. Do not treat this checkpoint as a release candidate.

## Verified local checkpoint (continued work, 2026-10-02)

- `kotlinc data/Model.kt /tmp/handover_model_sanity.kt -include-runtime -d /tmp/handover-model-sanity.jar && java -jar ...`: **PASS**, 12 deterministic invariants covering captured/skipped/pending, allowed state transitions and unique template keys.
- `kotlinc backup/ArchivePolicy.kt /tmp/handover_archive_sanity.kt -include-runtime -d /tmp/handover-archive-sanity.jar && java -jar ...`: **PASS**, happy path and rejection of duplicate or undeclared manifest entries, modified checksums, wrong size, missing metadata and invalid declaration.
- `python tools/source_sanity.py`: XML parsing, manifest allowBackup=false, removed INTERNET permission, localized resource presence, absence of keystores and CI files: **PASS**.
- **Not run**: Android Gradle compile, lint and instrumentation because this environment has neither Android SDK nor Gradle runtime nor external Maven/Gradle DNS. Source-only checks cannot establish that this app compiles.

The latest changes add English-first onboarding with demo data isolated in memory, stable CameraX binding across Compose recompositions, explicit treatment of pending checklist points before completion, revision-tracked manual matching of multiple photos, removal of exported PDFs on operation deletion, stronger ZIP manifest integrity checks and pagination for long PDF paragraphs. These implementation changes require Android-device validation.

## Offline smoke verification (continuation)
Commands from repository root:
```
kotlinc app/src/main/java/com/khaled/handover/data/{Model,ImageSampling}.kt app/src/main/java/com/khaled/handover/backup/ArchivePolicy.kt -d /tmp/handover-pure.jar
kotlinc -script -classpath /tmp/handover-pure.jar tools/model-smoke.kts
kotlinc -script -classpath /tmp/handover-pure.jar tools/backup-policy-smoke.kts
python3 tools/verify_checkpoint.py
```
All four commands passed locally. The tests do not load Android Room or CameraX, do not generate on-device PDFs and do not certify an installable release. GitHub write retry failed HTTP 403 (`Resource not accessible by integration`).

The verification sources are included under `tools/ModelSanity.kt`, `tools/ArchiveSanity.kt`, and `tools/source_sanity.py`. Reproduce offline with `tools/run-kotlin-sanity.sh` and an installed Java/Kotlin compiler. `app/src/test` contains equivalent JUnit tests for a future Android CI runner.

- Original asset ZIP exporter added (inspection-scoped source files + structured data + manifest). **Pending** actual Android SAF output validation and integration test with multiple media/large archives.
- Original vector icon added. **Pending** adaptive launcher visual review and high-density screenshot tests.

## Verified GitHub Android CI after compiler and lint fixes (2026-10-02)

- Successful run: https://github.com/khaledhasneen1993/Handover/actions/runs/37049194034
- Source SHA: `044470249ad9ecc7e79adc9090e86db0d10a6228`
- `:app:assembleDebug`, `:app:bundleRelease`, `:app:lintDebug`, `:app:testDebugUnitTest`: PASS, Gradle exit code 0.
- Android lint: 0 errors, 26 warnings. Remaining warnings include older library version suggestions, backup free-space allocation guidance and monochrome launcher icon.
- JVM unit tests: 4 model tests + 5 backup-policy tests, 0 failures and 0 skipped.
- CI artifact: `handover-development-artifacts` (artifact ID `11246056824`) contains debug APK, **unsigned** release AAB, lint and unit-test reports.
- APK install, on-device UI/camera operation, production signing, PDF text shaping and backup restore have not been verified. Do not confuse CI success with release certification.
