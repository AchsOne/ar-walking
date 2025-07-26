package components


import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arwalking.R
import android.graphics.RenderEffect as AndroidRenderEffect

// Datenklasse für Navigation Steps
data class NavigationStepData(val text: String, val icon: Int)

@Composable
fun NavigationDrawer(
    navigationSteps: List<NavigationStepData>,
    destinationLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMaximized by remember { mutableStateOf(false) }
    var offsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val dragSensitivity = 0.3f

    // Zoom-Effekt für bessere Interaktion
    val zoomScale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "ZoomScale"
    )

    // Responsiveness - maximale Höhe für Google Maps-like Verhalten
    val containerHeight by animateDpAsState(
        targetValue = if (isMaximized) 800.dp else 160.dp,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 400f
        )
    )



    // Container mit verbesserter Abhebung vom Kamerabild
    Box(
        modifier = modifier
            .fillMaxWidth()
            .requiredHeight(containerHeight)
            .graphicsLayer {
                scaleX = zoomScale
                scaleY = zoomScale
            }
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                    },
                    onDragEnd = {
                        isDragging = false
                        // Google Maps-ähnliche Snap-Logik
                        when {
                            offsetY < -80 -> isMaximized = true
                            offsetY > 80 -> isMaximized = false
                            !isMaximized && offsetY < -30 -> isMaximized = true
                            isMaximized && offsetY > 30 -> isMaximized = false
                        }
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetY += dragAmount.y * dragSensitivity

                        // Instant response für bessere UX
                        when {
                            offsetY < -120 -> {
                                isMaximized = true
                                offsetY = 0f
                            }
                            offsetY > 120 -> {
                                isMaximized = false
                                offsetY = 0f
                            }
                        }
                    }
                )
            }
    ) {
        // Dunkler Glass-Effekt für bessere Sichtbarkeit
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black.copy(alpha = 0.5f),
                            Color.White.copy(alpha = 0.55f),
                        )
                    )
                )
                .graphicsLayer {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        // Starker Blur-Effekt für Glasmorphism
                        renderEffect = AndroidRenderEffect.createBlurEffect(
                            30f, 30f, android.graphics.Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    }
                }
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.2f),
                            Color.White.copy(alpha = 0.05f),
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                )

        )

        // Handle Bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .requiredWidth(50.dp)
                    .requiredHeight(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.4f))
            )
        }

        // Close Button
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 20.dp, y = 28.dp)
                .requiredSize(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                )
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.x),
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.requiredSize(20.dp)
            )
        }

        // Maximize/Minimize Button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-20).dp, y = 28.dp)
                .requiredSize(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
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
                modifier = Modifier
                    .requiredSize(18.dp)
                    .rotate(if (isMaximized) 0f else 180f)
            )
        }

        // Content Area mit verbessertem Spacing
        if (isMaximized) {
            // Maximized View - alle Steps mit Fade-In-Effekt
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 85.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 120.dp)
            ) {
                // Aktiver Step mit Highlight
                if (navigationSteps.isNotEmpty()) {
                    val firstStep = navigationSteps.first()
                    StepCard(
                        step = firstStep,
                        isActive = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Weitere Steps mit Glasmorphism
                navigationSteps.drop(1).forEach { step ->
                    StepCard(
                        step = step,
                        isActive = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Destination mit besonderem Styling
                DestinationCard(
                    destinationLabel = destinationLabel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // Minimized View - nur aktueller Step
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 85.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
            ) {
                if (navigationSteps.isNotEmpty()) {
                    val currentStep = navigationSteps.first()
                    StepCard(
                        step = currentStep,
                        isActive = true,
                        isCompact = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
            .requiredHeight(if (isCompact) 70.dp else 80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) {
                    Color(0xff94ac0b)
                } else {
                    Color.Black.copy(alpha = 0.4f)
                }
            )
            .border(
                width = 1.dp,
                color = if (isActive) Color.Transparent else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon Container - wie ursprünglich
            Box(
                modifier = Modifier
                    .requiredSize(if (isCompact) 40.dp else 48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) Color.White.copy(alpha = 0.25f)
                        else Color.White.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = step.icon),
                    contentDescription = step.text,
                    modifier = Modifier.requiredSize(if (isCompact) 24.dp else 28.dp),
                    colorFilter = ColorFilter.tint(
                        if (isActive) Color.White else Color.White.copy(alpha = 0.9f)
                    )
                )
            }

            // Text
            Text(
                text = step.text,
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.9f),
                style = TextStyle(
                    fontSize = if (isCompact) 16.sp else 18.sp,
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
            .requiredHeight(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.9f))
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
            // Destination Icon
            Box(
                modifier = Modifier
                    .requiredSize(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xff94ac0b).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.mappin1),
                    contentDescription = "Destination",
                    modifier = Modifier.requiredSize(28.dp),
                    colorFilter = ColorFilter.tint(Color(0xff94ac0b))
                )
            }

            // Destination Text
            Text(
                text = destinationLabel,
                color = Color.Black,
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun NavigationStep(
    text: String,
    icon: Int,
    iconRotation: Float = 0f
) {
    Box(
        modifier = Modifier
            .requiredWidth(width = 312.dp)
            .requiredHeight(height = 47.dp)
    ) {
        Box(
            modifier = Modifier
                .requiredWidth(width = 312.dp)
                .requiredHeight(height = 47.dp)
                .clip(shape = RoundedCornerShape(8.dp))
                .background(color = Color.White)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
        )

        if (icon == R.drawable.door) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = text,
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 8.dp, y = 9.dp)
                    .requiredSize(size = 29.dp)
            )
            Text(
                text = text,
                color = Color.Black,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light
                ),
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 45.dp, y = 13.dp)
            )
        } else {
            Image(
                painter = painterResource(id = icon),
                contentDescription = text,
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 11.24.dp, y = 12.dp)
                    .requiredWidth(width = 22.dp)
                    .requiredHeight(height = 24.dp)
                    .rotate(degrees = iconRotation)
            )
            Text(
                text = text,
                color = Color.Black,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light
                ),
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 40.dp, y = 11.dp)
            )
        }
    }
}

