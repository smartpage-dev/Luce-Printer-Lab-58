#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p .test-build/classes .test-build/packets
javac --release 17 -d .test-build/classes app/src/main/java/hk/luce/printerlab/Protocol.java app/src/main/java/hk/luce/printerlab/AttemptPolicy.java app/src/main/java/hk/luce/printerlab/GuidePolicy.java tests/ProtocolTest.java tests/GuidePolicyTest.java tests/ParseSources.java
java -cp .test-build/classes hk.luce.printerlab.ProtocolTest .test-build/packets
java -cp .test-build/classes hk.luce.printerlab.GuidePolicyTest
java -cp .test-build/classes ParseSources app/src

# Compile the actual UI callback contract, not just a syntax parse.
python3 tools/test_ui_callbacks.py
