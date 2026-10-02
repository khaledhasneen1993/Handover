# Handover backup v1

Magic `HBK0` followed by a ZIP, or magic `HBK1` + 16 random-byte PBKDF2 salt + 12-byte GCM nonce + AES-256-GCM encrypted ZIP. Passwords are never stored. Key: PBKDF2-HMAC-SHA256, 310,000 iterations, 32 bytes; authentication tag 128 bits.

ZIP includes `manifest.json` with `format`, `schema`, `createdAt`, and SHA-256 and size of each content member; `data.json` contains `schema=1` and named arrays of stable table-column values; original assets are listed under `originals/<UUID>.jpg`. Only exact expected paths allowed. Maximum compressed+encrypted input ~1.3 GB, total expanded ~1.2 GB, maximum entries 10,000, per-photo 100 MB. No traversal, absolute path or symlink entries are accepted.

Restore: require a fresh empty app; decrypt to temporary storage and authenticate, extract to staging while enforcing quotas, compare manifest and stored file hashes, validate schema, move original files and insert rows in a Room transaction, then regenerate derived thumbnails. On ordinary exceptions moved files are deleted. Crash/power-loss recovery is still a gate; a high-assurance restore must journal the staged operation and recover on next launch.

**Implementation checkpoint:** existing `exports/*.pdf` files referenced by ReportRecord rows are included in the archive and manifest. The restore path checks their presence, hashes, size and destination conflicts. Plain/encrypted backup, source snapshots and crash recovery still require full Android device validation; only locally verified tests may be marked passed.

## Per-operation original-asset package (separate from app backup)
The report screen also exports an **unencrypted** `application/zip` of one operation's original image bytes (`media/<asset-id>.<ext>`) and `manifest.json` (`format: handover-asset-package`, `schema: 1`). The manifest identifies operation, checklist points, session phases and zones, the import/capture metadata provenance, byte lengths, SHA-256 hashes and timestamps. Every original is rehashed before writing; no database state is restored from this package. This ZIP contains sensitive original pixels and metadata and should only be shared intentionally. It is NOT an app restore archive and is never described as one in the UI.
