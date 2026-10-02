#!/usr/bin/env sh
# Portable standalone verification: no Android SDK, Gradle or dependencies needed.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
kotlinc "$ROOT/app/src/main/java/com/khaled/handover/data/Model.kt" "$ROOT/tools/ModelSanity.kt" -include-runtime -d "$TMP/model.jar"
java -jar "$TMP/model.jar"
kotlinc "$ROOT/app/src/main/java/com/khaled/handover/backup/ArchivePolicy.kt" "$ROOT/tools/ArchiveSanity.kt" -include-runtime -d "$TMP/archive.jar"
java -jar "$TMP/archive.jar"
python3 "$ROOT/tools/source_sanity.py"
