#!/bin/bash
set -e
cd "${0%/*}"
cd ../
HABSPEAKER_VERSION=$(cat package.json | jq -r '.version')
TARGET_PLATFORM="${1:-amd64}"
TARGET_PLATFORMS=("amd64", "arm64")
if [[ ! " ${TARGET_PLATFORMS[*]} " =~ " ${TARGET_PLATFORM} " ]]; then
    echo "unsupported platform $TARGET_PLATFORM"
fi
echo "builing HABSpeaker $HABSPEAKER_VERSION electron AppImage for linux $TARGET_PLATFORM"
IMAGE_TAG="habspeaker-electron:linux-$TARGET_PLATFORM-$HABSPEAKER_VERSION"
docker build --platform $TARGET_PLATFORM  -f tools/electron-dockerfile . -t $IMAGE_TAG
OUTPUT_PATH=$(pwd)/electron-release/$HABSPEAKER_VERSION
mkdir -p $OUTPUT_PATH
docker run -v $OUTPUT_PATH:/out $IMAGE_TAG bash -c "cp /code/electron-release/${HABSPEAKER_VERSION}/HABSpeaker_0.0.12.AppImage /out/HABSpeaker_${HABSPEAKER_VERSION}_linux_${TARGET_PLATFORM}.AppImage"