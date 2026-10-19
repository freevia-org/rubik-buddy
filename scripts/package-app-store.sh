#!/bin/bash
# Package the unsigned archive and copy-ready store assets for a future account owner.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
python3 scripts/validate-app-store.py
archive="$root/build/ios-device/RubikBuddy-unsigned.xcarchive"
[ -d "$archive" ] || { echo 'Run ./scripts/archive-ios.sh first.' >&2; exit 1; }
python3 scripts/validate-ios-archive.py "$archive" > "$root/build/app-store-archive-validation.json"
python3 - <<'PY'
import json
from pathlib import Path
expected = json.loads(Path('app-store/app.json').read_text())
actual = json.loads(Path('build/app-store-archive-validation.json').read_text())
assert actual['version'] == expected['version'], 'Rebuild: archive version differs from store metadata'
assert actual['bundle_identifier'] == expected['bundle_id'], 'Rebuild: archive bundle ID differs from metadata'
PY
(cd build/ios-device && shasum -a 256 -c RubikBuddy-unsigned.xcarchive.zip.sha256)
output="$root/build/app-store-submission"
mkdir -p "$output/app-store" "$output/docs"
cp -R app-store/. "$output/app-store/"
printf 'Read app-store/README.md. This kit contains an unsigned archive, not an installable IPA.\n' > "$output/README.txt"
cp build/ios-device/RubikBuddy-unsigned.xcarchive.zip* "$output/"
cp build/ios-device/BUILD-INFO-unsigned.txt build/app-store-archive-validation.json "$output/"
cp NOTICE "$output/ThirdPartyNotices.txt"
cp iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png "$output/AppIcon-1024.png"
cp docs/IOS_RELEASE.md docs/IOS_VALIDATION.md docs/IOS_LICENSES.md docs/privacy-policy.md "$output/docs/"
python3 scripts/validate-app-store.py > "$output/LOCAL-CHECKS.txt"
{
    echo "Preparation source: $(git rev-parse HEAD)"
    echo "Preparation state: $(git describe --always --dirty)"
    echo 'Archive source is recorded separately in BUILD-INFO-unsigned.txt.'
} > "$output/PREPARATION-INFO.txt"
(cd "$output" && find . -type f ! -name SHA256SUMS -exec shasum -a 256 {} \; > SHA256SUMS)
ditto -c -k --keepParent "$output" "$root/build/RubikBuddy-AppStore-Preparation.zip"
(cd build && shasum -a 256 RubikBuddy-AppStore-Preparation.zip > RubikBuddy-AppStore-Preparation.zip.sha256)
echo "Prepared $root/build/RubikBuddy-AppStore-Preparation.zip (contains an unsigned archive, not an installable IPA)"
