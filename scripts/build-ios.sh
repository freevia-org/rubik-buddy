#!/bin/bash
# Build an unsigned arm64 simulator app.
source "$(dirname "$0")/ios-env.sh"
configuration=${IOS_CONFIGURATION:-Debug}
case "$configuration" in Debug|Release) ;; *) echo 'IOS_CONFIGURATION must be Debug or Release.' >&2; exit 2 ;; esac
derived_data=${IOS_DERIVED_DATA:-$RUBIK_REPO_ROOT/build/xcode-derived-data}
git submodule update --init --recursive
./rubik-solver/build-ios-framework.sh
xcodebuild build \
    -project iosApp/iosApp.xcodeproj -scheme iosApp \
    -configuration "$configuration" -destination 'generic/platform=iOS Simulator' \
    ARCHS=arm64 -derivedDataPath "$derived_data" \
    CODE_SIGNING_ALLOWED=NO
