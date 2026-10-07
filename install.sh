#!/usr/bin/env bash
# AirPlay for Google TV — installer. No Android Studio / SDK needed: downloads adb (platform-tools) and the
# latest release APK, finds the TV and installs it.
#
#   curl -fsSL https://raw.githubusercontent.com/mkatadev/GoogleTVAirPlay/main/install.sh | bash
#   ./install.sh [--ip 192.168.1.42[:port]] [--pair] [--version 1.2.0] [--apk file.apk]
#
# On the TV first: Settings → System → About → tap "Android TV OS build" 7× → Developer options →
# enable USB debugging (cable) or Wireless debugging (Wi-Fi; pair once with --pair).
set -euo pipefail

REPO="mkatadev/GoogleTVAirPlay"
PACKAGE="pl.prodevcode.tvairplay"
ACTIVITY="$PACKAGE/.presentation.MainActivity"
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/tvairplay"

ip="" version="" apk="" pair=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ip) ip="$2"; shift 2 ;;
    --ip=*) ip="${1#--ip=}"; shift ;;
    --pair) pair=1; shift ;;
    --version) version="${2#v}"; shift 2 ;;
    --version=*) version="${1#--version=}"; version="${version#v}"; shift ;;
    --apk) apk="$2"; shift 2 ;;
    --apk=*) apk="${1#--apk=}"; shift ;;
    -h|--help) sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
  esac
done

say()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mwarning:\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "'$1' is required"; }
# `curl … | bash` leaves stdin as the script, so prompts must read from the terminal
ask()  { local reply; printf '%s' "$1" > /dev/tty; IFS= read -r reply < /dev/tty; printf '%s' "$reply"; }

need curl

# --- adb -------------------------------------------------------------------------------------------
if command -v adb >/dev/null 2>&1; then
  ADB=adb
elif [[ -x "$CACHE/platform-tools/adb" ]]; then
  ADB="$CACHE/platform-tools/adb"
else
  case "$(uname -s)" in
    Darwin) os=darwin ;;
    Linux) os=linux ;;
    *) die "Unsupported OS: $(uname -s). Install adb manually and re-run (on Windows use WSL)." ;;
  esac
  need unzip
  say "Downloading adb (Android platform-tools, ~15 MB) to $CACHE"
  mkdir -p "$CACHE"
  curl -fL --progress-bar "https://dl.google.com/android/repository/platform-tools-latest-$os.zip" -o "$CACHE/platform-tools.zip"
  unzip -qo "$CACHE/platform-tools.zip" -d "$CACHE" && rm -f "$CACHE/platform-tools.zip"
  ADB="$CACHE/platform-tools/adb"
fi
"$ADB" start-server >/dev/null 2>&1 || true

# --- APK -------------------------------------------------------------------------------------------
if [[ -z "$apk" ]]; then
  need python3
  if [[ -n "$version" ]]; then api="https://api.github.com/repos/$REPO/releases/tags/v$version"
  else api="https://api.github.com/repos/$REPO/releases/latest"; fi
  label="the latest release"; [[ -n "$version" ]] && label="v$version"
  say "Looking up $label"
  release_json="$(curl -fsSL -H 'Accept: application/vnd.github+json' "$api")" || die "Release not found: $api"
  read -r tag apk_url sha_url < <(printf '%s' "$release_json" | python3 -c '
import json, sys
r = json.load(sys.stdin)
urls = {a["name"]: a["browser_download_url"] for a in r.get("assets", [])}
apk = next((u for n, u in urls.items() if n.endswith(".apk")), "")
sha = next((u for n, u in urls.items() if n.endswith(".apk.sha256")), "")
print(r.get("tag_name", ""), apk, sha)')
  [[ -n "$apk_url" ]] || die "The release has no APK attached"
  mkdir -p "$CACHE"
  apk="$CACHE/${apk_url##*/}"
  say "Downloading ${apk_url##*/}"
  curl -fL --progress-bar "$apk_url" -o "$apk"
  if [[ -n "$sha_url" ]]; then
    expected="$(curl -fsSL "$sha_url" | awk '{print $1}')"
    if command -v sha256sum >/dev/null 2>&1; then actual="$(sha256sum "$apk" | awk '{print $1}')"
    else actual="$(shasum -a 256 "$apk" | awk '{print $1}')"; fi
    [[ "$expected" == "$actual" ]] || die "SHA-256 mismatch for $apk"
    say "SHA-256 verified"
  fi
else
  [[ -f "$apk" ]] || die "APK not found: $apk"
fi

# --- device ----------------------------------------------------------------------------------------
# serial<TAB>state lines, skipping the header and blank lines
devices() { "$ADB" devices | awk -F'\t' 'NR > 1 && NF >= 2 { print $1 "\t" $2 }'; }

if (( pair )); then
  echo "On the TV: Developer options → Wireless debugging → Pair device with pairing code."
  addr="$(ask 'Pairing IP:port shown on the TV: ')"
  code="$(ask 'Pairing code: ')"
  "$ADB" pair "$addr" "$code" || die "Pairing failed"
  [[ -n "$ip" ]] || ip="$(ask 'Now the IP:port from the main Wireless debugging screen: ')"
fi

if [[ -n "$ip" ]]; then
  [[ "$ip" == *:* ]] || ip="$ip:5555"
  say "Connecting to $ip"
  out="$("$ADB" connect "$ip" 2>&1)"; echo "$out"
  case "$out" in
    *"connected to"*) ;;
    *"failed to authenticate"*|*"unauthorized"*)
      die "Not authorized — accept the debugging prompt on the TV, or pair first with: $0 --pair" ;;
    *) die "Could not connect to $ip. Check Wireless debugging is on and the port matches (it changes after the TV sleeps)." ;;
  esac
