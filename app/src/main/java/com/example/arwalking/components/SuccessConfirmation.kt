package com.example.arwalking.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.arwalking.ui.theme.ARGreen
import com.example.arwalking.ui.theme.ARGreenLight
import com.example.arwalking.ui.theme.SuccessGreen
import kotlinx.coroutines.delay

enum class ConfirmationType {
    IMAGE_CAPTURED,
    UPLOAD_SUCCESS,
    TRAINING_COMPLETE
}

@Composable
fun SuccessConfirmation(
    isVisible: Boolean,
    type: ConfirmationType,
    title: String = "",
    message: String = "",
    landmarkName: String? = null,
    onDismiss: () -> Unit,
    onContinue: (() -> Unit)? = null,
    autoHideAfterMs: Long = 3000L
) {
    var showContent by remember { mutableStateOf(false) }
    var showCheckmark by remember { mutableStateOf(false) }
    
    // Auto-hide functionality
    LaunchedEffect(isVisible) {
        if (isVisible) {
            showContent = true
            delay(300) // Small delay for entrance animation
            showCheckmark = true
            
            if (autoHideAfterMs > 0) {
                delay(autoHideAfterMs)
                onDismiss()
            }
        } else {
            showContent = false
            showCheckmark = false
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)) + scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ),
        exit = fadeOut(animationSpec = tween(200)) + scaleOut(
            animationSpec = tween(200)
        )
    ) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Success Icon with Animation
                        AnimatedSuccessIcon(
                            type = type,
                            showCheckmark = showCheckmark
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Title
                        Text(
                            text = title.ifEmpty { getDefaultTitle(type) },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Message
                        Text(
                            text = message.ifEmpty { getDefaultMessage(type, landmarkName) },
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        
                        // Landmark name if provided
                        landmarkName?.let { name ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (onContinue != null) Arrangement.SpaceEvenly else Arrangement.Center
                        ) {
                            if (onContinue != null) {
                                Button(
                                    onClick = onContinue,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Weiter",
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (onContinue != null) 
                                        MaterialTheme.colorScheme.surfaceVariant 
                                    else 
                                        MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = if (onContinue != null) Modifier.weight(1f) else Modifier
                            ) {
                                Text(
                                    text = if (onContinue != null) "Schließen" else "OK",
                                    color = if (onContinue != null) 
                                        MaterialTheme.colorScheme.onSurfaceVariant 
                                    else 
                                        MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedSuccessIcon(
    type: ConfirmationType,
    showCheckmark: Boolean
) {
    val scale by animateFloatAsState(
        targetValue = if (showCheckmark) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconScale"
    )
    
    Box(
        modifier = Modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background Circle
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(SuccessGreen.copy(alpha = 0.1f))
                .scale(scale),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(SuccessGreen),
                contentAlignment = Alignment.Center
            ) {
                // Type-specific icon with checkmark overlay
                when (type) {
                    ConfirmationType.IMAGE_CAPTURED -> {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Image Captured",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    ConfirmationType.UPLOAD_SUCCESS -> {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Upload Success",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    ConfirmationType.TRAINING_COMPLETE -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Training Complete",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
        
        // Checkmark overlay for captured/uploaded states
        if (type != ConfirmationType.TRAINING_COMPLETE && showCheckmark) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ARGreen)
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun getDefaultTitle(type: ConfirmationType): String {
    return when (type) {
        ConfirmationType.IMAGE_CAPTURED -> "Bild erfolgreich aufgenommen!"
        ConfirmationType.UPLOAD_SUCCESS -> "Upload erfolgreich!"
        ConfirmationType.TRAINING_COMPLETE -> "Training abgeschlossen!"
    }
}

private fun getDefaultMessage(type: ConfirmationType, landmarkName: String?): String {
    return when (type) {
        ConfirmationType.IMAGE_CAPTURED -> "Das Bild wurde erfolgreich aufgenommen und wird nun verarbeitet."
        ConfirmationType.UPLOAD_SUCCESS -> "Das Trainingsbild wurde erfolgreich lokal gespeichert."
        ConfirmationType.TRAINING_COMPLETE -> "Das Training für ${landmarkName ?: "den Landmark"} wurde erfolgreich abgeschlossen."
    }
}