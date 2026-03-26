#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

BUILD_DIR="build"
CLASSES_DIR="$BUILD_DIR/classes"
JAR_PATH="$BUILD_DIR/smssync-server.jar"

rm -rf "$BUILD_DIR"
mkdir -p "$CLASSES_DIR"

echo "Compiling server..."
javac -d "$CLASSES_DIR" ClipboardServer.java

echo "Packaging jar..."
jar cfe "$JAR_PATH" ClipboardServer -C "$CLASSES_DIR" .

echo "Built $JAR_PATH"
