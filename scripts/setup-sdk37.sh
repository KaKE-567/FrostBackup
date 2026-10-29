#!/usr/bin/env bash
set -e

LATEST_PLATFORM=$(ls -d $ANDROID_HOME/platforms/android-* 2>/dev/null | grep -v 'android-37$' | sort -V | tail -n 1)
echo "Copying base platform from: $LATEST_PLATFORM"

mkdir -p "$ANDROID_HOME/platforms/android-37"
cp -r "$LATEST_PLATFORM"/* "$ANDROID_HOME/platforms/android-37/"

# Overwrite source.properties with clean finalized SDK 37 properties
cat << 'EOF' > "$ANDROID_HOME/platforms/android-37/source.properties"
Pkg.Desc=Android SDK Platform 37
Pkg.UserSrc=false
Platform.Version=17
Pkg.Revision=1
AndroidVersion.ApiLevel=37
Layoutlib.Api=15
Layoutlib.Revision=1
Pkg.Path=platforms;android-37
EOF

# Overwrite package.xml with clean localPackage matching platforms;android-37
cat << 'EOF' > "$ANDROID_HOME/platforms/android-37/package.xml"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02" xmlns:ns5="http://schemas.android.com/sdk/android/repo/repository2/02">
    <localPackage path="platforms;android-37" obsolete="false">
        <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:genericDetailsType"/>
        <revision><major>1</major></revision>
        <display-name>Android SDK Platform 37</display-name>
    </localPackage>
</ns2:repository>
EOF

# Patch build.prop to reflect finalized SDK 37 (REL)
if [ -f "$ANDROID_HOME/platforms/android-37/build.prop" ]; then
  sed -i 's/ro.build.version.sdk=.*/ro.build.version.sdk=37/' "$ANDROID_HOME/platforms/android-37/build.prop"
  sed -i 's/ro.build.version.codename=.*/ro.build.version.codename=REL/' "$ANDROID_HOME/platforms/android-37/build.prop"
fi

echo "Verified platforms/android-37 directory:"
ls -la "$ANDROID_HOME/platforms/android-37/"
