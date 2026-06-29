package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

val DEFAULT_AUTO_MODEL_ID = Uuid.parse("b7055fb4-39f9-4042-a88a-0d80ed76cf08")

val DEFAULT_PROVIDERS: List<ProviderSetting> = listOf(
    ProviderSetting.OpenAI(
        id = Uuid.parse("c8f3e2a1-9b4d-4e7c-8f6a-2d1e0c9b7a3f"),
        name = "Opencode",
        baseUrl = "https://opencode.ai/zen/v1",
    ),
)
