#!/usr/bin/env bash
set -euo pipefail

PUBLISH_DIR="${1:?Usage: build-rpm.sh <publish-dir> <output-dir> [version]}"
OUTPUT_DIR="${2:?Usage: build-rpm.sh <publish-dir> <output-dir> [version]}"
VERSION="${3:-1.1.1}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

# rpmbuild is sensitive to relative paths in %{_topdir}. Use absolute paths
# to avoid it attempting to cd into "/$OUTPUT_DIR/..." in some environments.
PUBLISH_DIR="$(cd "$PUBLISH_DIR" && pwd)"
OUTPUT_DIR="$(mkdir -p "$OUTPUT_DIR" && cd "$OUTPUT_DIR" && pwd)"
TOPDIR="$OUTPUT_DIR/rpmbuild"
SOURCE_ROOT="$OUTPUT_DIR/lockroot-$VERSION"

rm -rf "$TOPDIR" "$SOURCE_ROOT"
mkdir -p "$TOPDIR/BUILD" "$TOPDIR/RPMS" "$TOPDIR/SOURCES" "$TOPDIR/SPECS" "$TOPDIR/SRPMS"
mkdir -p "$SOURCE_ROOT/publish"

cp -a "$PUBLISH_DIR"/. "$SOURCE_ROOT/publish/"
cp "$ROOT_DIR/linux/packaging/lockroot.desktop" "$SOURCE_ROOT/lockroot.desktop"
cp "$ROOT_DIR/linux/packaging/io.github.regaan.Lockroot.metainfo.xml" "$SOURCE_ROOT/io.github.regaan.Lockroot.metainfo.xml"
cp "$ROOT_DIR/linux/Lockroot.Linux/Assets/lockroot-icon-256.png" "$SOURCE_ROOT/lockroot-icon-256.png"

tar -C "$OUTPUT_DIR" -czf "$TOPDIR/SOURCES/lockroot-$VERSION.tar.gz" "lockroot-$VERSION"
cp "$ROOT_DIR/linux/packaging/rpm/lockroot.spec" "$TOPDIR/SPECS/lockroot.spec"

rpmbuild -bb "$TOPDIR/SPECS/lockroot.spec" \
  --define "_topdir $TOPDIR" \
  --define "lockroot_version $VERSION"

find "$TOPDIR/RPMS" -type f -name '*.rpm' -exec cp {} "$OUTPUT_DIR/Lockroot-linux-x64-$VERSION.rpm" \;
