package com.example.arwalking.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arwalking.R
import com.example.arwalking.ui.theme.GradientUtils

/**
 * Data class for individual navigation steps
 * Each step has a descriptive text and a corresponding icon
 */
data class NavigationStepData(val text: String, val icon: Int)

/**
 * An intelligent navigation drawer inspired by the Google Maps style.
 * Shows the current route steps and can be resized via drag.
 */
@Composable
fun NavigationDrawer(
    navigationSteps: List<NavigationStepData>,
    destinationLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onNextStep: () -> Unit
) {
    var isMaximized by remember { mutableStateOf(false) }
    var offsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Smooth height animation like Google Maps — feels natural
    val containerHeight by animateDpAsState(
        targetValue = if (isMaximized) 750.dp else 280.dp, // Groß genug für alle Steps
        animationSpec = spring(
            dampingRatio = 0.8f, // Not too bouncy
            stiffness = 500f    // Fast enough for responsive UX
        ),
        label = "ContainerHeight"
    )

    // Subtle zoom effect while dragging — gives haptic-like feedback
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.01f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "Scale"
    )


    // Main container with drag gestures
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                    },
                    onDragEnd = {
                        isDragging = false
                        // Snap logic based on drag distance
                        when {
                            offsetY < -50 -> isMaximized = true
                            offsetY > 50 -> isMaximized = false
                        }
                        offsetY = 0f
                    },
                    onDrag = { _, dragAmount ->
                        offsetY += dragAmount.y

                        // Immediate response for smooth UX
                        when {
                            offsetY < -100 -> {
                                isMaximized = true
                                offsetY = 0f
                            }
                            offsetY > 100 -> {
                                isMaximized = false
                                offsetY = 0f
                            }
                        }
                    }
                )
            }
    ) {
        // Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = GradientUtils.safeVerticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black.copy(alpha = 0.5f),
                            Color.White.copy(alpha = 0.55f),
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
        )

        // Drag handle bar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 12.dp)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.8f))
        )

        // Header area with optimized UX layout
        // Logo positioned on the left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 20.dp, y = 7.dp)
                .size(85.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.size(90.dp)
            )
        }

        // "Next step" button in place of the former zoom controls
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 25.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .heightIn(min = 48.dp)
                    .clickable { onNextStep() }
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                Text(
                    text = "Nächster Schritt",
                    color = Color.White,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Icon(
                    painter = painterResource(id = R.drawable.chevron_left_1),
                    contentDescription = "Nächster Schritt",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { scaleX = -1f } // Mirror so the chevron points to the right
                )
            }
        }
        }

        // Maximize/Minimize button positioned on the right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-20).dp, y = 25.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                )
                .clickable { isMaximized = !isMaximized },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    id = if (isMaximized) R.drawable.minimize_2 else R.drawable.maximize_2_1
                ),
                contentDescription = if (isMaximized) "Minimize" else "Maximize",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        // Content area
        if (isMaximized) {
            // Expanded view - show all steps
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 95.dp) // Adjusted for new header layout
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 120.dp) // Increased bottom padding for larger drawer
            ) {
                // Current step (highlighted)
                if (navigationSteps.isNotEmpty()) {
                    StepCard(
                        step = navigationSteps.first(),
                        isActive = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Remaining steps
                navigationSteps.drop(1).forEach { step ->
                    StepCard(
                        step = step,
                        isActive = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Destination card
                DestinationCard(
                    destinationLabel = destinationLabel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // Collapsed view - show current step and next steps
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 95.dp) // Adjusted for new header layout consistency
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Current step (full width)
                if (navigationSteps.isNotEmpty()) {
                    StepCard(
                        step = navigationSteps.first(),
                        isActive = true,
                        isCompact = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Next steps (narrower width)
                navigationSteps.drop(1).take(2).forEach { step -> // Show max 2 next steps
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        StepCard(
                            step = step,
                            isActive = false,
                            isCompact = true,
                            modifier = Modifier.fillMaxWidth(0.96f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StepCard(
    step: NavigationStepData,
    isActive: Boolean,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(if (isCompact) 64.dp else 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) {
                    Color(0xFF94AC0B)
                } else {
                    Color.Black.copy(alpha = 0.3f)
                }
            )
            .border(
                width = 1.dp,
                color = if (isActive) Color.Transparent else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(if (isCompact) 36.dp else 44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive)
                            Color.White.copy(alpha = 0.2f)
                        else
                            Color.White.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = step.icon),
                    contentDescription = step.text,
                    modifier = Modifier.size(if (isCompact) 20.dp else 24.dp),
                    colorFilter = ColorFilter.tint(Color.White)
                )
            }

            // Step text
            Text(
                text = step.text,
                color = Color.White,
                style = TextStyle(
                    fontSize = if (isCompact) 15.sp else 17.sp,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DestinationCard(
    destinationLabel: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Destination icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF94AC0B).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.mappin1),
                    contentDescription = "Destination",
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(Color(0xFF94AC0B))
                )
            }

            // Destination text
            Text(
                text = destinationLabel,
                color = Color.Black,
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

