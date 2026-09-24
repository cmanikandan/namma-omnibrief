#!/usr/bin/env bash
set -euo pipefail

# Unit test runner for Namma Omnibrief Lite (iOS).
# Concatenates the OmniBriefCore sources with Tests/RunUnitTests.swift and runs them with
# `xcrun swift`. Uses Xcode.app if present, otherwise Xcode-beta.app, unless DEVELOPER_DIR is set.

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
