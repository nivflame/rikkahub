package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("time_info")
    data object TimeInfo : LocalToolOption()

    @Serializable
    @SerialName("tts")
    data object Tts : LocalToolOption()

    @Serializable
    @SerialName("ask_user")
    data object AskQuestion : LocalToolOption()

    @Serializable
    @SerialName("browser")
    data object Browser : LocalToolOption()

    @Serializable
    @SerialName("subagent")
    data object Subagent : LocalToolOption()

    @Serializable
    @SerialName("skill")
    data object Skill : LocalToolOption()
}
