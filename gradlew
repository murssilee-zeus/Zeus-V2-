#!/bin/sh
# Zeus V2 Gradle bootstrap wrapper.
# Uses Gradle 8.7 (compatible with Android Gradle Plugin 8.5.2).
set -e

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION="8.7"
DIST_NAME="gradle-${GRADLE_VERSION}-bin.zip"
DIST_URL="https://services.gradle.org/distributions/${DIST_NAME}"
CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/zeus-v2/${GRADLE_VERSION}"
GRADLE_DIR="${CACHE_DIR}/gradle-${GRADLE_VERSION}"

if [ ! -x "${GRADLE_DIR}/bin/gradle" ]; then
  mkdir -p "$CACHE_DIR"
  ZIP="${CACHE_DIR}/${DIST_NAME}"
  if [ ! -f "$ZIP" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 -o "$ZIP" "$DIST_URL"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP" "$DIST_URL"
    else
      echo "ERROR: curl or wget is required to bootstrap Gradle." >&2
      exit 1
    fi
  fi
  rm -rf "$GRADLE_DIR.tmp"
  mkdir -p "$GRADLE_DIR.tmp"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$ZIP" -d "$GRADLE_DIR.tmp"
  else
    echo "ERROR: unzip is required to bootstrap Gradle." >&2
    exit 1
  fi
  mv "$GRADLE_DIR.tmp/gradle-${GRADLE_VERSION}" "$GRADLE_DIR"
  rmdir "$GRADLE_DIR.tmp" 2>/dev/null || true
fi

exec "$GRADLE_DIR/bin/gradle" --no-daemon "$@"
