#!/bin/bash

PKG="com.example.nextkv"
TEST_CLASS="com.example.nextkv.ExampleInstrumentedTest"
TEST_RUNNER="$PKG.test/androidx.test.runner.AndroidJUnitRunner"
PERF_DATA="/data/data/$PKG/perf.data"
DURATION=10

EVENTS="task-clock,cpu-cycles,instructions,cache-misses,page-faults,branch-misses,stalled-cycles-frontend,stalled-cycles-backend,L1-dcache-load-misses,L1-icache-load-misses"

run_profile() {
    TEST_METHOD=$1
    echo "====================================="
    echo "Profiling $TEST_METHOD"
    echo "====================================="
    
    # Start the specific test
    adb shell am instrument -e class ${TEST_CLASS}#${TEST_METHOD} -w $TEST_RUNNER > /dev/null 2>&1 &
    
    # Wait for the process to appear
    sleep 3
    PID=$(adb shell pidof $PKG | tr -d '\r')
    
    if [ -z "$PID" ]; then
        echo "Failed to find PID for $PKG"
        return
    fi
    
    echo "Process $PKG running at PID $PID. Starting simpleperf stat..."
    # Run simpleperf stat
    adb shell run-as $PKG simpleperf stat -p $PID -e $EVENTS --duration $DURATION
    
    # Wait a bit for the test to finish gracefully (test runs for 15s, we profile for 10s)
    sleep 5
    adb shell am force-stop $PKG
    echo "Done profiling $TEST_METHOD"
    echo ""
}

# Compile and install test APKs first
echo "Compiling and installing test APKs..."
./gradlew installDebug installDebugAndroidTest > /dev/null 2>&1

adb shell pm clear $PKG

run_profile "profileMmkvSpMixed"
run_profile "profileNextkvSpMixed"
run_profile "profileMmkvMpMixed"
run_profile "profileNextkvMpMixed"

echo "All profiling complete!"
