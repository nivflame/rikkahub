package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.SourceCode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeVisualTransformation
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.components.ui.ExpressiveEmptyState

private val jsonLenient = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
}

@Composable
fun CustomHeaders(
    headers: List<CustomHeader>,
    onUpdate: (List<CustomHeader>) -> Unit,
    showAddButton: Boolean = true,
    emptyStateModifier: Modifier = Modifier,
) {
    if (headers.isEmpty()) {
        ExpressiveEmptyState(
            shape = MaterialShapes.Sunny.toShape(),
            icon = HugeIcons.SourceCode,
            title = "No custom headers",
            hint = "Headers sent with every request",
            modifier = emptyStateModifier,
            onAdd = if (showAddButton) {
                {
                    val updatedHeaders = headers.toMutableList()
                    updatedHeaders.add(CustomHeader("", ""))
                    onUpdate(updatedHeaders)
                }
            } else null,
        )
        return
    }
    CardGroup {
        headers.forEachIndexed { index, header ->
            item(
                supportingContent = {
                    var headerName by remember(header.name) { mutableStateOf(header.name) }
                    var headerValue by remember(header.value) { mutableStateOf(header.value) }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = headerName,
                            onValueChange = {
                                headerName = it
                                val updatedHeaders = headers.toMutableList()
                                updatedHeaders[index] = updatedHeaders[index].copy(name = it.trim())
                                onUpdate(updatedHeaders)
                            },
                            label = { Text(stringResource(R.string.assistant_page_header_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                        )
                        OutlinedTextField(
                            value = headerValue,
                            onValueChange = {
                                headerValue = it
                                val updatedHeaders = headers.toMutableList()
                                updatedHeaders[index] =
                                    updatedHeaders[index].copy(value = it.trim())
                                onUpdate(updatedHeaders)
                            },
                            label = { Text(stringResource(R.string.assistant_page_header_value)) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                        )
                    }
                },
                trailingContent = {
                    ReorderDeleteActions(
                        canMoveUp = index > 0,
                        canMoveDown = index < headers.lastIndex,
                        onMoveUp = { onUpdate(headers.moved(index, -1)) },
                        onMoveDown = { onUpdate(headers.moved(index, 1)) },
                        onDelete = { onUpdate(headers.filterIndexed { i, _ -> i != index }) },
                        deleteContentDescription = stringResource(R.string.assistant_page_delete_header),
                    )
                },
                headlineContent = {},
            )
        }
    }
}

@Composable
fun CustomBodies(
    customBodies: List<CustomBody>,
    onUpdate: (List<CustomBody>) -> Unit,
    showAddButton: Boolean = true,
    emptyStateModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyStates = customBodies.map { body ->
        remember(body.key) { mutableStateOf(body.key) }
    }
    val valueStates = customBodies.map { body ->
        remember(body.value) {
            mutableStateOf(jsonLenient.encodeToString(JsonElement.serializer(), body.value))
        }
    }
    val errorStates = customBodies.map { body ->
        remember(body) { mutableStateOf<String?>(null) }
    }

    if (customBodies.isEmpty()) {
        ExpressiveEmptyState(
            shape = MaterialShapes.Ghostish.toShape(),
            icon = HugeIcons.SourceCode,
            title = "No custom bodies",
            hint = "JSON request bodies appended to every request",
            modifier = emptyStateModifier,
            onAdd = if (showAddButton) {
                {
                    val updatedBodies = customBodies.toMutableList()
                    updatedBodies.add(CustomBody("", JsonPrimitive("")))
                    onUpdate(updatedBodies)
                }
            } else null,
        )
        return
    }

    val highlighter = LocalHighlighter.current
    val darkMode = LocalDarkMode.current
    val jsonTransformation = remember(highlighter, darkMode) {
        HighlightCodeVisualTransformation(
            language = "json",
            highlighter = highlighter,
            darkMode = darkMode,
        )
    }

    CardGroup {
        customBodies.forEachIndexed { index, body ->
            item(
                supportingContent = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = keyStates[index].value,
                            onValueChange = {
                                keyStates[index].value = it
                                val updatedBodies = customBodies.toMutableList()
                                updatedBodies[index] = updatedBodies[index].copy(key = it.trim())
                                onUpdate(updatedBodies)
                            },
                            label = { Text(stringResource(R.string.assistant_page_body_key)) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                        )
                        OutlinedTextField(
                            value = valueStates[index].value,
                            onValueChange = { newString ->
                                valueStates[index].value = newString
                                try {
                                    val newJsonValue = jsonLenient.parseToJsonElement(newString)
                                    val updatedBodies = customBodies.toMutableList()
                                    updatedBodies[index] =
                                        updatedBodies[index].copy(value = newJsonValue)
                                    onUpdate(updatedBodies)
                                    errorStates[index].value = null
                                } catch (e: Exception) {
                                    errorStates[index].value =
                                        context.getString(
                                            R.string.assistant_page_invalid_json,
                                            e.message?.take(100) ?: ""
                                        )
                                }
                            },
                            label = { Text(stringResource(R.string.assistant_page_body_value)) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = errorStates[index].value != null,
                            supportingText = {
                                if (errorStates[index].value != null) {
                                    Text(errorStates[index].value!!)
                                }
                            },
                            minLines = 1,
                            maxLines = 1,
                            visualTransformation = jsonTransformation,
                            textStyle = LocalTextStyle.current.merge(fontFamily = JetbrainsMono),
                        )
                    }
                },
                trailingContent = {
                    ReorderDeleteActions(
                        canMoveUp = index > 0,
                        canMoveDown = index < customBodies.lastIndex,
                        onMoveUp = { onUpdate(customBodies.moved(index, -1)) },
                        onMoveDown = { onUpdate(customBodies.moved(index, 1)) },
                        onDelete = { onUpdate(customBodies.filterIndexed { i, _ -> i != index }) },
                        deleteContentDescription = stringResource(R.string.assistant_page_delete_body),
                    )
                },
                headlineContent = {},
            )
        }
    }
}

private fun <T> List<T>.moved(index: Int, offset: Int): List<T> {
    val updated = toMutableList()
    val other = updated.removeAt(index + offset)
    updated.add(index, other)
    return updated
}

@Composable
private fun ReorderDeleteActions(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    deleteContentDescription: String,
) {
    Column {
        IconButton(enabled = canMoveUp, onClick = onMoveUp) {
            Icon(HugeIcons.ArrowUp01, contentDescription = "Move up")
        }
        IconButton(enabled = canMoveDown, onClick = onMoveDown) {
            Icon(HugeIcons.ArrowDown01, contentDescription = "Move down")
        }
        IconButton(onClick = onDelete) {
            Icon(
                HugeIcons.Delete01,
                contentDescription = deleteContentDescription,
            )
        }
    }
}
