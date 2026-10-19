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
        echo '--app-store requires a paid team and App Store distribution provisioning; exports locally without uploading.'
        echo 'Optional: IOS_BUNDLE_ID, IOS_BUILD_NUMBER, IOS_MARKETING_VERSION. No credentials are stored in the repository.'
        exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
esac
source "$(dirname "$0")/ios-env.sh"
source_revision=$(git rev-parse HEAD)
source_state=$(git describe --always --dirty)
build_number=${IOS_BUILD_NUMBER:-$(git rev-list --count HEAD)}
settings=("CURRENT_PROJECT_VERSION=$build_number")
if [ -n "${IOS_BUNDLE_ID:-}" ]; then settings+=("PRODUCT_BUNDLE_IDENTIFIER=$IOS_BUNDLE_ID"); fi
if [ -n "${IOS_MARKETING_VERSION:-}" ]; then settings+=("MARKETING_VERSION=$IOS_MARKETING_VERSION"); fi
[[ "$build_number" =~ ^[0-9]+$ ]] || { echo 'IOS_BUILD_NUMBER must be an integer.' >&2; exit 1; }
label=unsigned
if $signed; then
    [ -n "${IOS_DEVELOPMENT_TEAM:-}" ] || { echo 'Set IOS_DEVELOPMENT_TEAM to your Apple team ID.' >&2; exit 1; }
    [[ "$IOS_DEVELOPMENT_TEAM" =~ ^[A-Z0-9]{10}$ ]] || { echo 'Expected a 10-character Apple team ID.' >&2; exit 1; }
    if ! $store; then
        security find-identity -v -p codesigning | /usr/bin/grep -q '"Apple Development:' || {
            echo 'No Apple Development signing identity. Add your Apple account in Xcode and create a development certificate.' >&2
            exit 1
        }
    fi
    settings+=("DEVELOPMENT_TEAM=$IOS_DEVELOPMENT_TEAM" CODE_SIGN_STYLE=Automatic -allowProvisioningUpdates)
    label=development
    if $store; then
        label=app-store
        settings+=("CODE_SIGN_IDENTITY=Apple Distribution")
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
    /usr/libexec/PlistBuddy -c "Add :teamID string $IOS_DEVELOPMENT_TEAM" "$options"
    xcodebuild -exportArchive -archivePath "$archive" -exportPath "$output/$label-ipa" \
        -exportOptionsPlist "$options" -allowProvisioningUpdates
fi

ditto -c -k --keepParent "$archive" "$archive.zip"
{
    echo "Source: $source_revision"
    echo "Source state: $source_state"
    echo "Build number: $build_number"
    echo "Marketing version: ${IOS_MARKETING_VERSION:-project default}"
    xcodebuild -version
    if $signed; then echo "Signed export: $label-ipa/ (not uploaded)";
    else echo 'UNSIGNED: not installable on an iPhone. Use --signed after configuring an Apple development team.'; fi
} > "$output/BUILD-INFO-$label.txt"
(cd "$output" && shasum -a 256 "RubikBuddy-$label.xcarchive.zip" > "RubikBuddy-$label.xcarchive.zip.sha256")
echo "Archive: $archive.zip"
if ! $signed; then echo 'The archive is unsigned. Read docs/IOS_BUILD.md to prepare a signed iPhone build.'; fi
