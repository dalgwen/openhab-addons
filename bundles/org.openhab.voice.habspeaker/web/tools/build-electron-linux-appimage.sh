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
echo "Builing HABSpeaker $HABSPEAKER_VERSION electron AppImage for linux $TARGET_PLATFORM using Docker"
IMAGE_TAG="habspeaker-electron:linux-$TARGET_PLATFORM-$HABSPEAKER_VERSION"
DOCKER_BUILDKIT=1 docker buildx build --platform linux/$TARGET_PLATFORM -f tools/electron-dockerfile . -t $IMAGE_TAG --load
OUTPUT_PATH=$(pwd)/electron-release/$HABSPEAKER_VERSION
mkdir -p $OUTPUT_PATH
DOCKER_BUILDKIT=1 docker run --platform linux/$TARGET_PLATFORM -v $OUTPUT_PATH:/out $IMAGE_TAG bash -c "cp /code/electron-release/${HABSPEAKER_VERSION}/HABSpeaker_*.AppImage /out/"