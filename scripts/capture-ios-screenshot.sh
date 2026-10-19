#!/bin/bash
# Capture the current screen on a named test simulator without modifying the image.
set -euo pipefail
: "${IOS_SIMULATOR_UDID:?Set the UDID of your own booted test simulator}"
case "${1:-}" in iphone|ipad) family=$1 ;; *) echo 'Usage: IOS_SIMULATOR_UDID=... ./scripts/capture-ios-screenshot.sh iphone|ipad filename' >&2; exit 2 ;; esac
name=${2:?Supply a screenshot name without extension}
[[ "$name" =~ ^[a-zA-Z0-9_-]+$ ]] || { echo 'Use a plain filename.' >&2; exit 2; }
root="$(cd "$(dirname "$0")/.." && pwd)"
output="$root/app-store/screenshots/$family"
mkdir -p "$output"
xcrun simctl status_bar "$IOS_SIMULATOR_UDID" override --time '9:41' --dataNetwork wifi --wifiMode active --wifiBars 3 --batteryState charged --batteryLevel 100
trap 'xcrun simctl status_bar "$IOS_SIMULATOR_UDID" clear' EXIT
xcrun simctl io "$IOS_SIMULATOR_UDID" screenshot --type=jpeg --mask=ignored "$output/$name.jpg"
echo "Captured $output/$name.jpg; visually inspect it before submission."
