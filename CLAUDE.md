# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TimeShotRenamer is an Android application that renames image files to a standardized timestamp format (IMG_YYYYMMDD_HHMMSS.ext) based on EXIF metadata. The app uses Jetpack Compose for UI, follows MVVM architecture, and targets Android API 29+ with scoped storage support.

## Build Commands

All commands use Gradle wrapper (`gradlew.bat` on Windows, `./gradlew` on Unix):

- `gradlew assembleDebug` - Build debug APK for sideloading
- `gradlew bundleRelease` - Build release AAB for Play Store
- `gradlew testDebugUnitTest` - Run JVM unit tests
- `gradlew connectedDebugAndroidTest` - Run instrumented tests on device/emulator
- `gradlew lint` - Run lint checks
- `gradlew :app:compileDebugKotlin` - Quick syntax check during iteration

## Architecture

### MVVM Pattern

The app follows a strict MVVM architecture:

- **MainActivity.kt** - Single Activity host, sets up Compose theme and initializes ViewModel
- **PhotoRenamerScreen.kt** - Main Compose UI screen with permission handling, image list, selection controls, and preview dialog
- **MediaRenameViewModel.kt** - Core business logic managing state, EXIF parsing, MediaStore queries, and batch renaming via scoped storage APIs

### State Management

`MediaRenameViewModel` exposes two flows:
- `uiState: StateFlow<RenameUiState>` - Single source of truth for UI state including items list, loading status, filters, and rename summary
- `renameRequests: SharedFlow<RenameRequest>` - One-shot events for system write permission requests via MediaStore.createWriteRequest()

### Key Data Models

All defined in MediaRenameViewModel.kt:

- `ImageItem` - Represents a single image with URI, display name, suggested name, EXIF timestamps, compliance status, and selection state
- `ComplianceStatus` - Enum: Compliant (already properly named), NeedsRename (can be renamed), Unknown (no metadata)
- `RenameStatus` - Enum: Idle, Renaming, Success, Failed
- `RenameUiState` - Complete UI state with computed properties for counts and filtering

### EXIF Timestamp Extraction

The ViewModel extracts capture timestamps from EXIF metadata in this order:
1. TAG_DATETIME_ORIGINAL (preferred)
2. TAG_DATETIME
3. TAG_DATETIME_DIGITIZED
4. Falls back to DATE_TAKEN or DATE_MODIFIED from MediaStore

Handles subsecond precision via TAG_SUBSEC_TIME_* and timezone offsets via TAG_OFFSET_TIME_*.

### Naming Compliance Logic

Images are considered compliant if their filename (base or full):
- Matches `IMG_\d{8}_\d{6}` pattern exactly
- Contains embedded timestamp pattern `\d{8}_\d{6}`
- Contains hyphenated timestamp `\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2}-\d{1,}`
- Contains date-only pattern `\d{4}-\d{2}-\d{2}`

### Duplicate Name Handling

`ensureUniqueSuggestions()` prevents filename collisions by:
1. Tracking suggested names case-insensitively
2. Appending `_##` suffix to duplicates (starting from `_01`)
3. Preserving file extensions
4. Leaving already-compliant items unchanged

### Scoped Storage Rename Flow

Android 10+ requires explicit user permission for file modifications:
1. User selects images and taps FAB
2. `beginRename()` calls `MediaStore.createWriteRequest()` with target URIs
3. System shows permission dialog via `ActivityResultContracts.StartIntentSenderForResult()`
4. On approval, `performRename()` updates each URI via `ContentResolver.update()` with new DISPLAY_NAME
5. State updates reflect success/failure per item with error messages

## Permissions

- `READ_MEDIA_IMAGES` (API 33+) or `READ_EXTERNAL_STORAGE` (API ≤32) for scanning images
- MediaStore write requests handled dynamically via scoped storage (no WRITE_EXTERNAL_STORAGE needed)
- Permission checking logic in `PhotoRenamerScreen.kt` via `hasMediaPermission()` extension

## UI Components

PhotoRenamerScreen.kt contains these major composables:

- **PhotoRenamerContent** - Main scaffold with TopAppBar, list, FAB, and loading overlay
- **FilterCard** - Toggle for showing only non-compliant images, displays counts
- **SelectionControls** - Three AssistChip buttons: select all non-compliant, invert selection, clear selection
- **ImagePreviewCard** - Individual list item with thumbnail, metadata, rename arrow, checkbox, and status badge
- **SummaryCard** - Post-rename results banner (success/failure/partial)
- **RenameFab** - Extended FAB showing selected count and rename action
- **Full preview dialog** - Zoomable image viewer with pinch-to-zoom and double-tap gestures

## Testing

- Unit tests go in `app/src/test/java` following `<ClassUnderTest>Test.kt` naming
- Instrumented tests in `app/src/androidTest/java` for Compose UI
- Use `createAndroidComposeRule<MainActivity>()` for Compose testing
- Test method naming: `functionUnderTest_condition_expectedResult`

Key areas to test:
- EXIF timestamp parsing with various formats and missing data
- Filename collision resolution in `ensureUniqueSuggestions()`
- Selection state management (toggle, invert, select all, select non-compliant)
- Rename flow with permission denial and MediaStore update failures

## Development Notes

### Package Structure

Base package: `com.happycola233.TimeShot.Renamer`
- Main activity and screen composables at root
- Theme files in `ui.theme` subpackage
- ViewModel and data models in root (all in MediaRenameViewModel.kt)

### Chinese Localization

All user-facing strings in `res/values/strings.xml` are in Chinese (zh). App name: "时间戳图片命名器". English localization would require `res/values-en/strings.xml`.

### Date Formatting

Two formatters used:
- `EXIF_DATE_TIME_FORMATTER` - Parse EXIF timestamps (`yyyy:MM:dd HH:mm:ss`)
- `STANDARD_NAME_FORMATTER` - Generate target filenames (`yyyyMMdd_HHmmss`)
- `CAPTURE_TIME_FORMATTER` - Display timestamps in UI (`yyyy-MM-dd HH:mm:ss`)

### Compose Details

- Material 3 with dynamic color support via `TimeShotRenamerTheme`
- Edge-to-edge layout with `enableEdgeToEdge()`
- Coil for async image loading with crossfade animation
- LazyColumn for efficient scrolling of large image libraries
- StateFlow collected via `collectAsStateWithLifecycle()` for lifecycle awareness

### Build Configuration

- Min SDK: 29 (Android 10 - scoped storage requirement)
- Target/Compile SDK: 36
- Java 11 target
- Kotlin DSL (build.gradle.kts)
- Version catalog references via `libs.*` aliases
- Dependencies include: Compose BOM, Lifecycle, Coroutines, ExifInterface, Coil

## Common Development Tasks

### Adding New Selection Modes

1. Add function in `MediaRenameViewModel` that updates `_uiState` with modified `items` list
2. Expose function in `PhotoRenamerScreen` lambda parameters
3. Add UI control (button/chip) in `SelectionControls` or new composable
4. Pass function through component hierarchy

### Modifying Naming Rules

1. Update regex patterns in `MediaRenameViewModel` companion object
2. Modify `containsEmbeddedTimestamp()` logic for new compliance checks
3. Adjust `STANDARD_NAME_FORMATTER` if output format changes
4. Update string resources if UI labels need changes

### Adding New Image Metadata

1. Extend `ImageMetadata` data class with new field
2. Add column to MediaStore query projection in `loadAllImages()`
3. Extract field in cursor loop
4. Pass to `createImageItem()` and update `ImageItem` if needed
5. Display in UI via `ImagePreviewCard` details dialog
