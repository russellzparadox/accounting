#!/usr/bin/env bash
# Pull the app's internal files/qa/ folder from the emulator into ./qa-artifacts/qa (T13a).
# The tests write to targetContext.filesDir/qa (QaOutput.dir). adb pull cannot read Android/data on API 30+
# (shell uid), so the debug build (debuggable) is read with `run-as` = the app's own uid.
# 1) tar stream of files/qa. 2) fallback: per-file cat from the run-as listing. Diagnostics go to ::notice lines.
set -u
PKG=com.example.accounting
DEST=qa-artifacts
mkdir -p "$DEST"

echo "== run-as listing: files/qa (ls -la) =="
adb shell run-as "$PKG" ls -la files/qa </dev/null 2>&1 | tr -d '\r' | head -60 || true
adb shell run-as "$PKG" ls -la files </dev/null 2>&1 | tr -d '\r' | head -40 || true

echo "== primary: run-as tar c files/qa =="
if adb exec-out run-as "$PKG" tar c -C files qa </dev/null > "$DEST/qa.tar" 2> "$DEST/qa-pull-error.txt"; then
  tar xf "$DEST/qa.tar" -C "$DEST" && echo "tar extracted" || echo "tar extract failed"
else
  echo "run-as tar failed; stderr:"
  head -c 600 "$DEST/qa-pull-error.txt" || true
fi
rm -f "$DEST/qa.tar"

if ! find "$DEST/qa" -name '*.png' 2>/dev/null | grep -q .; then
  echo "tar produced no PNG; per-file run-as cat fallback"
  mkdir -p "$DEST/qa"
  adb shell run-as "$PKG" ls files/qa </dev/null 2>/dev/null | tr -d '\r' > "$DEST/qa-listing.txt" || true
  while IFS= read -r f; do
    case "$f" in
      *.png|*.pdf|*.csv)
        if adb exec-out run-as "$PKG" cat "files/qa/$f" </dev/null > "$DEST/qa/$f"; then
          echo "copied $f"
        fi
        ;;
    esac
  done < "$DEST/qa-listing.txt"
fi

echo "== pulled files =="
find "$DEST" -type f | sort | head -80
echo "::notice title=qa-pull::pulled $(find "$DEST/qa" -type f 2>/dev/null | wc -l) files from files/qa"
