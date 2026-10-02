# Third-party notices — :airplay-core

This module is a derivative work of
[jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) (GPL-3.0),
adapted into a reusable Android library (package `pl.prodevcode.airplay`).

Native components under `src/main/cpp/third_party`:

| Component | License | Notes |
|-----------|---------|-------|
| [UxPlay](https://github.com/FDH2/UxPlay) | GPL-3.0 | AirPlay/RAOP server; local patches in `src/main/cpp/patches/UxPlay` are already applied |
| [libplist](https://github.com/libimobiledevice/libplist) | LGPL-2.1 | |
| [OpenSSL](https://www.openssl.org) via [openssl-cmake](https://github.com/viaduck/openssl-cmake) | Apache-2.0 | built from source |
| [FFmpeg](https://ffmpeg.org) (libavcodec, ALAC decoder only) | LGPL-2.1 | built from source |
| [Oboe](https://github.com/google/oboe) | Apache-2.0 | prefab AAR |

The whole application is therefore distributed under **GPL-3.0** (see `/LICENSE`).
