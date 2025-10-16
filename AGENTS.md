# Repository Guidelines

## Project Structure & Module Organization
TimeShot Renamer is a single-module Android app. Production sources live in `app/src/main/java/com/happycola233/TimeShot/renamer/`; Compose UI components sit under `ui/`, and the rename state machine is defined in `MediaRenameViewModel.kt`. Android resources are located in `app/src/main/res/`, JVM tests in `app/src/test/java/`, and instrumentation scaffolding in `app/src/androidTest/java/`. Release builds and baseline profiles reside in `app/release/`, while design prompts live in `Dev_Files/`. Shared Gradle settings stay at the repository root, with module-level configuration in `app/build.gradle.kts`.

## Build, Test, and Development Commands
- `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`) produces a sideloadable debug APK.  
- `./gradlew bundleRelease` generates a Play-ready AAB.  
- `./gradlew :app:compileDebugKotlin` performs a fast Kotlin/Compose syntax pass.  
- `./gradlew lint` surfaces resource and styling warnings.  
- `./gradlew testDebugUnitTest` runs JVM tests; `./gradlew connectedDebugAndroidTest` executes device or emulator Compose/Espresso checks.

## Coding Style & Naming Conventions
Follow Kotlin defaults: four-space indentation, expression-oriented code, and explicit visibility on public APIs. Compose composables use PascalCase nouns (`PhotoRenamerScreen`), ViewModel intents use verb phrases (`refreshAllImages`), and suspend helpers carry the `suspend` modifier. Resource IDs remain lowercase with underscores (`string/filter_title`), and package paths mirror feature boundaries (`com.happycola233.TimeShot.renamer.ui.theme`).

## Testing Guidelines
Prefer JUnit4 for JVM tests, naming files `<ClassUnderTest>Test.kt` with methods formatted as `functionUnderTest_condition_expected`. Compose UI and Espresso flows belong in `app/src/androidTest/java/` and should mirror screen names (`PhotoRenamerScreenTest`) using `createAndroidComposeRule`. Focus coverage on EXIF timestamp parsing, rename collision handling, and multi-item approval paths. Run `testDebugUnitTest` before committing and `connectedDebugAndroidTest` ahead of UI-heavy merges.

## Commit & Pull Request Guidelines
Use Conventional Commits (`type(scope): summary`), e.g., `feat(rename): add unique suffix handling`, keeping subject lines ≤72 characters. Summaries should describe user-facing impact, with bodies noting implementation details and test evidence. Pull requests must include a clear problem statement, linked issues when available, a manual test matrix (device/emulator + API level), and screenshots or screen recordings for UI changes. Capture follow-up work with TODOs or checklist items to guide future iterations.
