package me.rerere.ai.provider

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap

fun OkHttpClient.forProxy(config: ProxyConfig): OkHttpClient {
    if (!config.enabled) return this
    val proxy = config.toJavaProxy()
        ?: error("Proxy is enabled but the address is invalid")
    val key = this to config.copy(address = config.address.trim())
    return clientCache.getOrPut(key) {
        newBuilder().apply {
            proxy(proxy)
            config.proxyAuthenticator()?.let { proxyAuthenticator(it) }
        }.build()
    }
}

fun ProxyConfig.proxyAuthenticator(): Authenticator? {
    val proxy = toJavaProxy() ?: return null
    if (proxy.type() != Proxy.Type.HTTP || username.isBlank()) return null
    return Authenticator { _, response ->
        if (response.request.header("Proxy-Authorization") != null) null
        else response.request.newBuilder()
            .header("Proxy-Authorization", Credentials.basic(username, password))
            .build()
    }
}

private val clientCache = ConcurrentHashMap<Pair<OkHttpClient, ProxyConfig>, OkHttpClient>()
