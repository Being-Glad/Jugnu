#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# dev-watch.sh  —  Jugnu / Metrolist hot-reload dev loop
#
# Watches all Kotlin, XML, and resource files for changes, then:
#   1. Rebuilds the app with Gradle
#   2. Installs it directly to the connected ADB device
#   3. Re-launches the app so you see the changes immediately
#
# Requirements:  brew install fswatch   (one-time setup)
# Usage:         ./dev-watch.sh
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

PACKAGE="com.jugnu.music.debug"
LAUNCH_ACTIVITY="com.metrolist.music.MainActivity"
DEVICE=$(adb devices | awk 'NR==2{print $1}')
WATCH_DIRS="app/src"

# ── Colours ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

log_info()    { echo -e "${CYAN}[watch]${RESET} $*"; }
log_success() { echo -e "${GREEN}[✓]${RESET} $*"; }
log_warn()    { echo -e "${YELLOW}[!]${RESET} $*"; }
log_error()   { echo -e "${RED}[✗]${RESET} $*"; }

# ── Checks ───────────────────────────────────────────────────────────────────
if ! command -v fswatch &>/dev/null; then
  log_error "fswatch not found. Install it with:  brew install fswatch"
  exit 1
fi

if [[ -z "$DEVICE" ]]; then
  log_error "No ADB device connected. Plug in your phone and enable USB debugging."
  exit 1
fi

log_info "Device: ${BOLD}$DEVICE${RESET}"
log_info "Watching: ${BOLD}$WATCH_DIRS${RESET}"
log_info "Press ${BOLD}Ctrl+C${RESET} to stop.\n"

# ── Build & install ──────────────────────────────────────────────────────────
build_and_install() {
  local changed_file="${1:-}"
  [[ -n "$changed_file" ]] && log_info "Changed: ${BOLD}$(basename "$changed_file")${RESET}"
  log_info "Building…"

  if ./gradlew :app:installFossDebug \
        --daemon \
        --build-cache \
        --configuration-cache \
        -q 2>&1; then
    log_success "Installed on $DEVICE"

    # Force-stop then relaunch so the fresh code is picked up immediately
    adb -s "$DEVICE" shell am force-stop "$PACKAGE" 2>/dev/null || true
    sleep 0.4
    adb -s "$DEVICE" shell am start -n "$PACKAGE/$LAUNCH_ACTIVITY" \
        --activity-clear-top 2>/dev/null || true

    log_success "App relaunched  🚀\n"
  else
    log_error "Build failed — fix the errors above and save again to retry.\n"
  fi
}

# ── Initial build ─────────────────────────────────────────────────────────────
build_and_install ""

# ── Watch loop ────────────────────────────────────────────────────────────────
# Debounce: ignore events that fire within 2 s of the last one (e.g. IDE saves
# multiple files at once for a single logical change).
LAST_BUILD=0
DEBOUNCE_SECS=2

fswatch \
  --recursive \
  --include='\.kt$' \
  --include='\.xml$' \
  --include='\.kts$' \
  --latency 1.0 \
  "$WATCH_DIRS" \
| while IFS= read -r changed_file; do
    NOW=$(date +%s)
    ELAPSED=$(( NOW - LAST_BUILD ))
    if (( ELAPSED >= DEBOUNCE_SECS )); then
      LAST_BUILD=$NOW
      build_and_install "$changed_file"
    fi
  done
