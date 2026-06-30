package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

val DEFAULT_AUTO_MODEL_ID = Uuid.parse("b7055fb4-39f9-4042-a88a-0d80ed76cf08")

val DEFAULT_PROVIDERS: List<ProviderSetting> = listOf(
    ProviderSetting.OpenAI(
        id = Uuid.parse("c8f3e2a1-9b4d-4e7c-8f6a-2d1e0c9b7a3f"),
        name = "Opencode",
        baseUrl = "https://opencode.ai/zen/v1",
        models = listOf(
            Model(
                modelId = "deepseek-v4-flash-free",
                displayName = "DeepSeek V4 Flash Free",
                abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
            ),
        ),
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("d5e7f8a2-3b6c-4d8e-9f0a-1b2c3d4e5f6a"),
        name = "Fireworks",
        baseUrl = "https://api.fireworks.ai/inference/v1",
    ),
)
