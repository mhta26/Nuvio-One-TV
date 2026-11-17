# Nuvio One TV stable release

This fork adds an independent poster refresh action, device-aware image concurrency,
and a guarded upload/verify/reimport action for the local library and completed watch
history. The application package remains `app.onetube.tv.personal.preview`, so existing personal previews update in place and it installs beside
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

The outputs are `../deliverables/Nuvio-One-TV-arm64-v8a.apk` and
`../deliverables/Nuvio-One-TV-armeabi-v7a.apk`; each is a standalone application
for its architecture. The build fills missing Nuvio backend settings from the
official public discovery endpoint, so a fresh personal release can generate login QR codes.
Existing complete custom backend settings are preserved. Personal releases use the fork signing key already committed to the original repository,
so local and GitHub builds can update the same preview package. This public test key
is not a private production signing identity. Override the `NUVIO_RELEASE_STORE_FILE`,
`NUVIO_RELEASE_KEY_ALIAS`, `NUVIO_RELEASE_KEY_PASSWORD` and `NUVIO_RELEASE_STORE_PASSWORD`
environment variables together when using your own private key. Account data is not included.

## GitHub Actions

Run **Actions → Nuvio One TV APK → Run workflow**. Provide the public Simkl, Trakt
and MDBList Client IDs through the inputs, or set `SIMKL_CLIENT_ID`, `TRAKT_CLIENT_ID`
and `MDBLIST_CLIENT_ID` as Actions repository variables for automatic repeat builds.
The stable workflow stops when these app identifiers are missing. A Trakt Client Secret
is not required. Download **Nuvio-One-TV-release** from the successful workflow's artifacts,
or use the normal GitHub Release created after all checks pass.
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

## Validation of the first personal preview

On 2026-11-15, the full debug APK assembled successfully and all 12 selected Simkl
storage/reimport tests passed. APK signature verification passed. Package inspection
confirmed the `Nuvio One TV` label, package `app.onetube.tv.personal.preview`, minSdk 24,
and both `armeabi-v7a` and `arm64-v8a`. Playback, account linking and performance on a
physical TV still require device testing; these checks do not establish runtime behavior.

Personal releases assemble fullRelease with R8 code optimization and resource shrinking, without debugger or Compose tooling. The personal application ID and signing identity remain compatible with earlier previews. The full release unit-test suite and full Android Lint run before packaging. Tests and R8 run in separate Gradle processes with one worker, avoiding concurrent compiler/optimizer memory pressure. The personal build timestamp is pinned across all invocations to preserve the same version and reuse compilation outputs.

## Trakt and MDBList app registration

Set `TRAKT_CLIENT_ID` and `MDBLIST_CLIENT_ID` as GitHub repository variables
(or secrets), or supply their public Client IDs in the manual workflow inputs.
The local helper accepts these names as environment variables too.
MDBList requires a **Device Code App** Client ID, not an API key or a PKCE app ID.
Trakt TV device-code login uses the Client ID alone, including for new public apps.
Set `TRAKT_REDIRECT_URI` to the exact URI registered in your Trakt app for token refresh.
No Trakt Client Secret is needed or embedded. OAuth requests go to `auth.trakt.tv`;
ordinary API calls remain on `api.trakt.tv`, following Trakt's current contract:
https://developer.trakt.tv/docs/authentication-oauth
https://github.com/trakt/trakt-api/blob/master/projects/api/src/contracts/oauth/index.ts

Existing APKs require an update to gain these changes and app identifiers.
The full test suite includes OAuth request contracts, MDBList authorization,
protected Simkl reimport, profile-cache isolation, backup coalescing and cancellation.

Image work scheduling uses physical RAM and Android's low-RAM flag in addition to
heap limits. TVs with up to 3 GB RAM use at most 8 asynchronous image requests,
4 per host and 2 bitmap decode workers, even when Android grants a 512 MB or
larger Java heap. This preserves image quality, cache sizes, playback budgets
and features while reducing competing image work during navigation.

## Stable identity and upgrade compatibility

The default personal release is version `1.2.0`, channel `stable`, with no Test
resource overlay. `NUVIO_ONE_TV_VERSION` (or the workflow's `app_version` input)
sets a future stable major.minor.patch version. Increment it for future updates (for
example `1.2.1`); build metadata alone does not make a semantic version newer for the
in-app updater. Version codes remain on the
previous personal build's monotonically increasing track, with the same package
and signing identity, so no uninstall or data reset is needed.

Stable GitHub releases use a semantic version tag with build metadata and are
published as normal releases, so the built-in stable updater uses this fork.
[Brand assets and packaging](branding/nuvio-one-tv/README.md) describe the new
logo, TV banners and lightweight launch screen.

## Code audit checks

The GitHub workflow runs all release unit tests, full Android Lint, then APK
packaging in separate Gradle processes. A pinned build timestamp keeps all phases
on the same version and reuses compilation outputs. `--test-only`, `--lint-only`
and `--package-only` modes are available for the helper; the default runs all
three. `Nuvio-One-TV-audit-reports` contains test XML/HTML and lint XML/HTML.
No APK is published when unit tests or lint fail.

The unit-test XML/HTML artifact is uploaded immediately after the tests, before lint.
See [the completed code audit](CODE-AUDIT-NUVIO-ONE-TV.md) for the stable release's
results, native-buffer regression coverage and APK verification.
