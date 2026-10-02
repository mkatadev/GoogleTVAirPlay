<div align="center">

# AirPlay for Google TV

**Darmowy, otwarty odbiornik AirPlay dla Google TV / Chromecasta**
Zdjęcia, muzyka i wideo z iPhone’a, iPada lub Maca — prosto na duży ekran.

[![Release](https://img.shields.io/github/v/release/mkatadev/GoogleTVAirPlay?label=pobierz&color=4c8dff)](https://github.com/mkatadev/GoogleTVAirPlay/releases/latest)
[![CI](https://github.com/mkatadev/GoogleTVAirPlay/actions/workflows/ci.yml/badge.svg)](https://github.com/mkatadev/GoogleTVAirPlay/actions/workflows/ci.yml)
[![Licencja: GPL-3.0](https://img.shields.io/badge/licencja-GPL--3.0-blue.svg)](LICENSE)
![Android TV 12+](https://img.shields.io/badge/Android%20TV-12%2B-3ddc84?logo=android&logoColor=white)

[🇬🇧 English](README.md) · 🇵🇱 Polski

<img src="docs/screenshots/home-pl.png" width="800" alt="Ekran główny — gotowy do odbioru AirPlay">

</div>

## Funkcje

- 📺 **Mirroring ekranu** z iPhone’a, iPada i Maca
- 🎬 **Wideo i zdjęcia** — telewizor sam odtwarza strumień (HLS), przewijanie pilotem
- 🎵 **Muzyka** — widoczny jako głośnik AirPlay, z okładką i informacjami o utworze
- ⚡ **Dekodowanie sprzętowe** — H.264 i HEVC (H.265), gdy telewizor to wspiera
- 🔒 **Opcjonalny PIN** przy każdym nowym połączeniu
- 🚀 **Działa w tle** i **startuje z telewizorem** — odbiornik zawsze gotowy
- 🎛️ Zaprojektowany pod pilota: UI w Compose for TV, bez dotyku
- 🌍 Polski i angielski

<div align="center">
<img src="docs/screenshots/settings-pl.png" width="800" alt="Ustawienia">
</div>

## Instalacja

Aplikacji nie ma w Google Play — zainstaluj APK z [**Releases**](https://github.com/mkatadev/GoogleTVAirPlay/releases/latest).

**Przez adb** (TV: Ustawienia → System → Informacje → 7× *Kompilacja systemu* → Opcje programisty → *Debugowanie USB / bezprzewodowe*):

```bash
adb connect <ip-telewizora>     # debugowanie bezprzewodowe — jednorazowo `adb pair <ip:port>`
adb install -r AirPlay-for-Google-TV-v*.apk
```

**Bez komputera:** skopiuj APK na pendrive albo otwórz link do wydania w menedżerze plików na TV
(np. *Downloader*, *X-plore*) i zainstaluj. Gdy TV zapyta, zezwól tej aplikacji na *Nieznane źródła*.

Po instalacji otwórz aplikację raz i przyznaj uprawnienie **Wyświetlanie nad innymi aplikacjami**, żeby odtwarzanie mogło przejąć ekran, gdy jesteś w innej aplikacji — aplikacja poprosi o nie przy pierwszym uruchomieniu:

<div align="center">
<img src="docs/screenshots/overlay-prompt-pl.png" width="49%" alt="Prośba w aplikacji: Zezwól na wyświetlanie nad innymi aplikacjami">
<img src="docs/screenshots/overlay-system.png" width="49%" alt="Ekran systemowy: AirPlay for Google TV — dozwolone">
</div>

## Jak używać

1. Telewizor i urządzenie Apple muszą być w tej samej sieci Wi-Fi.
2. Otwórz Zdjęcia, Muzykę, YouTube, Safari… i dotknij ikony AirPlay.
3. Wybierz telewizor (domyślna nazwa **Google TV**, do zmiany w Ustawieniach).

Podczas odtwarzania wideo: **OK** pauza/wznów · **◀ ▶** przewijanie (przytrzymaj, aby przyspieszyć) · **▲ ▼** skok ±10 % · **0–9** skok do 0–90 % · **Wstecz** stop.

## Budowanie ze źródeł

Wymagania: JDK 17, Android SDK 37. Rdzeń AirPlay to gotowy AAR, więc sama aplikacja nie potrzebuje NDK.

```bash
./gradlew :app:assembleDebug           # APK debug
./gradlew :app:testDebugUnitTest       # testy jednostkowe
./gradlew installChromecast            # build release → wybór TV → instalacja i uruchomienie
```

`installChromecast` sam skanuje urządzenia (USB, adb po Wi-Fi przez mDNS, port 5555 w lokalnych podsieciach /24) i przy kilku pyta, którego użyć — Enter powtarza ostatni wybór. `-Pchromecast=<ip[:port]>` lub `-Pchromecast=<serial adb>` pomija skanowanie.

### Podpisywanie release

`release.keystore` + `keystore.properties` w katalogu projektu (oba gitignored) podpisują build release; bez nich release jest podpisywany kluczem debug.

```bash
keytool -genkeypair -keystore release.keystore -alias tvairplay -keyalg RSA -keysize 4096 -validity 10000
```

`keystore.properties`: `storeFile=release.keystore`, `storePassword`, `keyAlias`, `keyPassword`. Zrób kopię zapasową — APK podpisany innym kluczem nie zaktualizuje aplikacji zainstalowanej na TV.

### Wydawanie (CI)

Wypchnięcie tagu publikuje podpisany APK + SHA-256 w GitHub Releases ([`release.yml`](.github/workflows/release.yml)):

```bash
git tag v1.2.0 && git push origin v1.2.0
```

`versionName` pochodzi z tagu, a `versionCode` jest z niego wyliczany (`1.2.3 → 10203`). Workflow wymaga sekretów repozytorium: `KEYSTORE_BASE64` (`base64 -i release.keystore`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. *Run workflow* w zakładce Actions buduje podpisany APK jako artefakt bez publikowania wydania. Każdy push i PR uruchamia [`ci.yml`](.github/workflows/ci.yml) (testy jednostkowe + build debug).

## Architektura

```
app/libs/airplay-core-1.0.0.aar   ← prebuilt rdzeń AirPlay (UxPlay, GPL-3.0), źródła: ../airplay-core
  pl.prodevcode.airplay.service.AirPlayService — foreground service, MediaSession, mDNS, renderery

:app  (tylko Google TV, Compose for TV)
  domain/         model · interfejsy repozytoriów · use case'y   ← bez zależności Android/Hilt poza javax.inject
  data/           ReceiverRepositoryImpl (bind do AirPlayService), SettingsRepositoryImpl, DeviceInfoRepositoryImpl
  presentation/   MainActivity · ReceiverScreen (idle / mirroring / wideo / audio) · SettingsScreen · theme
  di/             bindingi Hilt
```

Stack: AGP 9.4 (wbudowany Kotlin), Compose BOM 2026.06 + `androidx.tv:tv-material`, Hilt, KSP, Media3, Coroutines/Flow.

### Rdzeń AirPlay (`airplay-core`)

Kod natywny i serwis odbiornika są osobnym projektem **`../airplay-core`** (NDK r28 / CMake). Po zmianach:

```bash
cd ../airplay-core && ./gradlew :airplay-core:assembleRelease
cp airplay-core/build/outputs/aar/airplay-core-release.aar ../GoogleTVAirPlay/app/libs/airplay-core-1.0.0.aar
```

## Licencja

**GPL-3.0** — patrz [LICENSE](LICENSE). Rdzeń AirPlay wywodzi się z [UxPlay](https://github.com/FDH2/UxPlay) i
[jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server); informacje o komponentach zewnętrznych są w `airplay-core/NOTICE.md` oraz w aplikacji: *Ustawienia → Licencje open source*.

AirPlay jest znakiem towarowym Apple Inc. Projekt nie jest powiązany z Apple ani Google.

---

<div align="center">

Wspierane przez **ProDevCode** · [prodevcodepl@gmail.com](mailto:prodevcodepl@gmail.com) · [github.com/mkatadev](https://github.com/mkatadev)

</div>
