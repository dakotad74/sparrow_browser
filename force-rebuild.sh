#!/bin/bash
#
# Force complete rebuild - Nuclear option to bypass all Gradle caching
#

echo "=== FORCE REBUILD - Nuclear Cache Bypass ==="
echo ""

# Kill all Sparrow instances
echo "1. Killing Sparrow instances..."
pkill -f Sparrow
sleep 2

# Delete ALL build artifacts
echo "2. Deleting ALL build artifacts..."
rm -rf build/
rm -rf .gradle/
rm -rf ~/.gradle/caches/modules-2/files-2.1/com.sparrowwallet
rm -rf ~/.gradle/caches/*/sparrow*

# Modify source file to change hash
echo "3. Forcing source file modification..."
touch src/main/java/com/sparrowwallet/sparrow/coordination/CoordinationSessionManager.java

# Build with ALL cache options disabled
echo "4. Building with --rerun-tasks --no-build-cache --refresh-dependencies..."
./gradlew --rerun-tasks --no-build-cache --refresh-dependencies clean compileJava jar jpackage -x test

echo ""
echo "=== Build complete ==="
ls -lh build/libs/sparrow-*.jar build/jlinkbase/jlinkjars/sparrow-*.jar 2>/dev/null | awk '{print $6, $7, $8, $9}'
