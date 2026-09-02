package me.rerere.rikkahub.ui.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.collect
import androidx.core.net.toUri
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.service.AssistBubbleService
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.rememberCustomAsrState
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.ui.pages.chat.ChatFilesPickerSheet
import me.rerere.rikkahub.ui.theme.GoogleSansFlex
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

class AssistChatActivity : ComponentActivity() {
    private var overlayConversationId: Uuid = Uuid.random()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        intent?.getStringExtra("conversationId")?.let { raw ->
            runCatching { Uuid.parse(raw) }.getOrNull()?.let { overlayConversationId = it }
        }
        setContent {
            RikkahubTheme {
                AssistChatPage(
                    conversationId = overlayConversationId,
                    onOpenConversation = { openConversation(it) },
                    onDismiss = { finish() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        stopService(Intent(this, AssistBubbleService::class.java))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finish()
        startActivity(intent)
    }

    private fun openConversation(id: Uuid) {
        startActivity(
            Intent(this, RouteActivity::class.java).apply {
                action = RouteActivity.ACTION_OPEN_CONVERSATION
                putExtra("conversationId", id.toString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        finish()
    }
}

@Composable
private fun AssistChatPage(
    conversationId: Uuid,
    onOpenConversation: (Uuid) -> Unit,
    onDismiss: () -> Unit,
) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(conversationId.toString(), "", emptyList<android.net.Uri>(), false)
        }
    )
    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val generating = vm.conversationJob.collectAsStateWithLifecycle().value != null
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()
    val asr = rememberCustomAsrState()
    val toastState = rememberToasterState()
    val navigator = remember { Navigator(mutableListOf()) }
    val highlighter = koinInject<Highlighter>()
    val imeVisible = WindowInsets.isImeVisible
    val context = LocalContext.current

    var expanded by remember { mutableStateOf(false) }
    var showFilesSheet by remember { mutableStateOf(false) }
    var responseText by remember { mutableStateOf("") }
    var capsuleStatus by remember { mutableStateOf("Generating") }
    val inputState = vm.inputState

    LaunchedEffect(conversationId) {
        vm.conversation.collect { conv ->
            val message = conv.currentMessages
                .lastOrNull { it.role == MessageRole.ASSISTANT }
            val parts = message?.parts.orEmpty()
            val runningTool = parts
                .filterIsInstance<UIMessagePart.Tool>()
                .lastOrNull { !it.isExecuted && !it.isPending }
            val lastToolIndex = parts.indexOfLast { it is UIMessagePart.Tool }
            val postTool = if (lastToolIndex >= 0) parts.drop(lastToolIndex + 1) else parts
            val thinking = runningTool == null &&
                postTool.any { it is UIMessagePart.Reasoning && it.reasoning.isNotBlank() } &&
                postTool.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("") { it.text }.isBlank()
            capsuleStatus = when {
                runningTool != null -> when (runningTool.toolName) {
                    "WebSearch" -> "Searching the web"
                    "WebFetch" -> "Reading page"
                    "Bash" -> "Running command"
                    "Read" -> "Reading file"
                    "Write" -> "Writing file"
                    "Edit" -> "Editing file"
                    else -> "Running tool"
                }
                thinking -> "Thinking"
                else -> "Generating"
            }
            responseText = message?.toText().orEmpty()
        }
    }

    val showSheet = responseText.isNotBlank()

    fun sendMessage() {
        val parts = inputState.getContents()
        if (parts.isEmptyInputMessage()) return
        if (currentChatModel == null) {
            toastState.show("请先选择模型", type = com.dokar.sonner.ToastType.Error)
            return
        }
        vm.handleMessageSend(parts)
        inputState.clearInput()
        expanded = false
    }

    CompositionLocalProvider(
        LocalSettings provides setting,
        LocalToaster provides toastState,
        LocalNavController provides navigator,
        LocalASRState provides asr,
        LocalHighlighter provides highlighter,
        LocalUriHandler provides object : UriHandler {
            override fun openUri(uri: String) {
                if (Settings.canDrawOverlays(context)) {
                    context.startService(
                        Intent(context, AssistBubbleService::class.java)
                            .putExtra("conversationId", conversationId.toString())
                    )
                }
                context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
            }
        },
    ) {
        Toaster(
            state = toastState,
            darkTheme = LocalDarkMode.current,
            richColors = true,
            alignment = Alignment.TopCenter,
            showCloseButton = true,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if ((generating || conversation.currentMessages.isNotEmpty()) &&
                        Settings.canDrawOverlays(context)
                    ) {
                        context.startService(
                            Intent(context, AssistBubbleService::class.java)
                                .putExtra("conversationId", conversationId.toString())
                        )
                    }
                    onDismiss()
                },
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .then(
                        if (imeVisible) {
                            Modifier.padding(bottom = 8.dp)
                        } else {
                            Modifier.padding(bottom = 64.dp)
                        }
                    )
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .imePadding()
                    .consumeWindowInsets(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = showSheet,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    AssistResponseSheet(
                        text = responseText,
                        selectable = !generating,
                        onOpen = { onOpenConversation(conversationId) },
                        onClose = onDismiss,
                    )
                }
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = {
                        if (targetState) {
                            (expandVertically(expandFrom = Alignment.Bottom) + fadeIn(tween(220))) togetherWith
                                (shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(tween(180)))
                        } else {
                            (expandVertically(expandFrom = Alignment.Bottom) + fadeIn(tween(220))) togetherWith
                                (shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(tween(180)))
                        }
                    },
                    label = "assistExpand",
                ) { isExpanded ->
                    if (isExpanded) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {},
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 3.dp,
                        ) {
                            Column {
                                ChatInput(
                                    state = inputState,
                                    loading = generating,
                                    settings = setting,
                                    hazeState = hazeState,
                                    enableSearch = setting.enableWebSearch,
                                    autoFocus = true,
                                    showInputBorder = false,
                                    showSearchButton = false,
                                    onToggleSearch = {
                                        vm.updateSettings(setting.copy(enableWebSearch = !setting.enableWebSearch))
                                    },
                                    onCancelClick = {
                                        vm.stopGeneration()
                                    },
                                    onSendClick = { sendMessage() },
                                    onLongSendClick = { sendMessage() },
                                    onUpdateChatModel = { model ->
                                        val assistant = setting.getCurrentAssistant()
                                        vm.updateSettings(
                                            setting.copy(
                                                assistants = setting.assistants.map {
                                                    if (it.id == assistant.id) it.copy(chatModelId = model.id) else it
                                                }
                                            )
                                        )
                                    },
                                    onUpdateAssistant = { assistant ->
                                        vm.updateSettings(
                                            setting.copy(
                                                assistants = setting.assistants.map {
                                                    if (it.id == assistant.id) assistant else it
                                                }
                                            )
                                        )
                                    },
                                    onUpdateSearchService = { index ->
                                        vm.updateSettings(setting.copy(searchServiceSelected = index))
                                    },
                                    onMoreClick = { showFilesSheet = true },
                                )
                            }
                        }
                    } else {
                        AssistCapsule(
                            generating = generating,
                            statusText = capsuleStatus,
                            onClick = { expanded = true },
                        )
                    }
                }
            }
        }
        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                assistant = setting.getCurrentAssistant(),
                vm = vm,
                onDismiss = { showFilesSheet = false },
            )
        }
    }
}

