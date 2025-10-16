package com.happycola233.TimeShot.Renamer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.happycola233.TimeShot.Renamer.ComplianceStatus
import com.happycola233.TimeShot.Renamer.ImageItem
import com.happycola233.TimeShot.Renamer.RenameRequest
import com.happycola233.TimeShot.Renamer.RenameStatus
import com.happycola233.TimeShot.Renamer.RenameSummary
import com.happycola233.TimeShot.Renamer.RenameUiState
import com.happycola233.TimeShot.Renamer.ui.theme.TimeShotRenamerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun PhotoRenamerScreen(viewModel: MediaRenameViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasPermission by remember { mutableStateOf(context.hasMediaPermission()) }
    var hasRequestedInitialLoad by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.any { it.value }
        hasPermission = granted || context.hasMediaPermission()
        if (hasPermission) {
            hasRequestedInitialLoad = true
            viewModel.refreshAllImages()
        }
    }

    val renameLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.performRename()
        } else {
            viewModel.cancelRename()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.renameRequests.collect { request ->
            renameLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(hasPermission, hasRequestedInitialLoad) {
        if (hasPermission && !hasRequestedInitialLoad) {
            hasRequestedInitialLoad = true
            viewModel.refreshAllImages()
        }
    }

    DisposablePermissionObserver(
        context = context,
        lifecycleOwner = lifecycleOwner
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            hasRequestedInitialLoad = false
        }
    }

    val requestPermission = {
        permissionLauncher.launch(requiredMediaPermissions())
    }

    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    TimeShotRenamerTheme(
        usePureBlackInDarkTheme = uiState.settings.usePureBlackInDarkTheme
    ) {
        AnimatedContent(
            targetState = showSettings,
            transitionSpec = {
                val slideSpec = tween<IntOffset>(
                    durationMillis = 240,
                    easing = FastOutSlowInEasing
                )
                val fadeSpec = tween<Float>(
                    durationMillis = 180,
                    easing = FastOutSlowInEasing
                )
                if (targetState) {
                    (slideInHorizontally(
                        animationSpec = slideSpec,
                        initialOffsetX = { it }
                    ) + fadeIn(animationSpec = fadeSpec)).togetherWith(
                        slideOutHorizontally(
                            animationSpec = slideSpec,
                            targetOffsetX = { -it }
                        ) + fadeOut(animationSpec = fadeSpec)
                    )
                } else {
                    (slideInHorizontally(
                        animationSpec = slideSpec,
                        initialOffsetX = { -it }
                    ) + fadeIn(animationSpec = fadeSpec)).togetherWith(
                        slideOutHorizontally(
                            animationSpec = slideSpec,
                            targetOffsetX = { it }
                        ) + fadeOut(animationSpec = fadeSpec)
                    )
                }.using(
                    SizeTransform(clip = false)
                )
            },
            label = "settings_transition",
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) { settingsVisible ->
            if (settingsVisible) {
                SettingsScreen(
                    settings = uiState.settings,
                    onBack = { showSettings = false },
                    onTogglePureBlack = viewModel::updateUsePureBlackInDarkTheme,
                    onToggleForceRename = viewModel::updateForceRenameEnabled,
                    onFormatChange = viewModel::updateNameFormatTemplate,
                    onRestoreDefaultFormat = viewModel::resetNameFormatTemplate
                )
            } else {
                PhotoRenamerContent(
                    state = uiState,
                    hasPermission = hasPermission,
                    snackbarHostState = snackbarHostState,
                    onRequestPermission = requestPermission,
                    onRefresh = viewModel::refreshAllImages,
                    onToggleFilter = viewModel::updateFilterOnlyNonCompliant,
                    onRenameClick = viewModel::beginRename,
                    onToggleSelection = viewModel::toggleSelection,
                    onInvertSelection = viewModel::invertSelection,
                    onSelectNone = { viewModel.selectAll(select = false) },
                    onSelectNonCompliant = viewModel::selectNonCompliant,
                    onOpenSettings = { showSettings = true }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoRenamerContent(
    state: RenameUiState,
    hasPermission: Boolean,
    snackbarHostState: SnackbarHostState,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFilter: (Boolean) -> Unit,
    onRenameClick: () -> Unit,
    onToggleSelection: (Uri) -> Unit,
    onInvertSelection: () -> Unit,
    onSelectNone: () -> Unit,
    onSelectNonCompliant: () -> Unit
) {
    val visibleItems = remember(state.items, state.filterNonCompliantOnly) {
        if (state.filterNonCompliantOnly) {
            state.items.filter { it.compliance != ComplianceStatus.Compliant }
        } else {
            state.items
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val topBarInteractionSource = remember { MutableInteractionSource() }
    val scrollToTop: () -> Unit = {
        if (visibleItems.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
            }
        }
    }

    val isDarkTheme = isSystemInDarkTheme()
    val forceBlackTopBar = state.settings.usePureBlackInDarkTheme && isDarkTheme
    val topBarColors = if (forceBlackTopBar) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black,
            scrolledContainerColor = Color.Black,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    } else {
        TopAppBarDefaults.topAppBarColors()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.clickable(
                    interactionSource = topBarInteractionSource,
                    indication = null,
                    onClick = scrollToTop
                ),
                title = {
                    Text(
                        text = stringResource(id = R.string.title_app_bar),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = topBarColors,
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(id = R.string.action_settings)
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        enabled = hasPermission && !state.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(id = R.string.action_refresh)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (hasPermission && state.items.isNotEmpty()) {
                RenameFab(
                    isRenaming = state.isRenaming,
                    candidateCount = state.renameCandidateCount,
                    onClick = onRenameClick
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (!hasPermission) {
                PermissionRequestCard(onRequestPermission = onRequestPermission)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterCard(
                        filterEnabled = state.filterNonCompliantOnly,
                        total = state.items.size,
                        nonCompliant = state.nonCompliantCount,
                        selected = state.selectedCount,
                        onToggle = onToggleFilter
                    )

                    SelectionControls(
                        onInvertSelection = onInvertSelection,
                        onSelectNone = onSelectNone,
                        onSelectNonCompliant = onSelectNonCompliant
                    )

                    state.renameSummary?.let { summary ->
                        SummaryCard(summary = summary)
                    }

                    if (visibleItems.isEmpty() && !state.isLoading) {
                        EmptyPlaceholder(onRefresh = onRefresh)
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(visibleItems, key = { it.uri }) { item ->
                                ImagePreviewCard(
                                    item = item,
                                    onToggleSelection = onToggleSelection
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = state.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestCard(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.permission_message),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        AssistChip(
            onClick = onRequestPermission,
            label = { Text(text = stringResource(id = R.string.permission_action)) }
        )
    }
}

@Composable
private fun FilterCard(
    filterEnabled: Boolean,
    total: Int,
    nonCompliant: Int,
    selected: Int,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(id = R.string.filter_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(id = R.string.filter_summary, total, nonCompliant),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = filterEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun SelectionControls(
    onInvertSelection: () -> Unit,
    onSelectNone: () -> Unit,
    onSelectNonCompliant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        val iconTint = if (isSystemInDarkTheme()) Color.White else Color(0xFF1F1F1F)
        AssistChip(
            onClick = onSelectNonCompliant,
            label = { Text(text = stringResource(id = R.string.select_non_compliant)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.SelectAll,
                    contentDescription = null,
                    tint = iconTint
                )
            }
        )
        AssistChip(
            onClick = onInvertSelection,
            label = { Text(text = stringResource(id = R.string.select_all)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_invert_selection),
                    contentDescription = null,
                    tint = iconTint
                )
            }
        )
        AssistChip(
            onClick = onSelectNone,
            label = { Text(text = stringResource(id = R.string.select_none)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_clear_selection),
                    contentDescription = null,
                    tint = iconTint
                )
            }
        )
    }
}

@Composable
private fun SummaryCard(summary: RenameSummary) {
    val icon: ImageVector
    val containerColor: Color
    val contentColor: Color
    val message: String

    when {
        summary.success > 0 && summary.failed == 0 -> {
            icon = Icons.Rounded.CheckCircle
            containerColor = MaterialTheme.colorScheme.primaryContainer
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            message = stringResource(id = R.string.summary_success, summary.success)
        }
        summary.success == 0 && summary.failed > 0 -> {
            icon = Icons.Rounded.ErrorOutline
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
            message = stringResource(id = R.string.summary_failed, summary.failed)
        }
        else -> {
            icon = Icons.Rounded.Sync
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            message = stringResource(id = R.string.summary_partial, summary.success, summary.failed)
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
        }
    }
}

@Composable
private fun EmptyPlaceholder(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.placeholder_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        AssistChip(
            onClick = onRefresh,
            label = { Text(text = stringResource(id = R.string.placeholder_action)) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImagePreviewCard(
    item: ImageItem,
    onToggleSelection: (Uri) -> Unit
) {
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(20.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.3.dp)
    val borderModifier = if (item.isSelected) {
        Modifier.border(
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
            shape = cardShape
        )
    } else {
        Modifier
    }

    var showDetails by remember(item.uri) { mutableStateOf(false) }
    var showFullPreview by remember(item.uri) { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clip(cardShape)
            .combinedClickable(
                onClick = {
                    if (item.canRename) {
                        onToggleSelection(item.uri)
                    }
                },
                onLongClick = { showDetails = true }
            ),
        shape = cardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.uri)
                    .crossfade(true)
                    .size(256)
                    .build(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(
                        onClick = { showFullPreview = true },
                        onLongClick = { showDetails = true }
                    )
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                when {
                    item.canRename && item.suggestedName != null && item.suggestedName != item.displayName -> {
                        Text(
                            text = stringResource(id = R.string.rename_arrow, item.suggestedName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    item.matchesTargetName -> {
                        Text(
                            text = stringResource(id = R.string.label_name_same_as_target),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(id = R.string.label_name_compliant),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                val captureLine = item.captureLocalTime?.let { local ->
                    stringResource(
                        id = R.string.label_capture_time,
                        CAPTURE_TIME_FORMATTER.format(local)
                    )
                } ?: stringResource(id = R.string.label_capture_time_unknown)

                Text(
                    text = captureLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val savedLine = item.savedLocalTime?.let { saved ->
                    stringResource(
                        id = R.string.label_saved_time,
                        CAPTURE_TIME_FORMATTER.format(saved)
                    )
                } ?: stringResource(id = R.string.label_saved_time_unknown)

                Text(
                    text = savedLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                item.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = item.isSelected,
                    onCheckedChange = { onToggleSelection(item.uri) },
                    enabled = item.canRename
                )
                if (!(item.compliance == ComplianceStatus.Compliant && item.status == RenameStatus.Idle)) {
                    StatusBadge(status = item.status)
                }
            }
        }
    }

    if (showFullPreview) {
        val previewScope = rememberCoroutineScope()
        val scaleAnim = remember(item.uri) { Animatable(1f) }
        val offsetXAnim = remember(item.uri) { Animatable(0f) }
        val offsetYAnim = remember(item.uri) { Animatable(0f) }
        val scale = scaleAnim.value
        val offset = Offset(offsetXAnim.value, offsetYAnim.value)
        var containerSize by remember(item.uri) { mutableStateOf(IntSize.Zero) }
        var transformJob by remember(item.uri) { mutableStateOf<Job?>(null) }

        DisposableEffect(Unit) {
            onDispose {
                transformJob?.cancel()
                transformJob = null
            }
        }

        fun launchTransform(
            start: CoroutineStart = CoroutineStart.DEFAULT,
            block: suspend CoroutineScope.() -> Unit
        ) {
            transformJob?.cancel()
            val job = previewScope.launch(start = start, block = block)
            transformJob = job
            job.invokeOnCompletion { transformJob = null }
        }

        fun clampOffset(target: Offset, targetScale: Float): Offset {
            if (containerSize == IntSize.Zero) return target
            val width = containerSize.width.toFloat()
            val height = containerSize.height.toFloat()
            val minX = width - width * targetScale
            val minY = height - height * targetScale
            val clampedX = target.x.coerceIn(minX, 0f)
            val clampedY = target.y.coerceIn(minY, 0f)
            return Offset(clampedX, clampedY)
        }

        Dialog(
            onDismissRequest = { showFullPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                            .onSizeChanged { containerSize = it }
                            .pointerInput(showFullPreview, scale, offset, containerSize) {
                                detectTapGestures(
                                    onDoubleTap = { tapOffset ->
                                        launchTransform {
                                            val currentScale = scaleAnim.value
                                            val currentOffset = Offset(offsetXAnim.value, offsetYAnim.value)
                                            if (scaleAnim.isRunning) scaleAnim.stop()
                                            if (offsetXAnim.isRunning) offsetXAnim.stop()
                                            if (offsetYAnim.isRunning) offsetYAnim.stop()
                                            val scaleSpec = tween<Float>(
                                                durationMillis = 220,
                                                easing = FastOutSlowInEasing
                                            )
                                            val offsetSpec = tween<Float>(
                                                durationMillis = 220,
                                                easing = FastOutSlowInEasing
                                            )
                                            if (currentScale > 1f) {
                                                coroutineScope {
                                                    launch { scaleAnim.animateTo(1f, scaleSpec) }
                                                    launch { offsetXAnim.animateTo(0f, offsetSpec) }
                                                    launch { offsetYAnim.animateTo(0f, offsetSpec) }
                                                }
                                            } else {
                                                val targetScale = (currentScale * 2f).coerceIn(1f, 4f)
                                                val scaleFactor = targetScale / currentScale
                                                val focusedOffset = clampOffset(
                                                    currentOffset * scaleFactor + tapOffset * (1 - scaleFactor),
                                                    targetScale
                                                )
                                                coroutineScope {
                                                    launch { scaleAnim.animateTo(targetScale, scaleSpec) }
                                                    launch { offsetXAnim.animateTo(focusedOffset.x, offsetSpec) }
                                                    launch { offsetYAnim.animateTo(focusedOffset.y, offsetSpec) }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            .pointerInput(showFullPreview, containerSize) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    launchTransform(start = CoroutineStart.UNDISPATCHED) {
                                        val previousScale = scaleAnim.value
                                        val previousOffset = Offset(offsetXAnim.value, offsetYAnim.value)
                                        if (scaleAnim.isRunning) scaleAnim.stop()
                                        if (offsetXAnim.isRunning) offsetXAnim.stop()
                                        if (offsetYAnim.isRunning) offsetYAnim.stop()
                                        val newScale = (previousScale * zoom).coerceIn(1f, 4f)
                                        val scaleFactor = if (previousScale == 0f) 1f else newScale / previousScale
                                        val panBoost = if (newScale > 1f) newScale else 1f
                                        val rawOffset = if (newScale == 1f) {
                                            Offset.Zero
                                        } else {
                                            previousOffset * scaleFactor +
                                                centroid * (1 - scaleFactor) +
                                                pan * panBoost
                                        }
                                        scaleAnim.snapTo(newScale)
                                        val clamped = if (newScale == 1f) {
                                            Offset.Zero
                                        } else {
                                            clampOffset(rawOffset, newScale)
                                        }
                                        offsetXAnim.snapTo(clamped.x)
                                        offsetYAnim.snapTo(clamped.y)
                                    }
                                }
                            }
                    )
                }
                IconButton(
                    onClick = { showFullPreview = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(id = R.string.dialog_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showDetails) {
        val captureLine = item.captureLocalTime?.let { local ->
            stringResource(
                id = R.string.label_capture_time,
                CAPTURE_TIME_FORMATTER.format(local)
            )
        } ?: stringResource(id = R.string.label_capture_time_unknown)

        val savedLine = item.savedLocalTime?.let { saved ->
            stringResource(
                id = R.string.label_saved_time,
                CAPTURE_TIME_FORMATTER.format(saved)
            )
        } ?: stringResource(id = R.string.label_saved_time_unknown)

        AlertDialog(
            onDismissRequest = { showDetails = false },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(text = stringResource(id = R.string.dialog_close))
                }
            },
            title = {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.canRename && item.suggestedName != null && item.suggestedName != item.displayName) {
                        Text(
                            text = stringResource(id = R.string.rename_arrow, item.suggestedName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = captureLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = savedLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    item.errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun StatusBadge(status: RenameStatus) {
    val (label, tint, background) = when (status) {
        RenameStatus.Idle -> Triple(
            stringResource(id = R.string.status_pending),
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer
        )

        RenameStatus.Renaming -> Triple(
            stringResource(id = R.string.status_running),
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )

        RenameStatus.Success -> Triple(
            stringResource(id = R.string.status_done),
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )

        RenameStatus.Failed -> Triple(
            stringResource(id = R.string.status_failed),
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer
        )
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun RenameFab(
    isRenaming: Boolean,
    candidateCount: Int,
    onClick: () -> Unit
) {
    val label = if (candidateCount > 0) {
        stringResource(id = R.string.fab_label_with_count, candidateCount)
    } else {
        stringResource(id = R.string.fab_label_default)
    }
    val isEnabled = !isRenaming && candidateCount > 0
    val fabShape = RoundedCornerShape(16.dp)
    val modifier = Modifier
        .clip(fabShape)
        .then(if (isEnabled) Modifier else Modifier.alpha(0.6f))

    ExtendedFloatingActionButton(
        text = { Text(text = label) },
        icon = {
            if (isRenaming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.DriveFileRenameOutline,
                    contentDescription = null
                )
            }
        },
        onClick = { if (isEnabled) onClick() },
        modifier = modifier,
        expanded = candidateCount > 0,
        shape = fabShape
    )
}

@Composable
private fun DisposablePermissionObserver(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onPermissionChanged: (Boolean) -> Unit
) {
    val currentCallback = rememberUpdatedState(onPermissionChanged)
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _: androidx.lifecycle.LifecycleOwner, event: Lifecycle.Event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentCallback.value.invoke(context.hasMediaPermission())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFF)
@Composable
private fun PhotoRenamerPreview() {
    val sampleItems = listOf(
        ImageItem(
            uri = Uri.parse("content://preview/1"),
            displayName = "IMG_20240101_080000.jpg",
            suggestedName = "IMG_20240101_080000.jpg",
            captureInstant = Instant.now(),
            savedInstant = Instant.now(),
            extension = "jpg",
            compliance = ComplianceStatus.Compliant,
            status = RenameStatus.Success,
            errorMessage = null,
            isSelected = false
        ),
        ImageItem(
            uri = Uri.parse("content://preview/2"),
            displayName = "holiday.jpg",
            suggestedName = "IMG_20231224_193045.jpg",
            captureInstant = null,
            savedInstant = Instant.now(),
            extension = "jpg",
            compliance = ComplianceStatus.NeedsRename,
            status = RenameStatus.Idle,
            errorMessage = null,
            isSelected = true
        )
    )

    TimeShotRenamerTheme {
        PhotoRenamerContent(
            state = RenameUiState(
                items = sampleItems,
                filterNonCompliantOnly = false,
                renameSummary = RenameSummary(success = 1, failed = 0)
            ),
            hasPermission = true,
            snackbarHostState = SnackbarHostState(),
            onRequestPermission = {},
            onRefresh = {},
            onOpenSettings = {},
            onToggleFilter = {},
            onRenameClick = {},
            onToggleSelection = {},
            onInvertSelection = {},
            onSelectNone = {},
            onSelectNonCompliant = {}
        )
    }
}

private val CAPTURE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

private fun requiredMediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun Context.hasMediaPermission(): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
