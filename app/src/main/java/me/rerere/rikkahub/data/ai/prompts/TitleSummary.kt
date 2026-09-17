package me.rerere.rikkahub.data.ai.prompts

internal val DEFAULT_TITLE_PROMPT = """
    Generate a concise, sentence-case title (3-7 words) that captures the main topic or goal of this coding session. The title should be clear enough that the user recognizes the session in a list. Use sentence case: capitalize only the first word and proper nouns

    Good examples:
    - Fix login button on mobile
    - Add OAuth authentication
    - Debug failing CI tests
    - Refactor API client error handling

    Bad examples:
    - Code changes (too vague)
    - Investigate and fix the issue where the login button does not respond on mobile devices (too long)
    - Fix Login Button On Mobile (wrong case)

    <content>
    {content}
    </content>
""".trimIndent()
