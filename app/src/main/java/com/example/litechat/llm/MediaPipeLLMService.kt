package com.example.litechat.llm

import android.content.Context
import android.util.Log
import com.example.litechat.data.AppSettings
import com.example.litechat.data.HardwareAcceleration
import com.example.litechat.data.PerformanceMetrics
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Service for handling MediaPipe LLM inference operations
 */
class MediaPipeLLMService(private val context: Context) {
    
    companion object {
        private const val TAG = "MediaPipeLLMService"
        private const val MODEL_DIR = "models"
        private const val DEFAULT_MODEL_FILE = "gemma3-1b-it-int4.task"
        private const val DEFAULT_MAX_TOKENS = 2048
        private const val DEFAULT_TOP_K = 50
        private const val DEFAULT_TOP_P = 0.9f
        private const val DEFAULT_TEMPERATURE = 0.8f
        private const val TIMEOUT_THRESHOLD = 30000L // 30 seconds for first token
        private const val MAX_INFERENCE_TIME = 60000L // 60 seconds total timeout
    }
    
    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null
    private var isModelLoaded = false
    
    // Performance tracking variables
    private var inferenceStartTime: Long = 0L
    private var firstTokenTime: Long = 0L
    private var totalTokensGenerated: Int = 0
    private var currentMetrics = PerformanceMetrics()
    
    /**
     * Get the model file path
     */
    fun getModelFilePath(): String {
        val modelDir = File(context.filesDir, MODEL_DIR)
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        return File(modelDir, DEFAULT_MODEL_FILE).absolutePath
    }
    
    /**
     * Check if model file exists
     */
    fun isModelAvailable(): Boolean {
        val modelFile = File(getModelFilePath())
        return modelFile.exists() && modelFile.length() > 0
    }
    
    /**
     * Load the model with specified hardware acceleration
     */
    suspend fun loadModel(settings: AppSettings): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading model with acceleration: ${settings.hardwareAcceleration}")
            
            val modelPath = getModelFilePath()
            val modelFile = File(modelPath)
            
            // Validate model file
            if (!modelFile.exists()) {
                val error = "Model file not found: $modelPath"
                Log.e(TAG, error)
                return@withContext Result.failure(IOException(error))
            }
            
            val fileSize = modelFile.length()
            Log.d(TAG, "Model file size: $fileSize bytes")
            
            if (fileSize == 0L) {
                val error = "Model file is empty: $modelPath"
                Log.e(TAG, error)
                return@withContext Result.failure(IOException(error))
            }
            
            // Check if file size is reasonable (should be around 584MB for 1B model)
            val expectedSize = 584_417_280L // ~584MB
            if (fileSize < expectedSize * 0.8) { // Allow 20% tolerance
                Log.w(TAG, "Model file size ($fileSize) seems too small for 1B model (expected ~$expectedSize)")
            }
            
            // Determine backend based on hardware acceleration
            val preferredBackend = when (settings.hardwareAcceleration) {
                HardwareAcceleration.CPU -> LlmInference.Backend.CPU
                HardwareAcceleration.GPU -> LlmInference.Backend.GPU
            }
            
            Log.d(TAG, "Using backend: $preferredBackend")
            
            // Create LLM Inference options
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setPreferredBackend(preferredBackend)
                .setMaxTokens(DEFAULT_MAX_TOKENS)
                .build()
            
            // Create LLM Inference instance
            Log.d(TAG, "Creating LLM Inference instance...")
            llmInference = LlmInference.createFromOptions(context, options)
            Log.d(TAG, "LLM Inference instance created successfully")
            
            // Create session with inference parameters
            Log.d(TAG, "Creating LLM Inference session...")
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(DEFAULT_TOP_K)
                .setTopP(DEFAULT_TOP_P)
                .setTemperature(settings.temperature)
                .setGraphOptions(
                    GraphOptions.builder()
                        .setEnableVisionModality(false) // We're not using vision for now
                        .build()
                )
                .build()
            
            llmSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
            Log.d(TAG, "LLM Inference session created successfully")
            isModelLoaded = true
            
