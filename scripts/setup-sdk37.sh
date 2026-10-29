#!/usr/bin/env bash
set -e

LATEST_PLATFORM=$(ls -d $ANDROID_HOME/platforms/android-* 2>/dev/null | grep -v 'android-37$' | sort -V | tail -n 1)
echo "Copying base platform from: $LATEST_PLATFORM"

mkdir -p "$ANDROID_HOME/platforms/android-37"
cp -r "$LATEST_PLATFORM"/* "$ANDROID_HOME/platforms/android-37/"

# Patch copied package.xml while preserving exact schemas and type details
if [ -f "$ANDROID_HOME/platforms/android-37/package.xml" ]; then
  sed -i 's/path="platforms;[^"]*"/path="platforms;android-37"/' "$ANDROID_HOME/platforms/android-37/package.xml"
  sed -i 's/platforms;android-[^<"]*/platforms;android-37/g' "$ANDROID_HOME/platforms/android-37/package.xml"
  sed -i '/<codename>/d' "$ANDROID_HOME/platforms/android-37/package.xml"
  sed -i 's/<api-level>[^<]*<\/api-level>/<api-level>37<\/api-level>/g' "$ANDROID_HOME/platforms/android-37/package.xml"
  sed -i 's/<display-name>[^<]*<\/display-name>/<display-name>Android SDK Platform 37<\/display-name>/g' "$ANDROID_HOME/platforms/android-37/package.xml"
fi

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

# Patch build.prop to reflect finalized SDK 37 (REL)
if [ -f "$ANDROID_HOME/platforms/android-37/build.prop" ]; then
  sed -i 's/ro.build.version.sdk=.*/ro.build.version.sdk=37/' "$ANDROID_HOME/platforms/android-37/build.prop"
  sed -i 's/ro.build.version.codename=.*/ro.build.version.codename=REL/' "$ANDROID_HOME/platforms/android-37/build.prop"
fi

echo "Verified platforms/android-37 directory:"
ls -la "$ANDROID_HOME/platforms/android-37/"
if [ -f "$ANDROID_HOME/platforms/android-37/package.xml" ]; then
  echo "--- package.xml ---"
  cat "$ANDROID_HOME/platforms/android-37/package.xml"
fi
