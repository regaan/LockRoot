#!/usr/bin/env bash
set -euo pipefail

PUBLISH_DIR="${1:?Usage: build-appimage.sh <publish-dir> <output-dir> [version]}"
OUTPUT_DIR="${2:?Usage: build-appimage.sh <publish-dir> <output-dir> [version]}"
VERSION="${3:-1.1.1}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
APPDIR="$OUTPUT_DIR/Lockroot.AppDir"

rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr/bin" \
         "$APPDIR/usr/share/applications" \
         "$APPDIR/usr/share/metainfo" \
         "$APPDIR/usr/share/icons/hicolor/256x256/apps"

cp -a "$PUBLISH_DIR"/. "$APPDIR/usr/bin/"
chmod +x "$APPDIR/usr/bin/Lockroot"

cp "$ROOT_DIR/linux/packaging/lockroot.desktop" "$APPDIR/usr/share/applications/lockroot.desktop"
cp "$ROOT_DIR/linux/packaging/io.github.regaan.Lockroot.metainfo.xml" "$APPDIR/usr/share/metainfo/io.github.regaan.Lockroot.metainfo.xml"
cp "$ROOT_DIR/linux/Lockroot.Linux/Assets/lockroot-icon-256.png" "$APPDIR/usr/share/icons/hicolor/256x256/apps/lockroot.png"
cp "$ROOT_DIR/linux/packaging/appimage/AppRun" "$APPDIR/AppRun"
chmod +x "$APPDIR/AppRun"

cp "$ROOT_DIR/linux/packaging/lockroot.desktop" "$APPDIR/lockroot.desktop"
cp "$ROOT_DIR/linux/Lockroot.Linux/Assets/lockroot-icon-256.png" "$APPDIR/lockroot.png"

APPIMAGETOOL="$OUTPUT_DIR/appimagetool-x86_64.AppImage"
if [ ! -x "$APPIMAGETOOL" ]; then
  curl -L "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage" -o "$APPIMAGETOOL"
  chmod +x "$APPIMAGETOOL"
fi

ARCH=x86_64 "$APPIMAGETOOL" "$APPDIR" "$OUTPUT_DIR/Lockroot-linux-x64-$VERSION.AppImage"

# Clean up temporary build artifacts
rm -rf "$APPDIR" "$APPIMAGETOOL"

