<div align="center">

<img src="logo.png" alt="VZtats" width="140">

# VZtats

**A no-root performance monitor for Android, with an Adreno GPU clock lock.**

[![Pre-release](https://img.shields.io/badge/status-pre--release-orange)](https://github.com/Vauzi-17/vztats/releases)
[![Version](https://img.shields.io/badge/version-0.2-blue)](https://github.com/Vauzi-17/vztats/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

</div>

> [!WARNING]
> Pre-release: expect bugs and changes between versions. Device-specific
> [issue reports](https://github.com/Vauzi-17/vztats/issues) help the most.

<div align="center">

| Home | Floating monitor |
|:---:|:---:|
| <img src="screenshots/home.jpg" alt="Home screen" width="280"> | <img src="screenshots/floating-monitor.jpg" alt="Floating monitor settings" width="280"> |

</div>

## Features

- **Live monitoring:** GPU, per-core CPU, RAM, battery and temperatures, plus a GPU clock graph
- **Floating overlay:** three layouts, pick your own metrics, draggable
- **FPS** per game (needs [Shizuku](https://shizuku.rikka.app/))
- **RAM cleaner** (needs Shizuku): stop and block apps based on measured memory, restore them anytime
- **GPU Lock** (Adreno only): holds the GPU at its max clock, no root or ADB
- **Session recording** with CSV export

Full details: [docs/FEATURES.md](docs/FEATURES.md)

## Install

1. Download the APK from [Releases](https://github.com/Vauzi-17/vztats/releases) and install it.
2. Optional: install and start [Shizuku](https://shizuku.rikka.app/), then pair it in
   **Settings → Shizuku** for FPS and the RAM cleaner.

Needs Android 7.1+ on arm64. No root. Not on the Play Store: VZtats targets an old
API level on purpose so it can keep reading the GPU clock.

## Good to know

- **Clock %** is how close a clock is to its maximum, not CPU usage. Real CPU usage needs root.
- **GPU Lock** works only on Adreno, some kernels ignore it, and it **increases heat and battery drain**.
- **Apps blocked by the RAM cleaner don't send notifications** until you restore them.

All limitations: [docs/FEATURES.md](docs/FEATURES.md#known-limitations--bugs)

## Building

See [docs/BUILDING.md](docs/BUILDING.md).

## Credits

- [AdrenoGPU-Turbo-Mode](https://github.com/Fartopblu/AdrenoGPU-Turbo-Mode) by Fartopblu: the original app this is forked from (MIT)
- [libadrenotools](https://github.com/bylaws/libadrenotools) by Billy Laws (BSD 2-Clause)
- [Shizuku](https://github.com/RikkaApps/Shizuku) by RikkaApps (Apache 2.0)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) and Material 3 by Google (Apache 2.0)

UI inspired by [Scene](https://github.com/helloklf/vtools) and
[PerfMon-Plus](https://github.com/libxzr/PerfMon-Plus); no code taken from either.

## License

[MIT](LICENSE). `app/src/main/cpp/adrenotools/` is BSD 2-Clause (© 2021 Billy Laws),
see [its LICENSE](app/src/main/cpp/adrenotools/LICENSE).
