package com.example.litechat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.litechat.data.*
import com.example.litechat.download.ModelDownloadService
import com.example.litechat.llm.MediaPipeLLMService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import android.util.Log

/**
 * ViewModel for managing chat functionality and model operations
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val mediaPipeService = MediaPipeLLMService(application)
    private val downloadService = ModelDownloadService(application)
    private val settingsRepository = SettingsRepository(application)
    
    // UI State
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    // Model info - using constants from ModelInfo object
    
    init {
        viewModelScope.launch {
            // Initialize app settings
            settingsRepository.appSettings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        
        // Check model status on initialization
        checkModelStatus()
    }
    
    /**
     * Check if model is available and update status
     */
    private fun checkModelStatus() {
        viewModelScope.launch {
            val isAvailable = mediaPipeService.isModelAvailable()
            val status = if (isAvailable) ModelStatus.AVAILABLE else ModelStatus.NOT_AVAILABLE
            
            _uiState.update { 
                it.copy(
                    modelStatus = status,
                    modelInfo = com.example.litechat.data.ModelInfo(
                        name = "Gemma3-1B-IT",
                        url = com.example.litechat.data.ModelConstants.DEFAULT_MODEL_URL,
                        fileName = com.example.litechat.data.ModelConstants.DEFAULT_MODEL_FILE,
                        size = downloadService.getModelFileSize()
                    )
                )
            }
            
            // If model is available, try to load it
            if (isAvailable) {
                loadModel()
            }
        }
    }
    
    /**
     * Download the model
     */
    fun downloadModel() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(modelStatus = ModelStatus.DOWNLOADING) }
                
                downloadService.downloadModel(accessToken = uiState.value.settings.huggingFaceAccessToken.ifEmpty { null })
                    .collect { progress ->
                        _uiState.update { 
                            it.copy(
                                downloadProgress = progress,
                                                            modelInfo = com.example.litechat.data.ModelInfo(
                                name = "Gemma3-1B-IT",
                                url = com.example.litechat.data.ModelConstants.DEFAULT_MODEL_URL,
                                fileName = com.example.litechat.data.ModelConstants.DEFAULT_MODEL_FILE,
                                size = progress.totalBytes
                            )
                            )
                        }
                    }
                
                // Download completed, check status
                checkModelStatus()
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        modelStatus = ModelStatus.ERROR,
                        errorMessage = "Download failed: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Load the model into memory
     */
    private fun loadModel() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(modelStatus = ModelStatus.LOADING) }
                
                val settings = _uiState.value.settings
                val result = mediaPipeService.loadModel(settings)
                
                if (result.isSuccess) {
                    Log.d("ChatViewModel", "Model loaded successfully")
                    _uiState.update { 
                        it.copy(
                            modelStatus = ModelStatus.AVAILABLE,
                            isModelLoaded = true
                        )
                    }
                } else {
                    Log.e("ChatViewModel", "Model loading failed", result.exceptionOrNull())
                    _uiState.update { 
                        it.copy(
                            modelStatus = ModelStatus.ERROR,
                            errorMessage = "Model loading failed: ${result.exceptionOrNull()?.message}"
                        )
                    }
                }
                
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Model loading failed", e)
                _uiState.update { 
                    it.copy(
                        modelStatus = ModelStatus.ERROR,
                        errorMessage = "Model loading failed: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Send a message and generate AI response
     */
    fun sendMessage(message: String) {
        Log.d("ChatViewModel", "sendMessage called with: '${message.take(50)}...'")
        
        if (message.trim().isEmpty()) {
            Log.w("ChatViewModel", "Empty message, ignoring")
            return
        }
        
        if (_uiState.value.isGenerating) {
            Log.w("ChatViewModel", "Already generating, ignoring new message")
            return
        }
        
        Log.d("ChatViewModel", "Creating user message...")
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = message,
            isUser = true
        )
        
        // Add user message
        _uiState.update { 
            it.copy(
                messages = it.messages + userMessage,
                inputText = ""
            )
        }
        
        Log.d("ChatViewModel", "User message added, calling generateResponse...")
        // Generate AI response
        generateResponse(message)
    }
    
    /**
     * Generate AI response
     */
    private fun generateResponse(prompt: String) {
        Log.d("ChatViewModel", "generateResponse called with prompt: '${prompt.take(50)}...'")
        
        if (!_uiState.value.isModelLoaded) {
            Log.w("ChatViewModel", "Model not loaded, cannot generate response")
            _uiState.update { 
                it.copy(errorMessage = "Model not loaded. Please wait for model to load.")
            }
            return
        }
        
        Log.d("ChatViewModel", "Model is loaded, creating AI message...")
        val aiMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = "",
            isUser = false
        )
        
        // Add AI message placeholder
        _uiState.update { 
            it.copy(
                messages = it.messages + aiMessage,
                isGenerating = true
            )
        }
        
        Log.d("ChatViewModel", "Starting callback-based generation...")
        viewModelScope.launch {
            try {
                val settings = _uiState.value.settings
                var responseContent = ""
                
                // Build conversation history for context
                val conversationHistory = buildConversationHistory()
                Log.d("ChatViewModel", "Built conversation history with ${conversationHistory.size} messages")
                
                // Check if this is a follow-up message and log for debugging
                val isFollowUpMessage = conversationHistory.size > 2
                if (isFollowUpMessage) {
                    Log.d("ChatViewModel", "Detected follow-up message with ${conversationHistory.size} previous messages")
                }
                
                Log.d("ChatViewModel", "Starting to generate from MediaPipe service...")
                mediaPipeService.generateResponse(
                    prompt = prompt,
                    settings = settings,
                    conversationHistory = conversationHistory, // Pass conversation history for context
                    resultListener = { partialResult, done, metrics ->
                        Log.d("ChatViewModel", "Received token: '$partialResult', done: $done")
                        
                        if (partialResult.isNotEmpty()) {
                            responseContent += partialResult
                            Log.d("ChatViewModel", "Updated response content: '${responseContent.take(100)}...'")
                            
                            // Update the AI message with streaming content
                            _uiState.update { currentState ->
                                val updatedMessages = currentState.messages.map { message ->
                                    if (message.id == aiMessage.id) {
                                        message.copy(content = responseContent)
                                    } else {
                                        message
                                    }
                                }
                                currentState.copy(messages = updatedMessages)
                            }
                            Log.d("ChatViewModel", "Updated UI state with ${_uiState.value.messages.size} messages")
                        }
                        
                        if (done) {
                            Log.d("ChatViewModel", "Generation completed")
                            
                            // Use the metrics passed from the MediaPipe service
                            if (metrics != null) {
                                Log.d("ChatViewModel", "Performance metrics: TTFT=${metrics.firstTokenLatency}ms, TPS=${metrics.averageTokensPerSecond}")
                                
                                // Update the AI message with performance metrics
                                _uiState.update { currentState ->
                                    val updatedMessages = currentState.messages.map { message ->
                                        if (message.id == aiMessage.id) {
                                            message.copy(performanceMetrics = metrics)
                                        } else {
                                            message
                                        }
                                    }
                                    currentState.copy(
                                        messages = updatedMessages,
                                        isGenerating = false
                                    )
                                }
                            } else {
                                // No metrics available, just mark as not generating
                                _uiState.update { 
                                    it.copy(isGenerating = false)
                                }
                            }
                        }
                    }
                )
                
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in generateResponse", e)
                _uiState.update { 
                    it.copy(
                        isGenerating = false,
                        errorMessage = "Generation failed: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Handle timeout recovery by resetting session and retrying
     */
    private fun handleTimeoutRecovery() {
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Handling timeout recovery...")
                val settings = _uiState.value.settings
                
                // Reset the session to clear any stuck state
                mediaPipeService.resetSession(settings)
                Log.d("ChatViewModel", "Session reset for timeout recovery")
                
                // Update UI to show recovery attempt
                _uiState.update { 
                    it.copy(
                        errorMessage = "Previous request timed out. Session has been reset. Please try your message again."
                    )
                }
                
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Timeout recovery failed", e)
                _uiState.update { 
                    it.copy(
                        errorMessage = "Recovery failed. Please restart the app."
                    )
                }
            }
        }
    }
    
    /**
     * Check if user has completed first-time setup
     */
    fun hasCompletedFirstTimeSetup(): Boolean {
        val hasToken = _uiState.value.settings.huggingFaceAccessToken.isNotEmpty()
        val hasModel = _uiState.value.isModelLoaded || _uiState.value.modelStatus == ModelStatus.AVAILABLE
        return hasToken && hasModel
    }
    
    /**
     * Test timeout handling and session recovery
     */
    fun testTimeoutHandling() {
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Testing timeout handling...")
                val settings = _uiState.value.settings
                
                // Reset session to ensure clean state
                mediaPipeService.resetSession(settings)
                Log.d("ChatViewModel", "Session reset for timeout test")
                
                _uiState.update { 
                    it.copy(errorMessage = "Timeout handling test completed. Session has been reset.")
                }
                
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Timeout handling test failed", e)
                _uiState.update { 
                    it.copy(errorMessage = "Timeout test failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Build conversation history for context
     * This method collects messages from the current conversation to provide context for the LLM
     */
    private fun buildConversationHistory(): List<String> {
        val history = mutableListOf<String>()
        val messages = _uiState.value.messages
        
        // Skip the last message if it's the current AI message placeholder
        val messagesToProcess = if (messages.isNotEmpty() && !messages.last().isUser && messages.last().content.isEmpty()) {
            messages.dropLast(1)
        } else {
            messages
        }
        
        // Limit the number of messages to prevent context overflow
        // Keep only the most recent messages (last 4 messages = 2 exchanges)
        // This reduces input token consumption and leaves more room for responses
        val maxMessages = 4
        val limitedMessages = if (messagesToProcess.size > maxMessages) {
            messagesToProcess.takeLast(maxMessages)
        } else {
            messagesToProcess
        }
        
        limitedMessages.forEach { message ->
            // Only add non-empty messages to reduce context length
            if (message.content.trim().isNotEmpty()) {
                history.add(message.content.trim())
            }
        }
        
        Log.d("ChatViewModel", "Built conversation history with ${history.size} messages (limited to $maxMessages)")
        return history
    }
    
    /**
     * Update input text
     */
    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }
    
    /**
     * Clear conversation
     */
    fun clearConversation() {
        _uiState.update { it.copy(messages = emptyList()) }
        
        // Reset the MediaPipe session to clear model's internal context
        viewModelScope.launch {
            try {
                val settings = _uiState.value.settings
                mediaPipeService.resetSession(settings)
                Log.d("ChatViewModel", "Conversation cleared and session reset")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error resetting session", e)
            }
        }
    }
    
    /**
     * Update hardware acceleration setting
     */
    fun updateHardwareAcceleration(acceleration: HardwareAcceleration) {
        viewModelScope.launch {
            settingsRepository.updateHardwareAcceleration(acceleration)
            
            // Reload model with new settings if already loaded
            if (_uiState.value.isModelLoaded) {
                loadModel()
            }
        }
    }
    
    /**
     * Update temperature setting
     */
    fun updateTemperature(temperature: Float) {
        viewModelScope.launch {
            settingsRepository.updateTemperature(temperature)
            
            // Reset session with new temperature if model is loaded
            if (_uiState.value.isModelLoaded) {
                try {
                    val settings = _uiState.value.settings.copy(temperature = temperature)
                    mediaPipeService.resetSession(settings)
                    Log.d("ChatViewModel", "Session reset with new temperature: $temperature")
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error resetting session with new temperature", e)
                }
            }
        }
    }
    

    

    
    fun updateHuggingFaceAccessToken(token: String) {
        viewModelScope.launch {
            Log.d("ChatViewModel", "Updating HuggingFace token (length: ${token.length})")
            settingsRepository.updateHuggingFaceAccessToken(token)
            Log.d("ChatViewModel", "HuggingFace token updated successfully")
        }
    }
    
    /**
     * Update HuggingFace token and then start model download (for first-time setup)
     */
    fun updateHuggingFaceAccessTokenAndDownload(token: String) {
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Updating HuggingFace token and starting download (length: ${token.length})")
                
                // First update the token
                settingsRepository.updateHuggingFaceAccessToken(token)
                Log.d("ChatViewModel", "HuggingFace token updated successfully")
                
                // Then start the download
                downloadModel()
                Log.d("ChatViewModel", "Model download started after token update")
                
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in token update and download sequence", e)
                _uiState.update { 
                    it.copy(errorMessage = "Error setting up token and starting download: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    /**
     * Get available hardware acceleration options
     */
    fun getAvailableHardwareAcceleration(): List<HardwareAcceleration> {
        return mediaPipeService.getAvailableHardwareAcceleration()
    }
    
    /**
     * Get performance metrics
     */
    fun getPerformanceMetrics(): PerformanceMetrics {
        return mediaPipeService.getPerformanceMetrics()
    }
    
    override fun onCleared() {
        super.onCleared()
        mediaPipeService.release()
    }
}

/**
 * UI State for the chat screen
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.NOT_AVAILABLE,
    val modelInfo: ModelInfo = ModelInfo(),
    val downloadProgress: DownloadProgress? = null,
    val settings: AppSettings = AppSettings(),
    val isModelLoaded: Boolean = false,
    val errorMessage: String? = null
)
