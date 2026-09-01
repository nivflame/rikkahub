package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AIIconMatcherTest {
    @Test
    fun `llama cpp names resolve to llamacpp icon`() {
        assertEquals("llamacpp.svg", computeAIIconByName("llamacpp"))
        assertEquals("llamacpp.svg", computeAIIconByName("llama.cpp"))
        assertEquals("llamacpp.svg", computeAIIconByName("LlamaCPP"))
        assertEquals("llamacpp.svg", computeAIIconByName("llama-cpp server"))
    }

    @Test
    fun `meta and ollama matching unchanged`() {
        assertEquals("meta-color.svg", computeAIIconByName("meta-llama/Llama-3.2"))
        assertEquals("meta-color.svg", computeAIIconByName("llama3.2"))
        assertEquals("ollama.svg", computeAIIconByName("ollama"))
    }
}
