package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Delete01
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.Alignment
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

private val jsonLenient = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
}

@Composable
fun CustomHeaders(headers: List<CustomHeader>, onUpdate: (List<CustomHeader>) -> Unit) {
    CardGroup(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.assistant_page_custom_headers))
                FilledTonalIconButton(
                    onClick = {
                        val updatedHeaders = headers.toMutableList()
                        updatedHeaders.add(CustomHeader("", ""))
                        onUpdate(updatedHeaders)
                    }
                ) {
                    Icon(
                        HugeIcons.Add01,
                        contentDescription = stringResource(R.string.assistant_page_add_header)
                    )
                }
            }
        }
    ) {
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
                            modifier = Modifier.fillMaxWidth()
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                trailingContent = {
                    Row {
                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                val updatedHeaders = headers.toMutableList()
                                val previous = updatedHeaders.removeAt(index - 1)
                                updatedHeaders.add(index, previous)
                                onUpdate(updatedHeaders)
                            },
                        ) {
                            Icon(HugeIcons.ArrowUp01, contentDescription = "Move up")
                        }
                        IconButton(
                            enabled = index < headers.lastIndex,
                            onClick = {
                                val updatedHeaders = headers.toMutableList()
                                val next = updatedHeaders.removeAt(index + 1)
                                updatedHeaders.add(index, next)
                                onUpdate(updatedHeaders)
                            },
                        ) {
                            Icon(HugeIcons.ArrowDown01, contentDescription = "Move down")
                        }
                        IconButton(onClick = {
                            val updatedHeaders = headers.toMutableList()
                            updatedHeaders.removeAt(index)
                            onUpdate(updatedHeaders)
                        }) {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = stringResource(R.string.assistant_page_delete_header)
                            )
                        }
                    }
                },
                headlineContent = {},
            )
        }
    }
}

@Composable
fun CustomBodies(customBodies: List<CustomBody>, onUpdate: (List<CustomBody>) -> Unit) {
    val context = LocalContext.current
    val keyStates = customBodies.map { body ->
        remember(body.key) { mutableStateOf(body.key) }
    }
    val valueStates = customBodies.map { body ->
        remember(body.value) {
            mutableStateOf(jsonLenient.encodeToString(JsonElement.serializer(), body.value))
        }
    }

    CardGroup(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.assistant_page_custom_bodies))
                FilledTonalIconButton(
                    onClick = {
                        val updatedBodies = customBodies.toMutableList()
                        updatedBodies.add(CustomBody("", JsonPrimitive("")))
                        onUpdate(updatedBodies)
                    }
                ) {
                    Icon(
                        HugeIcons.Add01,
                        contentDescription = stringResource(R.string.assistant_page_add_body)
                    )
                }
            }
        }
    ) {
        customBodies.forEachIndexed { index, body ->
            var jsonParseError by remember { mutableStateOf<String?>(null) }
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
                            modifier = Modifier.fillMaxWidth()
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
                                    jsonParseError = null
                                } catch (e: Exception) {
                                    jsonParseError =
                                        context.getString(
                                            R.string.assistant_page_invalid_json,
                                            e.message?.take(100) ?: ""
                                        )
                                }
                            },
                            label = { Text(stringResource(R.string.assistant_page_body_value)) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = jsonParseError != null,
                            supportingText = {
                                if (jsonParseError != null) {
                                    Text(jsonParseError!!)
                                }
                            },
                            minLines = 3,
                            maxLines = 5,
                            visualTransformation = HighlightCodeVisualTransformation(
                                language = "json",
                                highlighter = LocalHighlighter.current,
                                darkMode = LocalDarkMode.current
                            ),
                            textStyle = LocalTextStyle.current.merge(fontFamily = JetbrainsMono),
                        )
                    }
                },
                trailingContent = {
                    Row {
                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                val updatedBodies = customBodies.toMutableList()
                                val previous = updatedBodies.removeAt(index - 1)
                                updatedBodies.add(index, previous)
                                onUpdate(updatedBodies)
                            },
                        ) {
                            Icon(HugeIcons.ArrowUp01, contentDescription = "Move up")
                        }
                        IconButton(
                            enabled = index < customBodies.lastIndex,
                            onClick = {
                                val updatedBodies = customBodies.toMutableList()
                                val next = updatedBodies.removeAt(index + 1)
                                updatedBodies.add(index, next)
                                onUpdate(updatedBodies)
                            },
                        ) {
                            Icon(HugeIcons.ArrowDown01, contentDescription = "Move down")
                        }
                        IconButton(onClick = {
                            val updatedBodies = customBodies.toMutableList()
                            updatedBodies.removeAt(index)
                            onUpdate(updatedBodies)
                        }) {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = stringResource(R.string.assistant_page_delete_body)
                            )
                        }
                    }
                },
                headlineContent = {},
            )
        }
    }
}
