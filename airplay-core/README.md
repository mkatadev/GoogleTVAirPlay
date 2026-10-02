# airplay-core

Android library module wrapping [UxPlay](https://github.com/FDH2/UxPlay) as an AirPlay receiver: RAOP/FairPlay,
screen mirroring (H.264/H.265), music (ALAC/AAC) and HLS video, mDNS advertising, foreground `AirPlayService`
with MediaSession. Derived from [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server).
**GPL-3.0** — see `/LICENSE` and [`NOTICE.md`](NOTICE.md).

Consumed by `:app` as `implementation(project(":airplay-core"))`. Public surface:
`pl.prodevcode.airplay.service.AirPlayService` (bind via `LocalBinder`, observe its `StateFlow`s),
`pl.prodevcode.airplay.Prefs` (shared settings keys), `BootReceiver` (auto-start).

## Native build

`src/main/cpp/CMakeLists.txt` builds, per ABI (arm64-v8a, armeabi-v7a, x86_64):

| Target | Source | Notes |
|--------|--------|-------|
| OpenSSL 3.x | tarball fetched by `third_party/openssl-cmake` | version in `OPENSSL_BUILD_VERSION` |
| FFmpeg (libavcodec/libavutil, ALAC decoder only) | `third_party/ffmpeg` | `cmake/BuildFFmpeg.cmake` |
| libplist, llhttp, playfair | `third_party/libplist`, `third_party/UxPlay/lib` | static |
| UxPlay core + Android bridge | `third_party/UxPlay/lib` **with `patches/UxPlay` applied** | `cmake/PatchUxPlay.cmake` |

Patches are applied at configure time onto a copy under the build dir, so `git status` in the submodule stays clean.
If a patch stops applying after an upstream bump, configure fails with the patch name — see `tools/third-party.sh
rebase-patches` in the repo root.

Requirements: NDK 28.2.13676358, CMake 3.22+, `make`, `perl`. Standalone: `./gradlew :airplay-core:assembleRelease`
→ `build/outputs/aar/airplay-core-release.aar`.

## Third-party versions

See [`src/main/cpp/third_party/VERSIONS.md`](src/main/cpp/third_party/VERSIONS.md) (generated) and the
*Third-party code & updates* section of the root README.
