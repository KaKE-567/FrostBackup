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

# Patch package.xml safely with Python
python3 - << 'PYEOF'
import os
import re
import xml.etree.ElementTree as ET

package_xml = os.path.expandvars('$ANDROID_HOME/platforms/android-37/package.xml')
if os.path.isfile(package_xml):
    with open(package_xml, 'r', encoding='utf-8') as f:
        content = f.read()

    content = re.sub(r'path="platforms;[^"]*"', 'path="platforms;android-37"', content)
    content = re.sub(r'<codename>[^<]*</codename>', '', content)
    content = re.sub(r'<api-level>[^<]*</api-level>', '<api-level>37</api-level>', content)
    content = re.sub(r'<display-name>[^<]*</display-name>', '<display-name>Android SDK Platform 37</display-name>', content)

    with open(package_xml, 'w', encoding='utf-8') as f:
        f.write(content)

    ET.fromstring(content)
    print("package.xml successfully patched and validated!")
PYEOF

# Patch build.prop to reflect finalized SDK 37 (REL)
if [ -f "$ANDROID_HOME/platforms/android-37/build.prop" ]; then
  sed -i 's/ro.build.version.sdk=.*/ro.build.version.sdk=37/' "$ANDROID_HOME/platforms/android-37/build.prop"
  sed -i 's/ro.build.version.codename=.*/ro.build.version.codename=REL/' "$ANDROID_HOME/platforms/android-37/build.prop"
fi

echo "Verified platforms/android-37 directory:"
ls -la "$ANDROID_HOME/platforms/android-37/"
