#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/codex-android-env.sh"
if ! command -v javac >/dev/null; then
    mkdir -p "$CODEX_ANDROID_WORK_DIR/jdk/temurin-17"
    curl -fL --retry 2 --connect-timeout 20 \
        https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse \
        -o "$CODEX_ANDROID_WORK_DIR/jdk17.tar.gz"
    tar -xzf "$CODEX_ANDROID_WORK_DIR/jdk17.tar.gz" -C "$CODEX_ANDROID_WORK_DIR/jdk/temurin-17" --strip-components=1
    export JAVA_HOME="$CODEX_ANDROID_WORK_DIR/jdk/temurin-17"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
command -v java >/dev/null
command -v python3 >/dev/null
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
    archive="$CODEX_ANDROID_WORK_DIR/android-commandline.zip"
    curl -fL --retry 2 --connect-timeout 20 \
        https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$archive"
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    unzip -q "$archive" -d "$ANDROID_HOME/cmdline-tools"
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
# Installing the SDK accepts its package licenses for this build environment.
set +o pipefail
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses
set -o pipefail
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" \
    'platforms;android-36' 'build-tools;35.0.0' 'build-tools;36.0.0' 'platform-tools' \
    'ndk;29.0.14206865' 'cmake;3.22.1'
