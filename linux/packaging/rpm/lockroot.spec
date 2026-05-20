Name: lockroot
Version: %{lockroot_version}
Release: 1%{?dist}
Summary: Local encrypted password manager
License: AGPL-3.0-or-later
URL: https://lockroot.rothackers.com
Source0: %{name}-%{version}.tar.gz
BuildArch: x86_64
AutoReqProv: no

%description
Lockroot stores password entries inside a local encrypted vault protected by a master password.

%prep
%setup -q

%build

%install
mkdir -p %{buildroot}/opt/lockroot
mkdir -p %{buildroot}/usr/bin
mkdir -p %{buildroot}/usr/share/applications
mkdir -p %{buildroot}/usr/share/metainfo
mkdir -p %{buildroot}/usr/share/icons/hicolor/256x256/apps

cp -a publish/. %{buildroot}/opt/lockroot/
chmod +x %{buildroot}/opt/lockroot/Lockroot

cat > %{buildroot}/usr/bin/lockroot <<'EOF'
#!/usr/bin/env sh
exec /opt/lockroot/Lockroot "$@"
EOF
chmod +x %{buildroot}/usr/bin/lockroot

cp lockroot.desktop %{buildroot}/usr/share/applications/lockroot.desktop
cp io.github.regaan.Lockroot.metainfo.xml %{buildroot}/usr/share/metainfo/io.github.regaan.Lockroot.metainfo.xml
cp lockroot-icon-256.png %{buildroot}/usr/share/icons/hicolor/256x256/apps/lockroot.png

%files
/opt/lockroot
/usr/bin/lockroot
/usr/share/applications/lockroot.desktop
/usr/share/metainfo/io.github.regaan.Lockroot.metainfo.xml
/usr/share/icons/hicolor/256x256/apps/lockroot.png

%changelog
* Wed May 20 2026 REGAAN <regaan48@gmail.com> - 1.1.1-1
- Initial Linux desktop release packaging.
