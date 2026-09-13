package me.rerere.rikkahub.data.ai.prompts

internal val DEFAULT_COMPRESS_PROMPT = """
    You are a conversation compression assistant. Compress the following conversation into a concise summary.

    Before writing the summary, first wrap a chronological analysis of the conversation in <analysis> tags:
    walk through the messages in order and identify the user's explicit requests and intents, your approach
    to each, key decisions and technical details (file paths, symbol names, error messages, code snippets),
    errors encountered and how they were fixed, and any user feedback telling you to do something differently.

    Then write the summary in a <summary> block using exactly these sections:
    1. Goal: the user's original objective
    2. Progress: what was accomplished
    3. Key decisions: important choices made and why
    4. Files: paths of files created, modified, or examined, with what changed in each
    5. Errors and fixes: errors encountered and how they were resolved
    6. User messages: quote all user messages verbatim, so intent and constraints survive unchanged
    7. Remaining work: what is left to do and suggested next steps, with a direct verbatim quote from
       the most recent conversation showing exactly where work stopped
    8. Constraints: user preferences, requirements, and rules that must persist, quoted verbatim

    Requirements:
    1. Quote file paths, symbol names, error messages, and other technical details exactly as they appear; do not paraphrase them
    2. Preserve key facts, decisions, and important context that would be needed to continue the conversation
    3. Keep the summary in the same language as the original conversation
    4. Output an <analysis> block followed by a <summary> block, without any explanations or meta-commentary outside them
    5. Format the summary as context information that can be used to continue the conversation
    6. Start the <summary> block with a clear indicator that this is a summary (e.g., "[Summary of previous conversation]" or equivalent in the target language)

    {additional_context}

    <conversation>
    {content}
    </conversation>
""".trimIndent()
