# Studia

A study tracker for Android: subjects → topics → timed focus sessions, with progress, hours, streaks and a timeline of every subject against its exam date. Kotlin + Jetpack Compose. Design follows the prototype in `mockup/studia.html`.

## Open and run

1. Android Studio → **Open** → this folder. Let Gradle sync finish.
2. Pick the **Medium Phone API 37** emulator (or plug in your phone with USB debugging on).
3. Press **Run ▶**.

From a terminal: `./gradlew :app:installDebug` (needs `JAVA_HOME=/opt/android-studio/jbr`).
Tests: `./gradlew :app:testDebugUnitTest`.

## Where things are

```
app/src/main/java/com/khawar/studia/
  data/Model.kt        subjects, topics, sessions, settings
  data/Store.kt        saves everything to one JSON file on the phone
  data/Calc.kt         streaks, pace, stats ranges, timeline maths (unit-tested)
  data/Sample.kt       example data for a fresh install
  AppViewModel.kt      every action the UI can take
  ui/StudiaApp.kt      top bar, tab bar, navigation
  ui/screens/          Focus, Subjects (+ timeline), Stats, Settings, session screen, sheets
  ui/components/       pixel numerals, wordmark, dial, slide-to-start, tiles, icons, haptics
  ui/theme/Theme.kt    colours (light and dark) and the Manrope font
```

Data is saved inside the app (`files/studia.json`) and, if you choose a folder in Settings, also to a `studia.json` file there, kept up to date after every change. Settings → "Start fresh" removes the sample data.

