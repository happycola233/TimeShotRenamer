package com.happycola233.TimeShot.Renamer

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import androidx.core.database.getLongOrNull
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.happycola233.TimeShot.Renamer.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DEFAULT_NAME_TEMPLATE = "IMG_yyyyMMdd_HHmmss"
private val SUPPORTED_DATE_TOKENS = listOf(
    "yyyy",
    "yy",
    "MM",
    "dd",
    "HH",
    "mm",
    "ss",
    "SSS"
).sortedByDescending { it.length }

class MediaRenameViewModel(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, Dispatchers.IO)

    private val app: Application = application
    private val contentResolver = application.contentResolver

    private val _uiState = MutableStateFlow(RenameUiState())
    val uiState: StateFlow<RenameUiState> = _uiState.asStateFlow()

    private val _renameRequests = MutableSharedFlow<RenameRequest>(extraBufferCapacity = 1)
    val renameRequests: SharedFlow<RenameRequest> = _renameRequests.asSharedFlow()

    private var pendingRenameUris: List<Uri> = emptyList()

    fun refreshAllImages() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    snackbarMessage = null,
                    renameSummary = null
                )
            }

            val currentSettings = _uiState.value.settings
            runCatching { loadAllImages(currentSettings) }
                .onSuccess { items ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = items
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            snackbarMessage = throwable.localizedMessage
                                ?: "Unable to load media items. Please confirm permissions."
                        )
                    }
                }
        }
    }

    fun updateFilterOnlyNonCompliant(enabled: Boolean) {
        _uiState.update { it.copy(filterNonCompliantOnly = enabled) }
    }

    fun updateUsePureBlackInDarkTheme(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(usePureBlackInDarkTheme = enabled)
            )
        }
    }

    fun updateForceRenameEnabled(enabled: Boolean) {
        _uiState.update { state ->
            val newSettings = state.settings.copy(forceRenameEnabled = enabled)
            state.copy(
                settings = newSettings,
                items = rebuildItemsForSettings(newSettings, state.items)
            )
        }
    }

    
    fun updateNameFormatTemplate(template: String) {
        val candidate = template
        if (candidate.isEmpty()) {
            val emptyError = app.getString(R.string.settings_format_error_empty)
            _uiState.update { state ->
                state.copy(
                    settings = state.settings.copy(
                        nameFormatTemplate = candidate,
                        nameFormatError = emptyError
                    )
                )
            }
            return
        }

        runCatching {
            val sanitizedPattern = sanitizeTemplate(candidate)
            val formatter = DateTimeFormatter.ofPattern(sanitizedPattern, Locale.getDefault())
            sanitizedPattern to formatter
        }.onSuccess { (pattern, formatter) ->
            _uiState.update { state ->
                val newSettings = state.settings.copy(
                    nameFormatTemplate = candidate,
                    sanitizedPattern = pattern,
                    formatter = formatter,
                    nameFormatError = null
                )
                state.copy(
                    settings = newSettings,
                    items = rebuildItemsForSettings(newSettings, state.items)
                )
            }
        }.onFailure { error ->
            val fallback = app.getString(R.string.settings_format_error_invalid)
            val message = error.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
            _uiState.update { state ->
                state.copy(
                    settings = state.settings.copy(
                        nameFormatTemplate = candidate,
                        nameFormatError = message
                    )
                )
            }
        }
    }

    fun resetNameFormatTemplate() {
        val sanitizedPattern = sanitizeTemplate(DEFAULT_NAME_TEMPLATE)
        val formatter = DateTimeFormatter.ofPattern(sanitizedPattern, Locale.getDefault())

        _uiState.update { state ->
            val newSettings = state.settings.copy(
                nameFormatTemplate = DEFAULT_NAME_TEMPLATE,
                sanitizedPattern = sanitizedPattern,
                formatter = formatter,
                nameFormatError = null
            )
            state.copy(
                settings = newSettings,
                items = rebuildItemsForSettings(newSettings, state.items)
            )
        }
    }


    fun toggleSelection(uri: Uri) {
        _uiState.update { state ->
            val updated = state.items.map { item ->
                if (item.uri == uri && item.canRename) {
                    item.copy(isSelected = !item.isSelected)
                } else {
                    item
                }
            }
            state.copy(items = updated)
        }
    }

    fun selectAll(select: Boolean) {
        _uiState.update { state ->
            val updated = state.items.map { item ->
                val shouldSelect = select && item.canRename
                if (item.isSelected == shouldSelect) item else item.copy(isSelected = shouldSelect)
            }
            state.copy(items = updated)
        }
    }

    fun invertSelection() {
        _uiState.update { state ->
            val updated = state.items.map { item ->
                if (item.canRename) {
                    item.copy(isSelected = !item.isSelected)
                } else if (item.isSelected) {
                    item.copy(isSelected = false)
                } else {
                    item
                }
            }
            state.copy(items = updated)
        }
    }

    fun selectNonCompliant() {
        _uiState.update { state ->
            val updated = state.items.map { item ->
                item.copy(isSelected = item.needsRename)
            }
            state.copy(items = updated)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun beginRename() {
        val state = _uiState.value
        if (state.isRenaming) return

        val targets = state.items.filter { it.isSelected && it.canRename }
        if (targets.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "请先选择需要重命名的图片。") }
            return
        }

        viewModelScope.launch(ioDispatcher) {
            runCatching {
                val intentSender = MediaStore.createWriteRequest(
                    contentResolver,
                    targets.map { it.uri }
                ).intentSender
                pendingRenameUris = targets.map { it.uri }
                _uiState.update {
                    it.copy(
                        isRenaming = true,
                        items = it.items.markRenaming(pendingRenameUris)
                    )
                }
                _renameRequests.tryEmit(RenameRequest(intentSender))
            }.onFailure { throwable ->
                pendingRenameUris = emptyList()
                _uiState.update {
                    it.copy(
                        isRenaming = false,
                        snackbarMessage = "无法申请写入授权：${throwable.localizedMessage ?: throwable.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun cancelRename() {
        if (pendingRenameUris.isEmpty()) return
        pendingRenameUris = emptyList()
        _uiState.update {
            it.copy(
                isRenaming = false,
                items = it.items.resetPendingStatuses(),
                snackbarMessage = "已取消重命名请求。"
            )
        }
    }

    fun performRename() {
        if (pendingRenameUris.isEmpty()) {
            _uiState.update { it.copy(isRenaming = false) }
            return
        }

        viewModelScope.launch(ioDispatcher) {
            val currentItems = _uiState.value.items.associateBy { it.uri }
            val updatedItems = currentItems.toMutableMap()
            var success = 0
            var failed = 0

            pendingRenameUris.forEach { uri ->
                val item = currentItems[uri] ?: return@forEach
                val suggested = item.suggestedName

                if (suggested.isNullOrBlank()) {
                    failed += 1
                    updatedItems[uri] = item.copy(
                        status = RenameStatus.Failed,
                        compliance = ComplianceStatus.Unknown,
                        errorMessage = "Missing valid target file name.",
                        matchesTargetName = false
                    )
                    return@forEach
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, suggested)
                }

                runCatching {
                    val rows = contentResolver.update(uri, values, null, null)
                    if (rows > 0) {
                        success += 1
                        updatedItems[uri] = item.copy(
                            displayName = suggested,
                            suggestedName = suggested,
                            compliance = ComplianceStatus.Compliant,
                            status = RenameStatus.Success,
                            errorMessage = null,
                            isSelected = false,
                            matchesTargetName = false
                        )
                    } else {
                        throw IllegalStateException("System rename request returned 0 rows.")
                    }
                }.onFailure { throwable ->
                    failed += 1
                    updatedItems[uri] = item.copy(
                        status = RenameStatus.Failed,
                        compliance = ComplianceStatus.Unknown,
                        errorMessage = throwable.localizedMessage ?: throwable.javaClass.simpleName,
                        matchesTargetName = false
                    )
                }
            }

            pendingRenameUris = emptyList()

            val sortedItems = updatedItems.values
                .sortedWith(
                    compareByDescending<ImageItem> { it.primaryInstant ?: Instant.EPOCH }
                        .thenBy { it.displayName.lowercase(Locale.getDefault()) }
                )

            _uiState.update {
                it.copy(
                    isRenaming = false,
                    items = ensureUniqueSuggestions(sortedItems),
                    renameSummary = RenameSummary(success = success, failed = failed),
                    snackbarMessage = buildRenameMessage(success, failed)
                )
            }
        }
    }

    private suspend fun loadAllImages(settings: UserSettings): List<ImageItem> = withContext(ioDispatcher) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        val items = mutableListOf<ImageItem>()

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val metadata = ImageMetadata(
                    displayName = cursor.getString(nameColumn),
                    mimeType = cursor.getString(mimeColumn),
                    dateTakenMillis = cursor.getLongOrNull(takenColumn),
                    dateModifiedSeconds = cursor.getLongOrNull(modifiedColumn)
                )
                items += createImageItem(uri, metadata, settings)
            }
        }

        ensureUniqueSuggestions(
            items.sortedWith(
                compareByDescending<ImageItem> { it.primaryInstant ?: Instant.EPOCH }
                    .thenBy { it.displayName.lowercase(Locale.getDefault()) }
            )
        )
    }

    private fun createImageItem(
        uri: Uri,
        metadata: ImageMetadata,
        settings: UserSettings
    ): ImageItem {
        val displayName = metadata.displayName.takeIf { !it.isNullOrBlank() }
            ?: fallbackDisplayName(uri)
        val extensionFromName = displayName.substringAfterLast('.', "")
        val hasRequiredPattern = hasRequiredDatePattern(displayName)
        val shouldForceRename = settings.forceRenameEnabled
        val shouldGenerateSuggestion = shouldForceRename || !hasRequiredPattern

        val exifResult = runCatching { resolveCaptureInstant(uri) }
        val captureInstant = exifResult.getOrNull()

        val dateTakenInstant = metadata.dateTakenMillis
            ?.takeIf { it > 0 }
            ?.let { Instant.ofEpochMilli(it) }

        val savedInstant = metadata.dateModifiedSeconds?.takeIf { it > 0 }
            ?.let { Instant.ofEpochSecond(it) }
            ?: dateTakenInstant

        val renameInstant = captureInstant
            ?: dateTakenInstant
            ?: savedInstant

        val errorMessage = exifResult.exceptionOrNull()?.localizedMessage
        val resolvedExtension = extensionFromName.ifBlank { guessExtensionFromMime(metadata.mimeType) }
        val suggestedName = if (shouldGenerateSuggestion) {
            generateSuggestedName(renameInstant, resolvedExtension, settings)
        } else {
            null
        }

        val isAlreadyCompliant = if (shouldGenerateSuggestion) {
            isNameAlreadyCompliant(
                displayName = displayName,
                suggestion = suggestedName,
                settings = settings
            )
        } else {
            true
        }
        val matchesTargetName = settings.forceRenameEnabled &&
            shouldGenerateSuggestion &&
            !suggestedName.isNullOrBlank() &&
            displayName.equals(suggestedName, ignoreCase = true)

        val compliance = when {
            !shouldGenerateSuggestion -> ComplianceStatus.Compliant
            isAlreadyCompliant -> ComplianceStatus.Compliant
            suggestedName != null -> ComplianceStatus.NeedsRename
            else -> ComplianceStatus.Unknown
        }
        val normalizedSuggestion = if (compliance == ComplianceStatus.NeedsRename) suggestedName else null

        return ImageItem(
            uri = uri,
            displayName = displayName,
            suggestedName = normalizedSuggestion,
            captureInstant = captureInstant,
            savedInstant = savedInstant,
            extension = resolvedExtension,
            compliance = compliance,
            status = RenameStatus.Idle,
            errorMessage = errorMessage,
            isSelected = false,
            matchesTargetName = matchesTargetName
        )
    }

    private fun rebuildItemsForSettings(
        settings: UserSettings,
        items: List<ImageItem>
    ): List<ImageItem> {
        val updated = items.map { item ->
            val hasRequiredPattern = hasRequiredDatePattern(item.displayName)
            val shouldGenerateSuggestion = settings.forceRenameEnabled || !hasRequiredPattern

            if (!shouldGenerateSuggestion) {
                return@map item.copy(
                    suggestedName = null,
                    compliance = ComplianceStatus.Compliant,
                    status = RenameStatus.Idle,
                    isSelected = false,
                    matchesTargetName = false
                )
            }

            val suggestion = generateSuggestedName(item.primaryInstant, item.extension, settings)
            val isCompliant = isNameAlreadyCompliant(item.displayName, suggestion, settings)
            val matchesTargetName = settings.forceRenameEnabled &&
                !suggestion.isNullOrBlank() &&
                item.displayName.equals(suggestion, ignoreCase = true)
            val compliance = when {
                isCompliant -> ComplianceStatus.Compliant
                suggestion != null -> ComplianceStatus.NeedsRename
                else -> ComplianceStatus.Unknown
            }
            item.copy(
                suggestedName = if (compliance == ComplianceStatus.NeedsRename) suggestion else null,
                compliance = compliance,
                status = RenameStatus.Idle,
                isSelected = if (compliance == ComplianceStatus.NeedsRename) item.isSelected else false,
                matchesTargetName = matchesTargetName
            )
        }
        return ensureUniqueSuggestions(updated)
    }

    private fun generateSuggestedName(
        instant: Instant?,
        extension: String,
        settings: UserSettings
    ): String? {
        if (instant == null) return null
        val ext = extension.ifBlank { "" }
        if (ext.isBlank()) return null
        val base = settings.formatter.format(instant.atZone(ZoneId.systemDefault()))
        return "$base.$ext"
    }

    private fun isNameAlreadyCompliant(
        displayName: String,
        suggestion: String?,
        settings: UserSettings
    ): Boolean {
        if (suggestion.isNullOrBlank()) return false
        if (displayName.equals(suggestion, ignoreCase = true)) return true

        val displayBase = displayName.substringBeforeLast('.', displayName)
        val displayExt = displayName.substringAfterLast('.', "")
        val suggestionBase = suggestion.substringBeforeLast('.', suggestion)
        val suggestionExt = suggestion.substringAfterLast('.', "")

        if (displayBase.equals(suggestionBase, ignoreCase = false) &&
            suggestionExt.equals(displayExt, ignoreCase = true)
        ) {
            return true
        }

        if (!settings.forceRenameEnabled &&
            settings.isUsingDefaultTemplate &&
            (displayBase.containsEmbeddedTimestamp() || displayName.containsEmbeddedTimestamp())
        ) {
            return true
        }

        return false
    }

    private fun ensureUniqueSuggestions(items: List<ImageItem>): List<ImageItem> {
        val seen = HashMap<String, Int>()
        return items.map { item ->
            val suggestion = item.suggestedName
            if (suggestion.isNullOrBlank()) {
                item
            } else {
                val key = suggestion.lowercase(Locale.getDefault())
                val count = seen[key] ?: 0
                seen[key] = count + 1

                if (count == 0 || item.compliance == ComplianceStatus.Compliant) {
                    item
                } else {
                    val base = suggestion.substringBeforeLast('.', suggestion)
                    val ext = suggestion.substringAfterLast('.', "")
                    val indexed = buildString {
                        append(base)
                        append('_')
                        append(String.format(Locale.getDefault(), "%02d", count))
                        if (ext.isNotBlank()) {
                            append('.')
                            append(ext)
                        }
                    }
                    item.copy(
                        suggestedName = indexed,
                        compliance = ComplianceStatus.NeedsRename,
                        matchesTargetName = false
                    )
                }
            }
        }
    }

    private fun buildRenameMessage(success: Int, failed: Int): String? = when {
        success > 0 && failed == 0 -> "已成功重命名 $success 张图片。"
        success == 0 && failed > 0 -> "重命名失败 $failed 张，详见列表。"
        success > 0 && failed > 0 -> "部分成功：$success 张成功、$failed 张失败。"
        else -> null
    }

    private fun resolveCaptureInstant(uri: Uri): Instant? =
        contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            exif.extractCaptureInstant()
        }

    private fun guessExtensionFromMime(mimeType: String?): String = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        "image/gif" -> "gif"
        else -> "jpg"
    }

    private fun fallbackDisplayName(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/') ?: "IMG_${uri.hashCode()}.jpg"

    private fun ExifInterface.extractCaptureInstant(): Instant? {
        val baseDateTime = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: getAttribute(ExifInterface.TAG_DATETIME)
            ?: getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
            ?: return null

        val subSec = getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL)
            ?: getAttribute(ExifInterface.TAG_SUBSEC_TIME)
            ?: getAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED)

        val offsetString = getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
            ?: getAttribute(ExifInterface.TAG_OFFSET_TIME)
            ?: getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED)

        return runCatching {
            val localDateTime = LocalDateTime.parse(baseDateTime, EXIF_DATE_TIME_FORMATTER)
            val nanos = subSec?.padEnd(3, '0')?.take(3)?.toIntOrNull()?.let { it * 1_000_000 } ?: 0
            val adjustedDateTime = localDateTime.plusNanos(nanos.toLong())

            if (!offsetString.isNullOrBlank()) {
                val normalizedOffset = if (offsetString.contains(":")) {
                    offsetString
                } else {
                    when (offsetString.length) {
                        5 -> offsetString
                        6 -> offsetString.substring(0, 3) + ":" + offsetString.substring(3)
                        else -> offsetString
                    }
                }
                val zoneOffset = java.time.ZoneOffset.of(normalizedOffset)
                adjustedDateTime.atOffset(zoneOffset).toInstant()
            } else {
                adjustedDateTime.atZone(ZoneId.systemDefault()).toInstant()
            }
        }.getOrNull()
    }

    private companion object {
        private val COMPACT_DATE_REGEX = Regex("(?<!\\d)\\d{8}(?!\\d)")
        private val UNDERSCORE_DATE_REGEX = Regex("(?<!\\d)\\d{4}_\\d{2}_\\d{2}(?!\\d)")
        private val EMBEDDED_UNDERSCORE_TIMESTAMP_REGEX = Regex("\\d{8}_\\d{6}")
        private val HYPHENATED_TIMESTAMP_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{1,}")
        private val DATE_ONLY_TIMESTAMP_REGEX = Regex("(?<!\\d)\\d{4}-\\d{2}-\\d{2}(?!\\d)")

        private val EXIF_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)

        private fun hasRequiredDatePattern(displayName: String): Boolean {
            val base = displayName.substringBeforeLast('.', displayName)
            return COMPACT_DATE_REGEX.containsMatchIn(base) ||
                UNDERSCORE_DATE_REGEX.containsMatchIn(base)
        }

        private fun String?.containsEmbeddedTimestamp(): Boolean {
            if (this.isNullOrBlank()) return false
            return EMBEDDED_UNDERSCORE_TIMESTAMP_REGEX.containsMatchIn(this) ||
                HYPHENATED_TIMESTAMP_REGEX.containsMatchIn(this) ||
                DATE_ONLY_TIMESTAMP_REGEX.containsMatchIn(this)
        }
    }
}

