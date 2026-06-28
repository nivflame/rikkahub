package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.BubbleChatQuestion
import me.rerere.hugeicons.stroke.Earth
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors

@Composable
fun SettingToolsPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Tools") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        CardGroup(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item(
                onClick = { navController.navigate(Screen.SettingBrowser) },
                leadingContent = { Icon(HugeIcons.Earth, null) },
                supportingContent = { Text("Enable or disable individual browser tools") },
                headlineContent = { Text("Browser") },
            )
            item(
                onClick = { navController.navigate(Screen.SettingSubagent) },
                leadingContent = { Icon(HugeIcons.AiBrain01, null) },
                supportingContent = { Text("Subagent prompts, model, and concurrency") },
                headlineContent = { Text("Subagent") },
            )
            item(
                onClick = { navController.navigate(Screen.SettingAskQuestion) },
                leadingContent = { Icon(HugeIcons.BubbleChatQuestion, null) },
                supportingContent = { Text("Edit the AskQuestion tool description") },
                headlineContent = { Text("AskQuestion") },
            )
        }
    }
}
