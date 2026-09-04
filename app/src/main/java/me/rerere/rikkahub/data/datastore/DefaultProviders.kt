package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

val DEFAULT_AUTO_MODEL_ID = Uuid.parse("b7055fb4-39f9-4042-a88a-0d80ed76cf08")

val DEFAULT_PROVIDERS: List<ProviderSetting> = listOf(
    ProviderSetting.OpenAI(
        id = Uuid.parse("c8f3e2a1-9b4d-4e7c-8f6a-2d1e0c9b7a3f"),
        name = "Opencode",
        baseUrl = "https://opencode.ai/zen/v1",
        enabled = false,
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("b448af34-f539-501d-9d93-59ff8a0b5761"),
        name = "Ollama",
        baseUrl = "https://ollama.com/v1",
        enabled = false,
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("51f03656-b929-542f-98f3-4abdfd2dc12e"),
        name = "Nvidia",
        baseUrl = "https://integrate.api.nvidia.com/v1",
        enabled = false,
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("0cba13e5-1072-5998-a122-b988f38c0067"),
        name = "Kilo",
        baseUrl = "https://api.kilo.ai/api/gateway",
        enabled = false,
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("e21ac69a-a6a4-5190-8198-45081bc92868"),
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        enabled = false,
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("7d5f3a2c-8b41-4e96-9c3a-1f2e8d6b4a70"),
        name = "Cline",
        baseUrl = "https://api.cline.bot/api/v1",
        enabled = false,
    ),
    ProviderSetting.Codex(
        id = Uuid.parse("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
        name = "Codex",
        enabled = false,
    ),
)
