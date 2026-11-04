#!/bin/bash
# Release archive; optional development or App Store Connect export (never uploads).
set -euo pipefail
signed=false
store=false
case "${1:-}" in
    '') ;;
    --signed) signed=true ;;
    --app-store) signed=true; store=true ;;
    --help|-h)
        echo 'Usage: ./scripts/archive-ios.sh [--signed|--app-store]'
        echo 'Unsigned output: build/ios-device/RubikBuddy-unsigned.xcarchive.zip'
        echo 'Signed export requires IOS_DEVELOPMENT_TEAM, a signing identity and provisioning.'
        echo '--app-store requires a paid team, local Apple Distribution identity, and IOS_APP_STORE_PROFILE (installed profile name/UUID).'
        echo 'Optional: IOS_BUNDLE_ID. Version and build come from release-version.json; legacy version overrides must match it.'
        exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
esac
source "$(dirname "$0")/ios-env.sh"
source_revision=$(git rev-parse HEAD)
source_state=$(git describe --always --dirty)
release_version=$(python3 - "$RUBIK_REPO_ROOT/release-version.json" <<'VERSION_PY'
import json, re, sys
from pathlib import Path
identity = json.loads(Path(sys.argv[1]).read_text())['canonical']
match = re.fullmatch(r'(\d+)\.(\d+)\.(\d+)\+([1-9]\d{0,2})', identity)
if not match:
    raise SystemExit('release-version.json must use MAJOR.MINOR.CODE+BUILD')
major, minor, code, build = map(int, match.groups())
if major > 20 or minor > 99 or code > 999 or build > 999:
    raise SystemExit('Release version components exceed the supported store version range')
print(f'{major}.{minor}.{code}\t{build}')
VERSION_PY
)
IFS=$'\t' read -r canonical_version build_number <<< "$release_version"
if [ -n "${IOS_BUILD_NUMBER:-}" ] && [ "$IOS_BUILD_NUMBER" != "$build_number" ]; then
    echo "IOS_BUILD_NUMBER must match release-version.json ($build_number)." >&2; exit 1
fi
if [ -n "${IOS_MARKETING_VERSION:-}" ] && [ "$IOS_MARKETING_VERSION" != "$canonical_version" ]; then
    echo "IOS_MARKETING_VERSION must match release-version.json ($canonical_version)." >&2; exit 1
fi
settings=("CURRENT_PROJECT_VERSION=$build_number" "MARKETING_VERSION=$canonical_version")
if [ -n "${IOS_BUNDLE_ID:-}" ]; then settings+=("PRODUCT_BUNDLE_IDENTIFIER=$IOS_BUNDLE_ID"); fi
[[ "$build_number" =~ ^[0-9]+$ ]] || { echo 'IOS_BUILD_NUMBER must be an integer.' >&2; exit 1; }
label=unsigned
if $signed; then
    [ -n "${IOS_DEVELOPMENT_TEAM:-}" ] || { echo 'Set IOS_DEVELOPMENT_TEAM to your Apple team ID.' >&2; exit 1; }
    [[ "$IOS_DEVELOPMENT_TEAM" =~ ^[A-Z0-9]{10}$ ]] || { echo 'Expected a 10-character Apple team ID.' >&2; exit 1; }
    settings+=("DEVELOPMENT_TEAM=$IOS_DEVELOPMENT_TEAM")
    if $store; then
        [ -n "${IOS_APP_STORE_PROFILE:-}" ] || { echo 'Set IOS_APP_STORE_PROFILE to your installed App Store profile name or UUID.' >&2; exit 1; }
        security find-identity -v -p codesigning | /usr/bin/grep -q '"Apple Distribution:' || {
            echo 'Install an Apple Distribution certificate with its private key in Keychain.' >&2
            exit 1
        }
        settings+=(CODE_SIGN_STYLE=Manual "CODE_SIGN_IDENTITY=Apple Distribution" "PROVISIONING_PROFILE_SPECIFIER=$IOS_APP_STORE_PROFILE")
        label=app-store
    else
        security find-identity -v -p codesigning | /usr/bin/grep -q '"Apple Development:' || {
            echo 'No Apple Development signing identity. Add your Apple account in Xcode and create a development certificate.' >&2
            exit 1
        }
        settings+=(CODE_SIGN_STYLE=Automatic -allowProvisioningUpdates)
        label=development
    fi
else
    settings+=(CODE_SIGNING_ALLOWED=NO)
fi

git submodule update --init --recursive
./rubik-solver/build-ios-framework.sh
output="$RUBIK_REPO_ROOT/build/ios-device"
archive="$output/RubikBuddy-$label.xcarchive"
mkdir -p "$output"
xcodebuild archive \
    -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
    -destination 'generic/platform=iOS' -archivePath "$archive" \
    -derivedDataPath "$RUBIK_REPO_ROOT/build/xcode-device" "${settings[@]}"

python3 scripts/validate-ios-archive.py "$archive" > "$output/VALIDATION-$label.json"

if $signed; then
    options=$(mktemp "$output/ExportOptions.XXXXXX")
    trap 'rm -f "$options"' EXIT
    if $store; then cp iosApp/ExportOptions-AppStore.plist "$options";
    else cp iosApp/ExportOptions.plist "$options"; fi
    python3 - "$archive" "$options" "$IOS_DEVELOPMENT_TEAM" "${IOS_APP_STORE_PROFILE:-}" <<'EXPORT_PY'
import plistlib, sys
from pathlib import Path
archive, options, team, profile = sys.argv[1:]
options = Path(options)
with options.open('rb') as stream:
    export = plistlib.load(stream)
export['teamID'] = team
if export['method'] == 'app-store-connect':
    with (Path(archive) / 'Info.plist').open('rb') as stream:
        metadata = plistlib.load(stream)
    app = Path(archive) / 'Products' / metadata['ApplicationProperties']['ApplicationPath']
    with (app / 'Info.plist').open('rb') as stream:
        bundle = plistlib.load(stream)['CFBundleIdentifier']
    export['provisioningProfiles'] = {bundle: profile}
with options.open('wb') as stream:
    plistlib.dump(export, stream)
EXPORT_PY
    if $store; then
        xcodebuild -exportArchive -archivePath "$archive" -exportPath "$output/$label-ipa" -exportOptionsPlist "$options"
    else
        xcodebuild -exportArchive -archivePath "$archive" -exportPath "$output/$label-ipa" -exportOptionsPlist "$options" -allowProvisioningUpdates
    fi
fi

ditto -c -k --keepParent "$archive" "$archive.zip"
{
    echo "Source: $source_revision"
    echo "Source state: $source_state"
    echo "Build number: $build_number"
    echo "Marketing version: $canonical_version"
    xcodebuild -version
    if $signed; then echo "Signed export: $label-ipa/ (not uploaded)";
    else echo 'UNSIGNED: not installable on an iPhone. Use --signed after configuring an Apple development team.'; fi
} > "$output/BUILD-INFO-$label.txt"
(cd "$output" && shasum -a 256 "RubikBuddy-$label.xcarchive.zip" > "RubikBuddy-$label.xcarchive.zip.sha256")
echo "Archive: $archive.zip"
if ! $signed; then echo 'The archive is unsigned. Read docs/IOS_BUILD.md to prepare a signed iPhone build.'; fi
