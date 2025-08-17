package com.example.litechat.data

import androidx.compose.runtime.Immutable

/**
 * Represents a chat message in the conversation
 */
@Immutable
data class ChatMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val performanceMetrics: PerformanceMetrics? = null
)

/**
 * Hardware acceleration options for LLM inference
 */
enum class HardwareAcceleration {
    CPU,
    GPU
}

/**
 * Status of the model in the app
 */
enum class ModelStatus {
    NOT_AVAILABLE,
    DOWNLOADING,
    AVAILABLE,
    LOADING,
    ERROR
}

object ModelConstants {
    const val DEFAULT_MODEL_URL = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"
    const val DEFAULT_MODEL_FILE = "gemma3-1b-it-int4.task"
    const val DEFAULT_MODEL_SIZE = 584_417_280L // ~584MB for the 1B model
    const val DEFAULT_HUGGINGFACE_TOKEN = "" // User must provide their own token
}

/**
 * Model information for UI display
 */
data class ModelInfo(
    val name: String = "Gemma3-1B-IT",
    val url: String = ModelConstants.DEFAULT_MODEL_URL,
    val fileName: String = ModelConstants.DEFAULT_MODEL_FILE,
    val size: Long = ModelConstants.DEFAULT_MODEL_SIZE
)

/**
 * Download progress information
 */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val percentage: Float
)

/**
 * Inference performance metrics
 */
data class PerformanceMetrics(
    val tokensPerSecond: Float = 0f,
    val firstTokenLatency: Long = 0L,
    val totalInferenceTime: Long = 0L,
    val totalTokensGenerated: Int = 0,
    val averageTokensPerSecond: Float = 0f,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Application settings
 */
data class AppSettings(
    val hardwareAcceleration: HardwareAcceleration = HardwareAcceleration.GPU,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val huggingFaceAccessToken: String = ModelConstants.DEFAULT_HUGGINGFACE_TOKEN,
    val showPerformanceMetrics: Boolean = true
)
