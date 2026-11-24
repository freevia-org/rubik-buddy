#!/bin/bash
# Sourced by the iOS build commands; no machine-specific paths are checked in.
set -euo pipefail
RUBIK_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RUBIK_REPO_ROOT"
export PATH="$HOME/.local/bin:$PATH"
xcodebuild -version >/dev/null 2>&1 || { echo 'Install full Xcode and finish its first-launch setup.' >&2; exit 1; }
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null) || { echo 'Install JDK 21 or set JAVA_HOME.' >&2; exit 1; }
    export JAVA_HOME
fi
export PATH="$JAVA_HOME/bin:$PATH"
command -v cmake >/dev/null || { echo 'Install CMake and add it to PATH.' >&2; exit 1; }
# CodeQL tracing or a Rosetta shell can report x86_64 on Apple Silicon.
# Require ARM64-capable hardware, not a particular architecture of this process.
ios_host_arm64=$(/usr/sbin/sysctl -n hw.optional.arm64 2>/dev/null || true)
ios_host_translated=$(/usr/sbin/sysctl -n sysctl.proc_translated 2>/dev/null || true)
printf 'iOS host: process=%s, arm64-capable=%s, translated=%s\n' \
    "$(/usr/bin/uname -m)" "${ios_host_arm64:-unknown}" "${ios_host_translated:-unavailable}"
[ "$ios_host_arm64" = 1 ] || { echo 'Apple Silicon is required by the supplied solver slices (ARM64 hardware capability not confirmed).' >&2; exit 1; }
