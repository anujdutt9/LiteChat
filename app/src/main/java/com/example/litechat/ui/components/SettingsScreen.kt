package com.example.litechat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.litechat.data.AppSettings
import com.example.litechat.data.HardwareAcceleration
import com.example.litechat.data.PerformanceMetrics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    availableHardwareAcceleration: List<HardwareAcceleration>,
    performanceMetrics: PerformanceMetrics,
    onHardwareAccelerationChange: (HardwareAcceleration) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onHuggingFaceTokenChange: (String) -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    text = "Settings",
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )
        
        // Settings content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Hardware Acceleration Section
            SettingsSection(title = "Hardware Acceleration") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableHardwareAcceleration.forEach { acceleration ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.hardwareAcceleration == acceleration,
                                onClick = { onHardwareAccelerationChange(acceleration) }
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Column {
                                Text(
                                    text = acceleration.name,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = getHardwareAccelerationDescription(acceleration),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Model Parameters Section
            SettingsSection(title = "Model Parameters") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Temperature
                    Column {
                        Text(
                            text = "Temperature: ${settings.temperature}",
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Controls randomness in responses (0.0 = deterministic, 1.0 = very random)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = settings.temperature,
                            onValueChange = onTemperatureChange,
                            valueRange = 0.0f..1.0f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    

                    

                }
            }
            
            // HuggingFace Access Token Section
            SettingsSection(title = "HuggingFace Access") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = settings.huggingFaceAccessToken,
                        onValueChange = onHuggingFaceTokenChange,
                        label = { Text("Access Token") },
                        placeholder = { Text("Enter your HuggingFace access token") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                // Hide keyboard when done
                            }
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                    
                    Text(
                        text = "Required to download models from HuggingFace. Get your token from https://huggingface.co/settings/tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Show save button only when token is entered
                    if (settings.huggingFaceAccessToken.isNotEmpty()) {
                        Button(
                            onClick = { /* Token is automatically saved on change */ },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false
                        ) {
                            Text("Token Saved ✓")
                        }
                    }
                }
            }
            
            // Performance Metrics Section
            SettingsSection(title = "Performance Metrics") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricRow(
                        label = "Time to First Token",
                        value = if (performanceMetrics.firstTokenLatency > 0) "${performanceMetrics.firstTokenLatency}ms" else "Not measured"
                    )
                    MetricRow(
                        label = "Average Tokens/Second",
                        value = if (performanceMetrics.averageTokensPerSecond > 0) "%.2f".format(performanceMetrics.averageTokensPerSecond) else "Not measured"
                    )
                    MetricRow(
                        label = "Total Tokens Generated",
                        value = "${performanceMetrics.totalTokensGenerated}"
                    )
                    MetricRow(
                        label = "Total Inference Time",
                        value = if (performanceMetrics.totalInferenceTime > 0) "${performanceMetrics.totalInferenceTime}ms" else "Not measured"
                    )
                    MetricRow(
                        label = "Last Updated",
                        value = if (performanceMetrics.lastUpdated > 0) {
                            val timeDiff = System.currentTimeMillis() - performanceMetrics.lastUpdated
                            when {
                                timeDiff < 60000 -> "${timeDiff / 1000}s ago"
                                timeDiff < 3600000 -> "${timeDiff / 60000}m ago"
                                else -> "${timeDiff / 3600000}h ago"
                            }
                        } else "Never"
                    )
                }
            }
            
            // Model Information Section
            SettingsSection(title = "Model Information") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoRow(
                        label = "Model Name",
                        value = "Gemma3-1B-IT"
                    )
                    InfoRow(
                        label = "Model Size",
                        value = "~584 MB"
                    )
                    InfoRow(
                        label = "Quantization",
                        value = "4-bit (INT4)"
                    )
                    InfoRow(
                        label = "License",
                        value = "Gemma License"
                    )
                }
            }
        }
    }
}

/**
 * Settings section wrapper
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

/**
 * Metric row for performance display
 */
@Composable
private fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Info row for model information
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Get description for hardware acceleration option
 */
private fun getHardwareAccelerationDescription(acceleration: HardwareAcceleration): String {
    return when (acceleration) {
        HardwareAcceleration.CPU -> "Slower but compatible with all devices"
        HardwareAcceleration.GPU -> "Faster inference using GPU acceleration"
    }
}
