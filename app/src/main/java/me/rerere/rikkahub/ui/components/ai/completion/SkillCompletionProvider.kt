package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.ui.text.TextRange
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.rikkahub.data.files.SkillMetadata

class SkillCompletionProvider(
    private val skills: List<SkillMetadata>,
) : ChatCompletionProvider {
    override val id: String = "skills"

    override suspend fun complete(context: ChatCompletionContext): ChatCompletionList? {
        if (context.hasSelection || skills.isEmpty()) return null
        val mention = findSkillMention(context.text, context.cursor) ?: return null
        val query = mention.query.lowercase()
        val items = skills
            .filter { query.isBlank() || it.name.lowercase().contains(query) }
            .map { skill ->
                ChatCompletionItem(
                    label = "/${skill.name}",
                    insertText = "/${skill.name} ",
                    detail = skill.description,
                    icon = HugeIcons.Puzzle,
                )
            }
        if (items.isEmpty()) return null
        return ChatCompletionList(
            providerId = id,
            replacementRange = mention.range,
            items = items,
        )
    }

    private data class SkillMention(
        val query: String,
        val range: TextRange,
    )

    private fun findSkillMention(text: String, cursor: Int): SkillMention? {
        if (cursor < 0 || cursor > text.length) return null
        val prefix = text.substring(0, cursor)
        val start = prefix.lastIndexOf('/')
        if (start != 0) return null
        val query = prefix.substring(start + 1)
        if (query.any { it.isWhitespace() }) return null
        return SkillMention(
            query = query,
            range = TextRange(start, cursor),
        )
    }
}
