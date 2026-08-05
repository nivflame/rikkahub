package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.KeepScreenOn
import me.rerere.rikkahub.ui.components.ui.RabbitLoadingIndicator

@Composable
fun CompressContextDialog(
    isCompressing: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int) -> Job,
    autoCompressEnabled: Boolean = false,
    autoCompressTokenThreshold: Int = 300000,
    autoCompressKeepPercentage: Int = 50,
    onUpdateAutoCompressSettings: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
) {
    var additionalPrompt by remember { mutableStateOf("") }
    var selectedTokens by remember { mutableIntStateOf(4000) }
    var keepRecentMessages by remember { mutableIntStateOf(32) }
    val tokenOptions = listOf(4000, 8000, 16000, 32000)
    val keepRecentOptions = listOf(0, 8, 16, 32, 64)
    var currentJob by remember { mutableStateOf<Job?>(null) }
    val isLoading = isCompressing || currentJob?.isActive == true

    // Monitor job completion (only for locally started jobs)
    LaunchedEffect(currentJob) {
        if (currentJob != null) {
            currentJob?.join()
            if (currentJob?.isCompleted == true && currentJob?.isCancelled == false) {
                onDismiss()
            }
            currentJob = null
        }
    }

    // Monitor external compressing state (survives activity recreation)
    var wasCompressing by remember { mutableStateOf(false) }
    LaunchedEffect(isCompressing) {
        if (isCompressing) {
            wasCompressing = true
        } else if (wasCompressing) {
            wasCompressing = false
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) {
                onDismiss()
            }
        },
        title = {
            Text(stringResource(R.string.chat_page_compress_context_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) {
                    KeepScreenOn()
                    // Loading state
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RabbitLoadingIndicator(
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.chat_page_compressing))
                    }
                } else {
                    Text(stringResource(R.string.chat_page_compress_context_desc))

                    // Auto Compression toggle at top
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Auto Compression",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = autoCompressEnabled,
                            onCheckedChange = { onUpdateAutoCompressSettings(it, autoCompressTokenThreshold, autoCompressKeepPercentage) },
                        )
                    }

                    if (autoCompressEnabled) {
                        Text(
                            text = "Token Threshold",
                            style = MaterialTheme.typography.labelMedium
                        )
                        val thresholdRange = 100000f..500000f
                        var thresholdValue by remember(autoCompressTokenThreshold) {
                            mutableFloatStateOf(autoCompressTokenThreshold.toFloat().coerceIn(thresholdRange))
                        }
                        Text("${(thresholdValue / 1000f).roundToInt()}K")
                        Slider(
                            value = thresholdValue,
                            onValueChange = {
                                thresholdValue = it
                                onUpdateAutoCompressSettings(autoCompressEnabled, it.roundToInt(), autoCompressKeepPercentage)
                            },
                            valueRange = thresholdRange,
                            steps = 7,
                        )
                        Text(
                            text = "Keep Recent Percentage",
                            style = MaterialTheme.typography.labelMedium
                        )
                        val percentageOptions = listOf(25, 50, 75)
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            percentageOptions.forEachIndexed { index, pct ->
                                SegmentedButton(
                                    selected = autoCompressKeepPercentage == pct,
                                    onClick = { onUpdateAutoCompressSettings(autoCompressEnabled, autoCompressTokenThreshold, pct) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = percentageOptions.size
                                    ),
                                    modifier = Modifier.width(56.dp),
                                ) {
                                    Text("$pct%")
                                }
                            }
                        }
                        Text(
                            text = "Automatically compresses context during generation when prompt tokens exceed the threshold. Keeps the specified percentage of recent messages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        HorizontalDivider(modifier = Modifier.fillMaxWidth())

                        // Additional context input (still useful for auto-compress)
                        OutlinedTextField(
                            value = additionalPrompt,
                            onValueChange = { additionalPrompt = it },
                            label = {
                                Text(stringResource(R.string.chat_page_compress_additional_prompt))
                            },
                            placeholder = {
                                Text(stringResource(R.string.chat_page_compress_additional_prompt_hint))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                        )

                        // Warning text
                        Text(
                            text = stringResource(R.string.chat_page_compress_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        // Token size selector
                        Text(
                            text = stringResource(R.string.chat_page_compress_target_tokens),
                            style = MaterialTheme.typography.labelMedium
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tokenOptions.forEachIndexed { index, tokens ->
                                SegmentedButton(
                                    selected = selectedTokens == tokens,
                                    onClick = { selectedTokens = tokens },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = tokenOptions.size
                                    )
                                ) {
                                    Text("$tokens")
                                }
                            }
                        }

                        // Keep recent messages selector
                        Text(
                            text = stringResource(R.string.chat_page_compress_keep_recent),
                            style = MaterialTheme.typography.labelMedium
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            keepRecentOptions.forEachIndexed { index, count ->
                                SegmentedButton(
                                    selected = keepRecentMessages == count,
                                    onClick = { keepRecentMessages = count },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = keepRecentOptions.size
                                    )
                                ) {
                                    Text("$count")
                                }
                            }
                        }

                        // Additional context input
                        OutlinedTextField(
                            value = additionalPrompt,
                            onValueChange = { additionalPrompt = it },
                            label = {
                                Text(stringResource(R.string.chat_page_compress_additional_prompt))
                            },
                            placeholder = {
                                Text(stringResource(R.string.chat_page_compress_additional_prompt_hint))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                        )

                        // Warning text
                        Text(
                            text = stringResource(R.string.chat_page_compress_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                }
            }
        },
        confirmButton = {
            if (isLoading) {
                TextButton(onClick = {
                    currentJob?.cancel()
                    currentJob = null
                    onDismiss()
                }) {
                    Text(stringResource(R.string.cancel))
                }
            } else if (!autoCompressEnabled) {
                TextButton(onClick = {
                    currentJob = onConfirm(additionalPrompt, selectedTokens, keepRecentMessages)
                }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        },
        dismissButton = {
            if (!isLoading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
