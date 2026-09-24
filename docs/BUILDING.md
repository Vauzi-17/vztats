# Building VZtats

For developers. If you just want to use the app, grab the APK from
[Releases](https://github.com/Vauzi-17/vztats/releases) instead.

## Requirements

- Android SDK with NDK (arm64)
- JDK 17–21
- `compileSdk 36`, `minSdk 25`, **`targetSdk 25`**

```bash
./gradlew assembleDebug
```

> [!IMPORTANT]
> `targetSdk` is deliberately pinned at 25. A higher `targetSdk` moves the app
> into a stricter SELinux domain that is denied read access to
> `/sys/class/kgsl`, which breaks the GPU frequency readout. (The lock ioctl
> itself keeps working, because `/dev/kgsl-3d0` is a device node rather than
> sysfs.) This is why the app is not Play Store compliant and is sideload-only.
>
> The `ExpiredTargetSdkVersion` lint check is disabled for the same reason.

## Signed release builds

Signing credentials are read from `local.properties` (gitignored) or from
environment variables, so the keystore and its passwords never enter the
repository. `*.jks` and `*.keystore` are gitignored as a second line of defence.

**Without** a configured keystore, `./gradlew assembleRelease` still succeeds and
produces `app-release-unsigned.apk` — cloning and building the project needs no
key. Only a machine holding the key can produce an installable APK.

### 1. Create a keystore

Pick your own passwords when prompted:

```bash
keytool -genkeypair -v -keystore vztats-release.jks \
  -alias vztats -keyalg RSA -keysize 4096 -validity 10000
```

Store it **outside** the repository.

### 2. Point the build at it

In `local.properties`:

```properties
vztats.storeFile=C:/path/to/vztats-release.jks
vztats.storePassword=...
vztats.keyAlias=vztats
vztats.keyPassword=...
```

CI can supply `VZTATS_STOREFILE`, `VZTATS_STOREPASSWORD`, `VZTATS_KEYALIAS` and
`VZTATS_KEYPASSWORD` as environment variables instead.

### 3. Build

```bash
./gradlew assembleRelease
```

The signed APK lands in `app/build/outputs/apk/release/app-release.apk`.

Verify it before publishing:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

`Verified using v2 scheme` should be `true`. v1 (JAR) signing is not used and is
not needed — the v2 scheme landed in API 24 and `minSdk` is 25, so every
supported device can verify it.

> [!CAUTION]
> Back the keystore up and keep it private. Every future update must be signed
> with the **same** key: Android refuses to install an update signed by a
> different one, and a lost keystore cannot be recovered — it would mean
> publishing under a new package name and asking users to reinstall.

## Building on GitHub Actions

`.github/workflows/build.yml` builds an APK on demand from any branch, tag or
commit: **Actions → Build APK → Run workflow**. Enter the branch and pick
`debug` or `release`.

It installs the same toolchain this page and `app/build.gradle.kts` pin
(JDK 17, platform 36, build-tools 36.1.0, NDK 27.0.12077973, CMake 3.22.1).
If you change a version in `app/build.gradle.kts`, change it in the
workflow's `env:` block as well.

| Build type | APK name | Where it goes |
|---|---|---|
| `debug` | `VZtats-v<version>-debug-<branch>-<commit>.apk` | Run artifact only |
| `release` | `VZtats-v<version>.apk` | Run artifact **and** a GitHub Release |

### Releasing a new version

1. Bump `versionCode` (always +1) and `versionName` in `app/build.gradle.kts`.
   `versionName` becomes the release tag (e.g. `0.2`, same style as `0.1`).
2. Write the user-facing notes in `docs/releases/<versionName>.md`
   (e.g. `docs/releases/0.2.md`). Commit both to `main`.
3. Run **Build APK** with branch `main` and build type `release`.

The workflow stops within seconds if the notes file is missing or the tag
already exists, before spending time on a build. The GitHub Release body is
your notes file, followed by a download table (file, size, SHA-256) and
GitHub's generated list of changes since the previous release.

**Draft** (default: on) creates the release as a draft, so you can read it and
press *Publish* yourself. **Pre-release** (default: on) keeps the
pre-release badge while the app is in 0.x.

Releases are signed only when all four repository secrets are set. Use the
**same keystore as earlier releases**, or Android refuses to install the
update over them:

| Secret | Value |
|---|---|
| `VZTATS_KEYSTORE_BASE64` | `base64 -w0 vztats-release.jks` |
| `VZTATS_STOREPASSWORD` | keystore password |
| `VZTATS_KEYALIAS` | key alias |
| `VZTATS_KEYPASSWORD` | key password |

Without them the release build still runs and uploads
`VZtats-v<version>-unsigned.apk` as an artifact, but no GitHub Release is
created, because an unsigned APK can't be installed.

## Project layout

| Path | What's in it |
|---|---|
| `app/src/main/java/com/vauzi/vztats/core/` | Sensor reads (GPU/CPU/power), prefs, session recording |
| `app/src/main/java/com/vauzi/vztats/ui/` | Compose UI — screens, components, theme |
| `app/src/main/java/com/vauzi/vztats/service/` | Foreground service, floating overlay, QS tile |
| `app/src/main/java/com/vauzi/vztats/shizuku/` | Shizuku plumbing (FPS sampling, shell exec) |
| `app/src/main/cpp/` | JNI bridge to the KGSL ioctl, plus bundled libadrenotools |
