# Publishing TeleHop

## Connect Modrinth

1. Sign in to the Modrinth account which can upload versions to the TeleHop project. Create a personal access token with **Read user data** (`USER_READ`) and **Create versions** (`VERSION_CREATE`). Use an expiry date you can track.
2. In GitHub, open **Settings → Secrets and variables → Actions**. Under **Secrets**, add `MODRINTH_TOKEN` with the token value.
3. Under **Variables**, add `MODRINTH_PROJECT_ID`. TeleHop's project ID is `cjTmNjaL`. You can confirm it with **Copy project ID** on the project page.
4. Enable GitHub Actions for the repository. The build uses read access. The publishing job requests `contents: write` to create the tag, draft and release assets. Tag rules must allow the workflow to create the requested tag. GitHub supplies its token automatically.

[Modrinth token settings](https://modrinth.com/settings/pats) · [TeleHop on Modrinth](https://modrinth.com/plugin/telehop) · [GitHub Actions settings](https://github.com/gitEpildev/TeleHop/settings/secrets/actions)

## Run a release

Open the Release workflow, select **Run workflow** and choose the source branch. Leave **Version** empty and **Release type** set to **auto**. The workflow reads the newest dated changelog entry and treats a plain version such as `4.0.0` as a full release. An explicitly entered version or channel must agree with the entry. Optional Markdown replaces the generated release body and passes the same spelling, punctuation and content checks. [Open the Release workflow](https://github.com/gitEpildev/TeleHop/actions/workflows/release.yml)

You can also push a stable tag such as `v4.0.0` at the intended commit. Choose one trigger per release. Alpha and beta channels are available for explicitly requested prereleases.

Before committing, add the newest dated release heading and its user-facing notes to `CHANGELOG.md`. Install the checker with `npm ci --prefix .github/release --ignore-scripts`, then run `python3 .github/release/prepare.py sync-version` once. It updates the TeleHop Maven coordinates and both plugin descriptors. Review and commit those changes together. The release checks require every version to agree. A tag must identify that same version. Pushing a branch commit runs CI for the exact SHA the release will use.

GitHub receives the Bukkit backend and Velocity proxy JARs and their SHA-256 manifest. CI and release runs download the built bundle again and check its exact files, versions, included adapters, templates and checksums before starting the runtime suite. Modrinth receives one entry per JAR using the verified GitHub assets and release notes. One Modrinth entry applies each loader tag to every version listed on it. The backend entry includes an additional loader only when the corresponding runtime checks cover the entire advertised range. The Bukkit backend entry uses the intersection verified for its published loader tags. The Velocity entry covers 17 Minecraft releases. Its two additional client versions share a protocol with a published Paper server. The live login and placement tests pair client 1.21.2 with Paper 1.21.3 and client 26.1 with Paper 26.1.2. [Platform table](../../README.md#installation)

The default public notes include the reviewed changelog entry and group commits since the nearest reachable previous version tag. Push and pull request CI checks the spelling and formatting of these generated notes before the release is started. For the first release, history starts after the standalone 2.2.0 reconciliation. Use conventional commit subjects such as `fix(paper): respect the self-coordinate permission`. A `!` or `BREAKING CHANGE:` footer places the change in the breaking changes section.

## Checks before publication

To reproduce the downloaded-bundle check locally, run `python3 .github/release/prepare.py verify-bundle DIRECTORY VERSION` with the directory containing the two distribution JARs, command probe and checksum file. The public download directory contains only the two production JARs and their checksums.

### Changelog requirements

- Put the newest release first, using a heading such as `## [4.0.0] - 2026-11-27`. Use unique versions, valid calendar dates and descending release order. An optional leading `[Unreleased]` section can hold work for later.
- Include at least one bullet describing the observable change. Replace placeholders such as `TBD` with the actual effect. Upgrade instructions may refer to older versions.
- Changes to shipped source, resources, Maven POMs or SQL must update the visible wording of the newest release entry. CI compares a PR with its base and a branch push with its previous commit. On a new branch it uses the common ancestor with the default branch. A release also checks changes since the preceding release.
- Use UK English in headings, paragraphs, bullets and link labels throughout the changelog and public notes. The pinned UK dictionary checks words such as *colour*, *behaviour* and *initialise*. Put exact API names and commands in backticks. Add genuine project terms to `.github/release/project-words.txt` for review. Code samples and URL destinations keep their spelling.
- Use ordinary punctuation. Long dashes, including HTML entities for them, fail throughout the Markdown. The run prints the source, line and reason. Human review covers accuracy, grammar and whether the change is clear to a server operator.

For example, a request for `3.0.0` with `2.2.0` as the newest dated entry stops before compilation. Correct the entry and descriptors together, push the change and release the new commit.

### Build requirements

Every branch push and PR runs the full Maven reactor, Checkstyle, repository validation, release regression tests, compatibility matrix and runtime tests. The **Release readiness** job requires every one of those jobs to succeed. The release workflow also performs a fresh clean build and runtime tests. Before publishing, it reads the latest push CI run on the exact commit and checks both its conclusion and **Release readiness**. A failed, cancelled, missing or skipped required check blocks publication. A pending run has up to 15 minutes to finish.

Packaging requires all six Maven modules, their JARs, passing Surefire reports from common, Paper and Velocity and matching versions in the Paper and Velocity distributions. Missing reports, zero tests in any of those modules, test failures, errors or skips stop the build. The backend compatibility check verifies that every plugin class uses Java 21-compatible bytecode, including the newer adapter. Maven compiles that adapter against the newer Paper API with JDK 25. This lets early Paper 1.21 releases remap the complete JAR on Java 21. The pinned 26.x servers run on Java 25.

Every Paper version in the platform table runs the server smoke test. Each boot checks command ownership with the default configuration, then restarts with renamed teleport commands. The shared-protocol login jobs use the client schema, backend and proxy from the compatibility inventory. PaperMC published only alpha-channel server builds for Minecraft 1.21.5, 1.21.9 and 26.1.1. The runtime matrix uses those upstream builds for these cases. [PaperMC download service](https://docs.papermc.io/misc/downloads-service/)

Velocity 3.3, 3.5 and 4.3.1 each run two proxy boots. Two Minecraft clients exercise configurable destinations, delegated staff actions and LuckPerms on Velocity 3.5 and 4.3.1 using two Paper backends and Multiverse. Players also select a saved home and a region and dimension through the inventory menus. A mixed-network check connects both proxy generations to the same backends, puts observers on the other proxy and repeats regional and named spawn travel in each direction. Separate checks use the published TeleHop 2.3.0 proxy and backend JARs to exercise rolling-upgrade behaviour. Three bridge instances exchange player updates and teleport packets across a real Redis restart. The Actions summary reports local Redis packet-forwarding latency. Live player transfer time includes additional client and server work.

The upgrade workflow boots the released 3.0.1 plugins, then checks the V4 migration and a second restart against MySQL 8.4 and MariaDB 11.4. The backend compatibility workflow builds Bukkit implementations, exercises the same backend JAR and runs a mixed server journey. It also checks the explicit Folia startup refusal. Build readiness and publication depend on both workflows.

The rollback test starts the published 2.3.0 Paper JAR with its own command file. It checks that TeleHop starts, that a named arrival reports the unavailable destination and that the player can return to the regional hub with `/spawn`.

On GitHub Actions, Maven also checks a Google-hosted mirror of Maven Central for released plugins and libraries. Checksums are required for downloads from the mirror. Snapshot dependencies still come from their configured repositories. [Maven Central mirror](https://storage-download.googleapis.com/maven-central/index.html)

The publishing job requires every runtime check to pass. Failed server jobs attach the available boot, proxy and client logs for diagnosis.

## Retry an interrupted release

Open the original run and select **Re-run failed jobs**. A retry checks the tag's commit and resumes any incomplete draft. The packager preserves shaded class and resource bytes and normalises ZIP ordering and timestamps. Rebuilding the same inputs produces the same release files. A resumed draft must contain the verified build bytes. The GitHub release body must match the validated notes on every attempt. After GitHub publication its existing JARs are used for Modrinth. Modrinth entries with a matching version, loader, changelog, compatibility and SHA-512 are skipped. A conflict stops the workflow and identifies the affected file or field. Fix a missing or expired credential in repository settings and retry. If the build artifact has expired, re-run all jobs on the original run.

## Reproduce validation locally

From the repository root, with Node.js 22.12 or later, Python 3.11 or later, Maven and the configured Java 21 and 25 toolchains, run the following commands. The prepare command writes a preview to `release-bundle/notes.md`. All commands below run locally.

```sh
npm ci --prefix .github/release --ignore-scripts --no-audit --no-fund
python3 .github/release/prepare.py check
REQUEST_CHANNEL=release python3 .github/release/prepare.py prepare
python3 -m unittest discover -v -s .github/release
mvn -B -ntp clean verify
bash .github/tests/bundle.sh
```


With Docker running, install the network test client and PyYAML into a local virtual environment. Supply the absolute paths to both Java executables and a new output directory. This starts temporary servers and databases on loopback, collects their logs and removes successful test worlds. To keep the worlds, add `--keep-worlds`.

```sh
npm ci --prefix .github/tests/network --ignore-scripts
python3 -m venv .venv
.venv/bin/pip install PyYAML==6.0.3
.venv/bin/python scripts/test-network.py matrix network-v3 network-v4 network-mixed client upgrade-mysql upgrade-mariadb \
  --java21 "$JAVA21_HOME/bin/java" --java25 "$JAVA25_HOME/bin/java" --output /tmp/telehop-v4-tests
```
