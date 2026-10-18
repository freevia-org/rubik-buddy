#!/bin/bash
# Run Kotlin/Native tests without relying on the GUI simulator test runner.
source "$(dirname "$0")/ios-env.sh"
if [ -z "${IOS_SIMULATOR_UDID:-}" ]; then
    echo 'Set IOS_SIMULATOR_UDID to an available simulator from: xcrun simctl list devices available' >&2
    exit 1
fi
./gradlew :rubik-vision:linkDebugTestIosSimulatorArm64 :rubik-solver:linkDebugTestIosSimulatorArm64 --max-workers=1 --console=plain
for module in rubik-vision rubik-solver; do
    xcrun simctl spawn --standalone "$IOS_SIMULATOR_UDID" "$PWD/$module/build/bin/iosSimulatorArm64/debugTest/test.kexe"
done
