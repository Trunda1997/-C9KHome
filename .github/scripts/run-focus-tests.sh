#!/usr/bin/env bash
set -euo pipefail

# The emulator action executes each script line in a separate shell. Keep the
# test exit code and artifact collection together in this single script.
test_exit_code=0
gradle :app:connectedDebugAndroidTest --stacktrace || test_exit_code=$?
mkdir -p app/build/reports
adb pull /sdcard/Pictures/C9KHome-focus app/build/reports/focus-screenshots || true
exit "$test_exit_code"
