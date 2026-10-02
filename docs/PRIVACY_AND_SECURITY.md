# Privacy and security boundaries

No backend, sign-in, advertising, analytics or internet use. CAMERA when taking a photo, system Photo Picker for imports, SAF for backup; no contacts/SMS/audio/background location/all-files access. Share only on explicit user action via FileProvider and temporary URI grants.

Hashes help detect changes relative to the local record; they are not independent evidence of capture time, authenticity or tamperproof storage. Camera time is from the device; imported EXIF time is untrusted. The report can hide selected metadata fields; **identifiers inside visible image pixels are NOT automatically redacted**. User reviews report photos before sharing.

Backup archives may contain every original photo and sensitive metadata. Optional password uses platform AES-GCM authenticated encryption and PBKDF2. Forgotten passwords cannot be recovered. The restoration parser validates format, file count, expanded size, path whitelist, manifest hashes and all entries before data insertion. Restore is intentionally create-only until an explicit and tested merge policy exists.

Open release blockers: test backup archive manipulation and interrupted restores on device; add optional system biometric gate without claiming at-rest DB encryption; inspect third-party dependency permissions; verify exported files are pruned and privacy form reflects built product.
