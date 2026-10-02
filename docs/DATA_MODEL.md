# Stable IDs and relational model

Room v1 tables: `inspections`, `template_snapshots`, `sessions`, `checklist`, `item_states`, `media`, `annotations`, `observations`, `comparison_pairs`, `accessories`, `reports`, `revision_events`. Internal keys are stable uppercase string enums; display labels are independently localized.

One inspection has one first session, optionally one return session; session `revision` records the last completion. The template snapshot JSON persists the actual checklist definition per inspection. Each media item records original relative path and thumbnail path, source, timestamp source, source SHA-256, image size. Foreign-key cascades remove metadata on deletion, while repository deletes local originals separately. `ReportRecord` captures the source revision and privacy choices.

Untrusted imported EXIF date is marked `UNVERIFIED_EXIF`; camera timestamps use the device clock. Location is not required and no location permission is requested. New schema changes must include tested migrations and retain existing template snapshots.
