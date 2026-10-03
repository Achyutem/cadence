# Building Cadence from a clean machine

Nothing here assumes Android Studio, and nothing here assumes you have Kotlin installed. Gradle
downloads its own Kotlin compiler as part of the build, so "installing Kotlin" is not a step.

Three things are actually required: a JDK, the Android SDK, and roughly 4 GB of disk for the SDK
plus Gradle's caches. The first build pulls dependencies and takes a few minutes. Later builds
take seconds.

## Arch Linux

### 1. JDK 17 and the basics

```bash
sudo pacman -S --needed jdk17-openjdk git unzip
```

Arch can hold several JDKs at once, so make 17 the active one:

```bash
sudo archlinux-java set java-17-openjdk
```

Check it took. If `java -version` says anything other than 17, the build fails in a way that does
not obviously point at Java:

```bash
java -version
```

Newer JDKs mostly work, but 17 is what this project is built and tested against, and it is the
version the Android Gradle Plugin is happiest on.

### 2. Android SDK command-line tools

Use the official zip rather than the AUR packages. It is distro independent, it is what Google
tests, and it avoids an AUR rebuild every time the SDK moves.

Download `commandlinetools-linux-*.zip` from <https://developer.android.com/studio#command-tools>,
then:

```bash
mkdir -p ~/android-sdk/cmdline-tools && unzip ~/Downloads/commandlinetools-linux-*.zip -d ~/android-sdk/cmdline-tools && mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
```

The `latest` rename is not optional. `sdkmanager` works out where the SDK root is by walking up
from its own location, and it only finds it if it sits in `cmdline-tools/latest/bin`.

### 3. Point the environment at it

```bash
echo 'export ANDROID_HOME="$HOME/android-sdk"' >> ~/.bashrc && echo 'export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"' >> ~/.bashrc && source ~/.bashrc
```

Use `~/.zshrc` instead if you are on zsh.

### 4. Install the pieces this project needs

```bash
sdkmanager --install "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

```bash
sdkmanager --licenses
```

Accept every licence prompt. An unaccepted licence fails the build with a message about missing
packages rather than about licences, which is a confusing half hour if you skip this.

## Building

Clone the repository, then tell Gradle where the SDK is. `local.properties` is deliberately not
committed, so this is per machine:

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

A debug APK, installable on a phone with USB debugging on:

```bash
./gradlew :app:assembleDebug
```

The result lands in `app/build/outputs/apk/debug/`.

Run the checks the project holds itself to. Lint runs with `warningsAsErrors`, so it fails on
anything it reports:

```bash
./gradlew :app:testDebugUnitTest :app:lintRelease
```

Instrumented tests need a connected device or a running emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## A release APK for your own phone

F-Droid signs its own builds with its own key, so none of this is needed for publishing. It is
only needed if you want to sideload a release build yourself.

The release build type produces an unsigned APK, because this project deliberately keeps signing
configuration out of the build file:

```bash
./gradlew :app:assembleRelease
```

Create a keystore once, and keep it somewhere safe and backed up. Losing it means you can never
update a sideloaded install again; Android refuses an update signed by a different key:

```bash
keytool -genkeypair -v -keystore ~/cadence-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias cadence
```

Sign and verify, using `apksigner` from the build tools:

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner sign --ks ~/cadence-release.jks --out ~/Cadence.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs ~/Cadence.apk
```

Keep the keystore out of the repository. `.gitignore` already covers `*.jks` and `*.keystore`,
but the safest place for it is not in the working tree at all.

## When it goes wrong

**`SDK location not found`.** No `local.properties`, or `ANDROID_HOME` is unset in the shell that
ran Gradle. Environment changes do not reach an already running Gradle daemon; `./gradlew --stop`
and try again.

**`Failed to install the following SDK components ... licences have not been accepted`.** Run
`sdkmanager --licenses` and accept them.

**`Unsupported class file major version`.** Gradle is running on the wrong JDK. Check
`./gradlew --version`, which prints the JVM it actually used, rather than `java -version`.

**`Could not determine the dependencies ... does not provide the required capabilities:
[JAVA_COMPILER]`.** `JAVA_HOME` points at a JRE rather than a JDK. Point it at a real JDK:
`JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew ...`.

**The first build appears to hang.** It is downloading Gradle 8.14.3 and the dependency set, a
few hundred megabytes. `./gradlew --info` shows what it is fetching.
