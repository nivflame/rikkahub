package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import kotlinx.coroutines.launch
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowTurnBackward
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.ArrowDown02
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Delete01
import coil3.compose.AsyncImage
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.FileEmpty02
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.FileZip
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.InformationSquare
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.PencilEdit02
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.SortByDown02
import me.rerere.hugeicons.stroke.SortByUp02
import me.rerere.rikkahub.Screen
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.drawable.toBitmap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import me.rerere.workspace.RootfsInstallProgress
import me.rerere.workspace.RootfsInstallStage
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WorkspaceDetailPage(id: String) {
    val navController = LocalNavController.current
    val vm: WorkspaceDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val state by vm.state.collectAsStateWithLifecycle()
    val installProgress by vm.installProgress.collectAsStateWithLifecycle()
    val installError by vm.installError.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<WorkspaceFileEntry?>(null) }
    var renameTarget by remember { mutableStateOf<WorkspaceFileEntry?>(null) }
    var propertiesTarget by remember { mutableStateOf<WorkspaceFileEntry?>(null) }
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { uri ->
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null
                } else null
            } ?: uri.lastPathSegment ?: "imported_file"
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@forEach
            vm.importFile(inputStream, fileName)
        }
    }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        vm.importFolder(treeUri, context)
    }
    var exportTarget by remember { mutableStateOf<WorkspaceFileEntry?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val entry = exportTarget.also { exportTarget = null } ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        val outputStream = context.contentResolver.openOutputStream(uri) ?: return@rememberLauncherForActivityResult
        vm.exportFile(entry, outputStream)
    }
    var showFabMenu by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    val rootfsReady = state.workspace?.shellStatus == WorkspaceShellStatus.READY.name

    BackHandler(enabled = state.path.isNotBlank()) {
        vm.goUp()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.workspace?.name ?: stringResource(R.string.workspace_detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(HugeIcons.Refresh01, contentDescription = null)
                    }
                    if (rootfsReady) {
                        IconButton(onClick = { navController.navigate(Screen.WorkspaceTerminal(id)) }) {
                            Icon(HugeIcons.ComputerTerminal01, contentDescription = null)
                        }
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = showFabMenu,
                button = {
                    ToggleFloatingActionButton(
                        checked = showFabMenu,
                        onCheckedChange = { showFabMenu = it },
                    ) {
                        val imageVector by remember {
                            derivedStateOf { if (checkedProgress > 0.5f) HugeIcons.Cancel01 else HugeIcons.Add01 }
                        }
                        Icon(
                            painter = rememberVectorPainter(imageVector),
                            contentDescription = null,
                            modifier = Modifier.animateIcon({ checkedProgress }),
                        )
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        showFabMenu = false
                        showCreateDialog = true
                    },
                    icon = { Icon(HugeIcons.Add01, contentDescription = null) },
                    text = { Text(stringResource(R.string.workspace_detail_create_menu)) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        showFabMenu = false
                        showImportDialog = true
                    },
                    icon = { Icon(HugeIcons.FileImport, contentDescription = null) },
                    text = { Text(stringResource(R.string.workspace_detail_import_menu)) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                WorkspaceAreaSelector(
                    selected = state.area,
                    onSelected = vm::selectArea,
                )
            }

            item {
                WorkspacePathBar(
                    path = state.path,
                    canGoUp = state.path.isNotBlank(),
                    onGoUp = vm::goUp,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.query.isNotBlank(),
                        onClick = { showSearchSheet = true },
                        label = {
                            Text(
                                state.query.ifBlank { stringResource(R.string.workspace_detail_search) },
                            )
                        },
                        leadingIcon = { Icon(HugeIcons.Search01, contentDescription = null) },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { showSortSheet = true },
                        label = { Text(workspaceSortByLabel(state.sortBy)) },
                        leadingIcon = {
                            Icon(
                                if (state.ascending) HugeIcons.SortByUp02 else HugeIcons.SortByDown02,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }

            state.error?.let { error ->
                item { ErrorCard(error) }
            }

            if (!state.loading && state.entries.isEmpty() && state.error == null) {
                item { EmptyDirectoryState() }
            }

            items(state.entries, key = { "${state.area.name}:${it.path}" }) { entry ->
                WorkspaceFileCard(
                    entry = entry,
                    onOpen = { vm.open(entry) },
                    onDelete = { deleteTarget = entry },
                    onRename = { renameTarget = entry },
                    onProperties = { propertiesTarget = entry },
                    onExport = {
                        exportTarget = entry
                        val suggestedName = if (entry.isDirectory) "${entry.name}.tar.zst" else entry.name
                        exportLauncher.launch(suggestedName)
                    },
                )
            }
        }
    }

    installProgress?.let { progress ->
        RootfsInstallDialog(progress)
    }

    installError?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissInstallError,
            title = { Text(stringResource(R.string.workspace_detail_rootfs_install_failed)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = vm::dismissInstallError) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
        )
    }

    renameTarget?.let { entry ->
        val siblings = state.entries
            .filter { it.path != entry.path }
            .map { it.name }
            .toSet()
        EditWorkspaceDialog(
            title = stringResource(R.string.common_rename),
            initialName = entry.name,
            existingNames = siblings,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                vm.rename(entry, name)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { entry ->
        RikkaConfirmDialog(
            show = true,
            destructive = true,
            title = if (entry.isDirectory) stringResource(R.string.workspace_detail_delete_directory) else stringResource(R.string.workspace_detail_delete_file),
            confirmText = stringResource(R.string.common_delete),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = {
                vm.delete(entry)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        ) {
            Text(stringResource(R.string.workspace_detail_will_delete, entry.path))
        }
    }

    propertiesTarget?.let { entry ->
        var permission by remember(entry) { mutableStateOf("") }
        LaunchedEffect(entry) {
            permission = vm.permissionOf(entry)
        }
        PropertiesDialog(
            entry = entry,
            permission = permission,
            onDismiss = { propertiesTarget = null },
        )
    }

    if (showSearchSheet) {
        SearchBottomSheet(
            initialQuery = state.query,
            initialRecursive = state.recursive,
            onDismiss = { showSearchSheet = false },
            onSearch = { query, recursive ->
                vm.setSearch(query, recursive)
                showSearchSheet = false
            },
            onClear = {
                vm.clearSearch()
                showSearchSheet = false
            },
        )
    }

    if (showSortSheet) {
        SortBottomSheet(
            current = state.sortBy,
            ascending = state.ascending,
            onDismiss = { showSortSheet = false },
            onSelect = { sortBy ->
                vm.setSortBy(sortBy)
                showSortSheet = false
            },
            onToggleOrder = vm::toggleOrder,
        )
    }

    if (showCreateDialog) {
        CreateDialog(
            onDismiss = { showCreateDialog = false },
            onCreateFile = { name ->
                vm.createFile(name)
                showCreateDialog = false
            },
            onCreateFolder = { name ->
                vm.createDirectory(name)
                showCreateDialog = false
            },
        )
    }

    if (showImportDialog) {
        ImportDialog(
            onDismiss = { showImportDialog = false },
            onImportFile = {
                showImportDialog = false
                filePicker.launch(arrayOf("*/*"))
            },
            onImportFolder = {
                showImportDialog = false
                folderPicker.launch(null)
            },
        )
    }
}

@Composable
private fun CreateDialog(
    onDismiss: () -> Unit,
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_detail_create)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.workspace_detail_enter_name)) },
                singleLine = true,
                isError = trimmed.contains('/'),
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { onCreateFile(trimmed) },
                    enabled = trimmed.isNotBlank() && !trimmed.contains('/'),
                ) {
                    Text(stringResource(R.string.workspace_detail_create_file))
                }
                TextButton(
                    onClick = { onCreateFolder(trimmed) },
                    enabled = trimmed.isNotBlank() && !trimmed.contains('/'),
                ) {
                    Text(stringResource(R.string.workspace_detail_create_folder))
                }
            }
        },
    )
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onImportFile: () -> Unit,
    onImportFolder: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_detail_import)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ImportOptionCard(
                    icon = HugeIcons.FileImport,
                    title = stringResource(R.string.workspace_detail_import_file),
                    description = stringResource(R.string.workspace_detail_import_file_desc),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onImportFile,
                )
                ImportOptionCard(
                    icon = HugeIcons.Folder01,
                    title = stringResource(R.string.workspace_detail_import_folder),
                    description = stringResource(R.string.workspace_detail_import_folder_desc),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = onImportFolder,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun ImportOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    containerColor: androidx.compose.ui.graphics.Color,
    iconColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = iconColor,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun RootfsInstallDialog(progress: RootfsInstallProgress) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = when (progress.stage) {
                        RootfsInstallStage.DOWNLOADING -> "Downloading"
                        RootfsInstallStage.EXTRACTING -> "Extracting"
                        RootfsInstallStage.ARCHIVING -> "Archiving"
                        RootfsInstallStage.INSTALLED -> "Installed"
                    },
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                val fraction = progress.totalBytes?.takeIf { it > 0 }?.let {
                    (progress.bytesRead.toFloat() / it).coerceIn(0f, 1f)
                }
                if (fraction != null && progress.stage == RootfsInstallStage.DOWNLOADING) {
                    CircularWavyProgressIndicator(progress = { fraction })
                } else {
                    CircularWavyProgressIndicator()
                }
                Text(
                    text = when (progress.stage) {
                        RootfsInstallStage.DOWNLOADING -> {
                            val total = progress.totalBytes?.let { " / ${formatBytes(it)}" }.orEmpty()
                            formatBytes(progress.bytesRead) + total
                        }

                        RootfsInstallStage.EXTRACTING,
                        RootfsInstallStage.ARCHIVING,
                        -> "${progress.entriesExtracted} items"

                        RootfsInstallStage.INSTALLED -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress.stage == RootfsInstallStage.EXTRACTING || progress.stage == RootfsInstallStage.ARCHIVING) {
                    progress.currentEntry?.let { entry ->
                        Text(
                            text = shortenPath(entry),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
    )
}

private fun shortenPath(path: String, maxSegments: Int = 2): String {
    val segments = path.split('/').filter { it.isNotBlank() }
    if (segments.size <= maxSegments) return path
    return ".../" + segments.takeLast(maxSegments).joinToString("/")
}

@Composable
private fun WorkspaceAreaSelector(
    selected: WorkspaceStorageArea,
    onSelected: (WorkspaceStorageArea) -> Unit,
) {
    val areas = listOf(
        WorkspaceStorageArea.FILES to stringResource(R.string.workspace_detail_area_files),
        WorkspaceStorageArea.LINUX to stringResource(R.string.workspace_detail_area_rootfs),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        areas.forEachIndexed { index, (area, label) ->
            SegmentedButton(
                selected = selected == area,
                onClick = { onSelected(area) },
                shape = SegmentedButtonDefaults.itemShape(index, areas.size),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun WorkspacePathBar(
    path: String,
    canGoUp: Boolean,
    onGoUp: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            enabled = canGoUp,
            onClick = onGoUp,
        ) {
            Icon(HugeIcons.ArrowTurnBackward, contentDescription = null)
        }
        Text(
            text = path.ifBlank { "/" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WorkspaceFileCard(
    entry: WorkspaceFileEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onProperties: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (entry.isDirectory) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkspaceFileIcon(entry)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (entry.isDirectory) {
                    formatDateTime(entry.updatedAt)
                } else {
                    "${formatDateTime(entry.updatedAt)} · ${formatBytes(entry.sizeBytes)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(HugeIcons.MoreVertical, contentDescription = null)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_rename)) },
                    leadingIcon = { Icon(HugeIcons.PencilEdit02, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_export)) },
                    leadingIcon = {
                        Icon(
                            imageVector = HugeIcons.FileImport,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onExport()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.workspace_detail_properties)) },
                    leadingIcon = {
                        Icon(
                            imageVector = HugeIcons.InformationSquare,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onProperties()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            imageVector = HugeIcons.Delete01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun WorkspaceFileIcon(entry: WorkspaceFileEntry) {
    val context = LocalContext.current
    val realFile = entry.file?.takeIf { it.exists() }

    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (realFile != null && (isPreviewableImage(entry.name) || isPreviewableVideo(entry.name))) {
            AsyncImage(
                model = realFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            return@Box
        }

        if (realFile != null && entry.name.lowercase(java.util.Locale.US).endsWith(".apk")) {
            val icon = remember(entry.path, entry.updatedAt) {
                runCatching {
                    val pm = context.packageManager
                    val info = pm.getPackageArchiveInfo(realFile.absolutePath, 0)
                    info?.applicationInfo?.let {
                        it.sourceDir = realFile.absolutePath
                        it.publicSourceDir = realFile.absolutePath
                        pm.getApplicationIcon(it)
                    }
                }.getOrNull()
            }
            val bitmap = remember(icon) {
                icon?.let { runCatching { it.toBitmap() }.getOrNull() }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
                return@Box
            }
        }

        Icon(
            imageVector = when {
                entry.isDirectory -> HugeIcons.Folder01
                isArchive(entry.name) -> HugeIcons.FileZip
                entry.name.contains('.') -> HugeIcons.File02
                else -> HugeIcons.FileEmpty02
            },
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = when {
                entry.isDirectory -> MaterialTheme.colorScheme.primary
                isArchive(entry.name) -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun isPreviewableImage(name: String): Boolean {
    val lower = name.lowercase(java.util.Locale.US)
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
        lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp") ||
        lower.endsWith(".heic") || lower.endsWith(".svg")
}

private fun isPreviewableVideo(name: String): Boolean {
    val lower = name.lowercase(java.util.Locale.US)
    return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") ||
        lower.endsWith(".mov") || lower.endsWith(".avi") || lower.endsWith(".3gp")
}

@Composable
private fun EmptyDirectoryState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = HugeIcons.Folder01,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.workspace_detail_empty_directory),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun isArchive(name: String): Boolean {
    val lower = name.lowercase(java.util.Locale.US)
    return lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz") ||
        lower.endsWith(".tgz") || lower.endsWith(".bz2") || lower.endsWith(".xz") ||
        lower.endsWith(".7z") || lower.endsWith(".rar") || lower.endsWith(".zst")
}

@Composable
private fun workspaceSortByLabel(sortBy: WorkspaceSortBy): String = when (sortBy) {
    WorkspaceSortBy.NAME -> stringResource(R.string.workspace_detail_sort_name)
    WorkspaceSortBy.DATE -> stringResource(R.string.workspace_detail_sort_date)
    WorkspaceSortBy.SIZE -> stringResource(R.string.workspace_detail_sort_size)
}

@Composable
private fun workspaceSelectedColors() = ListItemDefaults.colors(
    containerColor = MaterialTheme.colorScheme.secondaryContainer,
)

@Composable
private fun WorkspaceOptionSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            content()
        }
    }
}

@Composable
private fun WorkspaceOptionCardGroup(
    content: CardGroupScope.() -> Unit,
) {
    CardGroup(
        modifier = Modifier.fillMaxWidth(),
        itemSpacing = 4.dp,
        innerCorner = 12.dp,
        content = content,
    )
}

@Composable
private fun SearchBottomSheet(
    initialQuery: String,
    initialRecursive: Boolean,
    onDismiss: () -> Unit,
    onSearch: (String, Boolean) -> Unit,
    onClear: () -> Unit,
) {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var recursive by rememberSaveable(initialRecursive) { mutableStateOf(initialRecursive) }
    val selectedColors = workspaceSelectedColors()
    WorkspaceOptionSheet(
        title = stringResource(R.string.workspace_detail_search),
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.workspace_detail_search_hint)) },
            leadingIcon = { Icon(HugeIcons.Search01, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        WorkspaceOptionCardGroup {
            item(
                onClick = { recursive = !recursive },
                trailingContent = {
                    RadioButton(
                        selected = recursive,
                        onClick = null,
                    )
                },
                colors = if (recursive) selectedColors else null,
                headlineContent = { Text(stringResource(R.string.workspace_detail_search_subdirectories)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (query.isNotBlank()) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.workspace_detail_clear))
                }
            }
            Button(
                onClick = { onSearch(query.trim(), recursive) },
                enabled = query.isNotBlank(),
            ) {
                Text(stringResource(R.string.workspace_detail_search))
            }
        }
    }
}

@Composable
private fun SortBottomSheet(
    current: WorkspaceSortBy,
    ascending: Boolean,
    onDismiss: () -> Unit,
    onSelect: (WorkspaceSortBy) -> Unit,
    onToggleOrder: () -> Unit,
) {
    val options = listOf(
        WorkspaceSortBy.NAME to workspaceSortByLabel(WorkspaceSortBy.NAME),
        WorkspaceSortBy.DATE to workspaceSortByLabel(WorkspaceSortBy.DATE),
        WorkspaceSortBy.SIZE to workspaceSortByLabel(WorkspaceSortBy.SIZE),
    )
    val selectedColors = workspaceSelectedColors()
    WorkspaceOptionSheet(
        title = stringResource(R.string.workspace_detail_sort_by),
        onDismiss = onDismiss,
    ) {
        WorkspaceOptionCardGroup {
            item(
                onClick = onToggleOrder,
                leadingContent = {
                    Icon(
                        imageVector = if (ascending) HugeIcons.ArrowUp02 else HugeIcons.ArrowDown02,
                        contentDescription = null,
                    )
                },
                headlineContent = { Text(stringResource(R.string.workspace_detail_sort_order)) },
                supportingContent = {
                    Text(
                        text = stringResource(
                            if (ascending) R.string.workspace_detail_sort_ascending
                            else R.string.workspace_detail_sort_descending
                        ),
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                    )
                },
            )
        }
        WorkspaceOptionCardGroup {
            options.forEach { (sortBy, label) ->
                val selected = sortBy == current
                item(
                    onClick = { onSelect(sortBy) },
                    trailingContent = {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                        )
                    },
                    colors = if (selected) selectedColors else null,
                    headlineContent = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun PropertiesDialog(
    entry: WorkspaceFileEntry,
    permission: String,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val parent = entry.path.substringBeforeLast('/', "").ifBlank { "/" }
    val rows = listOf(
        stringResource(R.string.workspace_detail_properties_name) to entry.name,
        stringResource(R.string.workspace_detail_properties_parent) to parent,
        stringResource(R.string.workspace_detail_properties_size) to formatBytes(entry.sizeBytes),
        stringResource(R.string.workspace_detail_properties_modified) to formatFullDateTime(entry.updatedAt),
        stringResource(R.string.workspace_detail_properties_permission) to permission,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_detail_properties)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rows.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
                                }
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

private fun formatDateTime(epochMillis: Long): String {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(epochMillis))
}

private fun formatFullDateTime(epochMillis: Long): String {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(epochMillis))
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}

@Composable
internal fun String.toShellStatusLabel(): String = when (this) {
    WorkspaceShellStatus.DISABLED.name -> stringResource(R.string.workspace_detail_shell_disabled)
    WorkspaceShellStatus.INSTALLING.name -> stringResource(R.string.workspace_detail_shell_installing)
    WorkspaceShellStatus.READY.name -> stringResource(R.string.workspace_detail_shell_ready)
    WorkspaceShellStatus.BROKEN.name -> stringResource(R.string.workspace_detail_shell_broken)
    else -> lowercase()
}
