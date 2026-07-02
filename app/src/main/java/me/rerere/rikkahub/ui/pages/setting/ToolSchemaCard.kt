package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Edit01

@Composable
fun ToolSchemaCard(
    tool: Tool,
    modifier: Modifier = Modifier,
    onEditDescription: ((String) -> Unit)? = null,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(tool.description) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Description", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (onEditDescription != null) {
                IconButton(onClick = {
                    draft = tool.description
                    editing = true
                }) {
                    Icon(imageVector = HugeIcons.Edit01, contentDescription = "Edit description")
                }
            }
        }
        Text(
            text = tool.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val schema = runCatching { tool.parameters() }.getOrNull()
        (schema as? InputSchema.Obj)?.let { obj ->
            if (obj.properties.isNotEmpty()) {
                Text("Parameters", style = MaterialTheme.typography.titleSmall)
                obj.properties.forEach { (name, prop) ->
                    ParamRow(
                        name = name,
                        prop = prop,
                        required = obj.required?.contains(name) == true,
                        depth = 0,
                    )
                }
            }
        }
    }
    if (editing && onEditDescription != null) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit description") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall,
                    minLines = 4,
                    maxLines = 12,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onEditDescription(draft)
                    editing = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ParamRow(
    name: String,
    prop: JsonElement,
    required: Boolean,
    depth: Int,
) {
    val obj = prop.jsonObject
    val type = obj["type"]?.jsonPrimitive?.contentOrNull
    val description = obj["description"]?.jsonPrimitive?.contentOrNull
    Column(
        modifier = Modifier.padding(start = (depth * 12).dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            if (type != null) {
                Text(
                    text = " : $type",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (required) {
                Text(
                    text = " *",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (type == "array") {
            obj["items"]?.jsonObject?.let { items ->
                items["properties"]?.jsonObject?.forEach { (subName, subProp) ->
                    ParamRow(
                        name = subName,
                        prop = subProp,
                        required = items["required"]?.jsonArray?.any { it.jsonPrimitive.content == subName } == true,
                        depth = depth + 1,
                    )
                }
            }
        } else if (type == "object") {
            obj["properties"]?.jsonObject?.forEach { (subName, subProp) ->
                ParamRow(
                    name = subName,
                    prop = subProp,
                    required = obj["required"]?.jsonArray?.any { it.jsonPrimitive.content == subName } == true,
                    depth = depth + 1,
                )
            }
        }
    }
}
