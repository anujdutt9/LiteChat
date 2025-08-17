package com.example.litechat.ui

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.litechat.ui.components.ChatScreen
import com.example.litechat.ui.components.SettingsScreen
import com.example.litechat.ui.components.FirstTimeSetupScreen
import android.util.Log

/**
 * Main screen that handles navigation between chat and settings
 */
@Composable
fun MainScreen() {
    var showSettings by remember { mutableStateOf(false) }
    val viewModel: ChatViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Debug logging for UI state changes
    LaunchedEffect(uiState.messages.size) {
        Log.d("MainScreen", "UI State updated - Messages count: ${uiState.messages.size}")
        if (uiState.messages.isNotEmpty()) {
            val lastMessage = uiState.messages.last()
            Log.d("MainScreen", "Last message: isUser=${lastMessage.isUser}, content='${lastMessage.content.take(50)}...'")
        }
    }

    // Check if user has entered a HuggingFace token
    val hasToken = uiState.settings.huggingFaceAccessToken.isNotEmpty()
    
    // Show first-time setup if no token is provided
    if (!hasToken) {
        FirstTimeSetupScreen(
            onTokenEntered = { token ->
                // Update token and then start download
                viewModel.updateHuggingFaceAccessTokenAndDownload(token)
            }
        )
    } else if (showSettings) {
        SettingsScreen(
            settings = uiState.settings,
            availableHardwareAcceleration = viewModel.getAvailableHardwareAcceleration(),
            performanceMetrics = viewModel.getPerformanceMetrics(),
            onHardwareAccelerationChange = { acceleration ->
                viewModel.updateHardwareAcceleration(acceleration)
            },
            onTemperatureChange = { temperature ->
                viewModel.updateTemperature(temperature)
            },
            onHuggingFaceTokenChange = { token ->
                viewModel.updateHuggingFaceAccessToken(token)
            },
            onBackPressed = {
                showSettings = false
            }
        )
    } else {
        ChatScreen(
            uiState = uiState,
            onSendMessage = { message ->
                viewModel.sendMessage(message)
            },
            onUpdateInputText = { text ->
                viewModel.updateInputText(text)
            },
            onClearConversation = {
                viewModel.clearConversation()
            },
            onDownloadModel = {
                viewModel.downloadModel()
            },
            onOpenSettings = {
                showSettings = true
            }
        )
    }
}
