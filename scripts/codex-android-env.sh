#!/usr/bin/env bash
# Source this file from the setup/build scripts. Keep all generated state outside the user's home.
CODEX_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CODEX_ANDROID_WORK_DIR="${CODEX_ANDROID_WORK_DIR:-$(dirname "$CODEX_REPO_ROOT")}"
if [[ -x "$CODEX_ANDROID_WORK_DIR/jdk/temurin-17/bin/javac" ]]; then
    export JAVA_HOME="$CODEX_ANDROID_WORK_DIR/jdk/temurin-17"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
export ANDROID_HOME="${ANDROID_HOME:-$CODEX_ANDROID_WORK_DIR/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$CODEX_ANDROID_WORK_DIR/android-user}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$CODEX_ANDROID_WORK_DIR/gradle-cache}"
mkdir -p "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Duser.home=$ANDROID_USER_HOME"
# JVM clients do not automatically honor the shell's HTTP(S)_PROXY variables.
if [[ -n "${HTTPS_PROXY:-${https_proxy:-}}" ]]; then
    read -r codex_proxy_host codex_proxy_port < <(python3 - <<'PY'
import os, urllib.parse
p=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy'))
print(p.hostname, p.port or 8080)
PY
)
    export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dhttps.proxyHost=$codex_proxy_host -Dhttps.proxyPort=$codex_proxy_port -Dhttp.proxyHost=$codex_proxy_host -Dhttp.proxyPort=$codex_proxy_port"
fi

# The managed network CA is already trusted by the system Java trust store.
# Use it for downloaded JDKs as well; certificate verification remains enabled.
if [[ -f /etc/ssl/certs/java/cacerts && "$JAVA_TOOL_OPTIONS" != *-Djavax.net.ssl.trustStore=* ]]; then
    export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
