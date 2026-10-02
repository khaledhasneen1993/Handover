# Handover current development handoff — 2026-10-02

Repository **khaledhasneen1993/Handover**, authorized account email matches the owner's request. Repository was empty when inspected. Read access works, but `create_file` and `create_tree` both failed with **GitHub HTTP 403 Resource not accessible by integration** on the Khaled connected GitHub identity. There has been **no successful remote upload**. Local source snapshot is supplied in `Handover-source-checkpoint.zip`. Do not connect or commit to another repo.

## Existing local source (do not rebuild from scratch)

Single :app Kotlin + Compose / Material 3 Android project. `data/` contains Room v1, session/inspection templates, snapshots, media and revisions. `media/` contains CameraX lifecycle adapter, original asset SHA-256 with thumbnail generation. `ui/` contains English-first onboarding with isolated in-memory demo, home/search/archive, create/detail, per-point capture, annotation, compare with selectable baseline and return images, accessories, PDF options + preview, optional UI lock, language/theme, local reminders, encrypted optional backup. `report/` contains Android PdfDocument renderer with safe text pagination and explicitly reported missing second session. `backup/` contains ZIP + optional PBKDF2/AES-GCM envelope and strict admission checks. `export/` packages one operation's original photos + structured metadata as an explicitly user-triggered ZIP; distinct from backup.

Development decisions: Kotlin/Compose, AGP 9.1.1/Gradle 9.3.1, compileSdk 37, targetSdk 36, minSdk 26, CameraX 1.6.2, Room 2.8.5, default English; four language choices. No INTERNET permission, ads, analytics, Play Billing, in-app purchases, account or cloud server. Temporary branding Handover is *not* trademark-cleared; see `docs/NAMING_REVIEW.md`. Design reference map in `docs/SCREEN_REFERENCE_MAP.md`.

## Verified here

`tools/run-kotlin-sanity.sh` passes standalone Kotlin model and backup policy checks and `tools/source_sanity.py` passes XML/manifest/resource checks. ZIP archive was tested with `unzip -t`. No Android SDK or Gradle runtime or external DNS here; **no Android compile, lint, JUnit Gradle, APK, AAB, emulator, device, PDF visual test or hardware camera test has passed**. Gradle wrapper JAR is missing; the `gradlew` script has a conditional installed-Gradle fallback and CI uses `gradle/actions/setup-gradle`.

## Mandatory next steps when GitHub write/build environment is restored

1. Enable GitHub App **Contents read/write** for the exact `khaledhasneen1993/Handover` repo; confirm a test write before claiming upload. Preserve all local work, commit source without signing secrets or users' media. Obtain official Gradle 9.3.1 wrapper JAR from a verified distribution.
2. Run `gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:bundleRelease` and fix *actual* compile/runtime issues instead of inventing passing logs. CI workflow provided.
3. Android instrumentation and visual screenshot review for all required screens; test screen sizes, RTL, French/Spanish and large font/TalkBack. Render generated PDF pages as screenshots, especially Arabic shaping and embedded licensed font; this is currently NOT verified.
4. End-to-end actual CameraX camera, photo replacement, import EXIF rotations, manual comparison zoom/pan, rectangle/arrow annotation geometry, restore after process death and media safety.
5. Exercise plain/encrypted backup export/restore, ZIP damage and space exhaustion, restore power-loss journal, exporting a 100-photo report on a 4GB Android device. Test accessibility and native storage permissions.
6. Finalize name/trademark review, translation coverage, licensing/privacy, release signing with **only the owner's authorized keystore**, verified Play billing-free paid listing and approved publishing. No production release before owner consent.

Do not present the ZIP as a release-certified APK/AAB. The deliverable is source code at an unverified development checkpoint, with documented blockers and runnable source-level checks.
