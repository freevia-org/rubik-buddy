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
[ "$(uname -m)" = arm64 ] || { echo 'Apple Silicon is required by the supplied solver slices.' >&2; exit 1; }
