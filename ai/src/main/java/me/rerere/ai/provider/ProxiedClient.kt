package me.rerere.ai.provider

import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap

fun OkHttpClient.forProxy(config: ProxyConfig): OkHttpClient {
    val proxy = config.toJavaProxy() ?: return this
    val key = this to config.copy(address = config.address.trim())
    return clientCache.getOrPut(key) {
        newBuilder().apply {
            proxy(proxy)
            if (proxy.type() == Proxy.Type.HTTP && config.username.isNotBlank()) {
                proxyAuthenticator { _, response ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", Credentials.basic(config.username, config.password))
                        .build()
                }
            }
        }.build()
    }
}

private val clientCache = ConcurrentHashMap<Pair<OkHttpClient, ProxyConfig>, OkHttpClient>()