data class RenameUiState(
    val items: List<ImageItem> = emptyList(),
    val isLoading: Boolean = false,
    val filterNonCompliantOnly: Boolean = true,
    val isRenaming: Boolean = false,
    val renameSummary: RenameSummary? = null,
    val snackbarMessage: String? = null,
    val settings: UserSettings = UserSettings()
) {
    val nonCompliantCount: Int = items.count { it.compliance != ComplianceStatus.Compliant }
    val selectedCount: Int = items.count { it.isSelected }
    val renameCandidateCount: Int = items.count { it.isSelected && it.canRename }
}

data class ImageItem(
    val uri: Uri,
    val displayName: String,
    val suggestedName: String?,
    val captureInstant: Instant?,
    val savedInstant: Instant?,
    val extension: String,
    val compliance: ComplianceStatus,
    val status: RenameStatus,
    val errorMessage: String?,
    val isSelected: Boolean,
    val matchesTargetName: Boolean = false
) {
    val needsRename: Boolean
        get() = compliance == ComplianceStatus.NeedsRename && status != RenameStatus.Success

    val canRename: Boolean
        get() = suggestedName != null &&
            compliance == ComplianceStatus.NeedsRename &&
            status != RenameStatus.Success &&
            status != RenameStatus.Renaming

    val captureLocalTime: LocalDateTime?
        get() = captureInstant?.atZone(ZoneId.systemDefault())?.toLocalDateTime()

    val savedLocalTime: LocalDateTime?
        get() = savedInstant?.atZone(ZoneId.systemDefault())?.toLocalDateTime()

    val primaryInstant: Instant?
        get() = captureInstant ?: savedInstant
}

