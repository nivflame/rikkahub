package me.rerere.rikkahub.browser

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

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
    var conversationId by remember { mutableStateOf<Uuid?>(null) }
    var addressBar by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    val reply by produceState(initialValue = "", conversationId) {
        val id = conversationId ?: return@produceState
        chatService.getConversationFlow(id).collect { conversation ->
            value = conversation.currentMessages
                .lastOrNull { it.role == MessageRole.ASSISTANT }
                ?.parts?.filterIsInstance<UIMessagePart.Text>()
                ?.joinToString("") { it.text }
                ?: ""
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
            val id = conversationId ?: Uuid.random().also { conversationId = it }
            chatService.initializeConversation(id)
            chatService.sendMessage(id, listOf(UIMessagePart.Text(text)))
            prompt = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = addressBar,
                        onValueChange = { addressBar = it },
                        placeholder = { Text("Enter URL or search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigate() })
                    )
                },
                actions = {
                    IconButton(onClick = { navigate() }) {
                        Icon(imageVector = HugeIcons.ArrowRight01, contentDescription = "Go")
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).also { webView ->
                        controller = BrowserController(webView, onUrlChanged = { addressBar = it })
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
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
                IconButton(onClick = { sendPrompt() }) {
                    Icon(imageVector = HugeIcons.ArrowUp02, contentDescription = "Send")
                }
            }
            if (reply.isNotBlank()) {
                Text(
                    text = reply,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}
