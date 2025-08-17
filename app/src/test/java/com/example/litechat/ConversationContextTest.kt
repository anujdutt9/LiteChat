package com.example.litechat

import org.junit.Test
import org.junit.Assert.*

/**
 * Test for conversation context building functionality
 */
class ConversationContextTest {
    
    @Test
    fun testEmptyConversationHistory() {
        // Test with empty conversation history
        val conversationHistory = emptyList<String>()
        val currentPrompt = "What is the weather like?"
        
        // Simulate the context building logic
        val context = buildTestContext(conversationHistory, currentPrompt)
        
        assertEquals("What is the weather like?", context)
    }
    
    @Test
    fun testSingleExchange() {
        // Test with one user-assistant exchange
        val conversationHistory = listOf(
            "What is 2+2?",
            "2+2 equals 4."
        )
        val currentPrompt = "What about 3+3?"
        
        val context = buildTestContext(conversationHistory, currentPrompt)
        
        val expected = "User: What is 2+2?\nAssistant: 2+2 equals 4.\nUser: What about 3+3?\nAssistant: "
        assertEquals(expected, context)
    }
    
    @Test
    fun testMultipleExchanges() {
        // Test with multiple exchanges
        val conversationHistory = listOf(
            "Hello",
            "Hi there! How can I help you?",
            "What's the capital of France?",
            "The capital of France is Paris.",
            "What about Germany?",
            "The capital of Germany is Berlin."
        )
        val currentPrompt = "And Italy?"
        
        val context = buildTestContext(conversationHistory, currentPrompt)
        
        val expected = "User: Hello\nAssistant: Hi there! How can I help you?\n" +
                      "User: What's the capital of France?\nAssistant: The capital of France is Paris.\n" +
                      "User: What about Germany?\nAssistant: The capital of Germany is Berlin.\n" +
                      "User: And Italy?\nAssistant: "
        assertEquals(expected, context)
    }
    
    @Test
    fun testContextLengthLimiting() {
        // Test that long conversations are properly limited
        val longHistory = (1..25).flatMap { 
            listOf("User message $it", "Assistant response $it") 
        }
        val currentPrompt = "Final question"
        
        val context = buildTestContext(longHistory, currentPrompt)
        
        // Should only include the last 10 exchanges (20 messages)
        val lines = context.split("\n")
        val userLines = lines.filter { it.startsWith("User:") }
        val assistantLines = lines.filter { it.startsWith("Assistant:") }
        
        // Should have 11 user messages (10 from history + 1 current)
        assertEquals(11, userLines.size)
        // Should have 10 assistant messages from history + 1 empty one at the end
        assertEquals(11, assistantLines.size)
    }
    
    /**
     * Helper function to simulate the context building logic from MediaPipeLLMService
     */
    private fun buildTestContext(conversationHistory: List<String>, currentPrompt: String): String {
        if (conversationHistory.isEmpty()) {
            return currentPrompt
        }
        
        val contextBuilder = StringBuilder()
        
        // Limit conversation history to prevent token overflow
        // Keep only the most recent exchanges (roughly last 10 exchanges)
        val maxExchanges = 10
        val limitedHistory = if (conversationHistory.size > maxExchanges * 2) {
            conversationHistory.takeLast(maxExchanges * 2)
        } else {
            conversationHistory
        }
        
        // Add conversation history
        limitedHistory.forEachIndexed { index, message ->
            if (index % 2 == 0) {
                // User message
                contextBuilder.append("User: $message\n")
            } else {
                // Assistant message
                contextBuilder.append("Assistant: $message\n")
            }
        }
        
        // Add current prompt
        contextBuilder.append("User: $currentPrompt\n")
        contextBuilder.append("Assistant: ")
        
        return contextBuilder.toString()
    }
}
