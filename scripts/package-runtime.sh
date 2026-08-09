#!/usr/bin/env bash

set -eu

platform="${1:-}"

if [ -z "$platform" ]; then
    os_name="$(uname -s)"
    architecture="$(uname -m)"

    case "$os_name" in
        Darwin)
            os_name="macos"
            ;;
        Linux)
            os_name="linux"
            ;;
        MINGW*|MSYS*|CYGWIN*)
            os_name="windows"
            ;;
        *)
            echo "Unsupported operating system: $os_name" >&2
            exit 1
            ;;
    esac

    case "$architecture" in
        x86_64|amd64)
            architecture="x64"
            ;;
        arm64|aarch64)
            architecture="arm64"
            ;;
        *)
            echo "Unsupported architecture: $architecture" >&2
            exit 1
            ;;
    esac

    platform="$os_name-$architecture"
fi

case "$platform" in
    *[!a-z0-9._-]*|"")
        echo "Invalid platform identifier: $platform" >&2
        exit 1
        ;;
esac

archive="target/race-replay-lab-runtime-$platform.zip"

./mvnw \
    --batch-mode \
    --no-transfer-progress \
    "-Djlink.platform=$platform" \
    javafx:jlink

if [ ! -s "$archive" ]; then
    echo "Expected runtime archive was not created: $archive" >&2
    exit 1
fi

echo "Runtime archive created: $archive"
