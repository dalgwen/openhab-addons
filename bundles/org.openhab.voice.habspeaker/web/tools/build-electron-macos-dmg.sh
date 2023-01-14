#!/bin/bash
set -e
cd "${0%/*}"
cd ../
HABSPEAKER_VERSION=$(cat package.json | jq -r '.version')
LIBRESPOT_VERSION="FORK"
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
# Build Librespot
LIBRESPOT_BINARY=librespot-src/target/$RUST_TARGET/release/librespot
LIBRESPOT_LIBRARY=librespot-src/target/$RUST_TARGET/release/liblibrespot.rlib
echostep "Builing Librespot binaries"
if [[ ! -f $LIBRESPOT_BINARY || ! -f $LIBRESPOT_LIBRARY ]];then
    if [ ! -f librespot-src/Cargo.toml ];then
        echostep "Cloning Librespot $LIBRESPOT_VERSION source..."
        # git -c advice.detachedHead=false clone --quiet --branch $LIBRESPOT_VERSION https://github.com/librespot-org/librespot.git librespot-src
        # temporally use fork version to allow access token authentication
        git -c advice.detachedHead=false clone --quiet --branch master https://github.com/GiviMAD/librespot.git librespot-src
    else
        echostep "Librespot source already exists, assuming desired version"
    fi
    echostep "Builing Librespot for $RUST_TARGET"
    cd librespot-src
    cargo build --target $RUST_TARGET --release
    sleep 5
    cd ../
else
    echostep "Librespot binaries for $RUST_TARGET found, skipping build"
fi
echostep "Copying Librespot binaries to appropriate directory"
cp $LIBRESPOT_BINARY ./librespot/
cp $LIBRESPOT_LIBRARY ./librespot/
# Build habspekear dmg
echostep "Installing HABSpeaker node deps"
npm ci
echostep "Building HABSpeaker macOS DMG for $TARGET_PLATFORM"
npm run build:electron -- $ELECTRON_PLATFORM_ARG