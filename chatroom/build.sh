#!/usr/bin/env bash
# =============================================================
# build.sh  –  Compile and package the Chat Room application
# =============================================================
set -e

SRC_ROOT="src/main/java"
OUT_DIR="out"
JAR="chatroom.jar"

echo "=== Cleaning previous build ==="
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

echo "=== Compiling ==="
find "$SRC_ROOT" -name "*.java" | xargs javac -d "$OUT_DIR"

echo "=== Packaging JAR ==="
jar cfe "$JAR" chatroom.server.FileServer -C "$OUT_DIR" .

echo ""
echo "Build successful! JAR: $JAR"
echo ""
echo "-------------------------------------------------------"
echo "HOW TO RUN"
echo "-------------------------------------------------------"
echo ""
echo "STEP 1 – On the SERVER node (Machine 0):"
echo "  java -cp chatroom.jar chatroom.server.FileServer"
echo ""
echo "STEP 2 – On USER NODE 1 (Machine 1):"
echo "  java -cp chatroom.jar chatroom.client.UserNode 1 Lucy"
echo ""
echo "STEP 3 – On USER NODE 2 (Machine 2):"
echo "  java -cp chatroom.jar chatroom.client.UserNode 2 Joel"
echo ""
echo "Then type 'view' or 'post Hello World' at the prompt."
echo "-------------------------------------------------------"
