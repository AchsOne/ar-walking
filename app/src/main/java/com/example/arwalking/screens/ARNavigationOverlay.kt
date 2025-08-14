package com.example.arwalking.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.arwalking.R
import com.example.arwalking.data.FavoritesRepository
import com.example.arwalking.data.NavigationStep
import com.example.arwalking.data.ArrowDirection
import com.example.arwalking.ui.ARNavigationUIState
import components.NavigationDrawer
import components.NavigationStepData

/**
 * AR Navigation Overlay - Enhanced UI overlay for AR navigation
 * Integrates with the AR navigation system and provides real-time feedback
 */
@Composable
fun ARNavigationOverlay(
    mainNavController: NavController,
    destination: String = "Unbekanntes Ziel",
    startLocation: String = "Unbekannter Start",
    uiState: ARNavigationUIState,
    currentStep: NavigationStep?,
    onNextStep: () -> Unit = {},
    onPreviousStep: () -> Unit = {},
    onResetNavigation: () -> Unit = {},
    onToggleDebug: () -> Unit = {}
) {
    // Check if current route is a favorite (reactive)
    val favorites by FavoritesRepository.favorites.collectAsState()
    val isFavorite = favorites.any { 
        it.startLocation == startLocation && it.destination == destination 
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Semi-transparent gradient at the top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(300.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 800f
                    )
                )
        )

        // Top bar with navigation info
        TopNavigationBar(
            mainNavController = mainNavController,
            destination = destination,
            startLocation = startLocation,
            isFavorite = isFavorite,
            uiState = uiState,
            onToggleDebug = onToggleDebug,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // AR Status Indicator
        ARStatusIndicator(
            uiState = uiState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        // Landmark Recognition Feedback
        if (uiState.currentLandmarkId != null) {
            LandmarkFeedback(
                landmarkId = uiState.currentLandmarkId,
                confidence = uiState.matchConfidence,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(16.dp)
            )
        }

        // Navigation Drawer with enhanced step information
        EnhancedNavigationDrawer(
            currentStep = currentStep,
            uiState = uiState,
            onNextStep = onNextStep,
            onPreviousStep = onPreviousStep,
            onResetNavigation = onResetNavigation,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Arrow Direction Indicator (when arrow is not visible in AR)
        if (!uiState.isArrowVisible && currentStep != null) {
            ArrowDirectionIndicator(
                direction = currentStep.arrowDirection,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            )
        }
    }
}

@Composable
fun TopNavigationBar(
    mainNavController: NavController,
    destination: String,
    startLocation: String,
    isFavorite: Boolean,
    uiState: ARNavigationUIState,
    onToggleDebug: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = 70.dp)
            .padding(horizontal = 20.dp)
    ) {
        // Back button
        Icon(
            painter = painterResource(id = R.drawable.chevron_left_1),
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(35.dp)
                .clickable {
                    mainNavController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
                .padding(4.dp)
        )

        // Destination text with progress
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.6f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = destination,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
                modifier = Modifier
                    .drawBehind {
                        drawIntoCanvas { canvas ->
                            val shadowPaint = Paint().apply {
                                color = Color.Black.copy(alpha = 0.5f)
                                isAntiAlias = true
                            }
                        }
                    }
            )
            
            // Progress indicator
            if (uiState.totalSteps > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = uiState.progress,
                    modifier = Modifier
                        .width(120.dp)
                        .height(2.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
                Text(
                    text = "${uiState.currentStepIndex + 1}/${uiState.totalSteps}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Debug toggle button
            Icon(
                painter = painterResource(id = R.drawable.star_outline), // Use appropriate debug icon
                contentDescription = "Debug",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onToggleDebug() }
            )
            
            // Favorite button
            Icon(
                painter = painterResource(
                    id = if (isFavorite) R.drawable.star_filled else R.drawable.star_outline
                ),
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(35.dp)
                    .clickable {
                        if (isFavorite) {
                            val favorites = FavoritesRepository.favorites.value
                            val favoriteToRemove = favorites.find { 
                                it.startLocation == startLocation && it.destination == destination 
                            }
                            favoriteToRemove?.let {
                                FavoritesRepository.removeFavorite(it)
                            }
                        } else {
                            FavoritesRepository.addFavorite(startLocation, destination)
                        }
                    }
                    .padding(4.dp)
            )
        }
    }
}

