package me.rerere.rikkahub.ui.pages.setting.components

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Refresh03
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.ProviderSetting
import me.rerere.common.http.isJsonExprValid
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ai.ProviderBalanceText
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.JetbrainsMono

private val ApiPathRegex = Regex("""^(/|https?://|\{\{baseUrl\}\})[^ \t\n\r]*$""")

@Composable
fun SettingProviderBalanceOption(
    provider: ProviderSetting,
    balanceOption: BalanceOption,
    modifier: Modifier = Modifier,
    onEdit: (BalanceOption) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val defaults = BalanceOption()
    val canReset = balanceOption.apiPath != defaults.apiPath || balanceOption.resultPath != defaults.resultPath
    CardGroup(modifier = modifier) {
        item(
            onClick = { showSheet = true },
            headlineContent = {
                Text(
                    text = stringResource(R.string.setting_provider_page_balance_info),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProviderBalanceText(
                        providerSetting = provider,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Icon(imageVector = HugeIcons.ArrowRight01, contentDescription = null)
                }
            },
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_balance_info),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = balanceOption.enabled,
                        onCheckedChange = { onEdit(balanceOption.copy(enabled = it)) },
                    )
                }
                CardGroup(modifier = Modifier.padding(top = 16.dp)) {
                    item(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.setting_provider_page_balance_api_path),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        supportingContent = {
                            OutlinedTextField(
                                value = balanceOption.apiPath,
                                onValueChange = { onEdit(balanceOption.copy(apiPath = it)) },
                                isError = !balanceOption.apiPath.matches(ApiPathRegex),
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                    )
                    item(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.setting_provider_page_balance_json_key),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        supportingContent = {
                            OutlinedTextField(
                                value = balanceOption.resultPath,
                                onValueChange = { onEdit(balanceOption.copy(resultPath = it)) },
                                isError = !isJsonExprValid(balanceOption.resultPath),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetbrainsMono)
                            )
                        },
                    )
                    item(
                        onClick = { onEdit(BalanceOption(enabled = balanceOption.enabled)) },
                        modifier = if (canReset) Modifier else Modifier.graphicsLayer { alpha = 0.38f },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.setting_model_page_reset_to_default),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        trailingContent = {
                            Icon(imageVector = HugeIcons.Refresh03, contentDescription = null)
                        },
                    )
                }
            }
        }
    }
}
