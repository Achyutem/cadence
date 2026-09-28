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
here, and is submitted as a merge request adding `metadata/dev.achyutem.cadence.yml`:

```yaml
Categories:
  - Time
License: GPL-3.0-or-later
AuthorName: Achyutem
SourceCode: https://github.com/Achyutem/cadence
IssueTracker: https://github.com/Achyutem/cadence/issues

RepoType: git
Repo: https://github.com/Achyutem/cadence.git

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

Before opening the merge request, run F-Droid's own checks against it:

```bash
fdroid readmeta && fdroid rewritemeta dev.achyutem.cadence && fdroid lint dev.achyutem.cadence
```

```bash
fdroid build -v -l dev.achyutem.cadence
```

The second command builds the app the way the server will. If it passes locally it will almost
certainly pass on the buildserver, and a build that fails there is the most common reason a
submission stalls.
