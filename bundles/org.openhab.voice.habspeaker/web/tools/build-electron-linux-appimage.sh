#!/bin/bash
set -e
cd "${0%/*}"
cd ../
HABSPEAKER_VERSION=$(cat package.json | jq -r '.version')
TARGET_PLATFORM="${1:-amd64}"
TARGET_PLATFORMS=("amd64", "arm64")
if [[ ! "${TARGET_PLATFORMS[*]}" =~ "${TARGET_PLATFORM}" ]]; then
    echo "Unsupported platform $TARGET_PLATFORM"
    exit 1
fi
echostep() {
    echo "-------------------------------"
    echo "BUILD STEP: $1"
    echo "-------------------------------"
}
echo "Builing HABSpeaker $HABSPEAKER_VERSION electron AppImage for linux $TARGET_PLATFORM"
case $TARGET_PLATFORM in
  "arm64")
    ELECTRON_PLATFORM_ARG=--arm64
    ;;
  "amd64")
    ELECTRON_PLATFORM_ARG=--x64
    ;;
esac
# Build habspekear dmg
echostep "Installing HABSpeaker node deps"
npm ci
echostep "Building HABSpeaker macOS DMG for $TARGET_PLATFORM"
npm run build:electron -- --linux $ELECTRON_PLATFORM_ARG