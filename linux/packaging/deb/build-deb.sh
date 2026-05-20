#!/usr/bin/env bash
set -euo pipefail

PUBLISH_DIR="${1:?Usage: build-deb.sh <publish-dir> <output-dir> [version]}"
OUTPUT_DIR="${2:?Usage: build-deb.sh <publish-dir> <output-dir> [version]}"
VERSION="${3:-1.1.1}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PKG_DIR="$OUTPUT_DIR/deb-root"

rm -rf "$PKG_DIR"
mkdir -p "$PKG_DIR/DEBIAN" \
         "$PKG_DIR/opt/lockroot" \
         "$PKG_DIR/usr/bin" \
         "$PKG_DIR/usr/share/applications" \
         "$PKG_DIR/usr/share/metainfo" \
         "$PKG_DIR/usr/share/icons/hicolor/256x256/apps"

cp -a "$PUBLISH_DIR"/. "$PKG_DIR/opt/lockroot/"
chmod +x "$PKG_DIR/opt/lockroot/Lockroot"

cat > "$PKG_DIR/usr/bin/lockroot" <<'EOF'
#!/usr/bin/env sh
exec /opt/lockroot/Lockroot "$@"
EOF
chmod +x "$PKG_DIR/usr/bin/lockroot"

cp "$ROOT_DIR/linux/packaging/lockroot.desktop" "$PKG_DIR/usr/share/applications/lockroot.desktop"
cp "$ROOT_DIR/linux/packaging/io.github.regaan.Lockroot.metainfo.xml" "$PKG_DIR/usr/share/metainfo/io.github.regaan.Lockroot.metainfo.xml"
cp "$ROOT_DIR/linux/Lockroot.Linux/Assets/lockroot-icon-256.png" "$PKG_DIR/usr/share/icons/hicolor/256x256/apps/lockroot.png"

cat > "$PKG_DIR/DEBIAN/control" <<EOF
Package: lockroot
Version: $VERSION
Section: utils
Priority: optional
Architecture: amd64
Maintainer: REGAAN <regaan48@gmail.com>
Homepage: https://lockroot.rothackers.com
Depends: libc6, libx11-6, libfontconfig1, libfreetype6
Description: Local encrypted password manager
 Lockroot stores password entries inside a local encrypted vault protected
 by a master password.
EOF

dpkg-deb --build "$PKG_DIR" "$OUTPUT_DIR/Lockroot-linux-x64-$VERSION.deb"

# Clean up temporary build artifacts
rm -rf "$PKG_DIR"

