#!/usr/bin/env bash
set -euo pipefail

# Santa-safe unit test runner for Namma Omnibrief Lite (iOS)
# Executes all OmniBriefCore Swift source files + RunUnitTests.swift inside Apple's signed
# xcrun swift JIT interpreter so zero unsigned Mach-O binaries are spawned on gMac/Santa.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [[ -z "${DEVELOPER_DIR:-}" ]]; then
    if [[ -d "/Applications/Xcode.app/Contents/Developer" ]]; then
        export DEVELOPER_DIR="/Applications/Xcode.app/Contents/Developer"
    elif [[ -d "/Applications/Xcode-beta.app/Contents/Developer" ]]; then
        export DEVELOPER_DIR="/Applications/Xcode-beta.app/Contents/Developer"
    fi
fi

{
    echo "import Foundation"
    cat "${IOS_DIR}"/Sources/OmniBriefCore/*.swift "${IOS_DIR}/Tests/RunUnitTests.swift" | grep -v "^import Foundation"
} | xcrun swift -
