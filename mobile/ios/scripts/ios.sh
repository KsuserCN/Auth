#!/usr/bin/env bash
set -euo pipefail
IOS_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IOS_ACTION="${1:-debug}"
IOS_DERIVED_DATA="${KSUSER_IOS_DERIVED_DATA:-$IOS_ROOT/DerivedData}"
IOS_ARGS=(-project "$IOS_ROOT/KsuserAuth.xcodeproj" -scheme KsuserAuth -derivedDataPath "$IOS_DERIVED_DATA")
case "$IOS_ACTION" in
  debug|release)
    IOS_CONFIGURATION=Debug
    if [[ "$IOS_ACTION" == release ]]; then IOS_CONFIGURATION=Release; fi
    xcodebuild "${IOS_ARGS[@]}" -configuration "$IOS_CONFIGURATION" -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
    ;;
  test)
    IOS_DESTINATION="${KSUSER_IOS_TEST_DESTINATION:-}"
    if [[ -z "$IOS_DESTINATION" ]]; then
      IOS_DEVICE_ID="$(xcrun simctl list devices available -j | /usr/bin/python3 -c 'import json,sys; devices=json.load(sys.stdin)["devices"]; print(next(d["udid"] for group in devices.values() for d in group if d.get("isAvailable") and "iPhone" in d["name"]))')"
      IOS_DESTINATION="platform=iOS Simulator,id=$IOS_DEVICE_ID"
    fi
    xcodebuild "${IOS_ARGS[@]}" -configuration Debug -destination "$IOS_DESTINATION" -parallel-testing-enabled NO -test-timeouts-enabled YES -default-test-execution-time-allowance 120 -maximum-test-execution-time-allowance 180 -collect-test-diagnostics "${KSUSER_IOS_TEST_DIAGNOSTICS:-never}" CODE_SIGNING_ALLOWED=NO test
    ;;
  archive)
    IOS_ARCHIVE="${KSUSER_IOS_ARCHIVE_PATH:-$IOS_ROOT/DerivedData/KsuserAuth.xcarchive}"
    xcodebuild "${IOS_ARGS[@]}" -configuration Release -destination 'generic/platform=iOS' -archivePath "$IOS_ARCHIVE" archive
    ;;
  *) echo "Usage: $0 debug|release|test|archive" >&2; exit 2 ;;
esac
