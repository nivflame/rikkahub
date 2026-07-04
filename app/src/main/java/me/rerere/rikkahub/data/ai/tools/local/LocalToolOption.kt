package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("ask_user")
    data object AskQuestion : LocalToolOption()

    @Serializable
    @SerialName("browser")
    data object Browser : LocalToolOption()

    @Serializable
    @SerialName("web_search")
    data object WebSearch : LocalToolOption()

    @Serializable
    @SerialName("web_fetch")
    data object WebFetch : LocalToolOption()

    @Serializable
    @SerialName("subagent")
    data object Subagent : LocalToolOption()

    @Serializable
    @SerialName("skill")
    data object Skill : LocalToolOption()

    @Serializable
    @SerialName("tool_search")
    data object ToolSearch : LocalToolOption()
}
