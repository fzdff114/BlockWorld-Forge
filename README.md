# BlockWorld Forge
Native Android application for planning Minecraft Bedrock worlds from real-world map data and procedural terrain.

**The UI is native Kotlin + Jetpack Compose. It does not use HTML, CSS, JavaScript, WebView, or a website wrapped as an APK.**

## 0.1.0 — Native UI foundation
- Native Android dashboard and project draft creation
- Android document picker entry point for local map files
- World configuration: 4K / 6K / 10K / 15K / 20K, terrain styles and generation toggles
- Asset library and Bedrock export screens
- White canvas, blue primary actions, pale-blue highlights and native bottom navigation

**Current limitations:** Map parsing, map-provider adapters, terrain generation, persistent project storage, and real .mcworld export are not implemented yet. The file picker selects a local file and displays its name only.

## Build APK
GitHub Actions builds a debug APK on every push to main.
1. Open the repository's Actions tab.
2. Open the latest Android APK run.
3. Download the BlockWorld-Forge-Android-debug artifact.
4. Extract the ZIP and install app-debug.apk on Android.

Local build: open this repository in Android Studio with JDK 17 and run the assembleDebug Gradle task.

## Structure
- app/src/main/java/com/fzdff/blockworldforge/ — native Compose application
- app/src/main/res/values/ — Android theme
- .github/workflows/android-apk.yml — APK build workflow
- docs/ROADMAP.md — implementation roadmap
