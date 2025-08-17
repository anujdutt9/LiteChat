package com.example.litechat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.litechat.data.ChatMessage
import com.example.litechat.data.ModelStatus
import com.example.litechat.ui.ChatUiState
import com.example.litechat.ui.components.MarkdownText
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit,
    onUpdateInputText: (String) -> Unit,
    onClearConversation: () -> Unit,
    onDownloadModel: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top bar with status and actions
        ChatTopBar(
            modelStatus = uiState.modelStatus,
            isModelLoaded = uiState.isModelLoaded,
            onClearConversation = onClearConversation,
            onOpenSettings = onOpenSettings
        )
        
        // Model status indicator
        ModelStatusIndicator(
            modelStatus = uiState.modelStatus,
            downloadProgress = uiState.downloadProgress,
            onDownloadModel = onDownloadModel
        )
        
        // Messages list
        MessagesList(
            messages = uiState.messages,
            isGenerating = uiState.isGenerating,
            modifier = Modifier.weight(1f)
        )
        
        // Input field
        ChatInputField(
            text = uiState.inputText,
            onTextChange = onUpdateInputText,
            onSendMessage = onSendMessage,
            isGenerating = uiState.isGenerating,
            isModelLoaded = uiState.isModelLoaded
        )
    }
}

/**
 * Top bar with status and actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    modelStatus: ModelStatus,
    isModelLoaded: Boolean,
    onClearConversation: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = "LiteChat Demo",
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            // Model status indicator
            Text(
                text = when {
                    isModelLoaded -> "Ready"
                    modelStatus == ModelStatus.LOADING -> "Loading..."
                    modelStatus == ModelStatus.DOWNLOADING -> "Downloading..."
                    else -> "Not Ready"
                },
                fontSize = 12.sp,
                color = when {
                    isModelLoaded -> Color.Green
                    modelStatus == ModelStatus.LOADING || modelStatus == ModelStatus.DOWNLOADING -> Color(0xFFFF9800)
                    else -> Color.Red
                }
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Clear conversation button
            IconButton(onClick = onClearConversation) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear conversation"
                )
            }
            
            // Settings button
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        },
        modifier = modifier
    )
}

/**
 * Model status indicator with download progress
 */
@Composable
fun ModelStatusIndicator(
    modelStatus: ModelStatus,
    downloadProgress: com.example.litechat.data.DownloadProgress?,
    onDownloadModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = modelStatus != ModelStatus.AVAILABLE,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (modelStatus) {
                    ModelStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                    ModelStatus.DOWNLOADING -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = when (modelStatus) {
                        ModelStatus.NOT_AVAILABLE -> "Model needs to be downloaded"
                        ModelStatus.DOWNLOADING -> "Downloading model..."
                        ModelStatus.LOADING -> "Loading model..."
                        ModelStatus.ERROR -> "Error loading model"
                        else -> ""
                    },
                    fontWeight = FontWeight.Medium
                )
                
                if (modelStatus == ModelStatus.NOT_AVAILABLE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The AI model (~584MB) needs to be downloaded before you can start chatting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (modelStatus == ModelStatus.DOWNLOADING && downloadProgress != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LinearProgressIndicator(
                        progress = downloadProgress.percentage / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        text = "${downloadProgress.percentage.toInt()}% (${formatBytes(downloadProgress.bytesDownloaded)} / ${formatBytes(downloadProgress.totalBytes)})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (modelStatus == ModelStatus.NOT_AVAILABLE) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onDownloadModel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download Model")
                    }
                }
            }
        }
    }
}

/**
 * Messages list component
 */
@Composable
fun MessagesList(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages) { message ->
            MessageBubble(message = message)
        }
        

    }
    
    // Auto-scroll to bottom when new messages are added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
}

/**
 * Individual message bubble
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.isUser
    val backgroundColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                if (isUser) {
                    Text(
                        text = message.content,
                        color = textColor,
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    MarkdownText(
                        text = message.content,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Show performance metrics for AI messages
                    if (message.performanceMetrics == null) {
                        android.util.Log.d("MessageBubble", "Performance metrics is null")
                    } else {
                        val metrics = message.performanceMetrics!!
                        android.util.Log.d("MessageBubble", "Performance metrics found: TTFT=${metrics.firstTokenLatency}ms, TPS=${metrics.averageTokensPerSecond}")
                        
                        if (metrics.firstTokenLatency > 0 || metrics.averageTokensPerSecond > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                thickness = 0.5.dp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "TTFT: ${metrics.firstTokenLatency}ms",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Tokens/sec: %.2f".format(metrics.averageTokensPerSecond),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = formatTimestamp(message.timestamp),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}



/**
 * Chat input field
 */
@Composable
fun ChatInputField(
    text: String,
    onTextChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    isGenerating: Boolean,
    isModelLoaded: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = if (isModelLoaded) "Type a message..." else "Model not loaded..."
                    )
                },
                readOnly = !isModelLoaded || isGenerating,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.Sentences
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (text.isNotBlank() && isModelLoaded && !isGenerating) {
                            onSendMessage(text)
                        }
                    }
                ),
                maxLines = 4
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = { onSendMessage(text) },
                enabled = text.isNotBlank() && isModelLoaded && !isGenerating,
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send message"
                )
            }
        }
    }
}

/**
 * Format bytes to human readable string
 */
private fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    
    return "%.1f %s".format(size, units[unitIndex])
}

/**
 * Format timestamp to readable string
 */
private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(date)
}
