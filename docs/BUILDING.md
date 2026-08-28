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

## Project layout

| Path | What's in it |
|---|---|
| `app/src/main/java/com/vauzi/vztats/core/` | Sensor reads (GPU/CPU/power), prefs, session recording |
| `app/src/main/java/com/vauzi/vztats/ui/` | Compose UI — screens, components, theme |
| `app/src/main/java/com/vauzi/vztats/service/` | Foreground service, floating overlay, QS tile |
| `app/src/main/java/com/vauzi/vztats/shizuku/` | Shizuku plumbing (FPS sampling, shell exec) |
| `app/src/main/cpp/` | JNI bridge to the KGSL ioctl, plus bundled libadrenotools |
