package me.rerere.rikkahub.ui.pages.setting

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FloppyDisk
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.provider.CodexRotationMode
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.PoolableProvider
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.SiliconFlowPowerByIcon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomBodies
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomHeaders
import me.rerere.rikkahub.ui.pages.setting.components.CodexAccountsSection
import me.rerere.rikkahub.ui.pages.setting.components.ModelList
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import me.rerere.rikkahub.ui.pages.setting.components.ProviderPoolSection
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConnectionTester
import me.rerere.rikkahub.ui.pages.setting.components.SettingProviderBalanceOption
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

@Composable
internal fun BouncingTabButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    ToggleButton(
        checked = checked,
        onCheckedChange = { value ->
            if (!checked) {
                scope.launch {
                    scale.snapTo(0.84f)
                    scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 900f))
                }
            }
            onCheckedChange(value)
        },
        modifier = modifier.graphicsLayer { scaleX = scale.value },
        shapes = ToggleButtonShapes(shape = CircleShape, pressedShape = CircleShape, checkedShape = CircleShape),
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        content = { label() },
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingProviderDetailPage(id: Uuid, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val provider = settings.providers.find { it.id == id } ?: return
    var internalProvider by remember(provider) { mutableStateOf(provider) }
    val currentProvider = internalProvider
    val showAccounts = if (currentProvider is ProviderSetting.Codex) {
        currentProvider.rotation != CodexRotationMode.OFF
    } else {
        currentProvider is PoolableProvider && currentProvider.poolEnabled
    }
    val pages = providerPages(currentProvider, showAccounts)
    val pageCount = pages.size
    val pager = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    val pillScrollState = rememberScrollState()
    val pillPositions = remember(pageCount) {
        mutableStateListOf<Float>().apply { repeat(pageCount) { add(0f) } }
    }
    val pillWidths = remember(pageCount) {
        mutableStateListOf<Int>().apply { repeat(pageCount) { add(0) } }
    }
    var pillRowWidth by remember { mutableStateOf(0) }

    val onEdit = { newProvider: ProviderSetting ->
        val newSettings = settings.copy(
            providers = settings.providers.map {
                if (newProvider.id == it.id) {
                    newProvider
                } else {
                    it
                }
            }
        )
        vm.updateSettings(newSettings)
    }
    val onDelete = {
        vm.deleteProvider(provider)
        navController.popBackStack()
    }

    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    BackButton()
                },
                colors = CustomColors.topBarColors,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AutoAIIcon(provider.name, modifier = Modifier.size(22.dp))
                        Text(text = provider.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    Switch(
                        checked = currentProvider.enabled,
                        onCheckedChange = { enabled ->
                            onEdit(
                                when (val p = currentProvider) {
                                    is ProviderSetting.OpenAI -> p.copy(enabled = enabled)
                                    is ProviderSetting.Google -> p.copy(enabled = enabled)
                                    is ProviderSetting.Claude -> p.copy(enabled = enabled)
                                    is ProviderSetting.Codex -> p.copy(enabled = enabled)
                                }
                            )
                        },
                    )
                }
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .consumeWindowInsets(it)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { pillRowWidth = it.size.width }
                    .horizontalScroll(pillScrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                LaunchedEffect(pager.currentPage, pillPositions.size, pillScrollState.maxValue, pillRowWidth) {
                    val index = pager.currentPage
                    val position = pillPositions.getOrNull(index) ?: return@LaunchedEffect
                    val width = pillWidths.getOrNull(index) ?: return@LaunchedEffect
                    val viewportCenter = pillRowWidth / 2f
                    val centered = position - (viewportCenter - width / 2f)
                    val clamped = centered.coerceIn(0f, pillScrollState.maxValue.toFloat())
                    pillScrollState.animateScrollTo(clamped.roundToInt())
                }
                BouncingTabButton(
                    checked = pager.currentPage == 0,
                    onCheckedChange = { scope.launch { pager.animateScrollToPage(0) } },
                    modifier = Modifier
                        .onGloballyPositioned {
                            pillPositions[0] = it.positionInParent().x.toFloat()
                            pillWidths[0] = it.size.width
                        }
                        .padding(end = 8.dp),
                ) {
                    Text(stringResource(R.string.setting_provider_page_configuration))
                }
                BouncingTabButton(
                    checked = pager.currentPage == 1,
                    onCheckedChange = { scope.launch { pager.animateScrollToPage(1) } },
                    modifier = Modifier
                        .onGloballyPositioned {
                            pillPositions[1] = it.positionInParent().x.toFloat()
                            pillWidths[1] = it.size.width
                        }
                        .padding(end = 8.dp),
                ) {
                    Text(stringResource(R.string.setting_provider_page_models))
                }
                val accountsIndex = pages.indexOf(ProviderPage.ACCOUNTS)
                AnimatedVisibility(
                    visible = showAccounts,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut(),
                ) {
                    BouncingTabButton(
                        checked = pager.currentPage == accountsIndex,
                        onCheckedChange = { scope.launch { pager.animateScrollToPage(accountsIndex) } },
                        modifier = Modifier
                            .onGloballyPositioned {
                                if (accountsIndex >= 0) {
                                    pillPositions[accountsIndex] = it.positionInParent().x.toFloat()
                                    pillWidths[accountsIndex] = it.size.width
                                }
                            }
                            .padding(end = 8.dp),
                    ) {
                        Text("Accounts")
                    }
                }
                BouncingTabButton(
                    checked = pager.currentPage == pages.indexOf(ProviderPage.HEADERS),
                    onCheckedChange = { scope.launch { pager.animateScrollToPage(pages.indexOf(ProviderPage.HEADERS)) } },
                    modifier = Modifier
                        .onGloballyPositioned {
                            pillPositions[pages.indexOf(ProviderPage.HEADERS)] = it.positionInParent().x.toFloat()
                            pillWidths[pages.indexOf(ProviderPage.HEADERS)] = it.size.width
                        }
                        .padding(end = 8.dp),
                ) {
                    Text(stringResource(R.string.assistant_page_custom_headers))
                }
                BouncingTabButton(
                    checked = pager.currentPage == pages.indexOf(ProviderPage.BODIES),
                    onCheckedChange = { scope.launch { pager.animateScrollToPage(pages.indexOf(ProviderPage.BODIES)) } },
                    modifier = Modifier
                        .onGloballyPositioned {
                            pillPositions[pages.indexOf(ProviderPage.BODIES)] = it.positionInParent().x.toFloat()
                            pillWidths[pages.indexOf(ProviderPage.BODIES)] = it.size.width
                        },
                ) {
                    Text(stringResource(R.string.assistant_page_custom_bodies))
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (pages[page]) {
                    ProviderPage.CONFIGURATION -> SettingProviderConfigPage(
                        provider = internalProvider,
                        onEdit = { internalProvider = it },
                        onSave = {
                            onEdit(internalProvider)
                        },
                        onDelete = {
                            onDelete()
                        }
                    )

                    ProviderPage.MODELS -> SettingProviderModelPage(
                        provider = provider,
                        onEdit = onEdit
                    )

                    ProviderPage.ACCOUNTS -> if (internalProvider is ProviderSetting.Codex) {
                        SettingProviderCodexAccountsPage()
                    } else {
                        SettingProviderAccountsPage(
                            provider = internalProvider,
                            onEdit = { internalProvider = it }
                        )
                    }

                    ProviderPage.HEADERS -> SettingProviderHeadersPage(
                        provider = internalProvider,
                        onEdit = { internalProvider = it }
                    )

                    ProviderPage.BODIES -> SettingProviderCustomBodyPage(
                        provider = internalProvider,
                        onEdit = { internalProvider = it }
                    )
                }
            }
        }
    }
}

