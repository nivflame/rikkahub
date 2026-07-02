package me.rerere.rikkahub.browser

import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import androidx.navigation3.runtime.NavKey
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.FullScreen
import me.rerere.hugeicons.stroke.Home01
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.context.LocalSettings

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
        val conversationId = intent.getStringExtra("conversationId")
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        setContent {
            RikkahubTheme {
                CompositionLocalProvider(LocalNavController provides Navigator(remember { mutableListOf<NavKey>() })) {
                    BrowserScreen(
                        chatService = chatService,
                        settingsStore = settingsStore,
                        initialConversationId = conversationId
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScreen(
    chatService: ChatService,
    settingsStore: SettingsStore,
    initialConversationId: Uuid? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    var controller by remember { mutableStateOf<BrowserController?>(null) }
    var conversationId by remember { mutableStateOf(initialConversationId) }
    var initialLoaded by remember { mutableStateOf(false) }
    var addressBar by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var showFullReply by remember { mutableStateOf(false) }
    var inputExpanded by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var replyDismissed by remember { mutableStateOf(false) }
    var showHamburgerMenu by remember { mutableStateOf(false) }
    var showZoomDialog by remember { mutableStateOf(false) }
    var desktopMode by remember { mutableStateOf(false) }
    var zoomLevel by remember { mutableStateOf(100) }
    var mobileUA by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(inputExpanded) {
        if (inputExpanded) {
            kotlinx.coroutines.delay(100)
            runCatching { focusRequester.requestFocus() }
        }
    }

    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible && inputExpanded) {
            focusManager.clearFocus()
            inputExpanded = false
        }
    }

    BackHandler(enabled = inputExpanded) {
        inputExpanded = false
    }

    LaunchedEffect(settings.browserLastUrl, controller) {
        if (!initialLoaded && controller != null) {
            controller?.webView?.loadUrl(settings.browserLastUrl ?: HOME_URL)
            initialLoaded = true
        }
    }

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

    LaunchedEffect(ui.reply) {
        if (ui.reply.isNotBlank()) replyDismissed = false
    }

    val generating by produceState(initialValue = false, conversationId) {
        val id = conversationId ?: return@produceState
        chatService.getGenerationJobStateFlow(id).collect { value = it != null }
    }

    DisposableEffect(controller) {
        HeadlessBrowserSession.setActive(controller)
        onDispose { HeadlessBrowserSession.setActive(null) }
    }

    fun navigate() {
        val raw = addressBar.trim()
        if (raw.isBlank()) return
        val url = when {
            raw.contains("://") -> raw
            raw.contains(' ') -> "https://www.google.com/search?q=" + Uri.encode(raw)
            raw.contains('.') -> "https://$raw"
            else -> "https://www.google.com/search?q=" + Uri.encode(raw)
        }
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
            val id = conversationId ?: Uuid.random().also { conversationId = it }
            chatService.initializeConversation(id)
            chatService.sendMessage(id, listOf(UIMessagePart.Text(message)))
            prompt = ""
        }
    }

    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val topBarColor = MaterialTheme.colorScheme.surfaceContainer

    CompositionLocalProvider(LocalSettings provides settings) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = topBarColor,
            tonalElevation = 2.dp,
        ) {
            OutlinedTextField(
                value = addressBar,
                onValueChange = { addressBar = it },
                placeholder = { Text("Search or enter address", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        navigate()
                        focusManager.clearFocus()
                    }),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).also { webView ->
                        val c = BrowserController(
                            webView,
                            onUrlChanged = { url ->
                                addressBar = url
                                canGoBack = webView.canGoBack()
                                canGoForward = webView.canGoForward()
                                scope.launch { settingsStore.update { it.copy(browserLastUrl = url) } }
                            }
                        )
                        controller = c
                        mobileUA = webView.settings.userAgentString
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (inputExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            focusManager.clearFocus()
                            inputExpanded = false
                        }
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
            ) {
                if (generating) {
                    ui.steps.lastOrNull()?.let { step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackerPill(step, generating, containerColor)
                        }
                    }
                }
                if (!inputExpanded) {
                    val fabInteractionSource = remember { MutableInteractionSource() }
                    val fabPressed by fabInteractionSource.collectIsPressedAsState()
                    val fabScale by animateFloatAsState(
                        targetValue = if (fabPressed) 0.92f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "fabScale",
                    )

                    val infiniteTransition = rememberInfiniteTransition(label = "fabHalo")
                    val haloAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.1f,
                        targetValue = 0.25f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "haloAlpha",
                    )

                    val fabContent: @Composable () -> Unit = {
                        Box(
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .scale(if (generating) 1f else 0.9f)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(
                                            alpha = if (generating) haloAlpha else 0.12f,
                                        ),
                                    ),
                            )
                            FloatingActionButton(
                                onClick = { inputExpanded = true },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                interactionSource = fabInteractionSource,
                                modifier = Modifier.scale(fabScale),
                            ) {
                                Icon(imageVector = HugeIcons.MessageAdd01, contentDescription = "Ask the AI")
                            }
                        }
                    }

                    if (ui.reply.isNotBlank() && !replyDismissed) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(16.dp),
                                shadowElevation = 3.dp,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MarkdownBlock(
                                        content = ui.reply,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(max = 40.dp)
                                            .clipToBounds()
                                            .padding(end = 4.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    FilledTonalIconButton(onClick = { showFullReply = true }) {
                                        Icon(imageVector = HugeIcons.FullScreen, contentDescription = "Expand")
                                    }
                                    IconButton(onClick = { replyDismissed = true }) {
                                        Icon(imageVector = HugeIcons.Cancel01, contentDescription = "Dismiss")
                                    }
                                }
                            }
                            fabContent()
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            fabContent()
                        }
                    }
                }
                AnimatedVisibility(
                    visible = inputExpanded,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(28.dp),
                        shadowElevation = 3.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = prompt,
                                onValueChange = { prompt = it },
                                placeholder = { Text("Ask the AI about this page") },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                maxLines = 5,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    sendPrompt()
                                    inputExpanded = false
                                }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                ),
                            )
                            IconButton(onClick = { cancelGeneration() }) {
                                Icon(imageVector = HugeIcons.Cancel01, contentDescription = "Cancel")
                            }
                            FilledTonalIconButton(onClick = {
                                sendPrompt()
                                inputExpanded = false
                            }) {
                                Icon(imageVector = HugeIcons.ArrowUp02, contentDescription = "Send")
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = !inputExpanded,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = topBarColor,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { controller?.webView?.loadUrl(HOME_URL) }) {
                    Icon(imageVector = HugeIcons.Home01, contentDescription = "Home")
                }
                IconButton(
                    onClick = { controller?.webView?.goBack() },
                    enabled = canGoBack,
                ) {
                    Icon(
                        imageVector = HugeIcons.ArrowLeft01,
                        contentDescription = "Back",
                        tint = if (canGoBack) LocalContentColor.current
                            else LocalContentColor.current.copy(alpha = 0.38f),
                    )
                }
                IconButton(
                    onClick = { controller?.webView?.goForward() },
                    enabled = canGoForward,
                ) {
                    Icon(
                        imageVector = HugeIcons.ArrowRight01,
                        contentDescription = "Forward",
                        tint = if (canGoForward) LocalContentColor.current
                            else LocalContentColor.current.copy(alpha = 0.38f),
                    )
                }
                IconButton(onClick = { showHamburgerMenu = true }) {
                    Icon(imageVector = HugeIcons.Menu03, contentDescription = "Menu")
                    DropdownMenu(
                        expanded = showHamburgerMenu,
                        onDismissRequest = { showHamburgerMenu = false },
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .width(260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        showHamburgerMenu = false
                                        showZoomDialog = true
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Search01,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Zoom",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "$zoomLevel%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = HugeIcons.SmartPhone01,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Desktop Site",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = desktopMode,
                                    onCheckedChange = {
                                        desktopMode = it
                                        val ua = if (it) {
                                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                        } else {
                                            mobileUA
                                        }
                                        controller?.webView?.settings?.userAgentString = ua
                                        controller?.webView?.reload()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showZoomDialog) {
        Dialog(onDismissRequest = { showZoomDialog = false }) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .width(320.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        text = "$zoomLevel%",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalIconButton(onClick = {
                            zoomLevel = (zoomLevel - 10).coerceIn(50, 200)
                            controller?.webView?.settings?.textZoom = zoomLevel
                        }) {
                            Text("-", style = MaterialTheme.typography.headlineMedium)
                        }
                        Slider(
                            value = zoomLevel.toFloat(),
                            onValueChange = {
                                zoomLevel = it.toInt()
                                controller?.webView?.settings?.textZoom = it.toInt()
                            },
                            valueRange = 50f..200f,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(onClick = {
                            zoomLevel = (zoomLevel + 10).coerceIn(50, 200)
                            controller?.webView?.settings?.textZoom = zoomLevel
                        }) {
                            Text("+", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            zoomLevel = 100
                            controller?.webView?.settings?.textZoom = 100
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset")
                    }
                }
            }
        }
    }

    if (showFullReply) {
        Dialog(
            onDismissRequest = { showFullReply = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalIconButton(onClick = { showFullReply = false }) {
                            Icon(imageVector = HugeIcons.Cancel01, contentDescription = "Close")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MarkdownBlock(
                        content = ui.reply,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun TrackerPill(
    step: BrowserStep,
    generating: Boolean,
    containerColor: Color,
) {
    val (label, icon, active) = when (step) {
        is BrowserStep.Tool -> Triple("Agent calling ${step.name}", HugeIcons.Tools, generating)
        BrowserStep.Thinking -> Triple("Agent thinking", HugeIcons.AiBrain01, generating)
        BrowserStep.Done -> Triple("Agent finish", HugeIcons.Tick01, false)
    }

    val (targetContainer, targetContent) = when (step) {
        is BrowserStep.Tool -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        BrowserStep.Thinking -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        BrowserStep.Done -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val animatedContainer by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = tween(300),
        label = "pillContainer",
    )
    val animatedContent by animateColorAsState(
        targetValue = targetContent,
        animationSpec = tween(300),
        label = "pillContent",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pillPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pillPulseAlpha",
    )

    Surface(
        color = animatedContainer.copy(alpha = if (active) pulseAlpha else 1f),
        shape = CircleShape,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (active) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = animatedContent,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = animatedContent,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = animatedContent,
            )
        }
    }
}
