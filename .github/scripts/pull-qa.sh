#!/usr/bin/env bash
# Pull the app's qa/ folder from the emulator into ./qa-artifacts (T13a).
# 1) adb pull of the external files dir (the path the app writes to).
# 2) fallback when no PNG arrived: run-as (the debug build is debuggable), one file at a time.
set -u
PKG=com.example.accounting
REMOTE_PUB=/sdcard/Android/data/$PKG/files
DEST=qa-artifacts
mkdir -p "$DEST"

adb pull "$REMOTE_PUB/qa" "$DEST/" </dev/null || echo "adb pull of $REMOTE_PUB/qa failed"

if ! find "$DEST" -name '*.png' | grep -q .; then
  echo "adb pull produced no PNG; trying run-as fallback"
  echo "== device listing: $REMOTE_PUB =="
  adb shell "ls -la $REMOTE_PUB" </dev/null 2>&1 | head -40 || true
  BASE=/storage/emulated/0/Android/data/$PKG/files/qa
  echo "== run-as listing: $BASE =="
  adb shell run-as "$PKG" ls "$BASE" </dev/null 2>&1 | tr -d '\r' | head -60 || true
  mkdir -p "$DEST/qa"
  adb shell run-as "$PKG" ls "$BASE" </dev/null 2>/dev/null | tr -d '\r' > "$DEST/qa-listing.txt" || true
  while IFS= read -r f; do
    case "$f" in
      *.png|*.pdf|*.csv)
        if adb exec-out run-as "$PKG" cat "$BASE/$f" </dev/null > "$DEST/qa/$f"; then
          echo "copied $f"
        fi
        ;;
    esac
  done < "$DEST/qa-listing.txt"
fi
find "$DEST" -type f | sort | head -80
