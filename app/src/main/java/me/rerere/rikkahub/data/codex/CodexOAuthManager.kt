package me.rerere.rikkahub.data.codex

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.util.Log
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rerere.common.http.await
import me.rerere.rikkahub.data.ai.mcp.launchOAuthAuthorization
import me.rerere.rikkahub.R
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class CodexOAuthManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: OkHttpClient,
    private val repository: CodexAccountRepository,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var callbackPort: Int? = null
    private val sessions = ConcurrentHashMap<String, OAuthSession>()
    private val _status = MutableStateFlow<CodexOAuthStatus>(CodexOAuthStatus.Idle)
    val status: StateFlow<CodexOAuthStatus> = _status.asStateFlow()

    fun startLogin() {
        val state = randomUrlSafe(32)
        try {
            val verifier = randomUrlSafe(64)
            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray())
            )
            val port = ensureCallbackServer()
            val redirect = "http://localhost:$port/auth/callback"
            sessions[state] = OAuthSession(
                verifier = verifier,
                redirectUri = redirect,
            )
            _status.value = CodexOAuthStatus.Waiting

            val authUrl = Uri.parse(AUTHORIZE_URL).buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("redirect_uri", redirect)
                .appendQueryParameter("scope", DEFAULT_SCOPES)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("id_token_add_organizations", "true")
                .appendQueryParameter("codex_cli_simplified_flow", "true")
                .appendQueryParameter("originator", "codex_cli_rs")
                .build()
            launchOAuthAuthorization(context, authUrl.toString())
        } catch (error: Throwable) {
            sessions.remove(state)
            _status.value = CodexOAuthStatus.Error(
                if (error.message == CALLBACK_PORTS_UNAVAILABLE) {
                    context.getString(R.string.codex_oauth_ports_unavailable)
                } else {
                    error.message ?: "Unable to open the OpenAI sign-in page"
                }
            )
        }
    }

    fun consumeResult() {
        stopCallbackServer()
        _status.value = CodexOAuthStatus.Idle
    }

    @Synchronized
    private fun stopCallbackServer() {
        server?.stop(500, 1000)
        server = null
        callbackPort = null
        sessions.clear()
    }

    @Synchronized
    private fun ensureCallbackServer(): Int {
        callbackPort?.let { return it }
        var lastError: Throwable? = null
        for (port in CALLBACK_PORTS) {
            try {
                server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
                    routing {
                        get("/auth/callback") {
                            val callbackState = call.request.queryParameters["state"]
                            val code = call.request.queryParameters["code"]
                            val error = call.request.queryParameters["error"]
                            val session = callbackState?.let(sessions::remove)
                            when {
                                session == null -> {
                                    _status.value = CodexOAuthStatus.Error("OAuth state mismatch")
                                    call.respondText(callbackPage(false), ContentType.Text.Html)
                                }

                                !error.isNullOrBlank() -> {
                                    _status.value = CodexOAuthStatus.Error(error)
                                    call.respondText(callbackPage(false), ContentType.Text.Html)
                                }

                                code.isNullOrBlank() -> {
                                    _status.value = CodexOAuthStatus.Error("Missing authorization code")
                                    call.respondText(callbackPage(false), ContentType.Text.Html)
                                }

                                else -> {
                                    call.respondText(callbackPage(true), ContentType.Text.Html)
                                    scope.launch {
                                        try {
                                            awaitNetworkUnblocked()
                                            val account = exchangeCode(code, session)
                                            _status.value = CodexOAuthStatus.Success(account.id)
                                            runCatching { repository.refreshAccount(account.id) }
                                        } catch (error: Throwable) {
                                            Log.e(
                                                TAG,
                                                "OAuth token exchange failed: " +
                                                    "${error::class.java.name}: ${error.message}",
                                                error,
                                            )
                                            _status.value = CodexOAuthStatus.Error(
                                                error.message ?: "OAuth token exchange failed"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.start(wait = false)
                callbackPort = port
                return port
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IllegalStateException(CALLBACK_PORTS_UNAVAILABLE, lastError)
    }

    private suspend fun awaitNetworkUnblocked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        suspendCancellableCoroutine { continuation ->
            lateinit var callback: ConnectivityManager.NetworkCallback
            callback = object : ConnectivityManager.NetworkCallback() {
                override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                    if (!blocked && continuation.isActive) {
                        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                        continuation.resume(Unit)
                    }
                }
            }
            continuation.invokeOnCancellation {
                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            }
            connectivityManager.registerDefaultNetworkCallback(callback)
        }
    }

    private suspend fun exchangeCode(code: String, session: OAuthSession): CodexAccount {
        val response = client.newCall(
            Request.Builder()
                .url(TOKEN_URL)
                .post(
                    FormBody.Builder()
                        .add("grant_type", "authorization_code")
                        .add("client_id", CLIENT_ID)
                        .add("code", code)
                        .add("redirect_uri", session.redirectUri)
                        .add("code_verifier", session.verifier)
                        .build()
                )
                .build()
        ).await()
        val body = response.body.string()
        if (!response.isSuccessful) {
            error("Token exchange failed: ${response.code}")
        }
        return repository.saveLogin(body)
    }

    private fun callbackPage(success: Boolean): String {
        val status = if (success) "success" else "error"
        val deepLink = "rikkahub://codex/oauth?status=${URLEncoder.encode(status, Charsets.UTF_8.name())}"
        val icon = if (success) {
            """<svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path pathLength="1" class="draw" d="M20 6 9 17l-5-5"/></svg>"""
        } else {
            """<svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path pathLength="1" class="draw" d="M6 6l12 12M18 6 6 18"/></svg>"""
        }
        val headline = if (success) "Authorization complete" else "Authorization failed"
        val supporting = if (success) {
            "The account has been connected successfully."
        } else {
            "The sign-in did not complete. Try again."
        }
        return """
            <!doctype html>
            <html>
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta http-equiv="refresh" content="0; url=$deepLink">
                <title>RikkaHub</title>
                <style>
                  :root {
                    --surface: #FEF7FF;
                    --on-surface: #1D1B20;
                    --on-surface-variant: #49454F;
                    --secondary-container: #E8DEF8;
                    --on-secondary-container: #1D1926;
                    --tonal-outline: #CAC4D0;
                  }
                  @media (prefers-color-scheme: dark) {
                    :root {
                      --surface: #141218;
                      --on-surface: #E6E0E9;
                      --on-surface-variant: #CAC4D0;
                      --secondary-container: #4A4458;
                      --on-secondary-container: #E8DEF8;
                      --tonal-outline: #49454F;
                    }
                  }
                  * { margin: 0; padding: 0; box-sizing: border-box; }
                  body {
                    background: var(--surface);
                    color: var(--on-surface);
                    font-family: Roboto, "Segoe UI", system-ui, sans-serif;
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    padding: 24px;
                  }
                  .card { text-align: center; max-width: 420px; }
                  .icon-wrap {
                    width: 96px;
                    height: 96px;
                    border-radius: 50%;
                    background: var(--secondary-container);
                    color: var(--on-secondary-container);
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    margin-bottom: 24px;
                  }
                  .draw {
                    stroke-dasharray: 1;
                    stroke-dashoffset: 1;
                    animation: draw 300ms ease-out forwards;
                  }
                  @keyframes draw {
                    to { stroke-dashoffset: 0; }
                  }
                  h1 { font-size: 24px; font-weight: 400; margin-bottom: 8px; }
                  p { font-size: 14px; line-height: 20px; color: var(--on-surface-variant); }
                  .hint { margin-top: 12px; font-size: 13px; }
                </style>
              </head>
              <body>
                <main class="card">
                  <div class="icon-wrap">$icon</div>
                  <h1>$headline</h1>
                  <p>$supporting</p>
                  <p class="hint">You can close this tab and return to RikkaHub.</p>
                </main>
                <script>
                  window.location.replace("$deepLink");
                  setTimeout(function () { window.location.href = "$deepLink"; }, 500);
                </script>
              </body>
            </html>
        """.trimIndent()
    }

    private fun randomUrlSafe(size: Int): String {
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private const val TAG = "CodexOAuthManager"
        private const val CALLBACK_PORTS_UNAVAILABLE = "OAuth callback ports are unavailable"
        const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        const val TOKEN_URL = "https://auth.openai.com/oauth/token"
        const val AUTHORIZE_URL = "https://auth.openai.com/oauth/authorize"
        const val DEFAULT_SCOPES = "openid profile email offline_access"
        const val REFRESH_SCOPES = "openid profile email"
        private val CALLBACK_PORTS = listOf(1455, 1457)
    }
}

private data class OAuthSession(
    val verifier: String,
    val redirectUri: String,
)

sealed interface CodexOAuthStatus {
    data object Idle : CodexOAuthStatus
    data object Waiting : CodexOAuthStatus
    data class Success(val accountId: String) : CodexOAuthStatus
    data class Error(val message: String) : CodexOAuthStatus
}
