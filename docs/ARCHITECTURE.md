# Architecture and decisions

One :app module avoids premature modularization before a verified runnable path exists. Feature-oriented Kotlin packages isolate storage/media/report/backup and Compose UI. `InspectionRepository` owns Room transactions, file lifecycle and revision events; `GuidedCamera` owns CameraX lifecycle. ReportMaker has no Compose dependencies.

- Android API min 26: viable PDF/CameraX/biometric-era baseline, subject to device testing.
- Android API compile 37, target 36: Google Play new-app threshold since Aug 31 2026. Compose 1.12 requires SDK37 and AGP 9.1.1. AGP9.1.1 + Gradle9.3.1 + JDK17; CameraX1.6.2, Room2.8.5.
- AppCompat per-app language selection; default English is explicitly set at first start.
- Only Android SDK cryptography: PBKDF2-HMAC-SHA256 (310000 iterations, 256 bits) and AES-256-GCM with random per-backup salt and nonce. Authentication is checked before restore.
- Exported report is a separate file; original photos are never watermarked, cropped in place, or passed to another app without explicit user action.
- Files and Room are not one atomic resource. Stage/rename and cleanup limit crash windows; outstanding process-death recovery and integrity audit testing remain release gates.
- No local database encryption is implied by optional UI lock (implemented in source, pending device tests).
