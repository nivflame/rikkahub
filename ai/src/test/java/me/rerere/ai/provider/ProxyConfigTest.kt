package me.rerere.ai.provider

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy

class ProxyConfigTest {

    @Test
    fun `disabled proxy returns null`() {
        assertNull(ProxyConfig(enabled = false, address = "http://127.0.0.1:8080").toJavaProxy())
    }

    @Test
    fun `blank address returns null`() {
        assertNull(ProxyConfig(enabled = true, address = "   ").toJavaProxy())
    }

    @Test
    fun `garbage address returns null`() {
        assertNull(ProxyConfig(enabled = true, address = "not a url").toJavaProxy())
        assertNull(ProxyConfig(enabled = true, address = "http://").toJavaProxy())
    }

    @Test
    fun `http address parses to HTTP proxy`() {
        val proxy = ProxyConfig(enabled = true, address = "http://127.0.0.1:8080").toJavaProxy()!!

        assertEquals(Proxy.Type.HTTP, proxy.type())
        val address = proxy.address() as InetSocketAddress
        assertEquals("127.0.0.1", address.hostString)
        assertEquals(8080, address.port)
    }

    @Test
    fun `address without scheme parses to HTTP proxy`() {
        val proxy = ProxyConfig(enabled = true, address = "127.0.0.1:8080").toJavaProxy()!!

        assertEquals(Proxy.Type.HTTP, proxy.type())
        val address = proxy.address() as InetSocketAddress
        assertEquals("127.0.0.1", address.hostString)
        assertEquals(8080, address.port)
    }

    @Test
    fun `socks5 address parses to SOCKS proxy`() {
        val proxy = ProxyConfig(enabled = true, address = "socks5://10.0.0.1:1080").toJavaProxy()!!

        assertEquals(Proxy.Type.SOCKS, proxy.type())
        val address = proxy.address() as InetSocketAddress
        assertEquals("10.0.0.1", address.hostString)
        assertEquals(1080, address.port)
    }

    @Test
    fun `socks address parses to SOCKS proxy`() {
        val proxy = ProxyConfig(enabled = true, address = "socks://10.0.0.1:1080").toJavaProxy()!!

        assertEquals(Proxy.Type.SOCKS, proxy.type())
    }

    @Test
    fun `unsupported scheme returns null`() {
        assertNull(ProxyConfig(enabled = true, address = "ftp://127.0.0.1:2121").toJavaProxy())
    }

    @Test
    fun `address without port returns null`() {
        assertNull(ProxyConfig(enabled = true, address = "http://127.0.0.1").toJavaProxy())
    }

    @Test
    fun `forProxy returns same instance when disabled`() {
        val client = OkHttpClient()

        assertSame(client, client.forProxy(ProxyConfig()))
    }

    @Test
    fun `forProxy throws when enabled with garbage address`() {
        val client = OkHttpClient()

        try {
            client.forProxy(ProxyConfig(enabled = true, address = "not a url"))
            fail("Expected forProxy to throw for invalid address")
        } catch (e: IllegalStateException) {
            assertEquals("Proxy is enabled but the address is invalid", e.message)
        }
    }

    @Test
    fun `forProxy throws when enabled with blank address`() {
        val client = OkHttpClient()

        try {
            client.forProxy(ProxyConfig(enabled = true, address = ""))
            fail("Expected forProxy to throw for blank address")
        } catch (e: IllegalStateException) {
            assertEquals("Proxy is enabled but the address is invalid", e.message)
        }
    }

    @Test
    fun `forProxy returns same instance when disabled with garbage address`() {
        val client = OkHttpClient()

        assertSame(client, client.forProxy(ProxyConfig(enabled = false, address = "garbage")))
    }

    @Test
    fun `forProxy reuses cached client for same config`() {
        val client = OkHttpClient()
        val config = ProxyConfig(enabled = true, address = "http://127.0.0.1:8080")

        val first = client.forProxy(config)
        val second = client.forProxy(config)

        assertTrue(first !== client)
        assertSame(first, second)
    }
}