private enum class ProviderPage {
    CONFIGURATION,
    MODELS,
    ACCOUNTS,
    HEADERS,
    BODIES,
}

private fun providerPages(provider: ProviderSetting, showAccounts: Boolean): List<ProviderPage> =
    buildList {
        add(ProviderPage.CONFIGURATION)
        add(ProviderPage.MODELS)
        if (showAccounts) add(ProviderPage.ACCOUNTS)
        add(ProviderPage.HEADERS)
        add(ProviderPage.BODIES)
    }

@Composable
private fun SettingProviderConfigPage(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var toolbarExpanded by rememberSaveable { mutableStateOf(true) }
    var showSavedPill by remember { mutableStateOf(false) }

    LaunchedEffect(showSavedPill) {
        if (showSavedPill) {
            delay(2000)
            showSavedPill = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .floatingToolbarVerticalNestedScroll(
                    expanded = toolbarExpanded,
                    onExpand = { toolbarExpanded = true },
                    onCollapse = { toolbarExpanded = false },
                )
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        ProviderConfigure(
            provider = provider,
            onEdit = onEdit
        )

        if (provider is ProviderSetting.OpenAI) {
            SettingProviderBalanceOption(
                provider = provider,
                balanceOption = provider.balanceOption,
                onEdit = { onEdit(provider.copyProvider(balanceOption = it)) }
            )
        }

        // 硅基流动图标
        if (provider is ProviderSetting.OpenAI && provider.baseUrl.contains("siliconflow.cn")) {
            SiliconFlowPowerByIcon(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 16.dp)
            )
        }
        }

        HorizontalFloatingToolbar(
            expanded = toolbarExpanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
        ) {
            ProviderConnectionTester(
                internalProvider = provider,
            )
            if (!provider.builtIn) {
                IconButton(
                    onClick = { showDeleteDialog = true },
                ) {
                    Icon(
                        HugeIcons.Delete01,
                        null,
                    )
                }
            }
            Button(onClick = {
                onSave()
                showSavedPill = true
            }) {
                AnimatedContent(targetState = showSavedPill) { saved ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (saved) {
                            Icon(HugeIcons.CheckmarkCircle02, null)
                        } else {
                            Icon(HugeIcons.FloppyDisk, null)
                        }
                        AnimatedVisibility(toolbarExpanded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(
                                        if (saved) {
                                            R.string.setting_provider_page_save_success
                                        } else {
                                            R.string.setting_provider_page_save
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(stringResource(R.string.setting_provider_page_delete_dialog_title))
            },
            text = {
                Text(stringResource(R.string.setting_provider_page_delete_dialog_text))
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        )
    }
}

@Composable
private fun SettingProviderAccountsPage(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit
) {
    ProviderPoolSection(provider, onEdit)
}

@Composable
private fun SettingProviderCodexAccountsPage() {
    CodexAccountsSection()
}

@Composable
private fun ProviderPropertyPage(
    addLabelRes: Int,
    onAdd: () -> Unit,
    content: @Composable LazyItemScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false },
                ),
            contentPadding = PaddingValues(16.dp) + PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                content()
            }
        }
        HorizontalFloatingToolbar(
            expanded = expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
        ) {
            Button(onClick = onAdd) {
                Icon(HugeIcons.Add01, null)
                AnimatedVisibility(expanded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(addLabelRes))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingProviderHeadersPage(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit
) {
    ProviderPropertyPage(
        addLabelRes = R.string.assistant_page_add_header,
        onAdd = {
            onEdit(provider.updateHeaders(provider.customHeaders + CustomHeader("", "")))
        },
    ) {
        CustomHeaders(
            headers = provider.customHeaders,
            onUpdate = { onEdit(provider.updateHeaders(it)) },
            showAddButton = false,
            emptyStateModifier = Modifier.fillParentMaxHeight(0.85f),
        )
    }
}

@Composable
private fun SettingProviderCustomBodyPage(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit
) {
    ProviderPropertyPage(
        addLabelRes = R.string.assistant_page_add_body,
        onAdd = {
            onEdit(
                provider.updateBodies(
                    provider.customBodies + CustomBody("", JsonPrimitive(""))
                )
            )
        },
    ) {
        CustomBodies(
            customBodies = provider.customBodies,
            onUpdate = { onEdit(provider.updateBodies(it)) },
            showAddButton = false,
            emptyStateModifier = Modifier.fillParentMaxHeight(0.85f),
        )
    }
}

@Composable
private fun SettingProviderModelPage(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit
) {
    ModelList(
        providerSetting = provider,
        onUpdateProvider = onEdit
    )
}
