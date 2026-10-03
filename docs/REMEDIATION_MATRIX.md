# Handover corrective implementation matrix

Branch: `fix/restore-integrity-and-ux`, draft PR #1. Main baseline: `393c2851b49c35444922e0641b0d524aacf5a8e1` / passed CI 37049194034.

The table separates implementation from verified evidence. **Do not merge or release based on this PR until fresh PR CI and end-to-end Android tests are successful.**

| Requirement | Implementation in this PR | Verification / remaining gate |
|---|---|---|
| 1. Backup admission | Strict UUID, originals-only ID-derived paths, matching MIME/size/SHA-256, derive thumbnails, reject extra assets, validate SQL columns and foreign references before disk writes | New JVM RestoreAdmissionTest. Android plain/encrypted round-trip, truncated archives and malicious ZIP rejection still pending |
| 2. Crash recovery | Synchronous journal before moves, recover at app startup before showing records, reconcile orphan originals; committed originals survive failed thumbnail rebuild | Source-level checks only; process-kill and disk exhaustion instrumentation needed |
| 3. Return navigation | Single ViewModel gate, disabled return entry, handled SessionScreen errors and coroutine cancellation | Device/back-stack integration needed |
| 4. Navigation/state | Android BackHandler and SavedStateHandle for route, operation, phase, item, media; duplicate capture callback cleanup | Activity rotation, process kill, picker re-entry, drafts and task race tests pending |
| 5. PDF evidence | Existing implementation; no new claimed coverage | User-selected comparison pairs, annotations, correct timestamp provenance, fixed snapshot, session/image choices pending |
| 6. Privacy | Existing flags; not certified | Effective location toggle, metadata and irreversible export redaction with visual tests pending |
| 7. Annotation | Existing circle proof of concept | Actual image-space tap, rectangle/arrow/edit/undo, mirror EXIF and zoom pending |
| 8. Accessories and editing | Existing add only | Edit baseline/return quantities, optional photo, custom checkpoints pending |
| 9. Four languages | Existing partial UI localization | Resource/plural audit and Arabic PDF font licensed visual verification pending |
| 10. Revision and reports | Existing revision-event table | Immutable completed-session snapshot and report history UI pending |
| 11. Thumbnails | Restored thumbnails always ID-derived, orphan cleanup | Auto background regeneration after clearing and 100-photo profiling pending |
| 12. Source ZIP | Existing two exporters | Adopt one full-metadata schema and error-path verification pending |
| 13. Reminders/lock | Existing implementations | Commit-before-schedule, return-only reminders and no-biometric fallback pending |
| 14. UI review | No fabricated output | Actual screenshots, TalkBack, dark/RTL/large-font/device audit pending |
| 15. Delivery/CI | Existing Gradle 9.3.1 wrapper and Android CI; new policy unit tests | Fresh PR CI, Android instrumentation, release minification, signing authorization and real APK/AAB pending |

**Data policy:** restoration is create-only on an empty app. No existing inspection is silently erased. Source hashes detect changes relative to records; they are not independent forensic authentication. Existing green main builds and user-supplied visual mockups are not release approval.