@Composable
fun ARStatusIndicator(
    uiState: ARNavigationUIState,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        !uiState.isARSessionActive -> Color.Red
        !uiState.isTracking -> Color.Yellow
        uiState.matchConfidence > 0.5f -> Color.Green
        else -> Color.Gray
    }
    
    val statusText = when {
        !uiState.isARSessionActive -> "AR Inaktiv"
        !uiState.isTracking -> "Tracking verloren"
        uiState.matchConfidence > 0.5f -> "Landmarke erkannt"
        else -> "Suche Landmarke..."
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun LandmarkFeedback(
    landmarkId: String,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Landmarke erkannt",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = landmarkId,
                color = Color.White,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = confidence,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = if (confidence > 0.7f) Color.Green else if (confidence > 0.4f) Color.Yellow else Color.Red,
                trackColor = Color.Gray
            )
            Text(
                text = "${(confidence * 100).toInt()}%",
                color = Color.White,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun EnhancedNavigationDrawer(
    currentStep: NavigationStep?,
    uiState: ARNavigationUIState,
    onNextStep: () -> Unit,
    onPreviousStep: () -> Unit,
    onResetNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Convert current step to NavigationStepData for compatibility
    val navigationSteps = if (currentStep != null) {
        listOf(
            NavigationStepData(
                currentStep.instructionDe,
                getIconForDirection(currentStep.arrowDirection)
            )
        )
    } else {
        // Fallback steps
        listOf(
            NavigationStepData("Warten auf Navigation...", R.drawable.arrow_up_1)
        )
    }
    
    NavigationDrawer(
        navigationSteps = navigationSteps,
        destinationLabel = currentStep?.instructionDe ?: "Laden...",
        onClose = { /* Handled by back button */ },
        availableZoomRatios = listOf(0.7f, 1.0f, 2.0f), // Default zoom ratios
        currentZoomRatio = 1.0f,
        onZoomChange = { /* Handle zoom if needed */ },
        modifier = modifier
    )
}

@Composable
fun ArrowDirectionIndicator(
    direction: ArrowDirection,
    modifier: Modifier = Modifier
) {
    val arrowIcon = getIconForDirection(direction)
    val directionText = getTextForDirection(direction)
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = arrowIcon),
                contentDescription = directionText,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = directionText,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Get icon resource for arrow direction
 */
private fun getIconForDirection(direction: ArrowDirection): Int {
    return when (direction) {
        ArrowDirection.FORWARD -> R.drawable.arrow_up_1
        ArrowDirection.LEFT -> R.drawable.corner_up_left_1
        ArrowDirection.RIGHT -> R.drawable.corner_up_right_1
        ArrowDirection.BACK -> R.drawable.arrow_down_1
        ArrowDirection.UP -> R.drawable.arrow_up_1
        ArrowDirection.DOWN -> R.drawable.arrow_down_1
        ArrowDirection.NONE -> R.drawable.arrow_up_1
    }
}

/**
 * Get text description for arrow direction
 */
private fun getTextForDirection(direction: ArrowDirection): String {
    return when (direction) {
        ArrowDirection.FORWARD -> "Geradeaus"
        ArrowDirection.LEFT -> "Links abbiegen"
        ArrowDirection.RIGHT -> "Rechts abbiegen"
        ArrowDirection.BACK -> "Umkehren"
        ArrowDirection.UP -> "Nach oben"
        ArrowDirection.DOWN -> "Nach unten"
        ArrowDirection.NONE -> "Warten"
    }
}