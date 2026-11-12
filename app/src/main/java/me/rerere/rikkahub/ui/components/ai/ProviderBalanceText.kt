package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.MoneyBag02
import me.rerere.rikkahub.utils.SimpleCache
import me.rerere.rikkahub.utils.toDp
import org.koin.compose.koinInject
import java.util.concurrent.TimeUnit

private val cache = SimpleCache.builder<String, String>()
    .expireAfterWrite(2, TimeUnit.MINUTES)
    .build()

private data class BalanceResult(
    val isSuccess: Boolean,
    val text: String,
)

@Composable
fun ProviderBalanceText(
    providerSetting: ProviderSetting,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    if (!providerSetting.balanceOption.enabled || providerSetting !is ProviderSetting.OpenAI) {
        // Balance option is disabled or provider is not OpenAI type
        return
    }

    val providerManager = koinInject<ProviderManager>()

    val value = produceState(
        initialValue = BalanceResult(isSuccess = true, text = "~"),
        key1 = providerSetting.id,
        key2 = providerSetting.balanceOption
    ) {
        // Check cache first
        val cachedBalance = cache.getIfPresent("${providerSetting.id},${providerSetting.balanceOption.hashCode()}")
        if (cachedBalance != null) {
            value = BalanceResult(isSuccess = true, text = cachedBalance)
        } else {
            // Fetch balance from API
            runCatching {
                val balance = providerManager.getProviderByType(providerSetting).getBalance(providerSetting)
                // Cache the result
                cache.put("${providerSetting.id},${providerSetting.balanceOption.hashCode()}", balance)
                value = BalanceResult(isSuccess = true, text = balance)
            }.onFailure {
                // Handle error, don't cache error messages
                value = BalanceResult(isSuccess = false, text = it.message ?: "Failed to get balance")
            }
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (value.value.isSuccess) HugeIcons.MoneyBag02 else HugeIcons.Alert02,
            contentDescription = null,
            modifier = Modifier.size(style.fontSize.toDp()),
            tint = if (value.value.isSuccess) {
                color.takeOrElse { LocalContentColor.current }
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        Text(
            text = value.value.text,
            style = style,
            maxLines = 1,
            color = if (value.value.isSuccess) color else MaterialTheme.colorScheme.error
        )
    }
}
