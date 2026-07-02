package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle

class SlashCommandTransformation(
    private val backgroundColor: Color,
    private val textColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.isEmpty() || text.first() != '/') {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val end = text.indexOfFirst { it.isWhitespace() }.let { if (it == -1) text.length else it }
        if (end <= 1) return TransformedText(text, OffsetMapping.Identity)

        val transformed = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    background = backgroundColor,
                    color = textColor,
                ),
            ) {
                append(text.substring(0, end))
            }
            append(text.substring(end))
        }

        return TransformedText(transformed, OffsetMapping.Identity)
    }
}

@Composable
fun rememberSlashCommandTransformation(): SlashCommandTransformation {
    val backgroundColor = MaterialTheme.colorScheme.primaryContainer
    val textColor = MaterialTheme.colorScheme.onPrimaryContainer
    return remember(backgroundColor, textColor) {
        SlashCommandTransformation(backgroundColor, textColor)
    }
}
