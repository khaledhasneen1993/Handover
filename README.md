# Handover — Android development source

Offline-first, paid-once property-condition documentation for rental vehicles, devices and apartments. The app saves originals privately, guides users through condition checkpoints, supports manual before/after comparison, and prepares local PDF reports and optional password-protected backups. The default language is **English**; Arabic, French and Spanish are included as additional locales.

**Current status: Android CI passes; not approved for sale or public release.** The [verified run 37049194034](https://github.com/khaledhasneen1993/Handover/actions/runs/37049194034) on source commit `044470249ad9ecc7e79adc9090e86db0d10a6228` completed debug APK and unsigned release AAB builds, Android lint (0 errors, 26 warnings), and 9/9 JVM unit tests. Device camera tests, backup restoration, Arabic PDF visual review and production signing are still required. See [CI result](docs/CI_RESULT.md), [development handoff](docs/HANDOFF.md) and [release gates](docs/RELEASE_CHECKLIST.md).

## Development setup

- Official **Gradle 9.3.1 wrapper** (`./gradlew` and `gradle/wrapper/gradle-wrapper.jar`) is checked in.
- Android SDK 36; Android Gradle Plugin 9.1.1; JDK 17; application ID provisionally `com.khaled.handover`.
- To reproduce checks: `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:bundleRelease`.
- Locally runnable pure Kotlin and project policy smoke tests: `tools/run-kotlin-sanity.sh` and `python3 tools/source_sanity.py`.
- GitHub Actions runs those Android Gradle gates and retains development artifacts when produced. The release bundle is **not signed for distribution** without the owner's authorized signing key.

## Privacy

No account, advertisements, analytics, automatic uploads, Play Billing library or application INTERNET permission. Purchase is through the Google Play paid listing. Export and sharing are user-initiated; photos and backups can contain sensitive information. See [privacy policy](PRIVACY_POLICY.md).

## Release blockers

Full Android compilation/lint/tests, camera and process-death verification, complete RTL/accessibility and large-font reviews, Arabic PDF typography/embedded licensed font verification, full backup/restore device testing, 100-photo performance tests, screenshots from the running app, name and signing authorization. **Do not publish without explicit owner approval.**
