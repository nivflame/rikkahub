package me.rerere.ai.provider

import kotlinx.serialization.Serializable
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

@Serializable
data class ProxyConfig(
    val enabled: Boolean = false,
    val address: String = "",
    val username: String = "",
    val password: String = "",
) {
    fun toJavaProxy(): Proxy? {
        val trimmed = address.trim()
        if (!enabled || trimmed.isBlank()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: return null
        val scheme = uri.scheme?.lowercase()
        return when (scheme) {
            null, "http", "https" -> Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port))
            "socks5", "socks" -> Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(host, port))
            else -> null
        }
    }
}
