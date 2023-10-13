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
echo "Builing HABSpeaker $HABSPEAKER_VERSION electron for macOS $TARGET_PLATFORM"
case $TARGET_PLATFORM in
  "arm64")
    RUST_TARGET=aarch64-apple-darwin
    ELECTRON_PLATFORM_ARG=--arm64
    ;;
  "amd64")
    RUST_TARGET=x86_64-apple-darwin
    ELECTRON_PLATFORM_ARG=--x64
    ;;
esac
# Build habspekear dmg
echostep "Installing HABSpeaker node deps"
npm ci
echostep "Building HABSpeaker macOS DMG for $TARGET_PLATFORM"
export CSC_IDENTITY_AUTO_DISCOVERY=false # Skip code signing
npm run build:electron -- $ELECTRON_PLATFORM_ARG