@Composable
private fun AssistCapsule(
    generating: Boolean,
    statusText: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = !generating,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (generating) {
                val transition = rememberInfiniteTransition(label = "shimmer")
                val progress by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                    ),
                    label = "shimmerProgress",
                )
                val density = LocalDensity.current
                val bandPx = with(density) { 24.dp.toPx() }
                val travelPx = with(density) { 160.dp.toPx() }
                val base = MaterialTheme.colorScheme.onSurfaceVariant
                val highlight = MaterialTheme.colorScheme.onSurface
                val brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.0f to base.copy(alpha = 0.45f),
                        0.5f to highlight,
                        1.0f to base.copy(alpha = 0.45f),
                    ),
                    start = Offset(progress * (travelPx + bandPx) - bandPx, 0f),
                    end = Offset(progress * (travelPx + bandPx), 0f),
                )
                BasicText(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = GoogleSansFlex.Title.Normal.Medium,
                        brush = brush,
                        textAlign = TextAlign.Center,
                    ),
                )
            } else {
                Text(
                    text = "Ask something",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontFamily = GoogleSansFlex.Title.Emphasized.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AssistResponseSheet(
    text: String,
    selectable: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onOpen()
                true
            } else {
                false
            }
        }
    )
    val scrollState = rememberScrollState()
    LaunchedEffect(text) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 3.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "RikkaHub",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Swipe to open",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        onClick = onClose,
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            text = "Close",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            val fadePx = 28.dp.toPx()
                            if (size.height > fadePx * 2) {
                                val edge = (fadePx / size.height).coerceAtMost(0.5f)
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        edge to Color.Black,
                                        (1f - edge) to Color.Black,
                                        1f to Color.Transparent,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        }
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp),
                ) {
                    if (selectable) {
                        SelectionContainer {
                            MarkdownBlock(content = text)
                        }
                    } else {
                        MarkdownBlock(content = text)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
