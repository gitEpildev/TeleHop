#!/usr/bin/env bash
set -euo pipefail
version=$1
python3 .github/release/prepare.py reactor "$version"
python3 .github/release/prepare.py verify "$version"
mkdir -p release-bundle
cp "paper/target/telehop-paper-$version.jar" "velocity/target/telehop-velocity-$version.jar" release-bundle/
jar cf release-bundle/command-probe.jar -C .github/tests plugin.yml \
  -C paper/target/test-classes com/telehop/paper/smoke/CommandProbe.class
