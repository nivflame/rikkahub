package me.rerere.rikkahub.ui.pages.setting.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import me.rerere.ai.provider.ClaudePromptCacheTtl
import me.rerere.ai.provider.PoolAccount
import me.rerere.ai.provider.PoolableProvider
import me.rerere.ai.provider.ProviderSetting
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.ExpressiveEmptyState
import me.rerere.rikkahub.data.ai.formatPoolCooldown
import me.rerere.rikkahub.data.ai.nextAccountName
import me.rerere.rikkahub.utils.writeClipboardText
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.UserMultiple02
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass

@Composable
fun ProviderConfigure(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    onEdit: (provider: ProviderSetting) -> Unit,
    showTypeSelector: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        if (showTypeSelector && !provider.builtIn) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ProviderSetting.Types.forEachIndexed { index, type ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ProviderSetting.Types.size
                        ),
                        label = { Text(type.simpleName ?: "") },
                        selected = provider::class == type,
                        onClick = { onEdit(provider.convertTo(type)) }
                    )
                }
            }
        }

        when (provider) {
            is ProviderSetting.OpenAI -> ProviderConfigureOpenAI(provider, onEdit)
            is ProviderSetting.Google -> ProviderConfigureGoogle(provider, onEdit)
            is ProviderSetting.Claude -> ProviderConfigureClaude(provider, onEdit)
            is ProviderSetting.Codex -> CodexProviderConfigure(provider, onEdit)
        }

        if (provider is PoolableProvider) {
            CardGroup {
                toggleItem(
                    label = "Rotate Accounts",
                    checked = provider.poolEnabled,
                    onClick = { onEdit(provider.updatePool(!provider.poolEnabled, provider.poolAccounts)) },
                )
            }
        }
    }
}

private fun ProviderSetting.updatePool(
    enabled: Boolean,
    accounts: List<PoolAccount>,
): ProviderSetting = when (this) {
    is ProviderSetting.OpenAI -> copy(
        poolEnabled = enabled,
        poolAccounts = accounts,
    )

    is ProviderSetting.Google -> copy(
        poolEnabled = enabled,
        poolAccounts = accounts,
    )

    is ProviderSetting.Claude -> copy(
        poolEnabled = enabled,
        poolAccounts = accounts,
    )

    else -> this
}

