package me.rerere.rikkahub.ui.pages.setting.components

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.InformationCircle
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Search01
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Switch
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelRegistry
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ai.ModelAbilityTag
import me.rerere.rikkahub.ui.components.ai.ModelModalityTag
import me.rerere.rikkahub.ui.components.ai.ModelTypeTag
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ExpressiveEmptyState
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.SwipeDeleteBackground
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomBodies
import me.rerere.rikkahub.ui.pages.setting.BouncingTabButton
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private fun Model.withRegistryMetadata(): Model = copy(
    inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(modelId),
    outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(modelId),
    abilities = ModelRegistry.MODEL_ABILITIES.getData(modelId),
)

@Composable
internal fun ModelList(
    providerSetting: ProviderSetting,
    onUpdateProvider: (ProviderSetting) -> Unit
) {
    val providerManager = koinInject<ProviderManager>()
    val fetchKey = when (providerSetting) {
        is ProviderSetting.OpenAI -> listOf(
            "openai",
            providerSetting.baseUrl,
            providerSetting.apiKey,
            providerSetting.useResponseApi
        )

        is ProviderSetting.Google -> listOf(
            "google",
            providerSetting.baseUrl,
            providerSetting.apiKey
        )

        is ProviderSetting.Claude -> listOf(
            "claude",
            providerSetting.baseUrl,
            providerSetting.apiKey
        )

        is ProviderSetting.Codex -> listOf("codex")
    }
    val modelList by produceState(emptyList(), fetchKey) {
        runCatching {
            value = providerManager.getProviderByType(providerSetting)
                .listModels(providerSetting)
                .sortedBy { it.modelId }
        }.onFailure {
            it.printStackTrace()
        }
    }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onUpdateProvider(providerSetting.moveMove(from.index, to.index))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false },
                ),
            contentPadding = PaddingValues(16.dp) + PaddingValues(bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = lazyListState
        ) {
            // 模型列表
            if (providerSetting.models.isEmpty()) {
                item {
                    ExpressiveEmptyState(
                        shape = MaterialShapes.Cookie12Sided.toShape(),
                        icon = HugeIcons.Package01,
                        title = stringResource(R.string.setting_provider_page_no_models),
                        hint = stringResource(R.string.setting_provider_page_add_models_hint),
                        modifier = Modifier.fillParentMaxHeight(0.85f),
                    )
                }
            } else {
                items(providerSetting.models, key = { it.id }) { item ->
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = item.id
                    ) { isDragging ->
                        ModelCard(
                            model = item,
                            onDelete = {
                                onUpdateProvider(providerSetting.delModel(item))
                            },
                            onEdit = { editedModel ->
                                onUpdateProvider(providerSetting.editModel(editedModel))
                            },
                            showBuiltInTools = providerSetting is ProviderSetting.Google ||
                                (providerSetting is ProviderSetting.OpenAI && providerSetting.useResponseApi),
                            modifier = Modifier
                                .longPressDraggableHandle()
                                .graphicsLayer {
                                    if (isDragging) {
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                    } else {
                                        scaleX = 1f
                                        scaleY = 1f
                                    }
                                },
                        )
                    }
                }
            }
        }
        HorizontalFloatingToolbar(
            expanded = expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
        ) {
            AddModelButton(
                models = modelList,
                selectedModels = providerSetting.models,
                onAddModel = {
                    onUpdateProvider(providerSetting.addModel(it))
                },
                onRemoveModel = {
                    onUpdateProvider(providerSetting.delModel(it))
                },
                expanded = expanded,
                parentProvider = providerSetting,
                onUpdateProvider = onUpdateProvider
            )
        }
    }
}

