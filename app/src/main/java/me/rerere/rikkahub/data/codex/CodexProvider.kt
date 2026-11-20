package me.rerere.rikkahub.data.codex

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.openai.ResponseAPI
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.common.http.await
import me.rerere.rikkahub.AppScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody

class CodexProvider(
    private val client: OkHttpClient,
    private val repository: CodexAccountRepository,
    private val json: Json,
    private val scope: AppScope,
) : Provider<ProviderSetting.Codex> {

    override suspend fun listModels(providerSetting: ProviderSetting.Codex): List<Model> =
        withContext(Dispatchers.IO) {
            val account = repository.acquireAccount(providerSetting.rotation)
            val request = Request.Builder()
                .url("$CODEX_API_BASE/models?client_version=$CODEX_CLIENT_VERSION")
                .codexHeaders(account)
                .get()
                .build()
            val response = client.newCall(request).await()
            if (!response.isSuccessful) {
                if (response.code == 401) repository.markInvalid(account.id)
                error("Failed to get Codex models: ${response.code} ${response.body.string()}")
            }
            val models = (json.parseToJsonElement(response.body.string()) as? JsonObject)
                ?.get("models") as? JsonArray
                ?: return@withContext emptyList()
            models.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                if (item["visibility"]?.jsonPrimitive?.contentOrNull != "list") {
                    return@mapNotNull null
                }
                val slug = item["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val modalities = item["input_modalities"]?.jsonArray
                    ?.mapNotNull { modality ->
                        when ((modality as? JsonPrimitive)?.contentOrNull) {
                            "text" -> Modality.TEXT
                            "image" -> Modality.IMAGE
                            else -> null
                        }
                    }
                    ?.ifEmpty { listOf(Modality.TEXT) }
                    ?: listOf(Modality.TEXT, Modality.IMAGE)
                Model(
                    modelId = slug,
                    displayName = item["display_name"]?.jsonPrimitive?.contentOrNull ?: slug,
                    inputModalities = modalities,
                    abilities = buildList {
                        add(ModelAbility.TOOL)
                        if (
                            item["supported_reasoning_levels"]?.jsonArray?.isNotEmpty() == true ||
                            item["supports_reasoning_summaries"]?.jsonPrimitive?.booleanOrNull == true
                        ) {
                            add(ModelAbility.REASONING)
                        }
                    },
                )
            }
        }

    override suspend fun generateText(
        providerSetting: ProviderSetting.Codex,
        messages: List<UIMessage>,
        params: TextGenerationParams,
        onRetry: ((attempt: Int) -> Unit)?,
        ): MessageChunk {
        val account = repository.acquireAccount(providerSetting.rotation, params.model.modelId)
        return responseApiFor(account).generateText(
            providerSetting = syntheticSetting(providerSetting, account),
            messages = withDefaultInstructions(messages),
            params = withCodexParams(params, account, stream = false),
        )
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.Codex,
        messages: List<UIMessage>,
        params: TextGenerationParams,
        onRetry: ((attempt: Int) -> Unit)?,
    ): Flow<MessageChunk> {
        val account = repository.acquireAccount(providerSetting.rotation, params.model.modelId)
        return responseApiFor(account).streamText(
            providerSetting = syntheticSetting(providerSetting, account),
            messages = withDefaultInstructions(messages),
            params = withCodexParams(params, account, stream = true),
        ).catch { throwable ->
            handleStreamQuotaError(account, throwable)
            throw throwable
        }
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): Flow<ImageGenerationItem> {
        error("Image generation is not supported by the Codex provider")
    }

    private fun Request.Builder.codexHeaders(account: CodexAccount): Request.Builder {
        return header("Authorization", "Bearer ${account.accessToken}")
            .header("ChatGPT-Account-Id", account.chatgptAccountId)
            .header("OpenAI-Beta", "responses=experimental")
            .header("originator", "codex_cli_rs")
            .header("User-Agent", CODEX_USER_AGENT)
    }

    private fun syntheticSetting(providerSetting: ProviderSetting.Codex, account: CodexAccount) =
        ProviderSetting.OpenAI(
            id = providerSetting.id,
            enabled = providerSetting.enabled,
            name = providerSetting.name,
            models = providerSetting.models,
            baseUrl = CODEX_API_BASE,
            apiKey = account.accessToken,
            useResponseApi = true,
        )

    private fun withDefaultInstructions(messages: List<UIMessage>): List<UIMessage> =
        if (messages.any { it.role == MessageRole.SYSTEM }) {
            messages
        } else {
            listOf(UIMessage.system(DEFAULT_INSTRUCTIONS)) + messages
        }

    private fun responseApiFor(account: CodexAccount): ResponseAPI {
        val accountAwareClient = client.newBuilder()
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                parseCodexUsage(response.headers)?.let { usage ->
                    scope.launch { repository.updateUsage(account.id, usage) }
                }
                scope.launch {
                    repository.observeQuotaHeaders(account.id, response.headers.toMultimap())
                }
                if (response.code == 429) {
                    handleRateLimitResponse(account, response)
                }
                if (response.code == 401) {
                    scope.launch { repository.markInvalid(account.id) }
                }
                if (response.isSuccessful && response.header("Content-Type") == null) {
                    val body = response.body
                    response.newBuilder()
                        .header("Content-Type", "text/event-stream")
                        .body(
                            body.source().asResponseBody(
                                contentType = "text/event-stream".toMediaType(),
                                contentLength = body.contentLength(),
                            )
                        )
                        .build()
                } else {
                    response
                }
            }
            .build()
        return ResponseAPI(accountAwareClient)
    }

    private fun handleRateLimitResponse(account: CodexAccount, response: Response) {
        val body = runCatching { response.peekBody(MAX_ERROR_BODY_BYTES).string() }.getOrNull()
        val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
        when (
            classifyCodexRateLimit(
                statusCode = response.code,
                errorType = body?.let { parseCodexErrorType(it) },
                retryAfterHeaderSeconds = retryAfterSeconds,
            )
        ) {
            CodexRateLimitKind.QUOTA -> {
                val cooldown = body?.let { parseCodexUsageLimitEvent(it) }
                    ?: retryAfterSeconds?.let { seconds ->
                        (System.currentTimeMillis() + seconds * 1000) to CODEX_USAGE_LIMIT_ERROR
                    }
                    ?: return
                scope.launch { repository.markCooldown(account.id, cooldown.first, cooldown.second) }
            }

            CodexRateLimitKind.RETRY_AFTER -> {
                val seconds = retryAfterSeconds ?: return
                scope.launch {
                    repository.markCooldown(
                        account.id,
                        System.currentTimeMillis() + seconds * 1000,
                        "retry_after",
                    )
                }
            }

            CodexRateLimitKind.TRANSIENT, CodexRateLimitKind.NONE -> Unit
        }
    }

    private fun handleStreamQuotaError(account: CodexAccount, throwable: Throwable) {
        val message = throwable.message ?: return
        if (!message.contains(CODEX_USAGE_LIMIT_ERROR, ignoreCase = true)) return
        val cooldown = parseCodexUsageLimitEvent(message)
            ?: message.indexOf('{').takeIf { it >= 0 }?.let { start ->
                message.lastIndexOf('}').takeIf { it > start }
                    ?.let { end -> parseCodexUsageLimitEvent(message.substring(start, end + 1)) }
            }
            ?: return
        scope.launch { repository.markCooldown(account.id, cooldown.first, cooldown.second) }
    }

    private fun parseCodexErrorType(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val error = root["error"] as? JsonObject
            ?: (root["body"] as? JsonObject)?.get("error") as? JsonObject
        val payload = error ?: (root["body"] as? JsonObject) ?: root
        payload["type"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private companion object {
        const val CODEX_API_BASE = "${CodexAccountRepository.CODEX_BASE_URL}/codex"
        const val DEFAULT_INSTRUCTIONS = "You are a helpful assistant."
        const val MAX_ERROR_BODY_BYTES = 64L * 1024
        const val CODEX_USAGE_LIMIT_ERROR = "usage_limit_reached"
    }
}

internal const val CODEX_CLIENT_VERSION = "0.144.5"

internal val CODEX_USER_AGENT =
    "codex_cli_rs/$CODEX_CLIENT_VERSION (Android ${Build.VERSION.RELEASE}; " +
        "${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64"})"

