#!/usr/bin/env python3
"""Offline structural smoke only. DOES NOT replace Android Gradle or instrumented tests."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from zipfile import ZipFile

root = Path(__file__).resolve().parent.parent
for p in (root / "app/src/main/res").rglob("*.xml"):
    ET.parse(p)
ET.parse(root / "app/src/main/AndroidManifest.xml")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
assert '@string/app_name' in manifest
assert (root / "app/src/main/res/values/strings.xml").exists()
assert 'android.permission.MANAGE_EXTERNAL_STORAGE' not in manifest
assert 'android.permission.RECORD_AUDIO' not in manifest
assert 'android.permission.INTERNET" tools:node="remove' in manifest
app = (root / "app/build.gradle.kts").read_text()
assert 'targetSdk = 36' in app and 'minSdk = 26' in app
model = (root / "app/src/main/java/com/khaled/handover/data/Model.kt").read_text()
assert all(s in model for s in ('VEHICLE', 'DEVICE', 'APARTMENT', 'NOT_APPLICABLE', 'SKIPPED'))
repo = (root / "app/src/main/java/com/khaled/handover/data/Repository.kt").read_text()
assert 'dao.reports(id)' in repo and 'oldReports.forEach' in repo
assert 'recordChange(id, second.id, "COMPARISON_UPDATED")' in repo
for p in root.rglob("*"):
    if p.is_file() and p.suffix in {".kt", ".xml", ".kts", ".md", ".toml", ".yml", ".pro"}:
        content = p.read_text(errors="replace")
        assert 'ca-app-pub-' not in content, f"Found ad identifier in {p}"
        assert not re.search(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----', content), f"Private key in {p}"
print("PASS: parseable resources/manifest, declared resources, permission policy, target API, templates, delete/assessment audit, no ad IDs or private keys")
