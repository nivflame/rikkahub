package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantLocalToolPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_local_tools))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantLocalToolContent(
            modifier = Modifier.padding(innerPadding),
            assistant = assistant,
            onUpdate = { vm.update(it) }
        )
    }
}

@Composable
private fun AssistantLocalToolContent(
    modifier: Modifier = Modifier,
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    fun toggleLocalTool(option: LocalToolOption, enabled: Boolean) {
        val newLocalTools = if (enabled) {
            assistant.localTools + option
        } else {
            assistant.localTools - option
        }
        onUpdate(assistant.copy(localTools = newLocalTools))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardGroup {
            item(
                headlineContent = {
                    Text("Subagent")
                },
                supportingContent = {
                    Text("Let the assistant delegate multi-step tasks to background subagents")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Subagent),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Subagent, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text("Bash")
                },
                supportingContent = {
                    Text("Run shell commands in the workspace (requires workspace)")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Bash),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Bash, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text("Read")
                },
                supportingContent = {
                    Text("Read file contents in the workspace (requires workspace)")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Read),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Read, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text("Write")
                },
                supportingContent = {
                    Text("Create new files in the workspace (requires workspace)")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Write),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Write, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text("Edit")
                },
                supportingContent = {
                    Text("Make precise edits to existing files (requires workspace)")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Edit),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Edit, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text("Skill")
                },
                supportingContent = {
                    Text("Let the assistant load installed skills on demand")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Skill),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Skill, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_ask_user_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_ask_user_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.AskQuestion),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.AskQuestion, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text("WebSearch")
                },
                supportingContent = {
                    Text("Search the web")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.WebSearch),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.WebSearch, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text("WebFetch")
                },
                supportingContent = {
                    Text("Fetch and read content from a URL")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.WebFetch),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.WebFetch, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_browser_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_browser_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Browser),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Browser, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text("ToolSearch")
                },
                supportingContent = {
                    Text("Defer selected tools so the agent fetches their schemas on demand, saving context tokens")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.ToolSearch),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.ToolSearch, it) }
                    )
                }
            )
        }
    }
}