internal fun codexReasoningEffort(level: ReasoningLevel): String? {
    return when (level) {
        ReasoningLevel.AUTO -> null
        ReasoningLevel.LOW -> "low"
        ReasoningLevel.MEDIUM -> "medium"
        ReasoningLevel.HIGH -> "high"
        ReasoningLevel.XHIGH -> "xhigh"
        ReasoningLevel.MAX -> "max"
        ReasoningLevel.OFF -> "none"
    }
}

internal fun withCodexParams(
    params: TextGenerationParams,
    account: CodexAccount,
    stream: Boolean,
): TextGenerationParams {
    val reasoningEffort = params.model.abilities
        .takeIf { it.contains(ModelAbility.REASONING) }
        ?.let { codexReasoningEffort(params.reasoningLevel) }
    return params.copy(
        maxTokens = null,
        customHeaders = params.customHeaders + buildList {
            add(CustomHeader("ChatGPT-Account-Id", account.chatgptAccountId))
            add(CustomHeader("OpenAI-Beta", "responses=experimental"))
            add(CustomHeader("originator", "codex_cli_rs"))
            add(CustomHeader("User-Agent", CODEX_USER_AGENT))
            if (stream) add(CustomHeader("Accept", "text/event-stream"))
        },
        customBody = params.customBody + listOfNotNull(
            reasoningEffort?.let { effort ->
                CustomBody(
                    key = "reasoning",
                    value = buildJsonObject {
                        put("effort", effort)
                        put("summary", "auto")
                    },
                )
            },
        ),
    )
}
