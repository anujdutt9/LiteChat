package com.example.litechat.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Simple markdown renderer for basic formatting
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val annotatedString = parseMarkdown(text)
    
    Text(
        text = annotatedString,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium
    )
}

/**
 * Parse basic markdown formatting
 */
@Composable
private fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val textLength = text.length
        
        while (currentIndex < textLength) {
            // Handle bold text (**text**)
            if (currentIndex + 1 < textLength && 
                text[currentIndex] == '*' && 
                text[currentIndex + 1] == '*') {
                
                val endIndex = text.indexOf("**", currentIndex + 2)
                if (endIndex != -1) {
                    val boldText = text.substring(currentIndex + 2, endIndex)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldText)
                    pop()
                    currentIndex = endIndex + 2
                    continue
                }
            }
            
            // Handle italic text (*text*)
            if (currentIndex < textLength && text[currentIndex] == '*') {
                val endIndex = text.indexOf('*', currentIndex + 1)
                if (endIndex != -1) {
                    val italicText = text.substring(currentIndex + 1, endIndex)
                    pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    append(italicText)
                    pop()
                    currentIndex = endIndex + 1
                    continue
                }
            }
            
            // Handle code blocks (`code`)
            if (currentIndex < textLength && text[currentIndex] == '`') {
                val endIndex = text.indexOf('`', currentIndex + 1)
                if (endIndex != -1) {
                    val codeText = text.substring(currentIndex + 1, endIndex)
                    pushStyle(SpanStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 14.sp,
                        background = MaterialTheme.colorScheme.surfaceVariant
                    ))
                    append(codeText)
                    pop()
                    currentIndex = endIndex + 1
                    continue
                }
            }
            
            // Handle strikethrough text (~~text~~)
            if (currentIndex + 1 < textLength && 
                text[currentIndex] == '~' && 
                text[currentIndex + 1] == '~') {
                
                val endIndex = text.indexOf("~~", currentIndex + 2)
                if (endIndex != -1) {
                    val strikeText = text.substring(currentIndex + 2, endIndex)
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(strikeText)
                    pop()
                    currentIndex = endIndex + 2
                    continue
                }
            }
            
            // Handle headers (# Header)
            if (currentIndex < textLength && text[currentIndex] == '#') {
                var headerLevel = 0
                var tempIndex = currentIndex
                while (tempIndex < textLength && text[tempIndex] == '#') {
                    headerLevel++
                    tempIndex++
                }
                
                if (headerLevel <= 6 && tempIndex < textLength && text[tempIndex] == ' ') {
                    val endIndex = text.indexOf('\n', tempIndex)
                    val headerText = if (endIndex != -1) {
                        text.substring(tempIndex + 1, endIndex)
                    } else {
                        text.substring(tempIndex + 1)
                    }
                    
                    val fontSize = when (headerLevel) {
                        1 -> 24.sp
                        2 -> 20.sp
                        3 -> 18.sp
                        4 -> 16.sp
                        5 -> 14.sp
                        else -> 12.sp
                    }
                    
                    pushStyle(SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize
                    ))
                    append(headerText)
                    pop()
                    
                    if (endIndex != -1) {
                        currentIndex = endIndex
                    } else {
                        currentIndex = textLength
                    }
                    continue
                }
            }
            
            // Handle line breaks
            if (currentIndex < textLength && text[currentIndex] == '\n') {
                append('\n')
                currentIndex++
                continue
            }
            
            // Default: append character as-is
            if (currentIndex < textLength) {
                append(text[currentIndex])
                currentIndex++
            }
        }
    }
}
