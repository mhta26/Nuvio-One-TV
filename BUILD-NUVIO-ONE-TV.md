# Nuvio One TV personal preview

This fork adds an independent poster refresh action, device-aware image concurrency,
and a guarded upload/verify/reimport action for the local library and completed watch
history. The preview package is `app.onetube.tv.personal.preview`, so it installs beside
the original app. It does not inherit the original app's private data.

## Codex/Linux build

Requires Bash, curl, unzip, tar, Python 3, and a full JDK (17 recommended).
For a fresh full build, use at least 16 GB of free disk space and 8 GB RAM when
available; GitHub-hosted runners provide more room than a small Cloud workspace.
`CODEX_GRADLE_HEAP_MB` and `CODEX_GRADLE_METASPACE_MB` can tune compiler memory.
The setup script installs a JDK when absent and downloads Android SDK 36,
build tools 35.0.0, NDK 29.0.14206865, and CMake 3.22.1 into the workspace.
The installer accepts the Android package licenses. It preserves the shell's proxy
configuration and configures JVM HTTP(S) clients to use that proxy.

```bash
bash scripts/build-codex-preview.sh
```

Generated tools, Gradle cache and APK are stored in the parent of
the checkout by default; `CODEX_ANDROID_WORK_DIR` overrides that directory.
Service configuration stays in the ignored `local.properties` file. An optional
`SIMKL_CLIENT_ID` environment variable sets that public app identifier at build time.
Never supply a personal access token, Simkl account token or Client Secret in its place.
Other optional service settings must be configured separately when needed.

The output is `../deliverables/Nuvio-One-TV-preview.apk`. Previews use the fork signing key already committed to the original repository,
so local and GitHub builds can update the same preview package. This public test key
is not a private production signing identity. Override the `NUVIO_RELEASE_STORE_FILE`,
`NUVIO_RELEASE_KEY_ALIAS`, `NUVIO_RELEASE_KEY_PASSWORD` and `NUVIO_RELEASE_STORE_PASSWORD`
environment variables together when using your own private key. Account data is not included.

## GitHub Actions

Run **Actions → Nuvio One TV APK → Run workflow**. Enter the public Simkl Client ID
in the optional input, or set the repository variable `SIMKL_CLIENT_ID` for repeat builds.
Download **Nuvio-One-TV-preview** from the successful workflow's artifacts.
Fork workflows may require enabling Actions in the fork first.

## Poster refresh

Under Advanced settings, choose **Refresh posters and restart**. It clears the image
memory/disk caches, metadata lookup caches and derived Continue Watching enrichment.
After restarting, images download as the relevant screens are browsed. Library items,
history, exact resume positions, accounts and settings are preserved. No upload to Simkl
is performed by this action. Broken source URLs or unavailable custom poster services
still require fixing the source or its configuration.

## Simkl reimport

This is a separate, explicitly confirmed action. It uploads local items and completed
history, fetches a full independent Simkl snapshot, verifies coverage, then switches
the sources to Simkl and clears the verified old local records. No remote delete API
is called and no local backup is restored. Successful uploads remain in the account
if a later step fails. Local resume positions/custom progress settings, unmatched items,
whole-series marks without episode coordinates, pending library deletes, changed local
data or an account/profile change stop the destructive part of the operation. This is
not an export of every application setting or every local field to Simkl.
