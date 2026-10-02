#!/usr/bin/env python3
"""Lightweight offline source-integrity checks. Not a replacement for Android compilation."""
from pathlib import Path
import sys
from xml.etree import ElementTree as ET
root = Path(__file__).resolve().parents[1]
xmls=list(root.glob('app/src/main/res/**/*.xml'))+[root/'app/src/main/AndroidManifest.xml']
for file in xmls:
    ET.parse(file)
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
assert 'android:allowBackup="false"' in manifest
assert 'android.permission.INTERNET" tools:node="remove"' in manifest
assert 'android:required="false"' in manifest
assert 'android:exported="false"' in manifest
assert 'android:grantUriPermissions="true"' in manifest
for locale in ('values','values-ar','values-fr','values-es'):
    assert (root/f'app/src/main/res/{locale}/strings.xml').is_file(),locale
assert (root/'app/src/main/res/xml/file_paths.xml').is_file()
assert (root/'app/src/main/java/com/khaled/handover/backup/ArchivePolicy.kt').is_file()
assert (root/'app/src/main/java/com/khaled/handover/media/GuidedCamera.kt').is_file()
assert 'targetSdk = 36' in (root/'app/build.gradle.kts').read_text()
assert 'compileSdk = 36' in (root/'app/build.gradle.kts').read_text()
assert 'assembleDebug' in (root/'.github/workflows/android.yml').read_text()
assert not list(root.rglob('*.jks')) and not list(root.rglob('*.keystore'))
print(f'PASS: parsed {len(xmls)} XML resources; required manifest, languages, Gradle/CI and no bundled signing keys')