@Composable
private fun ModelSettingsForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    showBuiltInTools: Boolean = false,
) {
    val pageCount = if (showBuiltInTools) 3 else 2
    val pagerState = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    val pillScrollState = rememberScrollState()
    val pillPositions = remember(pageCount) {
        mutableStateListOf<Float>().apply { repeat(pageCount) { add(0f) } }
    }
    val pillWidths = remember(pageCount) {
        mutableStateListOf<Int>().apply { repeat(pageCount) { add(0) } }
    }
    var pillRowWidth by remember { mutableStateOf(0) }

    fun setModelId(id: String) {
        val inputModality = ModelRegistry.MODEL_INPUT_MODALITIES.getData(id)
        val outputModality = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(id)
        val abilities = ModelRegistry.MODEL_ABILITIES.getData(id)
        onModelChange(
            model.copy(
                modelId = id,
                displayName = id,
                inputModalities = inputModality,
                outputModalities = outputModality,
                abilities = abilities
            )
        )
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { pillRowWidth = it.size.width }
                .horizontalScroll(pillScrollState)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LaunchedEffect(pagerState.currentPage, pillPositions.size, pillScrollState.maxValue, pillRowWidth) {
                val index = pagerState.currentPage
                val position = pillPositions.getOrNull(index) ?: return@LaunchedEffect
                val width = pillWidths.getOrNull(index) ?: return@LaunchedEffect
                val viewportCenter = pillRowWidth / 2f
                val centered = position - (viewportCenter - width / 2f)
                val clamped = centered.coerceIn(0f, pillScrollState.maxValue.toFloat())
                pillScrollState.animateScrollTo(clamped.roundToInt())
            }
            BouncingTabButton(
                checked = pagerState.currentPage == 0,
                onCheckedChange = { scope.launch { pagerState.animateScrollToPage(0) } },
                modifier = Modifier
                    .onGloballyPositioned {
                        pillPositions[0] = it.positionInParent().x.toFloat()
                        pillWidths[0] = it.size.width
                    },
            ) {
                Text(stringResource(R.string.setting_provider_page_basic_settings))
            }
            BouncingTabButton(
                checked = pagerState.currentPage == 1,
                onCheckedChange = { scope.launch { pagerState.animateScrollToPage(1) } },
                modifier = Modifier
                    .onGloballyPositioned {
                        pillPositions[1] = it.positionInParent().x.toFloat()
                        pillWidths[1] = it.size.width
                    },
            ) {
                Text(stringResource(R.string.setting_provider_page_advanced_settings))
            }
            if (showBuiltInTools) {
                BouncingTabButton(
                    checked = pagerState.currentPage == 2,
                    onCheckedChange = { scope.launch { pagerState.animateScrollToPage(2) } },
                    modifier = Modifier
                        .onGloballyPositioned {
                            pillPositions[2] = it.positionInParent().x.toFloat()
                            pillWidths[2] = it.size.width
                        },
                ) {
                    Text(stringResource(R.string.setting_page_built_in_tools))
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> {
                    // 基本设置页面
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp, bottom = 96.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        OutlinedTextField(
                            value = model.modelId,
                            onValueChange = {
                                if (isEdit) {
                                    onModelChange(model.copy(modelId = it.trim()))
                                } else {
                                    setModelId(it.trim())
                                }
                            },
                            label = { Text(stringResource(R.string.setting_provider_page_model_id)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(stringResource(R.string.setting_provider_page_model_id_placeholder))
                            },
                        )

                        OutlinedTextField(
                            value = model.displayName,
                            onValueChange = {
                                onModelChange(model.copy(displayName = it.trim()))
                            },
                            label = { Text(stringResource(R.string.setting_provider_page_model_display_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                if (!isEdit) {
                                    Text(stringResource(R.string.setting_provider_page_model_display_name_placeholder))
                                }
                            }
                        )

                        ModelTypeSelector(
                            selectedType = model.type,
                            onTypeSelected = {
                                onModelChange(model.copy(type = it))
                            }
                        )

                        ModelModalitySelector(
                            model = model,
                            inputModalities = model.inputModalities,
                            onUpdateInputModalities = {
                                onModelChange(model.copy(inputModalities = it))
                            },
                            outputModalities = model.outputModalities,
                            onUpdateOutputModalities = {
                                onModelChange(model.copy(outputModalities = it))
                            }
                        )

                        if (model.type == ModelType.CHAT) {
                            ModelAbilitySelector(
                                abilities = model.abilities,
                                onUpdateAbilities = {
                                    onModelChange(model.copy(abilities = it))
                                }
                            )
                        }
                    }
                }

                1 -> {
                    // 高级设置页面
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CustomBodies(
                            customBodies = model.customBodies,
                            onUpdate = { bodies ->
                                onModelChange(model.copy(customBodies = bodies))
                            }
                        )
                    }
                }

                2 -> {
                    // 内置工具页面
                    if (showBuiltInTools) {
                        BuiltInToolsSettings(
                            tools = model.tools,
                            onUpdateTools = { tools ->
                                onModelChange(model.copy(tools = tools))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddModelButton(
    models: List<Model>,
    selectedModels: List<Model>,
    expanded: Boolean,
    onAddModel: (Model) -> Unit,
    onRemoveModel: (Model) -> Unit,
    parentProvider: ProviderSetting,
    onUpdateProvider: (ProviderSetting) -> Unit
) {
    val dialogState = useEditState<Model> { onAddModel(it) }
    val scope = rememberCoroutineScope()

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelPicker(
            models = models,
            selectedModels = selectedModels,
            onModelSelected = { model ->
                onAddModel(model.withRegistryMetadata())
            },
            onModelDeselected = { model ->
                onRemoveModel(model)
            },
            onAllModelSelected = {
                onUpdateProvider(
                    parentProvider.copyProvider(
                        models = parentProvider.models + it.filter { model ->
                            parentProvider.models.none { existing -> existing.modelId == model.modelId }
                        }.map { model ->
                            model.withRegistryMetadata()
                        }
                    )
                )
            },
            onAllModelDeselected = { filteredModels ->
                onUpdateProvider(
                    parentProvider.copyProvider(
                        models = parentProvider.models.filter { model ->
                            filteredModels.none { filtered -> filtered.modelId == model.modelId }
                        }
                    )
                )
            }
        )

        Button(
            onClick = {
                dialogState.open(Model())
            }
        ) {
            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    HugeIcons.Add01,
                    contentDescription = stringResource(R.string.setting_provider_page_add_model)
                )
                AnimatedVisibility(expanded) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.setting_provider_page_add_new_model),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    if (dialogState.isEditing) {
        dialogState.currentState?.let { modelState ->
            val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
            ModalBottomSheet(
                onDismissRequest = {
                    dialogState.dismiss()
                },
                sheetState = sheetState,
                sheetGesturesEnabled = false,
                dragHandle = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                dialogState.dismiss()
                            }
                        }
                    ) {
                        Icon(HugeIcons.ArrowDown01, null)
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_add_model),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ModelSettingsForm(
                                model = modelState,
                                onModelChange = { dialogState.currentState = it },
                                isEdit = false,
                                showBuiltInTools = parentProvider is ProviderSetting.Google ||
                                    (parentProvider is ProviderSetting.OpenAI && parentProvider.useResponseApi),
                            )
                        }

                        HorizontalFloatingToolbar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = -ScreenOffset),
                        ) {
                            FilledTonalButton(
                                onClick = { dialogState.dismiss() },
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                            Button(
                                onClick = {
                                    if (modelState.modelId.isNotBlank() && modelState.displayName.isNotBlank()) {
                                        dialogState.confirm()
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.setting_provider_page_add))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelPicker(
    models: List<Model>,
    selectedModels: List<Model>,
    onModelSelected: (Model) -> Unit,
    onModelDeselected: (Model) -> Unit,
    onAllModelSelected: (List<Model>) -> Unit,
    onAllModelDeselected: (List<Model>) -> Unit
) {
    var showModal by remember { mutableStateOf(false) }
    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = { showModal = false },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        ) {
            var filterText by remember { mutableStateOf("") }
            val filteredModels = remember(models, filterText) {
                val filterKeywords = filterText.split(" ").filter { it.isNotBlank() }
                models.fastFilter { model ->
                    if (filterKeywords.isEmpty()) {
                        true
                    } else {
                        filterKeywords.all { keyword ->
                            model.modelId.contains(keyword, ignoreCase = true) ||
                                model.displayName.contains(keyword, ignoreCase = true)
                        }
                    }
                }
            }
            val selectedIds = remember(selectedModels) {
                selectedModels.mapTo(mutableSetOf()) { it.modelId }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(8.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 标题栏和添加所有按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val totalModels = filteredModels.size
                    val selectedModelsCount = filteredModels.count { it.modelId in selectedIds }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.setting_provider_page_avaliable_models),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (selectedModelsCount == 0) {
                                "$totalModels"
                            } else {
                                "$selectedModelsCount / $totalModels"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val unselectedCount = filteredModels.size - selectedModelsCount

                    TonalToggleButton(
                        checked = unselectedCount == 0,
                        onCheckedChange = {
                            if (unselectedCount > 0) {
                                onAllModelSelected(filteredModels)
                            } else {
                                onAllModelDeselected(filteredModels)
                            }
                        },
                        shapes = ToggleButtonShapes(
                            shape = CircleShape,
                            pressedShape = CircleShape,
                            checkedShape = CircleShape,
                        ),
                    ) {
                        if (unselectedCount == 0) {
                            Icon(HugeIcons.Tick01, null)
                        } else {
                            Icon(HugeIcons.Add01, null)
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (unselectedCount > 0) stringResource(
                                R.string.setting_provider_page_select_all
                            ) else stringResource(R.string.setting_provider_page_deselect_models)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val segmentedColors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        contentPadding = PaddingValues(8.dp) + PaddingValues(bottom = 96.dp),
                    ) {
                        itemsIndexed(
                            filteredModels,
                            key = { _, model -> model.modelId },
                        ) { index, model ->
                            SegmentedListItem(
                                onClick = {
                                    if (model.modelId in selectedIds) {
                                        onModelDeselected(
                                            selectedModels.firstOrNull { it.modelId == model.modelId }
                                                ?: model
                                        )
                                    } else {
                                        onModelSelected(model)
                                    }
                                },
                                shapes = ListItemDefaults.segmentedShapes(
                                    index = index,
                                    count = filteredModels.size
                                ),
                                colors = segmentedColors,
                                leadingContent = {
                                    AutoAIIcon(
                                        model.modelId,
                                        Modifier.size(32.dp)
                                    )
                                },
                                supportingContent = {
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        val modelMeta = remember(model) {
                                            model.withRegistryMetadata()
                                        }
                                        ModelModalityTag(
                                            model = modelMeta,
                                        )
                                        ModelAbilityTag(
                                            model = modelMeta,
                                        )
                                    }
                                },
                                trailingContent = {
                                    val isSelected = model.modelId in selectedIds
                                    TonalToggleButton(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            if (isSelected) {
                                                onModelDeselected(
                                                    selectedModels.firstOrNull { it.modelId == model.modelId }
                                                        ?: model
                                                )
                                            } else {
                                                onModelSelected(model)
                                            }
                                        },
                                        shapes = ToggleButtonShapes(
                                            shape = CircleShape,
                                            pressedShape = CircleShape,
                                            checkedShape = CircleShape,
                                        ),
                                    ) {
                                        if (isSelected) {
                                            Icon(HugeIcons.Tick01, null)
                                        } else {
                                            Icon(HugeIcons.Add01, null)
                                        }
                                    }
                                },
                            ) {
                                Text(
                                    text = model.modelId,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }
                    val isImeVisible = WindowInsets.isImeVisible
                    val searchWidthFraction by animateFloatAsState(
                        targetValue = if (isImeVisible) 1f else 0.6f,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "searchWidth",
                    )
                    TextField(
                        value = filterText,
                        onValueChange = {
                            filterText = it
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .shadow(6.dp, CircleShape)
                            .fillMaxWidth(searchWidthFraction),
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        leadingIcon = {
                            Icon(HugeIcons.Search01, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.setting_provider_page_filter_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon = if (filterText.isNotEmpty()) {
                            {
                                Surface(
                                    onClick = { filterText = "" },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ) {
                                    Icon(
                                        HugeIcons.Cancel01,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(6.dp),
                                    )
                                }
                            }
                        } else null,
                        singleLine = true,
                    )
                }            }
        }
    }
    BadgedBox(
        badge = {
            if (models.isNotEmpty()) {
                Badge {
                    Text(models.size.toString())
                }
            }
        }
    ) {
        IconButton(
            onClick = {
                showModal = true
            }
        ) {
            Icon(HugeIcons.Package01, null)
        }
    }
}

@Composable
private fun ModelTypeSelector(
    selectedType: ModelType,
    onTypeSelected: (ModelType) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_model_type),
        style = MaterialTheme.typography.titleSmall
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ModelType.entries.forEachIndexed { index, type ->
            val typeWeight by animateFloatAsState(
                targetValue = if (selectedType == type) 1.3f else 1f,
                label = "typeWeight",
            )
            TonalToggleButton(
                checked = selectedType == type,
                onCheckedChange = { onTypeSelected(type) },
                modifier = Modifier.weight(typeWeight),
                colors = ToggleButtonDefaults.tonalToggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    ModelType.entries.size - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(
                    text = stringResource(
                        when (type) {
                            ModelType.CHAT -> R.string.setting_provider_page_chat_model
                            ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                            ModelType.IMAGE -> R.string.setting_provider_page_image_model
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun ModelModalitySelector(
    model: Model,
    inputModalities: List<Modality>,
    onUpdateInputModalities: (List<Modality>) -> Unit,
    outputModalities: List<Modality>,
    onUpdateOutputModalities: (List<Modality>) -> Unit
) {
    if (model.type == ModelType.CHAT) {
        Text(
            stringResource(R.string.setting_provider_page_input_modality),
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            Modality.entries.forEachIndexed { index, modality ->
                TonalToggleButton(
                    checked = modality in inputModalities,
                    onCheckedChange = {
                        if (it) {
                            onUpdateInputModalities(inputModalities + modality)
                        } else {
                            onUpdateInputModalities(inputModalities - modality)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ToggleButtonDefaults.tonalToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        Modality.entries.size - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (modality in inputModalities) {
                            Icon(
                                HugeIcons.Tick01,
                                null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            text = stringResource(
                                when (modality) {
                                    Modality.TEXT -> R.string.setting_provider_page_text
                                    Modality.IMAGE -> R.string.setting_provider_page_image
                                }
                            )
                        )
                    }
                }
            }
        }

        Text(
            stringResource(R.string.setting_provider_page_output_modality),
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            Modality.entries.forEachIndexed { index, modality ->
                TonalToggleButton(
                    checked = modality in outputModalities,
                    onCheckedChange = {
                        if (it) {
                            onUpdateOutputModalities(outputModalities + modality)
                        } else {
                            onUpdateOutputModalities(outputModalities - modality)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ToggleButtonDefaults.tonalToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        Modality.entries.size - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (modality in outputModalities) {
                            Icon(
                                HugeIcons.Tick01,
                                null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            text = stringResource(
                                when (modality) {
                                    Modality.TEXT -> R.string.setting_provider_page_text
                                    Modality.IMAGE -> R.string.setting_provider_page_image
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelAbilitySelector(
    abilities: List<ModelAbility>,
    onUpdateAbilities: (List<ModelAbility>) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_abilities),
        style = MaterialTheme.typography.titleSmall
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ModelAbility.entries.forEachIndexed { index, ability ->
            TonalToggleButton(
                checked = ability in abilities,
                onCheckedChange = {
                    if (it) {
                        onUpdateAbilities(abilities + ability)
                    } else {
                        onUpdateAbilities(abilities - ability)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ToggleButtonDefaults.tonalToggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    ModelAbility.entries.size - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (ability in abilities) {
                        Icon(
                            HugeIcons.Tick01,
                            null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = stringResource(
                            when (ability) {
                                ModelAbility.TOOL -> R.string.setting_provider_page_tool
                                ModelAbility.REASONING -> R.string.setting_provider_page_reasoning
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: Model,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onEdit: (Model) -> Unit,
    showBuiltInTools: Boolean = false,
) {
    val dialogState = useEditState<Model> {
        onEdit(it)
    }
    val swipeToDismissBoxState = rememberSwipeToDismissBoxState()


    if (dialogState.isEditing) {
        dialogState.currentState?.let { editingModel ->
            val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
            ModalBottomSheet(
                onDismissRequest = {
                    dialogState.dismiss()
                },
                sheetState = sheetState,
                sheetGesturesEnabled = false,
                dragHandle = null,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.setting_provider_page_edit_model),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ModelSettingsForm(
                                model = editingModel,
                                onModelChange = { dialogState.currentState = it },
                                isEdit = true,
                                showBuiltInTools = showBuiltInTools,
                            )
                        }

                        HorizontalFloatingToolbar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = -ScreenOffset),
                        ) {
                            FilledTonalButton(
                                onClick = { dialogState.dismiss() },
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                            Button(
                                onClick = {
                                    if (editingModel.displayName.isNotBlank()) {
                                        dialogState.confirm()
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.confirm))
                            }
                        }
                    }
                }
            }
        }
    }

    SwipeToDismissBox(
        state = swipeToDismissBoxState,
        backgroundContent = {
            SwipeDeleteBackground(
                state = swipeToDismissBoxState,
                onDelete = onDelete,
                deleteContentDescription = stringResource(R.string.chat_page_delete),
                cancelContentDescription = stringResource(R.string.cancel),
            )
        },
        enableDismissFromStartToEnd = false,
        gesturesEnabled = true,
        modifier = modifier
    ) {
        CardGroup {
            item(
                onClick = { dialogState.open(model.copy()) },
                leadingContent = {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        AutoAIIcon(
                            name = model.modelId,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                },
                headlineContent = {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ModelTypeTag(model = model)
                        ModelModalityTag(model = model)
                        ModelAbilityTag(model = model)
                    }
                },
            )
        }
    }
}

@Composable
private fun BuiltInToolsSettings(
    tools: Set<BuiltInTools>,
    onUpdateTools: (Set<BuiltInTools>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CardGroup {
            item(
                leadingContent = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.InformationCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.setting_page_built_in_tools),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.setting_page_built_in_tools_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        val availableTools = listOf(
            BuiltInTools.Search to Pair(
                stringResource(R.string.setting_page_built_in_tools_search),
                stringResource(R.string.setting_page_built_in_tools_search_desc)
            ),
            BuiltInTools.UrlContext to Pair(
                stringResource(R.string.setting_page_built_in_tools_url_context),
                stringResource(R.string.setting_page_built_in_tools_url_context_desc)
            ),
            BuiltInTools.ImageGeneration to Pair(
                stringResource(R.string.setting_page_built_in_tools_image_generation),
                stringResource(R.string.setting_page_built_in_tools_image_generation_desc)
            )
        )

        val toolItemColors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )

        CardGroup {
            availableTools.forEach { (tool, info) ->
                val (title, description) = info
                item(
                    onClick = {
                        if (tool in tools) {
                            onUpdateTools(tools - tool)
                        } else {
                            onUpdateTools(tools + tool)
                        }
                    },
                    headlineContent = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall
                        )
                    },
                    supportingContent = {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = tool in tools,
                            onCheckedChange = null,
                        )
                    },
                    colors = toolItemColors,
                )
            }
        }
    }
}