@Composable
fun ProviderPoolSection(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
) {
    val poolable = provider as? PoolableProvider ?: return
    val poolAccounts = poolable.poolAccounts
    val context = LocalContext.current
    val toaster = LocalToaster.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<PoolAccount?>(null) }
    var deleteTarget by remember { mutableStateOf<PoolAccount?>(null) }
    var expandedMenuId by remember { mutableStateOf<String?>(null) }

    val poolEnabled = poolable.poolEnabled

    var toolbarExpanded by rememberSaveable { mutableStateOf(true) }

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
                .padding(16.dp)
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (poolAccounts.isEmpty()) {
                ExpressiveEmptyState(
                    shape = MaterialShapes.Clover4Leaf.toShape(),
                    icon = HugeIcons.UserMultiple02,
                    title = "No accounts",
                    hint = "Add an account to rotate between them",
                )
            } else {
                CardGroup(
                    title = {
                        Text(text = "Accounts")
                    }
                ) {
                    poolAccounts.forEachIndexed { index, account ->
                        item(
                            onClick = { editTarget = account },
                headlineContent = {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val maskedKey = if (account.apiKey.length > 12) {
                        account.apiKey.take(6) + "..." + account.apiKey.takeLast(4)
                    } else {
                        account.apiKey
                    }
                    Text(
                        text = maskedKey,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetbrainsMono,
                    )
                    PoolAccountCooldown(account) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                trailingContent = {
                    IconButton(onClick = {
                        expandedMenuId = if (expandedMenuId == account.id.toString()) null else account.id.toString()
                    }) {
                        Icon(HugeIcons.MoreVertical, contentDescription = "More")
                        DropdownMenu(
                            expanded = expandedMenuId == account.id.toString(),
                            onDismissRequest = { expandedMenuId = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Move up") },
                                leadingIcon = { Icon(HugeIcons.ArrowUp01, null) },
                                enabled = index > 0,
                                onClick = {
                                    val updated = poolAccounts.toMutableList()
                                    val previous = updated.removeAt(index - 1)
                                    updated.add(index, previous)
                                    onEdit(provider.updatePool(poolEnabled, updated))
                                    expandedMenuId = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move down") },
                                leadingIcon = { Icon(HugeIcons.ArrowDown01, null) },
                                enabled = index < poolAccounts.lastIndex,
                                onClick = {
                                    val updated = poolAccounts.toMutableList()
                                    val next = updated.removeAt(index + 1)
                                    updated.add(index, next)
                                    onEdit(provider.updatePool(poolEnabled, updated))
                                    expandedMenuId = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Copy API key") },
                                leadingIcon = { Icon(HugeIcons.Copy01, null) },
                                onClick = {
                                    context.writeClipboardText(account.apiKey, sensitive = true)
                                    toaster.show("Copied", ToastType.Success)
                                    expandedMenuId = null
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        HugeIcons.Delete01,
                                        null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    deleteTarget = account
                                    expandedMenuId = null
                                },
                            )
                        }
                    }
                },
            )
        }
        }
        }
        }

        HorizontalFloatingToolbar(
            expanded = toolbarExpanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
        ) {
            Button(onClick = { showAddDialog = true }) {
                Icon(HugeIcons.Add01, null)
                AnimatedVisibility(toolbarExpanded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        Text("Add account")
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        PoolAccountDialog(
            account = null,
            accounts = poolAccounts,
            onConfirm = { name, apiKey ->
                onEdit(provider.updatePool(poolEnabled, poolAccounts + PoolAccount(name = name, apiKey = apiKey)))
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    editTarget?.let { account ->
        PoolAccountDialog(
            account = account,
            accounts = poolAccounts,
            onConfirm = { name, apiKey ->
                onEdit(
                    provider.updatePool(
                        enabled = poolEnabled,
                        accounts = poolAccounts.map {
                            if (it.id == account.id) it.copy(name = name, apiKey = apiKey) else it
                        }
                    )
                )
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }

    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this account?") },
            text = { Text("This account will be removed from rotation. The API key cannot be recovered.") },
            confirmButton = {
                Button(
                    onClick = {
                        onEdit(provider.updatePool(poolEnabled, poolAccounts - account))
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PoolAccountCooldown(
    account: PoolAccount,
    content: @Composable (String) -> Unit,
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(account.rateLimitedUntil) {
        while (account.rateLimitedUntil > System.currentTimeMillis()) {
            delay(30_000)
            nowMillis = System.currentTimeMillis()
        }
        nowMillis = System.currentTimeMillis()
    }
    val remainingMillis = account.rateLimitedUntil - nowMillis
    if (remainingMillis > 0) {
        content(
            buildString {
                append("Rate limited")
                account.rateLimitedModel?.let { append(" · $it") }
                append(" · ")
                append(formatPoolCooldown(remainingMillis))
                append(" left")
            }
        )
    }
}

@Composable
private fun PoolAccountDialog(
    account: PoolAccount?,
    accounts: List<PoolAccount>,
    onConfirm: (name: String, apiKey: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(account?.id) { mutableStateOf(account?.name.orEmpty()) }
    var apiKey by remember(account?.id) { mutableStateOf(account?.apiKey.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "Add Account" else "Edit Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.setting_provider_page_name) + " (optional)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.setting_provider_page_api_key)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name.trim().ifBlank { nextAccountName(accounts, account?.id) },
                        apiKey.trim(),
                    )
                },
                enabled = apiKey.isNotBlank(),
            ) {
                Text(if (account == null) stringResource(R.string.setting_provider_page_add) else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

fun ProviderSetting.convertTo(type: KClass<out ProviderSetting>): ProviderSetting {
    if (this::class == type) return this

    val apiKey = when (this) {
        is ProviderSetting.OpenAI -> this.apiKey
        is ProviderSetting.Google -> this.apiKey
        is ProviderSetting.Claude -> this.apiKey
        is ProviderSetting.Codex -> ""
    }
    val sourceBaseUrl = when (this) {
        is ProviderSetting.OpenAI -> this.baseUrl
        is ProviderSetting.Google -> this.baseUrl
        is ProviderSetting.Claude -> this.baseUrl
        is ProviderSetting.Codex -> ""
    }
    val targetDefaultBaseUrl = when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI().baseUrl
        ProviderSetting.Google::class -> ProviderSetting.Google().baseUrl
        ProviderSetting.Claude::class -> ProviderSetting.Claude().baseUrl
        else -> error("Unsupported provider type: $type")
    }
    val convertedBaseUrl = sourceBaseUrl.convertToTargetBaseUrl(targetDefaultBaseUrl)

    return when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            balanceOption = this.balanceOption, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        ProviderSetting.Google::class -> ProviderSetting.Google(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            balanceOption = this.balanceOption, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        ProviderSetting.Claude::class -> ProviderSetting.Claude(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            balanceOption = this.balanceOption, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        else -> error("Unsupported provider type: $type")
    }
}

internal fun ProviderSetting.defaultBaseUrlForReset(): String {
    return when (this) {
        is ProviderSetting.OpenAI -> ProviderSetting.OpenAI().baseUrl
        is ProviderSetting.Google -> ProviderSetting.Google().baseUrl
        is ProviderSetting.Claude -> ProviderSetting.Claude().baseUrl
        is ProviderSetting.Codex -> ""
    }
}

internal fun ProviderSetting.isUsingDefaultBaseUrl(): Boolean {
    if (this is ProviderSetting.Codex) return true
    val baseUrl = when (this) {
        is ProviderSetting.OpenAI -> this.baseUrl
        is ProviderSetting.Google -> this.baseUrl
        is ProviderSetting.Claude -> this.baseUrl
        is ProviderSetting.Codex -> ""
    }
    return baseUrl == defaultBaseUrlForReset()
}

private fun String.convertToTargetBaseUrl(targetDefaultBaseUrl: String): String {
    val sourceUrl = this.toHttpUrlOrNull() ?: return this
    val sourceHost = sourceUrl.host.lowercase()
    if (sourceHost in OFFICIAL_PROVIDER_HOSTS) return targetDefaultBaseUrl
    val targetUrl = targetDefaultBaseUrl.toHttpUrlOrNull() ?: return this
    val convertedPath = sourceUrl.encodedPath.convertToTargetPath(targetUrl.encodedPath)
    return sourceUrl.newBuilder().encodedPath(convertedPath).build().toString()
}

private fun String.convertToTargetPath(targetPath: String): String {
    val source = this.normalizePath()
    val target = targetPath.normalizePath()
    val replaced = when {
        source.lowercase().endsWith(V1_BETA_SUFFIX) -> source.dropLast(V1_BETA_SUFFIX.length) + target
        source.lowercase().endsWith(V1_SUFFIX) -> source.dropLast(V1_SUFFIX.length) + target
        source.isBlank() -> target
        else -> source + target
    }
    return replaced.normalizePath()
}

private fun String.normalizePath(): String {
    val value = this.trim()
    if (value.isEmpty() || value == "/") return ""
    val path = if (value.startsWith("/")) value else "/$value"
    return path.trimEnd('/')
}

private fun String.isValidBaseUrl(): Boolean = this.toHttpUrlOrNull() != null

private const val OPENAI_OFFICIAL_HOST = "api.openai.com"
private const val GOOGLE_OFFICIAL_HOST = "generativelanguage.googleapis.com"
private const val CLAUDE_OFFICIAL_HOST = "api.anthropic.com"
private const val V1_SUFFIX = "/v1"
private const val V1_BETA_SUFFIX = "/v1beta"
private val OFFICIAL_PROVIDER_HOSTS = setOf(
    OPENAI_OFFICIAL_HOST,
    GOOGLE_OFFICIAL_HOST,
    CLAUDE_OFFICIAL_HOST
)

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProviderConfigureOpenAI(
    provider: ProviderSetting.OpenAI,
    onEdit: (provider: ProviderSetting.OpenAI) -> Unit
) {
    val toaster = LocalToaster.current

    provider.description()

    val enableLabel = stringResource(R.string.setting_provider_page_enable)

    CardGroup {
        toggleItem(
            label = enableLabel,
            checked = provider.enabled,
            onClick = { onEdit(provider.copy(enabled = !provider.enabled)) },
        )
    }

    val nameLabel = stringResource(R.string.setting_provider_page_name)
    val apiKeyLabel = stringResource(R.string.setting_provider_page_api_key)
    val baseUrlLabel = stringResource(R.string.setting_provider_page_api_base_url)
    val apiPathLabel = stringResource(R.string.setting_provider_page_api_path)
    var keyVisible by remember { mutableStateOf(false) }

    CardGroup {
        item(
            headlineContent = { FieldLabel(nameLabel) },
            supportingContent = {
                OutlinedTextField(
                    value = provider.name,
                    onValueChange = { onEdit(provider.copy(name = it.trim())) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = nameLabel },
                )
            },
        )
        apiKeyItem(
            label = apiKeyLabel,
            value = provider.apiKey,
            readOnly = provider.poolEnabled,
            keyVisible = keyVisible,
            onKeyVisibilityChange = { keyVisible = it },
            onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
        )
        item(
            headlineContent = { FieldLabel(baseUrlLabel) },
            supportingContent = {
                OutlinedTextField(
                    value = provider.baseUrl,
                    onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = baseUrlLabel },
                    isError = provider.baseUrl.isNotBlank() && !provider.baseUrl.isValidBaseUrl(),
                    trailingIcon = if (!provider.isUsingDefaultBaseUrl()) {
                        {
                            IconButton(
                                onClick = { onEdit(provider.copy(baseUrl = provider.defaultBaseUrlForReset())) },
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Refresh03,
                                    contentDescription = stringResource(R.string.setting_model_page_reset_to_default),
                                )
                            }
                        }
                    } else null,
                )
            },
        )
        item(
            headlineContent = { FieldLabel(apiPathLabel) },
            supportingContent = {
                OutlinedTextField(
                    value = if (provider.useResponseApi) "/responses" else provider.chatCompletionsPath,
                    onValueChange = { onEdit(provider.copy(chatCompletionsPath = it.trim())) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = apiPathLabel },
                    enabled = !provider.builtIn,
                    readOnly = provider.useResponseApi,
                )
            },
        )
    }

    val responseAPIWarning = stringResource(R.string.setting_provider_page_response_api_warning)
    val responseAPILabel = stringResource(R.string.setting_provider_page_response_api)
    val includeHistoryReasoningLabel = stringResource(R.string.setting_provider_page_include_history_reasoning)
    CardGroup {
        toggleItem(
            label = responseAPILabel,
            checked = provider.useResponseApi,
            onClick = {
                val newValue = !provider.useResponseApi
                onEdit(provider.copy(useResponseApi = newValue))
                if (newValue && provider.baseUrl.toHttpUrlOrNull()?.host != "api.openai.com") {
                    toaster.show(message = responseAPIWarning, type = ToastType.Warning)
                }
            },
        )
        toggleItem(
            label = includeHistoryReasoningLabel,
            checked = provider.includeHistoryReasoning,
            onClick = { onEdit(provider.copy(includeHistoryReasoning = !provider.includeHistoryReasoning)) },
        )
        toggleItem(
            label = "Auto Retry",
            checked = provider.autoRetry,
            onClick = { onEdit(provider.copy(autoRetry = !provider.autoRetry)) },
        )
    }
}

@Composable
private fun ProviderConfigureClaude(
    provider: ProviderSetting.Claude,
    onEdit: (provider: ProviderSetting.Claude) -> Unit
) {
    provider.description()

    val enableLabel = stringResource(R.string.setting_provider_page_enable)

    CardGroup {
        toggleItem(
            label = enableLabel,
            checked = provider.enabled,
            onClick = { onEdit(provider.copy(enabled = !provider.enabled)) },
        )
    }

    val nameLabel = stringResource(R.string.setting_provider_page_name)
    val apiKeyLabel = stringResource(R.string.setting_provider_page_api_key)
    val baseUrlLabel = stringResource(R.string.setting_provider_page_api_base_url)
    var keyVisible by remember { mutableStateOf(false) }

    CardGroup {
        item(
            headlineContent = { FieldLabel(nameLabel) },
            supportingContent = {
                OutlinedTextField(
                    value = provider.name,
                    onValueChange = { onEdit(provider.copy(name = it.trim())) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = nameLabel },
                    maxLines = 3,
                )
            },
        )
        apiKeyItem(
            label = apiKeyLabel,
            value = provider.apiKey,
            readOnly = provider.poolEnabled,
            keyVisible = keyVisible,
            onKeyVisibilityChange = { keyVisible = it },
            onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
        )
        item(
            headlineContent = { FieldLabel(baseUrlLabel) },
            supportingContent = {
                OutlinedTextField(
                    value = provider.baseUrl,
                    onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = baseUrlLabel },
                    isError = provider.baseUrl.isNotBlank() && !provider.baseUrl.isValidBaseUrl(),
                    trailingIcon = if (!provider.isUsingDefaultBaseUrl()) {
                        {
                            IconButton(
                                onClick = { onEdit(provider.copy(baseUrl = provider.defaultBaseUrlForReset())) },
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Refresh03,
                                    contentDescription = stringResource(R.string.setting_model_page_reset_to_default),
                                )
                            }
                        }
                    } else null,
                )
            },
        )
    }

    val promptCachingLabel = stringResource(R.string.setting_provider_page_claude_prompt_caching)
    CardGroup {
        toggleItem(
            label = promptCachingLabel,
            checked = provider.promptCaching,
            onClick = { onEdit(provider.copy(promptCaching = !provider.promptCaching)) },
        )
        if (provider.promptCaching) {
            item(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.setting_provider_page_claude_prompt_cache_ttl),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                supportingContent = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
                        ClaudePromptCacheTtl.entries.forEachIndexed { index, ttl ->
                            val segmentWeight by animateFloatAsState(
                                targetValue = if (provider.promptCacheTtl == ttl) 1.5f else 1f,
                                label = "ttlWeight",
                            )
                            TonalToggleButton(
                                checked = provider.promptCacheTtl == ttl,
                                onCheckedChange = { onEdit(provider.copy(promptCacheTtl = ttl)) },
                                modifier = Modifier
                                    .weight(segmentWeight)
                                    .height(48.dp),
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    ClaudePromptCacheTtl.entries.size - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                colors = ToggleButtonDefaults.toggleButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text(
                                    text = stringResource(
                                        when (ttl) {
                                            ClaudePromptCacheTtl.FIVE_MINUTES -> R.string.setting_provider_page_claude_prompt_cache_ttl_5m
                                            ClaudePromptCacheTtl.ONE_HOUR -> R.string.setting_provider_page_claude_prompt_cache_ttl_1h
                                        }
                                    )
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ProviderConfigureGoogle(
    provider: ProviderSetting.Google,
    onEdit: (provider: ProviderSetting.Google) -> Unit
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val currentProvider by rememberUpdatedState(provider)
    val serviceAccountJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }
                if (content == null) return@launch
                val json = Json.parseToJsonElement(content).jsonObject
                onEdit(
                    currentProvider.copy(
                        projectId = json["project_id"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: currentProvider.projectId,
                        serviceAccountEmail = json["client_email"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: currentProvider.serviceAccountEmail,
                        privateKey = json["private_key"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: currentProvider.privateKey,
                    )
                )
                toaster.show("Service account imported", type = ToastType.Success)
            } catch (e: Exception) {
                toaster.show("Failed to import: ${e.message}", type = ToastType.Error)
            }
        }
    }

    provider.description()

    val enableLabel = stringResource(R.string.setting_provider_page_enable)

    CardGroup {
        toggleItem(
            label = enableLabel,
            checked = provider.enabled,
            onClick = { onEdit(provider.copy(enabled = !provider.enabled)) },
        )
    }

    val nameLabel = stringResource(R.string.setting_provider_page_name)
    val apiKeyLabel = stringResource(R.string.setting_provider_page_api_key)
    val baseUrlLabel = stringResource(R.string.setting_provider_page_api_base_url)
    var keyVisible by remember { mutableStateOf(false) }

    CardGroup {
        item(
            headlineContent = { FieldLabel(nameLabel) },
            supportingContent = {
                OutlinedTextField(
                    value = provider.name,
                    onValueChange = { onEdit(provider.copy(name = it.trim())) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = nameLabel },
                )
            },
        )
        if (!(provider.vertexAI && provider.useServiceAccount)) {
            apiKeyItem(
                label = apiKeyLabel,
                value = provider.apiKey,
                readOnly = provider.poolEnabled,
                keyVisible = keyVisible,
                onKeyVisibilityChange = { keyVisible = it },
                onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
            )
        }
        if (!provider.vertexAI) {
            item(
                headlineContent = { FieldLabel(baseUrlLabel) },
                supportingContent = {
                    OutlinedTextField(
                        value = provider.baseUrl,
                        onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = baseUrlLabel },
                        isError = provider.baseUrl.isNotBlank() && (
                            !provider.baseUrl.isValidBaseUrl() || !provider.baseUrl.endsWith("/v1beta")
                            ),
                        supportingText = if (!provider.baseUrl.endsWith("/v1beta")) {
                            { Text("The base URL usually ends with `/v1beta`") }
                        } else null,
                        trailingIcon = if (!provider.isUsingDefaultBaseUrl()) {
                            {
                                IconButton(
                                    onClick = { onEdit(provider.copy(baseUrl = provider.defaultBaseUrlForReset())) },
                                ) {
                                    Icon(
                                        imageVector = HugeIcons.Refresh03,
                                        contentDescription = stringResource(R.string.setting_model_page_reset_to_default),
                                    )
                                }
                            }
                        } else null,
                    )
                },
            )
        }
    }

    val vertexAILabel = stringResource(R.string.setting_provider_page_vertex_ai)
    val useServiceAccountLabel = stringResource(R.string.setting_provider_page_use_service_account)
    CardGroup {
        toggleItem(
            label = vertexAILabel,
            checked = provider.vertexAI,
            onClick = { onEdit(provider.copy(vertexAI = !provider.vertexAI)) },
        )
        if (provider.vertexAI) {
            toggleItem(
                label = useServiceAccountLabel,
                checked = provider.useServiceAccount,
                onClick = { onEdit(provider.copy(useServiceAccount = !provider.useServiceAccount)) },
            )
        }
    }

    if (provider.vertexAI && provider.useServiceAccount) {
        var privateKeyVisible by remember { mutableStateOf(false) }
        val importLabel = stringResource(R.string.setting_provider_page_import_service_account_json)
        val emailLabel = stringResource(R.string.setting_provider_page_service_account_email)
        val privateKeyLabel = stringResource(R.string.setting_provider_page_private_key)
        val locationLabel = stringResource(R.string.setting_provider_page_location)
        val projectIdLabel = stringResource(R.string.setting_provider_page_project_id)

        CardGroup {
            item(
                onClick = { serviceAccountJsonLauncher.launch(arrayOf("application/json", "*/*")) },
                headlineContent = {
                    Text(
                        text = importLabel,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            )
            item(
                headlineContent = { FieldLabel(emailLabel) },
                supportingContent = {
                    OutlinedTextField(
                        value = provider.serviceAccountEmail,
                        onValueChange = { onEdit(provider.copy(serviceAccountEmail = it.trim())) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                },
            )
            item(
                headlineContent = { FieldLabel(privateKeyLabel) },
                supportingContent = {
                    OutlinedTextField(
                        value = provider.privateKey,
                        onValueChange = { onEdit(provider.copy(privateKey = it.trim())) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6,
                        minLines = 3,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetbrainsMono),
                        visualTransformation = if (privateKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            ApiKeyTrailingIcons(privateKeyVisible, { privateKeyVisible = !privateKeyVisible }, provider.privateKey)
                        },
                    )
                },
            )
            item(
                headlineContent = { FieldLabel(locationLabel) },
                supportingContent = {
                    OutlinedTextField(
                        value = provider.location,
                        onValueChange = { onEdit(provider.copy(location = it.trim())) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                },
            )
            item(
                headlineContent = { FieldLabel(projectIdLabel) },
                supportingContent = {
                    OutlinedTextField(
                        value = provider.projectId,
                        onValueChange = { onEdit(provider.copy(projectId = it.trim())) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                },
            )
        }
    }
}

private fun CardGroupScope.apiKeyItem(
    label: String,
    value: String,
    readOnly: Boolean,
    keyVisible: Boolean,
    onKeyVisibilityChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
) {
    item(
        modifier = if (readOnly) {
            Modifier.graphicsLayer { alpha = 0.38f }
        } else Modifier,
        headlineContent = { FieldLabel(label) },
        supportingContent = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label },
                maxLines = 3,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    ApiKeyTrailingIcons(keyVisible, { onKeyVisibilityChange(!keyVisible) }, value)
                },
            )
        },
    )
}

@Composable
private fun ApiKeyTrailingIcons(
    keyVisible: Boolean,
    onToggleVisible: () -> Unit,
    value: String,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    Row {
        IconButton(onClick = onToggleVisible) {
            Icon(if (keyVisible) HugeIcons.ViewOff else HugeIcons.View, contentDescription = null)
        }
        if (value.isNotBlank()) {
            IconButton(
                onClick = {
                    context.writeClipboardText(value, sensitive = true)
                    toaster.show("Copied", ToastType.Success)
                },
            ) {
                Icon(HugeIcons.Copy01, contentDescription = null)
            }
        }
    }
}