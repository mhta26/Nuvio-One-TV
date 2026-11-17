#!/usr/bin/env bash
set -euo pipefail
build_mode="${1:---all}"
case "$build_mode" in --all|--test-only|--lint-only|--package-only) ;; *) echo "Unsupported build mode" >&2; exit 1 ;; esac
source "$(dirname "${BASH_SOURCE[0]}")/codex-android-env.sh"
cd "$CODEX_REPO_ROOT"
if ! command -v javac >/dev/null || [[ ! -f "$ANDROID_HOME/platforms/android-36/android.jar" || ! -f "$ANDROID_HOME/ndk/29.0.14206865/source.properties" ]]; then
    bash scripts/setup-codex-android.sh
    source "scripts/codex-android-env.sh"
fi
# Keep service configuration local. An existing local.properties file is preserved.
python3 - <<'PY'
import os
from pathlib import Path
p=Path('local.properties')
lines=p.read_text().splitlines() if p.exists() else []
def set_value(key, value, overwrite=False):
    prefix=key+'='
    if any(line.startswith(prefix) for line in lines):
        if overwrite: lines[:]=[line for line in lines if not line.startswith(prefix)]
        else: return
    lines.append(prefix+value)
set_value('sdk.dir', os.environ['ANDROID_HOME'].replace('\\','/'), True)
for key,value in {
    'DOVI_NATIVE_ENABLED':'true', 'DOVI_EXTRACTOR_HOOK_READY':'true',
    'DOVI_ENABLE_REAL_LINK':'true', 'DOVI_LIBDOVI_PREBUILT_ROOT':'DV7/libdovi',
    'SIMKL_APP_NAME':'nuvio-one-tv'
}.items(): set_value(key,value)
for key in ('SIMKL_CLIENT_ID', 'TRAKT_CLIENT_ID', 'TRAKT_REDIRECT_URI', 'MDBLIST_CLIENT_ID'):
    value = os.environ.get(key, '').strip()
    if value:
        if any(c in value for c in '\r\n'):
            raise SystemExit(f'{key} must be a single line')
        set_value(key, value, True)
p.write_text('\n'.join(lines)+'\n')
p.chmod(0o600)
PY
python3 scripts/configure-preview-backend.py --properties local.properties
# The repository's existing public fork key keeps previews compatible across local and CI builds.
# Override all four variables to use a private signing identity instead.
export NUVIO_RELEASE_STORE_FILE="${NUVIO_RELEASE_STORE_FILE:-$CODEX_REPO_ROOT/keystore/livetv-fork.jks}"
export NUVIO_RELEASE_KEY_ALIAS="${NUVIO_RELEASE_KEY_ALIAS:-livetv}"
export NUVIO_RELEASE_KEY_PASSWORD="${NUVIO_RELEASE_KEY_PASSWORD:-livetvfork}"
export NUVIO_RELEASE_STORE_PASSWORD="${NUVIO_RELEASE_STORE_PASSWORD:-livetvfork}"
if [[ ! -f "$NUVIO_RELEASE_STORE_FILE" ]]; then
    mkdir -p "$(dirname "$NUVIO_RELEASE_STORE_FILE")"
    keytool -genkeypair -keystore "$NUVIO_RELEASE_STORE_FILE" \
        -storepass:env NUVIO_RELEASE_STORE_PASSWORD -alias "$NUVIO_RELEASE_KEY_ALIAS" \
        -keypass:env NUVIO_RELEASE_KEY_PASSWORD -dname 'CN=Nuvio One TV Preview,O=Personal Build,C=US' \
        -keyalg RSA -keysize 2048 -validity 10000
fi
# Keep the same version across two Gradle processes. Releasing the compiler heap
# before R8 avoids running both memory-heavy stages together on the CI runner.
release_version="${NUVIO_ONE_TV_VERSION:-1.1.2}"
if [[ ! "$release_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "NUVIO_ONE_TV_VERSION must be a stable major.minor.patch version" >&2
    exit 1
fi
preview_minutes="${NUVIO_BUILD_MINUTES:-$(python3 -c 'import time; print((int(time.time()) - 1767225600) // 60)')}"
run_preview_gradle() {
    bash ./gradlew "$@" \
        -PnuvioPersonalAppId=app.onetube.tv.personal.preview -PlivetvTestBuild=false -Pdebuggable=false \
        "-PlivetvVersionName=$release_version" \
        "-PnuvioPreviewBuildMinutes=$preview_minutes" \
        --no-daemon --max-workers=1 --no-parallel \
        -Pkotlin.compiler.execution.strategy=in-process \
        "-Dorg.gradle.jvmargs=-Xmx${CODEX_GRADLE_HEAP_MB:-3584}m -XX:MaxMetaspaceSize=${CODEX_GRADLE_METASPACE_MB:-768}m -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"
}
if [[ "$build_mode" == --all || "$build_mode" == --test-only ]]; then
    run_preview_gradle :app:testFullReleaseUnitTest
fi
if [[ "$build_mode" == --test-only ]]; then exit 0; fi
if [[ "$build_mode" == --all || "$build_mode" == --lint-only ]]; then
    run_preview_gradle :app:lintFullRelease
fi
if [[ "$build_mode" == --lint-only ]]; then exit 0; fi
run_preview_gradle :app:assembleFullRelease
mkdir -p "$CODEX_ANDROID_WORK_DIR/deliverables"
for abi in arm64-v8a armeabi-v7a; do
    preview_source="app/build/outputs/apk/full/release/app-full-$abi-release.apk"
    preview_target="$CODEX_ANDROID_WORK_DIR/deliverables/Nuvio-One-TV-$abi.apk"
    if [[ ! "$preview_source" -ef "$preview_target" ]]; then
        ln -f "$preview_source" "$preview_target" 2>/dev/null || cp "$preview_source" "$preview_target"
    fi
    "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify "$preview_target"
    native_code=$("$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$preview_target" | sed -n '/^native-code:/p')
    [[ "$native_code" == "native-code: '$abi'" ]]
    # Never distribute a debuggable APK as the TV performance build.
    if "$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$preview_target" | grep -q '^application-debuggable'; then
        echo "Refusing to distribute a debuggable APK" >&2
        exit 1
    fi
    sha256sum "$preview_target"
done
