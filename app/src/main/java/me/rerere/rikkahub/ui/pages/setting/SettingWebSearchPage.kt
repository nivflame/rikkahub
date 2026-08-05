package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingWebSearchPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val engines = listOf("google" to "Google", "brave" to "Brave")

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("WebSearch") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = CustomColors.listItemColors.containerColor
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Search Engine",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Select which search engine the WebSearch tool uses to scrape results via WebView",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        engines.forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = engines.size
                                ),
                                onClick = {
                                    vm.updateSettings(settings.copy(webSearchEngine = value))
                                },
                                selected = settings.webSearchEngine == value
                            ) {
                                Text(label)
                            }
                        }
                    }

                    FormItem(
                        label = {
                            Text("Result Count")
                        },
                        description = {
                            Text("How many search results to return")
                        }
                    ) {
                        val resultRange = 5f..50f
                        var localCount by remember(settings.webSearchResultCount) {
                            mutableFloatStateOf(settings.webSearchResultCount.toFloat())
                        }
                        Text("${localCount.toInt()} results")
                        Slider(
                            value = localCount,
                            onValueChange = {
                                localCount = it
                                vm.updateSettings(settings.copy(webSearchResultCount = it.toInt()))
                            },
                            valueRange = resultRange,
                            steps = 8,
                        )
                    }

                    FormItem(
                        label = {
                            Text("Search Delay (seconds)")
                        },
                        description = {
                            Text("Delay between consecutive WebSearch calls to avoid rate limiting. 0 = no delay.")
                        }
                    ) {
                        var localDelay by remember(settings.webSearchDelay) {
                            mutableFloatStateOf(settings.webSearchDelay.toFloat())
                        }
                        Text("${localDelay.toInt()} s")
                        Slider(
                            value = localDelay,
                            onValueChange = {
                                localDelay = it
                                vm.updateSettings(settings.copy(webSearchDelay = it.toInt()))
                            },
                            valueRange = 0f..10f,
                            steps = 9,
                        )
                    }
                }
            }
        }
    }
}