fi

online_devices() { devices | awk -F'\t' '$2 == "device" { print $1 }'; }
# bash 3 (macOS) has no mapfile
online=(); while IFS= read -r s; do online+=("$s"); done < <(online_devices)
if (( ${#online[@]} == 0 )); then
  mdns=(); while IFS= read -r s; do [[ -n "$s" ]] && mdns+=("$s"); done \
    < <("$ADB" mdns services 2>/dev/null | awk -F'\t' '$2 ~ /_adb-tls-connect/ { print $3 }' | sort -u)
  if (( ${#mdns[@]} > 0 )); then
    say "Wireless debugging found at: ${mdns[*]}"
    for a in "${mdns[@]}"; do "$ADB" connect "$a" >/dev/null 2>&1 || true; done
    online=(); while IFS= read -r s; do online+=("$s"); done < <(online_devices)
  fi
fi
if (( ${#online[@]} == 0 )); then
  if devices | grep -q $'\tunauthorized'; then
    die "A device is connected but unauthorized — accept the debugging prompt on the TV and re-run."
  fi
  echo "No TV found over USB or Wi-Fi."
  echo "Wireless debugging (Android 11+): pair once with '$0 --pair', later just '$0 --ip <ip:port>'."
  echo "Older 'ADB debugging' over network: '$0 --ip <tv-ip>'."
  exit 1
fi

if (( ${#online[@]} == 1 )); then
  serial="${online[0]}"
else
  echo "Several devices are online:"
  for i in "${!online[@]}"; do
    model="$("$ADB" -s "${online[$i]}" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
    printf '  %d) %s  %s\n' $((i + 1)) "${online[$i]}" "$model"
  done
  choice="$(ask "Install on [1]: ")"
  choice="${choice:-1}"
  serial="${online[$((choice - 1))]:-}"
  [[ -n "$serial" ]] || die "Invalid choice"
fi

model="$("$ADB" -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
if ! "$ADB" -s "$serial" shell pm list features 2>/dev/null | grep -q android.software.leanback; then
  warn "$serial ($model) does not look like an Android TV device"
fi

# --- install ---------------------------------------------------------------------------------------
say "Installing on $serial ($model) …"
if ! out="$("$ADB" -s "$serial" install -r "$apk" 2>&1)"; then
  echo "$out"
  if [[ "$out" == *INCONSISTENT_CERTIFICATES* || "$out" == *UPDATE_INCOMPATIBLE* ]]; then
    reply="$(ask "An existing install is signed with a different key. Uninstall it (settings are lost) and retry? [y/N] ")"
    [[ "$reply" =~ ^[Yy]$ ]] || exit 1
    "$ADB" -s "$serial" uninstall "$PACKAGE" >/dev/null || true
    "$ADB" -s "$serial" install -r "$apk"
  else
    die "Installation failed"
  fi
fi
"$ADB" -s "$serial" shell am start -n "$ACTIVITY" >/dev/null 2>&1 || true
say "Done — AirPlay for Google TV ${tag:+$tag }is installed and running on $model."
echo "Open it once and allow “Display over other apps” when asked."
