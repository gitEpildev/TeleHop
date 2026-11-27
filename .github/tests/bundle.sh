#!/usr/bin/env bash
set -euo pipefail
version=$1
python3 .github/release/prepare.py reactor "$version"
python3 .github/release/prepare.py verify "$version"
python3 .github/release/package_jars.py "$version" release-bundle
jar cf release-bundle/command-probe.jar -C .github/tests plugin.yml \
  -C paper/target/test-classes com/telehop/paper/smoke/CommandProbe.class
