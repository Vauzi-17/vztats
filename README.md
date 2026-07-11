# AdrenoGPU-Turbo-Mode

Locks the maximum frequency of Adreno GPUs ("Turbo mode") **without root or ADB**,
by disabling the KGSL driver's dynamic clock scaling (DCVS) through a userspace ioctl.

Example on Samsung Galaxy Z Fold4:

![Example.](example.gif)

## How it works

The app opens `/dev/kgsl-3d0` — the Adreno kernel driver device that every rendering
app is already allowed to use — and issues `IOCTL_KGSL_SETPROPERTY` with
`KGSL_PROP_PWRCTRL`. Disabling power control pins the GPU at its highest clock. No
system files are modified, so no root is required.

## Features (v2.0)

- **Jetpack Compose + Material 3 UI**, light/dark, with Material You dynamic color.
- **Verified turbo status** — the app reports whether the kernel actually accepted
  the request and shows a live "clock at max" confirmation read back from sysfs,
  instead of assuming it worked.
- **Floating overlay toggle** — a draggable ON/OFF button you can use over a running
  game (needs the "display over other apps" permission).
- **Keep-alive foreground service** that re-applies turbo after the screen unlocks,
  working around the "stuck at low clock" behaviour on some devices.
- **Quick Settings tile** to toggle turbo without opening the app.
- **Live monitor** — frequency graph, current/max clock, % of max, and GPU
  temperature straight from the kernel thermal zones.
- **Optional auto-safety** — automatically disables turbo above a temperature limit
  or below a battery floor. Off by default, so if you want max clocks at all times,
  just leave it off.

## You should be aware of the following

1. Turbo mode only works on Adreno (Qualcomm/KGSL) GPUs.
2. The application does not require ADB or ROOT access.
3. Locking the maximum GPU frequency may increase device heating and battery drain ⚠️
4. Turbo mode may not work on all devices. If the status shows "applied but not
   verified" or "failed", the kernel likely ignored the request.
5. On some devices Turbo turns off when the GPU is idle. Use the floating toggle
   while the game is running.
6. On some devices the GPU may get stuck at a low frequency after locking the
   screen. The keep-alive service tries to re-apply Turbo automatically on unlock.

## Building

Requires the Android SDK, an Adreno-capable NDK (arm64), and JDK 17–21.
compileSdk 36, minSdk 25, **targetSdk 25**.

> targetSdk is deliberately kept at 25. A higher targetSdk moves the app into a
> stricter SELinux domain that is denied read access to `/sys/class/kgsl`, which
> breaks the GPU-frequency readout (the turbo ioctl itself keeps working because
> `/dev/kgsl-3d0` is a device node, not sysfs). This is why the app is not
> Play Store compliant and is meant for sideloading.

```
./gradlew assembleDebug
```

## Third party

- [libadrenotools](https://github.com/bylaws/libadrenotools/) by Billy Laws.
