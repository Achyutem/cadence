# Publishing on F-Droid

F-Droid does not accept an APK. It builds the app itself, from a tag in this repository, on its
own machines, and signs the result with its own key. Everything below exists to make that build
possible for someone who has never seen this project.

## What F-Droid requires, and where Cadence stands

| Requirement | Where it lives |
|---|---|
| A recognised free software licence | [`LICENSE`](../LICENSE), GPL-3.0-or-later |
| No proprietary dependencies | Jetpack and kotlinx only, see `gradle/libs.versions.toml` |
| No non-free bundled assets | Geist Variable, SIL OFL, licence in `licenses/` |
| No signing config in the build | `app/build.gradle.kts` has no `signingConfigs` block |
| No committed binaries | `.gitignore` covers `*.apk`, `*.aab`, `*.jks`, `*.keystore` |
| Builds without `local.properties` | It is ignored; F-Droid sets `ANDROID_HOME` |
| A tag per release | `v<versionName>`, see below |
| Store listing | `fastlane/metadata/android/en-US/` |

`gradle/wrapper/gradle-wrapper.jar` is a tracked binary. This is expected and is not a blocker:
F-Droid's build server replaces the wrapper with its own Gradle before building.

## Toolchain, checked against the buildserver rather than assumed

Three things routinely stall a first submission. None of them apply here, and each was checked
against `fdroidserver` itself rather than guessed at:

- **Gradle version.** `gradlew-fdroid` reads `distributionUrl` from `gradle-wrapper.properties`
  and downloads that version against a checksum. Gradle 8.14.3, which this project pins, is in
  its built-in list, and it also consults a live transparency log for anything newer.
- **compileSdk 36.** `buildserver/provision-android-sdk` preinstalls older platforms but
  deliberately leaves `platforms/` and `build-tools/` group-writable so Gradle can fetch newer
  ones during the build. A recent compileSdk is not a blocker.
- **Java toolchains.** The buildserver sets `org.gradle.java.installations.auto-download=false`,
  so a build that declares a Java toolchain it does not have fails outright. This project sets
  `compileOptions` and `jvmTarget` instead and declares no toolchain, so there is nothing to
  provision.

Keep it that way. Adding `jvmToolchain(...)` would break the F-Droid build without breaking
anything locally, which is the worst kind of regression to find out about from a reviewer.

## Anti-features

None apply. Cadence has no ads, no tracking, no non-free dependencies, no non-free assets, no
non-free network services and no upstream non-free build tooling. If an anti-feature is ever
added to the listing, the fix is to remove the cause, not to argue the label.

## Releasing

Version numbers live in `app/build.gradle.kts`. `versionCode` is a plain integer that must
increase on every release; `versionName` is what people see.

1. Bump `versionCode` and `versionName`.
2. Write `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, at most 500 characters.
3. Commit, then tag: `git tag -a v0.1.0 -m "Cadence 0.1.0" && git push --tags`.

F-Droid is configured with `UpdateCheckMode: Tags`, so a new tag is the whole release process.
There is nothing to upload.

## Screenshots

Still missing. They go in `fastlane/metadata/android/en-US/images/phoneScreenshots/`, named so
they sort in the order they should appear (`1.png`, `2.png`, ...). PNG or JPEG, between 320 and
3840 pixels on the long edge, at most eight of them.

An optional `images/icon.png` at 512x512 overrides the launcher icon on the listing. Without it
F-Droid extracts the adaptive icon from the APK, which is fine.

## The metadata file

This lives in F-Droid's own [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) repository, not
here, and is submitted as a merge request adding `metadata/dev.achyutem.cadence.yml`.

Replace `OWNER` with whichever account this repository ends up published under. It must be the
public URL F-Droid will clone from, not a personal remote.

```yaml
Categories:
  - Time
License: GPL-3.0-or-later
AuthorName: Achyutem
SourceCode: https://github.com/OWNER/cadence
IssueTracker: https://github.com/OWNER/cadence/issues

RepoType: git
Repo: https://github.com/OWNER/cadence.git

Builds:
  - versionName: 0.1.0
    versionCode: 1
    commit: v0.1.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.1.0
CurrentVersionCode: 1
```

## Step by step, the first time

1. **Publish the repository.** The URL must be publicly cloneable without credentials. F-Droid's
   buildserver has no account on your forge.

2. **Tag the release you want built**, and push the tag. F-Droid builds a tag, never a branch:

   ```bash
   git tag -a v0.1.0 -m "Cadence 0.1.0" && git push --tags
   ```

3. **Install `fdroidserver`.** On Arch, keep it out of the system Python:

   ```bash
   sudo pacman -S --needed python-pipx && pipx install fdroidserver
   ```

4. **Fork and clone `fdroiddata`.** This is F-Droid's package index, and it is on GitLab even if
   your app lives on GitHub:

   ```bash
   git clone https://gitlab.com/YOUR_GITLAB_USER/fdroiddata.git && cd fdroiddata
   ```

5. **Scaffold the metadata.** `fdroid import` reads the repository and writes a first draft of
   the file for you, which is less error prone than typing it:

   ```bash
   fdroid import --url https://github.com/OWNER/cadence
   ```

   Then open `metadata/dev.achyutem.cadence.yml` and reconcile it against the block below. Import
   guesses; it does not always guess right about `subdir` or the version fields.

6. **Check it, then build it the way the server will.** The second command is the one that
   matters. A submission that fails here fails there too, and that is the most common reason a
   first merge request sits for weeks:

   ```bash
   fdroid readmeta && fdroid rewritemeta dev.achyutem.cadence && fdroid lint dev.achyutem.cadence
   ```

   ```bash
   fdroid build -v -l dev.achyutem.cadence
   ```

7. **Open the merge request** against `fdroid/fdroiddata`, with the single new metadata file in
   it. Say in the description that you are the upstream author.

8. **Expect review, not silence.** A maintainer will look at the licence, the dependencies and
   the build. Answer on the merge request. Once it merges, the app appears in the index at the
   next build cycle, usually within a day or two.

After the first acceptance, releases are just tags. `UpdateCheckMode: Tags` means F-Droid notices
a new tag and builds it without any further submission.

## Updating the listing

Changing the description or the screenshots does not need a merge request. F-Droid reads
`fastlane/metadata/` straight out of your repository at build time, so an edit here plus a new
tag is the whole process.
