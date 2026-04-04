#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# FocusFlow — Android native module installer
#
# Run this AFTER: npx expo prebuild --platform android
#
# Usage:
#   chmod +x android-native/install.sh
#   ./android-native/install.sh
# ─────────────────────────────────────────────────────────────────────────────

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
PKG_DIR="$ANDROID_DIR/app/src/main/java/com/tbtechs/focusflow"
RES_DIR="$ANDROID_DIR/app/src/main/res"
MANIFEST="$ANDROID_DIR/app/src/main/AndroidManifest.xml"

if [ ! -d "$ANDROID_DIR" ]; then
  echo "❌  android/ directory not found."
  echo "    Run 'npx expo prebuild --platform android' first."
  exit 1
fi

echo "📦  Copying Kotlin source files..."

# Modules
mkdir -p "$PKG_DIR/modules"
cp "$SCRIPT_DIR/app/src/main/java/com/tbtechs/focusflow/modules/"*.kt "$PKG_DIR/modules/"

# Services
mkdir -p "$PKG_DIR/services"
cp "$SCRIPT_DIR/app/src/main/java/com/tbtechs/focusflow/services/"*.kt "$PKG_DIR/services/"

# Widget
mkdir -p "$PKG_DIR/widget"
cp "$SCRIPT_DIR/app/src/main/java/com/tbtechs/focusflow/widget/"*.kt "$PKG_DIR/widget/"
echo "   ✓ widget/ Kotlin files copied"

echo "🖼   Copying resource files..."

# XML config files
mkdir -p "$RES_DIR/xml"
cp "$SCRIPT_DIR/app/src/main/res/xml/accessibility_service_config.xml" "$RES_DIR/xml/"
cp "$SCRIPT_DIR/app/src/main/res/xml/device_admin.xml" "$RES_DIR/xml/"
cp "$SCRIPT_DIR/app/src/main/res/xml/widget_info.xml" "$RES_DIR/xml/"
echo "   ✓ xml/ resources copied"

# Widget layout
mkdir -p "$RES_DIR/layout"
cp "$SCRIPT_DIR/app/src/main/res/layout/widget_focusflow.xml" "$RES_DIR/layout/"
echo "   ✓ layout/ resources copied"

# Drawable (widget background)
mkdir -p "$RES_DIR/drawable"
cp "$SCRIPT_DIR/app/src/main/res/drawable/widget_background.xml" "$RES_DIR/drawable/"
echo "   ✓ drawable/ resources copied"

echo "📝  Patching strings.xml..."

STRINGS_FILE="$RES_DIR/values/strings.xml"

if ! grep -q "accessibility_service_description" "$STRINGS_FILE" 2>/dev/null; then
  sed -i 's|</resources>|    <string name="accessibility_service_description">FocusFlow uses Accessibility to detect and block distracting apps during your focus sessions. No personal data or messages are read.</string>\n</resources>|' "$STRINGS_FILE"
  echo "   ✓ accessibility_service_description added"
else
  echo "   ✓ accessibility_service_description already present"
fi

if ! grep -q "widget_description" "$STRINGS_FILE" 2>/dev/null; then
  sed -i 's|</resources>|    <string name="widget_description">Shows your active focus session and time remaining on the home screen.</string>\n</resources>|' "$STRINGS_FILE"
  echo "   ✓ widget_description added"
else
  echo "   ✓ widget_description already present"
fi

echo "📋  Patching AndroidManifest.xml..."

# ── Ensure xmlns:tools namespace is declared in <manifest> ───────────────────
# Required before any tools:ignore attributes are inserted by this script.
if ! grep -q 'xmlns:tools' "$MANIFEST"; then
  sed -i 's|<manifest |<manifest xmlns:tools="http://schemas.android.com/tools" |' "$MANIFEST"
  echo "   ✓ xmlns:tools namespace added to manifest root"
else
  echo "   ✓ xmlns:tools already present"
fi

if [ ! -f "$MANIFEST" ]; then
  echo "❌  AndroidManifest.xml not found at $MANIFEST"
  exit 1
fi

# ── Permissions ──────────────────────────────────────────────────────────────

patch_permission() {
  local PERM="$1"
  local EXTRA="${2:-}"
  if ! grep -q "$PERM" "$MANIFEST"; then
    sed -i "s|</manifest>|    <uses-permission android:name=\"$PERM\"$EXTRA />\n</manifest>|" "$MANIFEST"
    echo "   ✓ added $PERM"
  else
    echo "   ✓ $PERM already present"
  fi
}

patch_permission "android.permission.PACKAGE_USAGE_STATS"        ' tools:ignore="ProtectedPermissions"'
patch_permission "android.permission.SYSTEM_ALERT_WINDOW"
patch_permission "android.permission.FOREGROUND_SERVICE"
patch_permission "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
patch_permission "android.permission.RECEIVE_BOOT_COMPLETED"
patch_permission "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
patch_permission "android.permission.BIND_ACCESSIBILITY_SERVICE"  ' tools:ignore="ProtectedPermissions"'

# ── ForegroundTaskService ─────────────────────────────────────────────────────