            Log.d(TAG, "Model loaded successfully with backend: $preferredBackend")
            Log.i(TAG, "Hardware acceleration: ${settings.hardwareAcceleration} -> Backend: $preferredBackend")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            Log.e(TAG, "Model path: ${getModelFilePath()}")
            Log.e(TAG, "Model file exists: ${File(getModelFilePath()).exists()}")
            Log.e(TAG, "Model file size: ${File(getModelFilePath()).length()} bytes")
            isModelLoaded = false
            Result.failure(e)
        }
    }
    
    /**
     * Generate response with streaming using callback pattern (following Google's approach)
     */
    fun generateResponse(
        prompt: String,
        settings: AppSettings,
        conversationHistory: List<String> = emptyList(), // Kept for backward compatibility but not used
        resultListener: (partialResult: String, done: Boolean, metrics: PerformanceMetrics?) -> Unit
    ) {
        try {
            validateAndRecoverSession(settings)
            Log.d(TAG, "Session validation passed, proceeding with generation")
            
            // Reset session before each generation to ensure clean state
            Log.d(TAG, "Resetting session before generation...")
            if (conversationHistory.isNotEmpty()) {
                resetSessionForFollowUp(settings)
            } else {
                resetSession(settings)
            }
            
            Log.d(TAG, "Generating response for prompt: ${prompt.take(50)}...")
            Log.d(TAG, "Conversation history has ${conversationHistory.size} previous exchanges")
            
            // Start performance tracking
            inferenceStartTime = System.currentTimeMillis()
            firstTokenTime = 0L
            totalTokensGenerated = 0
            
            val session = llmSession!!
            
            // Build conversation context for follow-up messages
            val contextToUse = if (conversationHistory.isEmpty()) {
                prompt
            } else {
                buildProperConversationContext(prompt, conversationHistory)
            }

            Log.d(TAG, "Built conversation context with length: ${contextToUse.length}")
            
            // Estimate input tokens (rough approximation: 1 token ≈ 4 characters)
            val estimatedInputTokens = contextToUse.length / 4
            Log.d(TAG, "Estimated input tokens: $estimatedInputTokens (context length: ${contextToUse.length})")
            Log.d(TAG, "Available tokens for response: ${DEFAULT_MAX_TOKENS - estimatedInputTokens}")
            
            // Warn if input tokens are consuming too much of the budget
            val availableTokens = DEFAULT_MAX_TOKENS - estimatedInputTokens
            if (availableTokens < 1000) {
                Log.w(TAG, "Low available tokens for response: $availableTokens. Consider reducing conversation history.")
            } else if (availableTokens < 2000) {
                Log.w(TAG, "Moderate available tokens for response: $availableTokens")
            }

            // Add the context as query chunk
            if (contextToUse.trim().isNotEmpty()) {
                Log.d(TAG, "Adding context as query chunk: ${contextToUse.take(200)}...")
                session.addQueryChunk(contextToUse.trim())
            } else {
                Log.w(TAG, "Empty context, this might cause issues")
            }
            
            Log.d(TAG, "Starting response generation...")
            var hasReceivedAnyResult = false
            var consecutiveEmptyResults = 0
            val maxEmptyResults = 10 // Prevent infinite loops
            val maxInferenceTime = MAX_INFERENCE_TIME // Use constant for timeout
            
            // Start timeout timer
            val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                Log.w(TAG, "Response generation timed out after ${maxInferenceTime}ms")
                resultListener("", true, null)
            }
            timeoutHandler.postDelayed(timeoutRunnable, maxInferenceTime)
            
            // Add first token timeout detection
            val firstTokenTimeoutRunnable = Runnable {
                if (firstTokenTime == 0L) {
                    Log.w(TAG, "First token timeout after ${TIMEOUT_THRESHOLD}ms")
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    resultListener("", true, null)
                }
            }
            timeoutHandler.postDelayed(firstTokenTimeoutRunnable, TIMEOUT_THRESHOLD)
            
            // Generate response asynchronously (following Google's pattern)
            var totalResponseLength = 0
            session.generateResponseAsync { partialResult, done ->
                try {
                    Log.d(TAG, "Callback received - partialResult length: ${partialResult.length}, done: $done")
                    
                    if (partialResult.isNotEmpty()) {
                        totalResponseLength += partialResult.length
                        Log.d(TAG, "Received partial result: ${partialResult.take(50)}...")
                        Log.d(TAG, "Total response length so far: $totalResponseLength characters")
                        
                        // Log the last few characters to see if we're hitting a stopping condition
                        if (partialResult.length > 20) {
                            Log.d(TAG, "Last 20 chars of partial result: '${partialResult.takeLast(20)}'")
                        }
                        
                        hasReceivedAnyResult = true
                        consecutiveEmptyResults = 0 // Reset counter
                        
                        // Track first token time
                        if (firstTokenTime == 0L) {
                            firstTokenTime = System.currentTimeMillis()
                            val ttft = firstTokenTime - inferenceStartTime
                            Log.i(TAG, "Time to First Token: ${ttft}ms")
                            Log.d(TAG, "First token received at: ${firstTokenTime}, started at: ${inferenceStartTime}")
                            
                            // Cancel first token timeout since we received the first token
                            timeoutHandler.removeCallbacks(firstTokenTimeoutRunnable)
                        }
                        
                        // Count tokens (rough estimation - each partial result is typically one token)
                        totalTokensGenerated++
                        
                        // Send the result through the callback (no metrics during streaming)
                        resultListener(partialResult, done, null)
                        Log.d(TAG, "Sent token: '${partialResult.take(20)}...'")
                    } else {
                        Log.d(TAG, "Empty partial result received (count: ${consecutiveEmptyResults + 1})")
                        consecutiveEmptyResults++
                        
                        // Still call the listener even for empty results
                        resultListener(partialResult, done, null)
                        
                        // If we get too many consecutive empty results, force completion
                        if (consecutiveEmptyResults >= maxEmptyResults) {
                            Log.w(TAG, "Too many consecutive empty results, forcing completion")
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            timeoutHandler.removeCallbacks(firstTokenTimeoutRunnable)
                            resultListener("", true, null)
                            return@generateResponseAsync
                        }
                    }
                    
                    if (done) {
                        Log.d(TAG, "Response generation completed with total length: $totalResponseLength characters")
                        
                        // Log if the response seems truncated
                        if (totalResponseLength < 100) {
                            Log.w(TAG, "Response seems very short (${totalResponseLength} chars), might be truncated")
                        } else if (totalResponseLength < 500) {
                            Log.w(TAG, "Response seems short (${totalResponseLength} chars), might be incomplete")
                        }
                        
                        // Cancel both timeout handlers
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        timeoutHandler.removeCallbacks(firstTokenTimeoutRunnable)
                        
                        // Calculate final performance metrics for this specific generation
                        val totalInferenceTime = System.currentTimeMillis() - inferenceStartTime
                        val ttft = if (firstTokenTime > 0) firstTokenTime - inferenceStartTime else 0L
                        val avgTokensPerSecond = if (totalInferenceTime > 0) {
                            (totalTokensGenerated * 1000f) / totalInferenceTime
                        } else 0f
                        
                        // Create metrics for this specific generation
                        val generationMetrics = PerformanceMetrics(
                            tokensPerSecond = avgTokensPerSecond,
                            firstTokenLatency = ttft,
                            totalInferenceTime = totalInferenceTime,
                            totalTokensGenerated = totalTokensGenerated,
                            averageTokensPerSecond = avgTokensPerSecond,
                            lastUpdated = System.currentTimeMillis()
                        )
                        
                        // Update current metrics (for backward compatibility)
                        currentMetrics = generationMetrics
                        
                        Log.i(TAG, "Performance Metrics - TTFT: ${ttft}ms, Total Time: ${totalInferenceTime}ms, Tokens: $totalTokensGenerated, Avg TPS: ${String.format("%.2f", avgTokensPerSecond)}")
                        Log.d(TAG, "Final metrics calculation - firstTokenTime: $firstTokenTime, inferenceStartTime: $inferenceStartTime, totalTokensGenerated: $totalTokensGenerated")
                        
                        // If we never received any partial results, log the issue
                        if (!hasReceivedAnyResult) {
                            Log.w(TAG, "No partial results received from MediaPipe LLM Inference API")
                        }
                        
                        // Send completion with metrics
                        resultListener("", true, generationMetrics)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in async callback", e)
                    // Cancel both timeout handlers
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    timeoutHandler.removeCallbacks(firstTokenTimeoutRunnable)
                    // Call the listener with error information
                    resultListener("", true, null)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            // Call the listener with error information
            resultListener("", true, null)
        }
    }
    
    /**
     * Build proper conversation context for the MediaPipe LLM model
     * This follows the correct format that the Gemma model expects
     */
    private fun buildProperConversationContext(currentPrompt: String, conversationHistory: List<String>): String {
        if (conversationHistory.isEmpty()) {
            return currentPrompt
        }
        
        val contextBuilder = StringBuilder()
        
        // Build conversation context in a simple format
        // Format: User: message\nAssistant: response\nUser: message\nAssistant: response\n...
        
        conversationHistory.forEachIndexed { index, message ->
            if (index % 2 == 0) {
                // User message
                contextBuilder.append("User: ${message.trim()}\n")
            } else {
                // Assistant message
                contextBuilder.append("Assistant: ${message.trim()}\n")
            }
        }
        
        // Add current prompt
        contextBuilder.append("User: ${currentPrompt.trim()}\n")
        contextBuilder.append("Assistant: ")
        
        val fullContext = contextBuilder.toString()
        Log.d(TAG, "Built conversation context with ${conversationHistory.size} previous messages, total length: ${fullContext.length}")
        
        // Log a warning if context is getting too long
        if (fullContext.length > 8000) {
            Log.w(TAG, "Conversation context is very long (${fullContext.length} chars), this may cause performance issues")
        }
        
        return fullContext
    }
    
    /**
     * Check if the session is in a valid state for generation
     */
    private fun isSessionValid(): Boolean {
        return isModelLoaded && llmSession != null && llmInference != null
    }
    
    /**
     * Validate session state and throw appropriate exception if invalid
     */
    private fun validateSessionState() {
        if (!isModelLoaded) {
            throw IllegalStateException("Model is not loaded")
        }
        if (llmSession == null) {
            throw IllegalStateException("LLM session is null")
        }
        if (llmInference == null) {
            throw IllegalStateException("LLM inference is null")
        }
    }
    
    /**
     * Reset the session (useful for clearing conversation context)
     */
    fun resetSession(settings: AppSettings) {
        try {
            Log.d(TAG, "Resetting MediaPipe session...")
            
            if (llmSession != null) {
                llmSession!!.close()
                Log.d(TAG, "Previous session closed")
                
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(DEFAULT_TOP_K)
                    .setTopP(DEFAULT_TOP_P)
                    .setTemperature(settings.temperature)
                    .setGraphOptions(
                        GraphOptions.builder()
                            .setEnableVisionModality(false)
                            .build()
                    )
                    .build()
                
                llmSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
                Log.d(TAG, "New session created successfully with temperature: ${settings.temperature}")
            } else {
                Log.w(TAG, "No existing session to reset")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting session", e)
            // Try to recover by recreating the session
            try {
                Log.d(TAG, "Attempting to recover session...")
                if (llmInference != null) {
                    val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(DEFAULT_TOP_K)
                        .setTopP(DEFAULT_TOP_P)
                        .setTemperature(settings.temperature)
                        .setGraphOptions(
                            GraphOptions.builder()
                                .setEnableVisionModality(false)
                                .build()
                        )
                        .build()
                    
                    llmSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
                    Log.d(TAG, "Session recovery successful")
                }
            } catch (recoveryException: Exception) {
                Log.e(TAG, "Session recovery failed", recoveryException)
                isModelLoaded = false
            }
        }
    }
    
    /**
     * Reset the session with different parameters for follow-up messages
     */
    fun resetSessionForFollowUp(settings: AppSettings) {
        try {
            Log.d(TAG, "Resetting MediaPipe session for follow-up message...")
            
            if (llmSession != null) {
                llmSession!!.close()
                Log.d(TAG, "Previous session closed")
                
                // Try different parameters for follow-up messages
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(20) // Lower top-k for more focused responses
                    .setTopP(0.9f) // Lower top-p for more deterministic responses
                    .setTemperature(0.5f) // Lower temperature for more focused responses
                    .setGraphOptions(
                        GraphOptions.builder()
                            .setEnableVisionModality(false)
                            .build()
                    )
                    .build()
                
                llmSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
                Log.d(TAG, "New session created successfully with follow-up parameters")
            } else {
                Log.w(TAG, "No existing session to reset")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting session for follow-up", e)
        }
    }
    
    /**
     * Check if session needs to be reset (for handling long conversations)
     */
    fun shouldResetSession(): Boolean {
        // Reset session if it's been used for too many messages
        // This helps prevent context overflow issues
        return false // For now, let's keep sessions persistent but monitor performance
    }
    
    /**
     * Validate and potentially recover session state
     */
    private fun validateAndRecoverSession(settings: AppSettings) {
        try {
            validateSessionState()
        } catch (e: Exception) {
            Log.w(TAG, "Session validation failed, attempting recovery", e)
            resetSession(settings)
        }
    }
    
    /**
     * Get available hardware acceleration options
     */
    fun getAvailableHardwareAcceleration(): List<HardwareAcceleration> {
        val available = mutableListOf<HardwareAcceleration>()
        
        // CPU is always available
        available.add(HardwareAcceleration.CPU)
        
        // Check for GPU support
        try {
            // Test if GPU backend is available
            LlmInference.Backend.GPU
            available.add(HardwareAcceleration.GPU)
            Log.d(TAG, "GPU acceleration available")
        } catch (e: Exception) {
            Log.w(TAG, "GPU not available: ${e.message}")
        }
        
        return available
    }
    
    /**
     * Get performance metrics
     */
    fun getPerformanceMetrics(): PerformanceMetrics {
        return currentMetrics
    }
    
    /**
     * Release resources
     */
    fun release() {
        try {
            llmSession?.close()
            llmInference?.close()
            llmSession = null
            llmInference = null
            isModelLoaded = false
            Log.d(TAG, "MediaPipe LLM service released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPipe LLM service", e)
        }
    }
    
    /**
     * Check if model is currently loaded
     */
    fun isModelLoaded(): Boolean = isModelLoaded


}

