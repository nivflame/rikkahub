package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BubbleChatQuestion
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.message.tools.ToolUIContext
import me.rerere.rikkahub.ui.components.message.tools.ToolUIRegistry
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.components.ui.ChainOfThoughtScope
import me.rerere.rikkahub.ui.components.ui.DotLoading
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.utils.JsonInstant

private const val ASK_USER_TOOL_NAME = "AskQuestion"

@Composable
fun ChainOfThoughtScope.ChatMessageToolStep(
    tool: UIMessagePart.Tool,
    loading: Boolean = false,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
) {
    // AskQuestion 是交互式问答流程, 不走注册式渲染框架
    if (tool.toolName == ASK_USER_TOOL_NAME) {
        AskUserToolStep(tool = tool, loading = loading, onToolAnswer = onToolAnswer)
        return
    }

    val renderer = remember(tool.toolName) { ToolUIRegistry.resolve(tool.toolName) }
    val context = remember(tool, loading) {
        ToolUIContext(
            tool = tool,
            arguments = tool.inputAsJson(),
            content = if (tool.isExecuted) {
                runCatching {
                    JsonInstant.parseToJsonElement(
                        tool.output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                    )
                }.getOrElse { JsonObject(emptyMap()) }
            } else {
                null
            },
            loading = loading,
        )
    }

    var showResult by remember { mutableStateOf(false) }
    var showDenyDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(true) }
    val isPending = tool.approvalState is ToolApprovalState.Pending
    val isDenied = tool.approvalState is ToolApprovalState.Denied
    val images = tool.output.filterIsInstance<UIMessagePart.Image>()

    // 摘要由注册的渲染器决定; 图片输出与拒绝原因为所有工具通用
    val hasExtraContent = renderer.hasSummary(context) || isDenied || images.isNotEmpty()

    ControlledChainOfThoughtStep(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        icon = {
            if (loading) {
                DotLoading(
                    size = 10.dp
                )
            } else {
                Icon(
                    imageVector = renderer.icon(context),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        },
        label = {
            Text(
                text = renderer.title(context),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.shimmer(isLoading = loading),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        extra = if (isPending && onToolApproval != null) {
            {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = { showDenyDialog = true },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Cancel01,
                            contentDescription = stringResource(R.string.chat_message_tool_deny),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { onToolApproval(tool.toolCallId, true, "") },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Tick01,
                            contentDescription = stringResource(R.string.chat_message_tool_approve),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        } else {
            null
        },
        onClick = if (context.content != null || isPending || images.isNotEmpty()) {
            { showResult = true }
        } else {
            null
        },
        content = if (hasExtraContent) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    renderer.Summary(context)
                    if (images.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.wrapContentWidth(),
                        ) {
                            items(images) { image ->
                                ZoomableAsyncImage(
                                    model = image.url,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .height(64.dp)
                                        .wrapContentWidth(),
                                )
                            }
                        }
                    }
                    if (isDenied) {
                        val reason = (tool.approvalState as ToolApprovalState.Denied).reason
                        Text(
                            text = stringResource(R.string.chat_message_tool_denied) +
                                if (reason.isNotBlank()) ": $reason" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        } else {
            null
        },
    )

    if (showDenyDialog && onToolApproval != null) {
        ToolDenyReasonDialog(
            onDismiss = { showDenyDialog = false },
            onConfirm = { reason ->
                showDenyDialog = false
                onToolApproval(tool.toolCallId, false, reason)
            }
        )
    }

    if (showResult) {
        ModalBottomSheet(
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            ),
            onDismissRequest = { showResult = false },
            content = {
                renderer.Preview(
                    context = context,
                    onDismissRequest = { showResult = false },
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChainOfThoughtScope.AskUserToolStep(
    tool: UIMessagePart.Tool,
    loading: Boolean,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)?,
) {
    val isPending = tool.approvalState is ToolApprovalState.Pending
    val isAnswered = tool.approvalState is ToolApprovalState.Answered
    val arguments = tool.inputAsJson()

    // Parse questions from arguments
    val questions = remember(arguments) {
        runCatching {
            arguments.jsonObject["questions"]?.jsonArray?.map { q ->
                val obj = q.jsonObject
                AskUserQuestion(
                    question = obj["question"]?.jsonPrimitive?.contentOrNull ?: "",
                    header = obj["header"]?.jsonPrimitive?.contentOrNull ?: "",
                    options = obj["options"]?.jsonArray?.mapNotNull { opt ->
                        runCatching {
                            val o = opt.jsonObject
                            AskUserOption(
                                label = o["label"]?.jsonPrimitive?.contentOrNull ?: "",
                                description = o["description"]?.jsonPrimitive?.contentOrNull ?: "",
                                preview = o["preview"]?.jsonPrimitive?.contentOrNull
                            )
                        }.getOrNull()
                    } ?: emptyList(),
                    multiSelect = obj["multiSelect"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                )
            } ?: emptyList()
        }.getOrElse { emptyList() }
    }

    // Answers keyed by question text: single-select stores the label (or "Other" text), multi-select stores comma-joined labels.
    val answers = remember { mutableStateMapOf<String, String>() }
    val multiAnswers = remember { mutableStateMapOf<String, Set<String>>() }
    val otherText = remember { mutableStateMapOf<String, String>() }
    val otherSelected = remember { mutableStateMapOf<String, Boolean>() }
    val notes = remember { mutableStateMapOf<String, String>() }
    var focusedOption by remember { mutableStateOf<Pair<String, AskUserOption>?>(null) }

    val firstQuestion = questions.firstOrNull()?.question ?: "..."

    var expanded by remember { mutableStateOf(true) }

    ControlledChainOfThoughtStep(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        icon = {
            if (loading) {
                DotLoading(size = 10.dp)
            } else {
                Icon(
                    imageVector = HugeIcons.BubbleChatQuestion,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        },
        label = {
            Text(
                text = if (questions.size <= 1) firstQuestion else stringResource(
                    R.string.chat_message_tool_ask_questions,
                    questions.size
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.shimmer(isLoading = loading),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                questions.forEach { q ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (q.header.isNotBlank()) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = q.header.take(12),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                text = q.question,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        if (isPending && onToolAnswer != null) {
                            val hasPreview = !q.multiSelect && q.options.any { !it.preview.isNullOrBlank() }
                            if (hasPreview) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        q.options.forEach { option ->
                                            AskOptionRow(
                                                label = option.label,
                                                description = option.description,
                                                selected = answers[q.question] == option.label,
                                                onClick = {
                                                    answers[q.question] = option.label
                                                    focusedOption = q.question to option
                                                },
                                            )
                                        }
                                        AskOtherRow(
                                            selected = otherSelected[q.question] == true,
                                            onToggle = { otherSelected[q.question] = !(otherSelected[q.question] ?: false) },
                                            text = otherText[q.question] ?: "",
                                            onTextChange = {
                                                otherText[q.question] = it
                                                otherSelected[q.question] = true
                                            },
                                        )
                                    }
                                    Surface(
                                        tonalElevation = 1.dp,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            text = (focusedOption?.takeIf { it.first == q.question }?.second?.preview)
                                                ?: q.options.firstOrNull { it.label == answers[q.question] }?.preview
                                                ?: "Preview",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .verticalScroll(rememberScrollState()),
                                        )
                                    }
                                }
                            } else {
                                q.options.forEach { option ->
                                    if (q.multiSelect) {
                                        val selectedSet = multiAnswers[q.question] ?: emptySet()
                                        AskOptionRow(
                                            label = option.label,
                                            description = option.description,
                                            selected = selectedSet.contains(option.label),
                                            onClick = {
                                                val current = selectedSet.toMutableSet()
                                                if (current.contains(option.label)) current.remove(option.label) else current.add(option.label)
                                                multiAnswers[q.question] = current
                                            },
                                        )
                                    } else {
                                        AskOptionRow(
                                            label = option.label,
                                            description = option.description,
                                            selected = answers[q.question] == option.label,
                                            onClick = { answers[q.question] = option.label },
                                        )
                                    }
                                }
                                AskOtherRow(
                                    selected = otherSelected[q.question] == true,
                                    onToggle = { otherSelected[q.question] = !(otherSelected[q.question] ?: false) },
                                    text = otherText[q.question] ?: "",
                                    onTextChange = {
                                        otherText[q.question] = it
                                        otherSelected[q.question] = true
                                    },
                                )
                            }
                            OutlinedTextField(
                                value = notes[q.question] ?: "",
                                onValueChange = { notes[q.question] = it },
                                label = { Text("Add a note (optional)", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall,
                                singleLine = false,
                                minLines = 1,
                                maxLines = 2,
                            )
                        } else if (isAnswered) {
                            val answeredState = tool.approvalState as ToolApprovalState.Answered
                            val answerJson = runCatching {
                                JsonInstant.parseToJsonElement(answeredState.answer)
                            }.getOrNull()
                            val answerText = answerJson?.jsonObject?.get("answers")
                                ?.jsonObject?.get(q.question)?.jsonPrimitive?.contentOrNull
                                ?: answeredState.answer
                            Text(
                                text = answerText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                // Submit button
                if (isPending && onToolAnswer != null) {
                    FilledTonalButton(
                        onClick = {
                            val answerPayload = buildJsonObject {
                                put("answers", buildJsonObject {
                                    questions.forEach { q ->
                                        val answer = if (q.multiSelect) {
                                            val selected = (multiAnswers[q.question] ?: emptySet()).toMutableList()
                                            if (otherSelected[q.question] == true && !otherText[q.question].isNullOrBlank()) {
                                                selected.add(otherText[q.question]!!)
                                            }
                                            selected.joinToString(", ")
                                        } else {
                                            if (otherSelected[q.question] == true) otherText[q.question] ?: "" else answers[q.question] ?: ""
                                        }
                                        put(q.question, JsonPrimitive(answer))
                                    }
                                })
                                put("annotations", buildJsonObject {
                                    questions.forEach { q ->
                                        val selectedLabel = if (q.multiSelect) (multiAnswers[q.question] ?: emptySet()).firstOrNull() else answers[q.question]
                                        val preview = q.options.firstOrNull { it.label == selectedLabel }?.preview
                                        val note = notes[q.question] ?: ""
                                        if (preview != null || note.isNotBlank()) {
                                            put(q.question, buildJsonObject {
                                                preview?.let { put("preview", JsonPrimitive(it)) }
                                                if (note.isNotBlank()) put("notes", JsonPrimitive(note))
                                            })
                                        }
                                    }
                                })
                            }
                            onToolAnswer(tool.toolCallId, answerPayload.toString())
                        },
                        enabled = questions.all { q ->
                            if (q.multiSelect) {
                                !(multiAnswers[q.question].isNullOrEmpty()) || (otherSelected[q.question] == true && !otherText[q.question].isNullOrBlank())
                            } else {
                                !answers[q.question].isNullOrBlank() || (otherSelected[q.question] == true && !otherText[q.question].isNullOrBlank())
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Tick01,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.chat_message_tool_submit),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        },
    )
}

private data class AskUserOption(
    val label: String,
    val description: String,
    val preview: String?,
)

private data class AskUserQuestion(
    val question: String,
    val header: String,
    val options: List<AskUserOption>,
    val multiSelect: Boolean,
)

@Composable
private fun AskOptionRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 1.dp else 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(4.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AskOtherRow(
    selected: Boolean,
    onToggle: () -> Unit,
    text: String,
    onTextChange: (String) -> Unit,
) {
    Column {
        Surface(
            onClick = onToggle,
            shape = MaterialTheme.shapes.small,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "Other", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (selected) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = false,
                minLines = 1,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun ToolDenyReasonDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.chat_message_tool_deny_dialog_title))
        },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(stringResource(R.string.chat_message_tool_deny_dialog_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2,
                maxLines = 4
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }) {
                Text(stringResource(R.string.chat_message_tool_deny))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
