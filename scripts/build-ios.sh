#!/bin/bash
# Build an unsigned arm64 simulator app.
source "$(dirname "$0")/ios-env.sh"
git submodule update --init --recursive
./rubik-solver/build-ios-framework.sh
xcodebuild build \
    -project iosApp/iosApp.xcodeproj -scheme iosApp \
    -configuration Debug -destination 'generic/platform=iOS Simulator' \
    ARCHS=arm64 -derivedDataPath "$PWD/build/xcode-derived-data" \
    CODE_SIGNING_ALLOWED=NO