if ! grep -q "ForegroundTaskService" "$MANIFEST"; then
  sed -i 's|</application>|        <service\n            android:name="com.tbtechs.focusflow.services.ForegroundTaskService"\n            android:enabled="true"\n            android:exported="false"\n            android:foregroundServiceType="specialUse">\n            <property\n                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"\n                android:value="productivity" />\n        </service>\n    </application>|' "$MANIFEST"
  echo "   ✓ ForegroundTaskService registered"
else
  echo "   ✓ ForegroundTaskService already registered"
fi

# ── AppBlockerAccessibilityService ───────────────────────────────────────────

if ! grep -q "AppBlockerAccessibilityService" "$MANIFEST"; then
  sed -i 's|</application>|        <service\n            android:name="com.tbtechs.focusflow.services.AppBlockerAccessibilityService"\n            android:enabled="true"\n            android:exported="true"\n            android:label="FocusFlow Focus Mode"\n            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">\n            <intent-filter>\n                <action android:name="android.accessibilityservice.AccessibilityService" />\n            </intent-filter>\n            <meta-data\n                android:name="android.accessibilityservice"\n                android:resource="@xml/accessibility_service_config" />\n        </service>\n    </application>|' "$MANIFEST"
  echo "   ✓ AppBlockerAccessibilityService registered"
else
  echo "   ✓ AppBlockerAccessibilityService already registered"
fi

# ── BootReceiver ─────────────────────────────────────────────────────────────

if ! grep -q "BootReceiver" "$MANIFEST"; then
  sed -i 's|</application>|        <receiver\n            android:name="com.tbtechs.focusflow.services.BootReceiver"\n            android:enabled="true"\n            android:exported="true">\n            <intent-filter>\n                <action android:name="android.intent.action.BOOT_COMPLETED" />\n                <action android:name="android.intent.action.QUICKBOOT_POWERON" />\n            </intent-filter>\n        </receiver>\n    </application>|' "$MANIFEST"
  echo "   ✓ BootReceiver registered"
else
  echo "   ✓ BootReceiver already registered"
fi

# ── FocusFlowWidget ──────────────────────────────────────────────────────────

if ! grep -q "FocusFlowWidget" "$MANIFEST"; then
  sed -i 's|</application>|        <receiver\n            android:name="com.tbtechs.focusflow.widget.FocusFlowWidget"\n            android:exported="true">\n            <intent-filter>\n                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />\n            </intent-filter>\n            <meta-data\n                android:name="android.appwidget.provider"\n                android:resource="@xml/widget_info" />\n        </receiver>\n    </application>|' "$MANIFEST"
  echo "   ✓ FocusFlowWidget registered"
else
  echo "   ✓ FocusFlowWidget already registered"
fi

# ── FocusDayDeviceAdminReceiver ───────────────────────────────────────────────

if ! grep -q "FocusDayDeviceAdminReceiver" "$MANIFEST"; then
  sed -i 's|</application>|        <receiver\n            android:name="com.tbtechs.focusflow.services.FocusDayDeviceAdminReceiver"\n            android:description="@string/accessibility_service_description"\n            android:exported="true"\n            android:label="FocusFlow"\n            android:permission="android.permission.BIND_DEVICE_ADMIN">\n            <intent-filter>\n                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />\n            </intent-filter>\n            <meta-data\n                android:name="android.app.device_admin"\n                android:resource="@xml/device_admin" />\n        </receiver>\n    </application>|' "$MANIFEST"
  echo "   ✓ FocusDayDeviceAdminReceiver registered"
else
  echo "   ✓ FocusDayDeviceAdminReceiver already registered"
fi

# ── NotificationActionReceiver ────────────────────────────────────────────────
# Handles taps on ✓ Done / +15m / +30m / Skip action buttons in the foreground
# notification. All PendingIntents are package-restricted (setPackage = ours),
# so exported=false is correct and prevents external apps from triggering actions.

if ! grep -q "NotificationActionReceiver" "$MANIFEST"; then
  sed -i 's|</application>|        <receiver\n            android:name="com.tbtechs.focusflow.services.NotificationActionReceiver"\n            android:exported="false">\n            <intent-filter>\n                <action android:name="com.tbtechs.focusflow.notif.COMPLETE" />\n                <action android:name="com.tbtechs.focusflow.notif.EXTEND" />\n                <action android:name="com.tbtechs.focusflow.notif.SKIP" />\n            </intent-filter>\n        </receiver>\n    </application>|' "$MANIFEST"
  echo "   ✓ NotificationActionReceiver registered"
else
  echo "   ✓ NotificationActionReceiver already registered"
fi

echo ""
echo "✅  All native files installed and manifest patched."
echo ""
echo "────────────────────────────────────────────────────────────"
echo "  ONE MANUAL STEP STILL REQUIRED:"
echo "────────────────────────────────────────────────────────────"
echo ""
echo "  Register FocusDayPackage in:"
echo "    android/app/src/main/java/com/tbtechs/focusflow/MainApplication.kt"
echo ""
echo "  Add import:"
echo "    import com.tbtechs.focusflow.modules.FocusDayPackage"
echo ""
echo "  In getPackages():"
echo "    packages.add(FocusDayPackage())"
echo ""
echo "  Then build:"
echo "    eas build --platform android --profile preview"
echo ""
