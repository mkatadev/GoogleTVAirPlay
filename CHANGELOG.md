# Changelog

All notable changes to AirPlay for Google TV are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow [Semantic Versioning](https://semver.org/).
The section for the version being released is used verbatim as the GitHub release notes.

## [Unreleased]

## [1.2.0] - 2026-10-06

### Added
- **Apple Home (HomeKit)** integration (Settings → Smart home → Apple Home): the TV appears in the Home app on iPhone, iPad and Mac as a *Television* accessory — switch the AirPlay receiver on and off, use the remote keys (play/pause, next/previous track, seek in AirPlay Video) and control volume/mute, locally or through a home hub. Pairing with a setup code shown on the TV; *Remove from Home* forgets all pairings.
- Own HomeKit Accessory Protocol implementation in the new `:homekit` module (TLV8, SRP-6a, pair-setup/pair-verify, encrypted sessions, characteristic events, `_hap._tcp` advertisement) with end-to-end JVM tests; no bridge or Home Assistant required.
- With HomeKit enabled the receiver service stays running in the background (also after boot) so Home can turn the receiver on while it is off; the notification shows the receiver as off.

### Changed
- Shared JVM test helpers moved to the `:testing` module.

### Fixed
- Update check no longer depends on the GitHub REST API (60 unauthenticated requests per hour per IP, shared by every device on the same network): the latest version comes from the `releases/latest` redirect, assets are addressed by their fixed names.

## [1.1.0] - 2026-10-03

### Added
- **Diagnostics** screen (Settings → Diagnostics): receiver, network address, mDNS registration per service and port checks with plain-language hints, live session counters, restart button.
- **Trusted devices**: a sender that entered the PIN once is remembered and skips it next time; manage them under Settings → Security (forget / forget all). *Remember paired devices* toggle.
- **Mirroring latency presets** (Settings → Media): Low / Balanced / Smooth, applied live.
- **Screen dimming** for audio-only sessions after a configurable idle time (Settings → General); any remote key wakes it; a paused, dimmed session lets the TV sleep.
- **Update check and in-app install**: Settings → About compares with GitHub Releases, downloads the signed APK, verifies its SHA-256 and hands it to the system installer (confirmed with the remote). The idle screen hints when an update is available.
- **Audio track and subtitle selection** for AirPlay Video (HLS): ▼ on the playback overlay opens the track menu; subtitles render through Media3; *Subtitles on by default* setting.
- Now-playing screen: focus lands on play/pause, ◀ ▶ and media keys switch tracks directly.
- **App language** (Settings → General): *Same as the TV*, English or Polish, applied instantly and remembered (per-app locale, also in the Android 13+ system language settings).
- **Device name editor** with ready-made suggestions (Living Room TV, Bedroom TV, …).
- Settings sub-pages (device name, language, dimming, latency, trusted devices) are separate screens; Back returns to the row you came from.
- PIN prompt: shown on every screen, with a 60 s countdown ring; *Cancel* / Back hides it while the PIN stays valid; blocks remote keys from reaching the screen underneath.
- `install.sh` one-liner installer documented in the README (EN/PL).
- Project rules for AI assistants (`.github/copilot-instructions.md`).

### Changed
- **PIN pairing is on by default.**
- The receiver re-announces itself over mDNS when the TV changes network (Wi-Fi ↔ Ethernet, new DHCP lease) — no manual Stop/Start.
- Locking the iPhone no longer ends audio/video playback: the session is held for 8 s over the short connection drop and a reconnecting sender is treated as the same client.
- Media controls moved to a Media3 `MediaSession` (system now-playing surface, media keys, notification) backed by the sender's audio session or the video player.
- Architecture: `:app` is Clean Architecture + MVI with a contract (UiState · Intent · Effect) per screen and a pure-Kotlin domain; `AirPlayService` is split into focused collaborators (video session, now-playing state, volume sync, media session, notifications, network watcher, device identity).
- Toolchain: Gradle 9.8, Compose compiler plugin 2.4.20, Compose BOM 2026.09, Media3 1.11.1, coroutines 1.11, Oboe 1.11; `androidx.media` dropped, `androidx.appcompat` added for per-app locales.
- CI runs unit tests of both modules and Android Lint; compiler and Lint are warning-free.

### Fixed
- `LifecycleService.onStartCommand` was not calling `super`, so the service lifecycle never reached STARTED.
- `DacpController` used the deprecated `resolveService` path on API 34+.

## [1.0.0] - 2026-10-02

### Added
- First release: AirPlay receiver for Google TV / Chromecast — screen mirroring, AirPlay Video (HLS) with D-pad seeking, AirPlay audio with cover art and track info, hardware H.264/HEVC decoding, optional PIN, background service with start on boot, English and Polish UI.

[Unreleased]: https://github.com/mkatadev/GoogleTVAirPlay/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/mkatadev/GoogleTVAirPlay/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/mkatadev/GoogleTVAirPlay/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/mkatadev/GoogleTVAirPlay/releases/tag/v1.0.0
