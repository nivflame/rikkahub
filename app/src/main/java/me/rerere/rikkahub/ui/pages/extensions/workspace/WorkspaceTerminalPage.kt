package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh01
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.ColorMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun WorkspaceTerminalPage(id: String) {
    val vm: WorkspaceDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val state by vm.state.collectAsStateWithLifecycle()
    var restartTrigger by remember { mutableIntStateOf(0) }

    RikkahubTheme(colorMode = ColorMode.DARK) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = state.workspace?.name?.let { stringResource(R.string.workspace_terminal_title_with_name, it) } ?: stringResource(R.string.workspace_terminal_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = { BackButton() },
                    actions = {
                        IconButton(onClick = {
                            state.workspace?.root?.let { WorkspaceTerminalSessionHolder.remove(it) }
                            restartTrigger++
                        }) {
                            Icon(
                                imageVector = HugeIcons.Refresh01,
                                contentDescription = "Restart terminal",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            WorkspaceTerminalContent(
                root = state.workspace?.root,
                contentPadding = innerPadding,
                restartTrigger = restartTrigger,
            )
        }
    }
}

@Composable
private fun WorkspaceTerminalContent(
    root: String?,
    contentPadding: PaddingValues,
    restartTrigger: Int,
) {
    val context = LocalContext.current
    val terminalTextSizePx = with(LocalDensity.current) { 12.sp.roundToPx() }
    val terminalTypeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
    }
    var finished by remember(root, restartTrigger) { mutableStateOf(root?.let { WorkspaceTerminalSessionHolder.isFinished(it) } ?: false) }
    var controlDown by remember(root) { mutableStateOf(false) }
    var altDown by remember(root) { mutableStateOf(false) }
    val viewClient = remember(root) {
        WorkspaceTerminalViewClient(context)
    }
    viewClient.controlDown = controlDown
    viewClient.altDown = altDown

    val sessionState by produceState<TerminalSessionUiState>(
        initialValue = TerminalSessionUiState.Loading,
        root,
        restartTrigger,
    ) {
        val current = root
        value = if (current == null) {
            TerminalSessionUiState.Loading
        } else {
            val existing = WorkspaceTerminalSessionHolder.get(current)
            if (existing != null && !WorkspaceTerminalSessionHolder.isFinished(current)) {
                TerminalSessionUiState.Ready(existing)
            } else {
                val prepared = withContext(Dispatchers.IO) {
                    if (!workspaceRootfsReady(context, current)) {
                        false
                    } else {
                        prepareWorkspaceTerminalSession(context, current)
                        true
                    }
                }
                if (!prepared) {
                    TerminalSessionUiState.NotInstalled
                } else {
                    if (!isActive) return@produceState
                    val session = WorkspaceTerminalSessionHolder.create(current, context)
                    if (!isActive) {
                        WorkspaceTerminalSessionHolder.remove(current)
                        return@produceState
                    }
                    TerminalSessionUiState.Ready(session)
                }
            }
        }
    }

    val currentState = sessionState
    if (currentState !is TerminalSessionUiState.Ready) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (currentState is TerminalSessionUiState.NotInstalled) {
                    stringResource(R.string.workspace_terminal_not_installed)
                } else {
                    stringResource(R.string.workspace_terminal_loading)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
        return
    }
    val session = currentState.session
    val sessionClient = root?.let { WorkspaceTerminalSessionHolder.getClient(it) }

    LaunchedEffect(session) {
        finished = root?.let { WorkspaceTerminalSessionHolder.isFinished(it) } ?: false
    }

    DisposableEffect(session) {
        sessionClient?.onFinished = { finished = true }
        onDispose {
            if (finished) {
                root?.let { WorkspaceTerminalSessionHolder.remove(it) }
            } else {
                root?.let { WorkspaceTerminalSessionHolder.detachView(it) }
            }
            viewClient.terminalView = null
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
        color = Color.Black,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        TerminalView(viewContext, null).apply {
                            isFocusable = true
                            isFocusableInTouchMode = true
                            setTextSize(terminalTextSizePx)
                            setTypeface(terminalTypeface)
                            setTerminalViewClient(viewClient)
                            attachSession(session)
                            sessionClient?.terminalView = this
                            viewClient.terminalView = this
                            setOnTouchListener { _, event ->
                                if (event.action == MotionEvent.ACTION_UP) {
                                    viewClient.focusAndShowKeyboard()
                                }
                                false
                            }
                            post {
                                viewClient.focusAndShowKeyboard()
                            }
                        }
                    },
                    update = { terminalView ->
                        terminalView.isFocusable = true
                        terminalView.isFocusableInTouchMode = true
                        terminalView.setTextSize(terminalTextSizePx)
                        terminalView.setTypeface(terminalTypeface)
                        terminalView.setTerminalViewClient(viewClient)
                        sessionClient?.terminalView = terminalView
                        viewClient.terminalView = terminalView
                        terminalView.setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_UP) {
                                viewClient.focusAndShowKeyboard()
                            }
                            false
                        }
                        terminalView.attachSession(session)
                        terminalView.onScreenUpdated()
                    },
                )
                if (finished) {
                    Text(
                        text = stringResource(R.string.workspace_terminal_exited),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                        .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
                TerminalExtraKeysBar(
                    controlDown = controlDown,
                    altDown = altDown,
                    onControlToggle = { controlDown = !controlDown },
                    onAltToggle = { altDown = !altDown },
                    onSendText = { session.writeText(it) },
                )
        }
    }
}

@Composable
private fun TerminalExtraKeysBar(
    controlDown: Boolean,
    altDown: Boolean,
    onControlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onSendText: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TerminalExtraKey("ESC", Modifier.weight(1f)) { onSendText("\u001B") }
            TerminalExtraKey("/", Modifier.weight(1f)) { onSendText("/") }
            TerminalExtraKey("-", Modifier.weight(1f)) { onSendText("-") }
            TerminalExtraKey("HOME", Modifier.weight(1f)) { onSendText("\u001B[H") }
            TerminalExtraKey("UP", Modifier.weight(1f)) { onSendText("\u001B[A") }
            TerminalExtraKey("END", Modifier.weight(1f)) { onSendText("\u001B[F") }
            TerminalExtraKey("PGUP", Modifier.weight(1f)) { onSendText("\u001B[5~") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TerminalExtraKey("TAB", Modifier.weight(1f)) { onSendText("\t") }
            TerminalExtraKey("CTRL", Modifier.weight(1f), selected = controlDown, onClick = onControlToggle)
            TerminalExtraKey("ALT", Modifier.weight(1f), selected = altDown, onClick = onAltToggle)
            TerminalExtraKey("LEFT", Modifier.weight(1f)) { onSendText("\u001B[D") }
            TerminalExtraKey("DOWN", Modifier.weight(1f)) { onSendText("\u001B[B") }
            TerminalExtraKey("RIGHT", Modifier.weight(1f)) { onSendText("\u001B[C") }
            TerminalExtraKey("PGDN", Modifier.weight(1f)) { onSendText("\u001B[6~") }
        }
    }
}

@Composable
private fun TerminalExtraKey(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                },
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
        },
    )
}

private fun TerminalSession.writeText(text: String) {
    val bytes = text.toByteArray()
    write(bytes, 0, bytes.size)
}

private sealed interface TerminalSessionUiState {
    data object Loading : TerminalSessionUiState
    data object NotInstalled : TerminalSessionUiState
    data class Ready(val session: TerminalSession) : TerminalSessionUiState
}
