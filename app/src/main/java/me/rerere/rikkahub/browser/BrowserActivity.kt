package me.rerere.rikkahub.browser

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.FullScreen
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

private const val HOME_URL = "https://www.google.com"

private sealed interface BrowserStep {
    data class Tool(val name: String) : BrowserStep
    data object Thinking : BrowserStep
    data object Done : BrowserStep
}

private data class BrowserUiState(
    val reply: String = "",
    val steps: List<BrowserStep> = emptyList(),
)

class BrowserActivity : ComponentActivity() {
    private val chatService: ChatService by inject()
    private val settingsStore: SettingsStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BrowserScreen(chatService = chatService, settingsStore = settingsStore)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScreen(chatService: ChatService, settingsStore: SettingsStore) {
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<BrowserController?>(null) }
    var conversationId by remember { mutableStateOf(BrowserSessionState.conversationId) }
    var addressBar by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var showFullReply by remember { mutableStateOf(false) }

    val ui by produceState(initialValue = BrowserUiState(), conversationId) {
        val id = conversationId ?: return@produceState
        chatService.getConversationFlow(id).collect { conversation ->
            val lastAssistant = conversation.currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
            val reply = lastAssistant
                ?.parts?.filterIsInstance<UIMessagePart.Text>()
                ?.joinToString("") { it.text }
                ?: ""
            val steps = lastAssistant?.parts
                ?.mapNotNull { part ->
                    when (part) {
                        is UIMessagePart.Tool -> BrowserStep.Tool(part.toolName)
                        is UIMessagePart.Reasoning -> BrowserStep.Thinking
                        is UIMessagePart.Text -> BrowserStep.Done
                        else -> null
                    }
                }
                ?.takeLast(3)
                ?: emptyList()
            value = BrowserUiState(reply = reply, steps = steps)
        }
    }

    DisposableEffect(controller) {
        HeadlessBrowserSession.setActive(controller)
        onDispose { HeadlessBrowserSession.setActive(null) }
    }

    fun navigate() {
        val raw = addressBar.trim()
        if (raw.isBlank()) return
        val url = if (raw.contains("://")) raw else "https://$raw"
        controller?.webView?.loadUrl(url)
    }

    fun cancelGeneration() {
        val id = conversationId ?: return
        scope.launch { chatService.stopGeneration(id) }
    }

    fun sendPrompt() {
        val text = prompt.trim()
        if (text.isBlank()) return
        scope.launch {
            settingsStore.update { settings ->
                val current = settings.getCurrentAssistant()
                if (LocalToolOption.Browser in current.localTools) {
                    settings
                } else {
                    settings.copy(
                        assistants = settings.assistants.map { assistant ->
                            if (assistant.id == current.id) {
                                assistant.copy(localTools = assistant.localTools + LocalToolOption.Browser)
                            } else assistant
                        }
                    )
                }
            }
            val currentUrl = controller?.currentUrl() ?: ""
            val message = if (currentUrl.isNotBlank()) {
                "The user is currently viewing this page in the browser: $currentUrl\n\nUser request: $text"
            } else {
                text
            }
            val id = conversationId ?: Uuid.random().also {
                conversationId = it
                BrowserSessionState.conversationId = it
            }
            chatService.initializeConversation(id)
            chatService.sendMessage(id, listOf(UIMessagePart.Text(message)))
            prompt = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton() },
                title = {
                    OutlinedTextField(
                        value = addressBar,
                        onValueChange = { addressBar = it },
                        placeholder = { Text("Enter URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigate() })
                    )
                },
                actions = {
                    FilledTonalIconButton(onClick = { navigate() }) {
                        Icon(imageVector = HugeIcons.ArrowRight01, contentDescription = "Go")
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).also { webView ->
                        val c = BrowserController(
                            webView,
                            onUrlChanged = {
                                addressBar = it
                                BrowserSessionState.lastUrl = it
                            }
                        )
                        controller = c
                        val home = BrowserSessionState.lastUrl ?: HOME_URL
                        webView.loadUrl(home)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
            ) {
                if (ui.steps.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ui.steps.forEach { step -> StepDot(step) }
                    }
                }
                if (ui.reply.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = ui.reply,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalIconButton(onClick = { showFullReply = true }) {
                                Icon(imageVector = HugeIcons.FullScreen, contentDescription = "Expand")
                            }
                        }
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            placeholder = { Text("Ask the AI about this page") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { sendPrompt() })
                        )
                        FilledTonalIconButton(onClick = { cancelGeneration() }) {
                            Icon(imageVector = HugeIcons.Cancel01, contentDescription = "Cancel")
                        }
                        FilledTonalIconButton(onClick = { sendPrompt() }) {
                            Icon(imageVector = HugeIcons.ArrowUp02, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }

    if (showFullReply) {
        Dialog(onDismissRequest = { showFullReply = false }) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AI response", style = MaterialTheme.typography.titleMedium)
                        FilledTonalIconButton(onClick = { showFullReply = false }) {
                            Icon(imageVector = HugeIcons.Cancel01, contentDescription = "Close")
                        }
                    }
                    Text(
                        text = ui.reply,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepDot(step: BrowserStep) {
    val (icon, tint) = when (step) {
        is BrowserStep.Tool -> HugeIcons.Tools to MaterialTheme.colorScheme.primary
        BrowserStep.Thinking -> HugeIcons.AiBrain01 to MaterialTheme.colorScheme.tertiary
        BrowserStep.Done -> HugeIcons.Tick01 to MaterialTheme.colorScheme.secondary
    }
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
    }
}
