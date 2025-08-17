package com.example.litechat.download

import android.content.Context
import android.util.Log
import com.example.litechat.data.DownloadProgress
import com.example.litechat.data.ModelConstants.DEFAULT_MODEL_URL
import com.example.litechat.data.ModelConstants.DEFAULT_MODEL_FILE
import com.example.litechat.data.ModelConstants.DEFAULT_MODEL_SIZE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Service for downloading model files from HuggingFace
 */
class ModelDownloadService(private val context: Context) {
    
    companion object {
        private const val TAG = "ModelDownloadService"
        private const val MODEL_DIR = "models"
        private const val BUFFER_SIZE = 8192
        private const val TIMEOUT_SECONDS = 300L // 5 minutes
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    /**
     * Get the model directory
     */
    private fun getModelDirectory(): File {
        val modelDir = File(context.filesDir, MODEL_DIR)
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        return modelDir
    }
    
    /**
     * Download model with progress tracking
     */
    fun downloadModel(url: String = DEFAULT_MODEL_URL, accessToken: String? = null): Flow<DownloadProgress> = flow {
        try {
            Log.d(TAG, "Starting download for model from: $url")
            
            val modelFile = File(getModelDirectory(), DEFAULT_MODEL_FILE)
            
            // Create request
            val requestBuilder = Request.Builder().url(url)
            
            // Add authorization header if access token is provided
            if (!accessToken.isNullOrEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer $accessToken")
                Log.d(TAG, "Using HuggingFace access token for download")
                Log.d(TAG, "Token starts with: ${accessToken.take(10)}...")
            } else {
                Log.w(TAG, "No access token provided for HuggingFace download")
            }
            
            val request = requestBuilder.build()
            
            // Log request details for debugging
            Log.d(TAG, "Request URL: ${request.url}")
            Log.d(TAG, "Request headers: ${request.headers}")
            
            // Execute request
            val response = httpClient.newCall(request).execute()
            
            Log.d(TAG, "Response code: ${response.code}")
            Log.d(TAG, "Response message: ${response.message}")
            Log.d(TAG, "Response headers: ${response.headers}")
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "Error response body: $errorBody")
                throw IOException("Download failed with code: ${response.code}, message: ${response.message}, body: $errorBody")
            }
            
            val body = response.body
            if (body == null) {
                throw IOException("Response body is null")
            }
            
            val contentLength = body.contentLength()
            if (contentLength <= 0) {
                throw IOException("Invalid content length: $contentLength")
            }
            
            Log.d(TAG, "Content length: $contentLength bytes")
            
            // Download with progress tracking
            var bytesDownloaded = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            
            body.byteStream().use { inputStream ->
                modelFile.outputStream().use { outputStream ->
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        
                        val progress = DownloadProgress(
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = contentLength,
                            percentage = (bytesDownloaded.toFloat() / contentLength) * 100
                        )
                        
                        emit(progress)
                        
                        Log.d(TAG, "Download progress: ${progress.percentage}%")
                    }
                }
            }
            
            Log.d(TAG, "Download completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Check if model file exists and is valid
     */
    fun isModelDownloaded(): Boolean {
        val modelFile = File(getModelDirectory(), DEFAULT_MODEL_FILE)
        return modelFile.exists() && modelFile.length() > 0
    }
    
    /**
     * Get model file size
     */
    fun getModelFileSize(): Long {
        val modelFile = File(getModelDirectory(), DEFAULT_MODEL_FILE)
        return if (modelFile.exists()) modelFile.length() else 0L
    }
    
    /**
     * Delete model file
     */
    fun deleteModel(): Boolean {
        val modelFile = File(getModelDirectory(), DEFAULT_MODEL_FILE)
        return if (modelFile.exists()) {
            val deleted = modelFile.delete()
            Log.d(TAG, "Model file deleted: $deleted")
            deleted
        } else {
            Log.w(TAG, "Model file does not exist")
            false
        }
    }
    
    /**
     * Get available disk space
     */
    fun getAvailableDiskSpace(): Long {
        return getModelDirectory().freeSpace
    }
    
    /**
     * Validate model file integrity (basic check)
     */
    fun validateModelFile(): Boolean {
        val modelFile = File(getModelDirectory(), DEFAULT_MODEL_FILE)
        if (!modelFile.exists()) {
            return false
        }
        
        // Basic validation: check if file size is reasonable (> 1MB)
        val fileSize = modelFile.length()
        return fileSize > 1024 * 1024 // 1MB minimum
    }

    /**
     * Check if a HuggingFace URL requires authentication
     */
    suspend fun checkIfUrlRequiresAuth(url: String = DEFAULT_MODEL_URL): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .head() // Use HEAD request to check access without downloading
                .build()
            
            val response = httpClient.newCall(request).execute()
            val requiresAuth = response.code == 401 || response.code == 403
            
            Log.d(TAG, "URL auth check: ${response.code} - requires auth: $requiresAuth")
            requiresAuth
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking URL auth requirement", e)
            true // Assume it requires auth if we can't check
        }
    }
}
