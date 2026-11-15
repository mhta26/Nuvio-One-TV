#!/usr/bin/env bash
set -euo pipefail
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
if os.environ.get('SIMKL_CLIENT_ID'):
    set_value('SIMKL_CLIENT_ID',os.environ['SIMKL_CLIENT_ID'],True)
p.write_text('\n'.join(lines)+'\n')
p.chmod(0o600)
PY
export NUVIO_RELEASE_STORE_FILE="${NUVIO_RELEASE_STORE_FILE:-$CODEX_ANDROID_WORK_DIR/signing/nuvio-preview.jks}"
export NUVIO_RELEASE_KEY_ALIAS="${NUVIO_RELEASE_KEY_ALIAS:-androiddebugkey}"
export NUVIO_RELEASE_KEY_PASSWORD="${NUVIO_RELEASE_KEY_PASSWORD:-android}"
export NUVIO_RELEASE_STORE_PASSWORD="${NUVIO_RELEASE_STORE_PASSWORD:-android}"
if [[ ! -f "$NUVIO_RELEASE_STORE_FILE" ]]; then
    mkdir -p "$(dirname "$NUVIO_RELEASE_STORE_FILE")"
    keytool -genkeypair -keystore "$NUVIO_RELEASE_STORE_FILE" \
        -storepass:env NUVIO_RELEASE_STORE_PASSWORD -alias "$NUVIO_RELEASE_KEY_ALIAS" \
        -keypass:env NUVIO_RELEASE_KEY_PASSWORD -dname 'CN=Nuvio One TV Preview,O=Personal Build,C=US' \
        -keyalg RSA -keysize 2048 -validity 10000
fi
bash ./gradlew :app:assembleFullDebug :app:testFullDebugUnitTest \
    --tests 'com.nuvio.tv.data.simkl.SimklLocalReimportServiceTest' \
    --tests 'com.nuvio.tv.data.simkl.SimklSyncRepositoryStorageTest' \
    -PlivetvDebugAppId=app.onetube.tv.personal.preview -PlivetvTestBuild=true -Pdebuggable=true \
    --no-daemon --max-workers=2 \
    '-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8'
mkdir -p "$CODEX_ANDROID_WORK_DIR/deliverables"
cp app/build/outputs/apk/full/debug/app-full-debug.apk "$CODEX_ANDROID_WORK_DIR/deliverables/Nuvio-One-TV-preview.apk"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify "$CODEX_ANDROID_WORK_DIR/deliverables/Nuvio-One-TV-preview.apk"
sha256sum "$CODEX_ANDROID_WORK_DIR/deliverables/Nuvio-One-TV-preview.apk"
