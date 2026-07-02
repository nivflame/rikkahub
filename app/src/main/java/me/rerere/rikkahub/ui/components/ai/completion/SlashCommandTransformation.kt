package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle

class SlashCommandOutputTransformation(
    private val backgroundColor: Color,
    private val textColor: Color,
) : OutputTransformation {
    override fun transformOutput(text: TextFieldBuffer) {
        if (text.length == 0 || text[0] != '/') return

        val end = text.indexOfFirst { it.isWhitespace() }.let { if (it == -1) text.length else it }
        if (end <= 1) return

        text.setSpanStyle(
            style = SpanStyle(
                background = backgroundColor,
                color = textColor,
            ),
            start = 0,
            end = end,
        )
    }
}

@Composable
fun rememberSlashCommandTransformation(): SlashCommandOutputTransformation {
    val backgroundColor = MaterialTheme.colorScheme.primaryContainer
    val textColor = MaterialTheme.colorScheme.onPrimaryContainer
    return remember(backgroundColor, textColor) {
        SlashCommandOutputTransformation(backgroundColor, textColor)
    }
}
