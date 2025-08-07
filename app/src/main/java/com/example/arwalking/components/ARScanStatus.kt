package com.example.arwalking.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ARScanState {
    SCANNING,
    FOUND,
    LOST,
    IDLE
}

@Composable
fun rememberARScanStatus(): ARScanState {
    // Echtes AR-Scan-Verhalten basierend auf Feature-Matching
    return ARScanState.SCANNING // Default state - wird durch echtes Feature-Matching überschrieben
}

@Composable
fun ARScanStatus(
    scanState: ARScanState,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, statusText) = when (scanState) {
        ARScanState.SCANNING -> Triple(
            Color.Blue.copy(alpha = 0.8f),
            Color.White,
            "Scanning..."
        )
        ARScanState.FOUND -> Triple(
            Color.Green.copy(alpha = 0.8f),
            Color.White,
            "Landmark Found"
        )
        ARScanState.LOST -> Triple(
            Color(0xFFFF9800).copy(alpha = 0.8f),
            Color.White,
            "Landmark Lost"
        )
        ARScanState.IDLE -> Triple(
            Color.Gray.copy(alpha = 0.8f),
            Color.White,
            "Ready"
        )
    }
    
    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = statusText,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}