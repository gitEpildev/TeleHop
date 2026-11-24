# Publishing TeleHop

## Connect Modrinth

1. Sign in to the Modrinth account which can upload versions to the TeleHop project. Create a personal access token with **Read user data** (`USER_READ`) and **Create versions** (`VERSION_CREATE`). Use an expiry date you can track.
2. In GitHub, open **Settings → Secrets and variables → Actions**. Under **Secrets**, add `MODRINTH_TOKEN` with the token value.
3. Under **Variables**, add `MODRINTH_PROJECT_ID`. TeleHop's project ID is `cjTmNjaL`. You can confirm it with **Copy project ID** on the project page.
4. Enable GitHub Actions for the repository. The build uses read access. The publishing job requests `contents: write` to create the tag, draft and release assets. Tag rules must allow the workflow to create the requested tag. GitHub supplies its token automatically.

[Modrinth token settings](https://modrinth.com/settings/pats) · [TeleHop on Modrinth](https://modrinth.com/plugin/telehop) · [GitHub Actions settings](https://github.com/gitEpildev/TeleHop/settings/secrets/actions)

## Run a release

For a stable release, open **Actions → Release → Run workflow**, select the source branch and enter the version, for example `3.0.0`. Leave **Release type** set to **release**. Optional Markdown replaces the generated release body and passes the same spelling, punctuation and content checks. Run the workflow once.

You can also push a stable tag such as `v3.0.0` at the intended commit. Choose one trigger per release. Alpha and beta channels are available for explicitly requested prereleases.

Prepare the version in source control first. The requested version, newest dated entry in `CHANGELOG.md`, root POM, each module's parent and project version, Paper descriptor and Velocity annotation must agree. A tag must identify that same version. Pushing a branch commit runs CI for the exact SHA the release will use.

GitHub receives a release with separate Paper and Velocity JARs. Modrinth receives one entry per loader, using the files downloaded from GitHub and the GitHub release body. The Paper entry lists published Paper server versions. The Velocity entry lists client versions, including Minecraft 1.21.2, which shares the 1.21.3 protocol. [Platform table](../../README.md#installation)

The default public notes include the reviewed changelog entry and group commits since the nearest reachable previous version tag. Push and pull request CI checks the spelling and formatting of these generated notes before the release is started. For the first release, history starts after the standalone 2.2.0 reconciliation. Use conventional commit subjects such as `fix(paper): respect the self-coordinate permission`. A `!` or `BREAKING CHANGE:` footer places the change in the breaking changes section.

## Checks before publication

### Changelog requirements

- Put the newest release first, using a heading such as `## [3.0.0] - 2026-11-24`. Use unique versions, valid calendar dates and descending release order. An optional leading `[Unreleased]` section can hold work for later.
- Include at least one bullet describing the observable change. Replace placeholders such as `TBD` with the actual effect. Upgrade instructions may refer to older versions.
- Changes to shipped source, resources, Maven POMs or SQL must update the visible wording of the newest release entry. CI compares a PR with its base and a branch push with its previous commit. On a new branch it uses the common ancestor with the default branch. A release also checks changes since the preceding release.
- Use UK English in headings, paragraphs, bullets and link labels throughout the changelog and public notes. The pinned UK dictionary checks words such as *colour*, *behaviour* and *initialise*. Put exact API names and commands in backticks. Add genuine project terms to `.github/release/project-words.txt` for review. Code samples and URL destinations keep their spelling.
- Use ordinary punctuation. Long dashes, including HTML entities for them, fail throughout the Markdown. The run prints the source, line and reason. Human review covers accuracy, grammar and whether the change is clear to a server operator.

For example, a request for `3.0.0` with `2.2.0` as the newest dated entry stops before compilation. Correct the entry and descriptors together, push the change and release the new commit.

### Build requirements

Every branch push and PR runs the full Maven reactor, Checkstyle, repository validation, release regression tests, compatibility matrix and runtime tests. The **Release readiness** job requires every one of those jobs to succeed. The release workflow also performs a fresh clean build and runtime tests. Before publishing, it reads the latest push CI run on the exact commit and checks both its conclusion and **Release readiness**. A failed, cancelled, missing or skipped required check blocks publication. A pending run has up to 15 minutes to finish.

Packaging requires all six Maven modules, their JARs, passing Surefire reports from common, Paper and Velocity and matching versions in the Paper and Velocity distributions. Missing reports, zero tests in any of those modules, test failures, errors or skips stop the build. The Paper compatibility check verifies that every plugin class uses Java 21-compatible bytecode, including the newer adapter. Maven compiles that adapter against the newer Paper API with JDK 25. This lets early Paper 1.21 releases remap the complete JAR on Java 21. Paper 26.1.2 and 26.2 still require Java 25 to run.

Every Paper version in the platform table runs the server smoke test. Each boot checks command ownership with the default configuration, then restarts with renamed teleport commands. The Minecraft 1.21.2 client check uses Paper 1.21.3 through Velocity. PaperMC published only alpha-channel server builds for Minecraft 1.21.5 and 1.21.9. The runtime matrix uses those upstream builds for those two cases. [PaperMC download service](https://docs.papermc.io/misc/downloads-service/)

Velocity 3.3, 3.5 and 4.3.1 each run two proxy boots. Two Minecraft clients exercise configurable destinations, delegated staff actions and LuckPerms on Velocity 3.5 and 4.3.1 using two Paper backends and Multiverse. Players also select a saved home and a region and dimension through the inventory menus. A mixed-network check connects both proxy generations to the same backends, puts observers on the other proxy and repeats regional and named spawn travel in each direction. Separate checks use the published TeleHop 2.3.0 proxy and backend JARs to exercise rolling-upgrade behaviour. Two bridge instances exchange player updates and teleport packets across a real Redis restart. The Actions summary reports local Redis packet-forwarding latency. Live player transfer time includes additional client and server work.

The publishing job requires every runtime check to pass. Failed server jobs attach both available boot logs for diagnosis.

## Retry an interrupted release

Open the original run and select **Re-run failed jobs**. A retry checks the tag's commit and resumes any incomplete draft. A resumed draft must contain the verified build bytes. The GitHub release body must match the validated notes on every attempt. After GitHub publication its existing JARs are used for Modrinth. Modrinth entries with a matching version, loader, changelog, compatibility and SHA-512 are skipped. A conflict stops the workflow and identifies the affected file or field. Fix a missing or expired credential in repository settings and retry. If the build artifact has expired, re-run all jobs on the original run.

## Reproduce validation locally

From the repository root, with Node.js 22.12 or later, Python 3.11 or later, Maven and the configured Java 21 and 25 toolchains, run the following commands. The prepare command writes a preview to `release-bundle/notes.md`. All commands below run locally.

```sh
npm ci --prefix .github/release --ignore-scripts --no-audit --no-fund
python3 .github/release/prepare.py check
REQUEST_VERSION=3.0.0 REQUEST_CHANNEL=release python3 .github/release/prepare.py prepare
python3 -m unittest discover -v -s .github/release
mvn -B -ntp clean verify
bash .github/tests/bundle.sh 3.0.0
```
