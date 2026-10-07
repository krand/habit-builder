# Habit Builder

A native Android habit tracker built with Kotlin and Jetpack Compose. It has Today, Your week, and Habits tabs, with a monthly history screen for each habit.

The first implementation supports yes/no, counter, single-choice, and multiple-choice marks; per-habit day colors and comments; selecting one or more dates within a single habit in Week; and month/year statistics. Counter habits have an editable daily threshold. Habit definitions, marks, colors, comments, and thresholds are saved locally on the device. A fresh installation starts with no habits.

On **Today**, **Week**, or **Habits**, hold a habit panel and drag it up or down to reorder it. Holding near the top or bottom scrolls longer lists. The order is shared across the three tabs and saved when you release the panel. Accessibility actions also let you move a panel up or down.

On the **Habits** tab, **Export to Excel** opens Android's file save dialog and creates an `.xlsx` workbook. **Week view** lays out the saved weeks with Monday–Sunday cells; colored cells keep their color and a dot indicates a comment. **Daily entries** contains every saved habit/date entry with the full mark, color, and comment, including future dates. **Habits** lists the current habit settings and counter thresholds. The export saves a copy and does not change app data.

UX requirements and unresolved product decisions are collected in [`docs/ux/habit-ux-spec.md`](docs/ux/habit-ux-spec.md). The single Charcoal reference mockup is [`docs/ux/habit-mockup.html`](docs/ux/habit-mockup.html).

## Build and install on a phone

Open this project in Android Studio, wait for Gradle sync, choose an emulator or USB-connected phone, and press **Run**. Set the project's Gradle JDK to Java 21. The app's minimum supported Android version is Android 7.0 (API 24).

For a personal test installation, build a debug APK with Java 21 and the checked-in Gradle wrapper:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :app:assembleDebug
```

The installable file is `app/build/outputs/apk/debug/app-debug.apk`. On the phone, enable Developer options and USB debugging, connect it to the Mac, and accept the debugging prompt. Then run:

```sh
~/Library/Android/sdk/platform-tools/adb devices
~/Library/Android/sdk/platform-tools/adb -d install -r app/build/outputs/apk/debug/app-debug.apk
```

The `-d` flag targets the physical phone when an emulator is also running. `-r` updates an existing installation and keeps its local data. Android Studio's **Run** button can also build and install the debug app directly on the connected phone.

For an APK you intend to keep or share, use Android Studio's **Build > Generate Signed Bundle / APK**, choose **APK**, create and save a private keystore outside the project, select the **release** variant, and finish the wizard. Install the resulting signed APK with the same `adb -d install -r path/to/your-release.apk` command, or open the APK on the phone and allow installation from the file manager or browser when Android prompts. Keep the keystore and passwords: future updates must use the same signing key. Before distributing beyond personal testing, replace the example `applicationId` in `app/build.gradle.kts` with your own permanent package name.

An installation that already has saved habits keeps them after an update. To check the new empty first-run state on a test device, uninstall the app or clear its storage first; either action permanently removes that installation's saved habits, marks, colors, and comments. A debug APK and a release APK signed with a new key cannot update each other under the same package name.

The wrapper pins Gradle 8.13. The SDK path is configured locally in `local.properties`; that machine-specific file is not part of the project wrapper setup.