data class UserSettings(
    val usePureBlackInDarkTheme: Boolean = false,
    val forceRenameEnabled: Boolean = false,
    val nameFormatTemplate: String = DEFAULT_NAME_TEMPLATE,
    val nameFormatError: String? = null,
    val sanitizedPattern: String = sanitizeTemplate(DEFAULT_NAME_TEMPLATE),
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(sanitizedPattern, Locale.getDefault())
) {
    val isUsingDefaultTemplate: Boolean
        get() = nameFormatTemplate == DEFAULT_NAME_TEMPLATE

    fun sampleFileName(extension: String = "jpg"): String {
        val base = formatter.format(
            LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
        )
        return "$base.$extension"
    }
}

private fun sanitizeTemplate(template: String): String {
    require(template.isNotEmpty()) { "命名格式不能为空" }
    val builder = StringBuilder()
    var index = 0
    val length = template.length

    while (index < length) {
        val token = SUPPORTED_DATE_TOKENS.firstOrNull { template.startsWith(it, index) }
        if (token != null) {
            builder.append(token)
            index += token.length
            continue
        }

        val literalBuilder = StringBuilder()
        while (index < length) {
            val nextToken = SUPPORTED_DATE_TOKENS.firstOrNull { template.startsWith(it, index) }
            if (nextToken != null) {
                break
            }
            val ch = template[index]
            if (ch == '\'') {
                literalBuilder.append("''")
            } else {
                literalBuilder.append(ch)
            }
            index++
        }

        if (literalBuilder.isNotEmpty()) {
            builder.append('\'')
            builder.append(literalBuilder)
            builder.append('\'')
        }
    }

    return builder.toString()
}

data class RenameSummary(val success: Int, val failed: Int)

data class RenameRequest(val intentSender: IntentSender)

data class ImageMetadata(
    val displayName: String?,
    val mimeType: String?,
    val dateTakenMillis: Long?,
    val dateModifiedSeconds: Long?
)

enum class ComplianceStatus {
    Compliant,
    NeedsRename,
    Unknown
}

enum class RenameStatus {
    Idle,
    Renaming,
    Success,
    Failed
}

private fun List<ImageItem>.markRenaming(pendingUris: List<Uri>): List<ImageItem> =
    map { item ->
        if (pendingUris.contains(item.uri)) {
            item.copy(status = RenameStatus.Renaming, errorMessage = null)
        } else {
            item
        }
    }

private fun List<ImageItem>.resetPendingStatuses(): List<ImageItem> =
    map { item ->
        if (item.status == RenameStatus.Renaming) {
            item.copy(status = RenameStatus.Idle)
        } else {
            item
        }
    }
