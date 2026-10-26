#!/usr/bin/env bash
# Validate the final APK's ZIP and native ELF alignment for 16 KB Android pages.
# Usage: tools/check-android-16kb.sh path/to/app-release.apk
set -euo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
  echo "Usage: $0 path/to/app.apk" >&2
  exit 2
fi
APK=$(realpath "$1")
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$ANDROID_HOME" || ! -d "$ANDROID_HOME/build-tools" || ! -d "$ANDROID_HOME/ndk" ]]; then
  echo "::error::Android SDK/NDK not found (set ANDROID_HOME or ANDROID_SDK_ROOT)"
  exit 1
fi

ZIPALIGN=$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -type f -name zipalign -print | sort -V | tail -n 1)
if [[ -z "$ZIPALIGN" ]]; then
  echo "::error::Android zipalign tool not found under $ANDROID_HOME/build-tools"
  exit 1
fi
"$ZIPALIGN" -c -P 16 -v 4 "$APK"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
unzip -o -q "$APK" 'lib/*/*.so' -d "$WORK"
shopt -s nullglob
LIBS=("$WORK"/lib/*/*.so)
if [[ ${#LIBS[@]} -eq 0 ]]; then
  echo "::error title=16 KB ELF::No native shared libraries found in APK"
  exit 1
fi

OBJDUMP=$(find "$ANDROID_HOME/ndk" -path '*/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-objdump' -type f -print | sort -V | tail -n 1)
if [[ -z "$OBJDUMP" ]]; then
  echo "::error::llvm-objdump not found in Android NDK"
  exit 1
fi

FAILURES=0
for LIB in "${LIBS[@]}"; do
  ALIGNMENTS=$("$OBJDUMP" -p "$LIB" | awk '$1 == "LOAD" {print $NF}' | sort -u | tr '\n' ' ')
  if [[ -z "$ALIGNMENTS" ]]; then
    echo "::error title=16 KB ELF::Could not read LOAD alignment for ${LIB#"$WORK"/}"
    FAILURES=$((FAILURES + 1))
  elif [[ "$ALIGNMENTS" == *"2**12"* ]]; then
    echo "::error title=16 KB ELF::${LIB#"$WORK"/} has a LOAD segment aligned to 4 KB ($ALIGNMENTS)"
    FAILURES=$((FAILURES + 1))
  else
    echo "::notice title=16 KB ELF::${LIB#"$WORK"/} LOAD alignment: $ALIGNMENTS"
  fi
done

echo "Inspected ${#LIBS[@]} native shared libraries across packaged ABIs; failures: $FAILURES"
if [[ "$FAILURES" -gt 0 ]]; then
  echo "::error::Native ELF alignment check failed"
  exit 1
fi
