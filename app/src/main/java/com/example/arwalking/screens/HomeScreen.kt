package com.example.arwalking.screens
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.arwalking.R
import com.example.arwalking.components.LocationDropdown
import com.example.arwalking.components.MenuOverlay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.arwalking.OpenCvCameraActivity
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    var startDropdownExpanded by remember { mutableStateOf(false) }
    var destinationDropdownExpanded by remember { mutableStateOf(false) }
    var selectedStart by remember { mutableStateOf("Start suchen...") }
    var selectedDestination by remember { mutableStateOf("Ziel suchen...") }
    var showMenuOverlay by remember { mutableStateOf(false) }
    var showErrorMessage by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    val startOptions = listOf(
        "Büro Prof. Dr. Wolff (PT 3.0.60)",
        "Büro Prof. Dr. Ludwig (PT 3.0.84C) ",
        "Mensa (coming soon)",
        "Parkplatz (coming soon)",
        "Test 1",
        "Test 2",
        "Test 3",
        "Test 4",
        "Ort1",
        "Ort2",
        "Ort3",
    )

    val destinationOptions = listOf(
        "Büro Prof. Dr. Wolff (PT 3.0.60)",
        "Büro Prof. Dr. Ludwig (PT 3.0.84C) ",
        "Mensa (coming soon)",
        "Parkplatz (coming soon)",
        "Test 1",
        "Test 2",
        "Test 3",
        "Test 4",
        "Ort1",
        "Ort2",
        "Ort3",
    )

    val context = LocalContext.current
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Validation: Check if both start and destination are selected
            if (selectedStart == "Start suchen..." || selectedDestination == "Ziel suchen...") {
                showErrorMessage = true
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                return@rememberLauncherForActivityResult
            }

            // Validation: Check if start and destination are different
            if (selectedStart == selectedDestination) {
                showErrorMessage = true
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                return@rememberLauncherForActivityResult
            }

            val destination = if (selectedDestination != "Ziel suchen...") selectedDestination else "Unbekanntes Ziel"
            val startLocation = if (selectedStart != "Start suchen...") selectedStart else "Unbekannter Start"
            val encodedDestination = URLEncoder.encode(destination, StandardCharsets.UTF_8.toString())
            val encodedStartLocation = URLEncoder.encode(startLocation, StandardCharsets.UTF_8.toString())
            navController.navigate("camera_navigation/$encodedDestination/$encodedStartLocation")
        }
    }

    fun navigateWithPermission() {
        // Validation: Check if both start and destination are selected
        if (selectedStart == "Start suchen..." || selectedDestination == "Ziel suchen...") {
            showErrorMessage = true
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }

        // Validation: Check if start and destination are different
        if (selectedStart == selectedDestination) {
            showErrorMessage = true
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val destination = if (selectedDestination != "Ziel suchen...") selectedDestination else "Unbekanntes Ziel"
            val startLocation = if (selectedStart != "Start suchen...") selectedStart else "Unbekannter Start"
            val encodedDestination = URLEncoder.encode(destination, StandardCharsets.UTF_8.toString())
            val encodedStartLocation = URLEncoder.encode(startLocation, StandardCharsets.UTF_8.toString())
            navController.navigate("camera_navigation/$encodedDestination/$encodedStartLocation")
        } else {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Auto-hide error message after 3 seconds
    LaunchedEffect(showErrorMessage) {
        if (showErrorMessage) {
            delay(3000)
            showErrorMessage = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Background Image
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Top gradient overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .requiredHeight(200.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom gradient overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .requiredWidth(412.dp)
                .requiredHeight(200.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f)
                        )
                    )
                )
        )

        // Top Logo Section
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "AR Walking Logo",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 120.dp)
                .requiredWidth(280.dp)
                .requiredHeight(80.dp)
        )

        // Menu Icon in top right with animation
        val menuInteractionSource = remember { MutableInteractionSource() }
        val isMenuPressed by menuInteractionSource.collectIsPressedAsState()

        val menuScale by animateFloatAsState(
            targetValue = if (isMenuPressed) 0.9f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessHigh
            ), label = "menuScale"
        )

        Icon(
            painter = painterResource(id = R.drawable.menu_1),
            contentDescription = "Menu",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-20).dp, y = 60.dp)
                .size(28.dp)
                .graphicsLayer {
                    scaleX = menuScale
                    scaleY = menuScale
                }
                .clickable(
                    interactionSource = menuInteractionSource,
                    indication = null
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenuOverlay = true
                }
        )

        // Vertical dots connection
        val dotCount = 30
        val totalHeight = 266.dp

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredHeight(totalHeight)
                .requiredWidth(4.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(dotCount) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.8f))
                    )
                }
            }
        }

        // Start Location Dropdown
        LocationDropdown(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-170).dp),
            selectedText = selectedStart,
            options = startOptions,
            isExpanded = startDropdownExpanded,
            onExpandedChange = { expanded ->
                startDropdownExpanded = expanded

                // Schließe das andere Dropdown wenn dieses geöffnet wird
                if (expanded && destinationDropdownExpanded) {
                    destinationDropdownExpanded = false
                }
            },
            onOptionSelected = { selectedStart = it },
            onTextChange = { selectedStart = it },
            iconResource = null // Blue dot will be drawn directly
        )

        // Destination Location Dropdown
        LocationDropdown(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 170.dp),
            selectedText = selectedDestination,
            options = destinationOptions,
            isExpanded = destinationDropdownExpanded,
            onExpandedChange = { expanded ->
                destinationDropdownExpanded = expanded

                // Schließe das andere Dropdown wenn dieses geöffnet wird
                if (expanded && startDropdownExpanded) {
                    startDropdownExpanded = false
                }
            },
            onOptionSelected = { selectedDestination = it },
            onTextChange = { selectedDestination = it },
            iconResource = R.drawable.mappin1,
            iconTint = Color(0xFFD31526),
            expandUpward = true // Dropdown nach oben klappen
        )

        // Start Button with modern design and animations
        val buttonInteractionSource = remember { MutableInteractionSource() }
        val isPressed by buttonInteractionSource.collectIsPressedAsState()

        val buttonScale by animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ), label = "buttonScale"
        )

        val buttonElevation by animateFloatAsState(
            targetValue = if (isPressed) 4.dp.value else 12.dp.value,
            animationSpec = tween(100), label = "buttonElevation"
        )

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 180.dp)
                .width(140.dp)
                .height(56.dp)
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                }
                .clickable(
                    interactionSource = buttonInteractionSource,
                    indication = null
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navigateWithPermission()
                },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF94AD0C)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = buttonElevation.dp
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.navigation21),
                    contentDescription = "navigation",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Starten",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    style = TextStyle(
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }
        /**
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp) // Weniger Abstand als der andere
                .width(140.dp)
                .height(56.dp)
                .clickable {
                    context.startActivity(Intent(context, OpenCvCameraActivity::class.java))
                },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0F66C5) // andere Farbe
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "OpenCV Test",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600
                )
            }
        }
        **/

        // Location Finding Button (Google Maps style) - positioned in bottom right
        val locationButtonInteractionSource = remember { MutableInteractionSource() }
        val isLocationButtonPressed by locationButtonInteractionSource.collectIsPressedAsState()

        val locationButtonScale by animateFloatAsState(
            targetValue = if (isLocationButtonPressed) 0.9f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessHigh
            ), label = "locationButtonScale"
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = -20.dp, y = (-50).dp)
                .size(56.dp)
                .graphicsLayer {
                    scaleX = locationButtonScale
                    scaleY = locationButtonScale
                }
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .border(
                    width = 0.5.dp,
                    color = Color(0xFF0F0F86),
                    shape = CircleShape
                )
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.12f)
                )
                .clickable(
                    interactionSource = locationButtonInteractionSource,
                    indication = null
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    // TODO: Add location finding functionality here
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.location_searching),
                contentDescription = "Find current location",
                tint = Color(0xFF94AD0C),
                modifier = Modifier.size(24.dp)
            )
        }

        // Menu Overlay
        MenuOverlay(
            isVisible = showMenuOverlay,
            onDismiss = { showMenuOverlay = false },
            onFavoriteSelected = { favorite ->
                selectedStart = favorite.startLocation
                selectedDestination = favorite.destination
            }
        )

        // Error Message with animation - zentriert im Bildschirm
        androidx.compose.animation.AnimatedVisibility(
            visible = showErrorMessage,
            enter = scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(
                animationSpec = tween(300)
            ),
            exit = scaleOut(
                targetScale = 0.8f,
                animationSpec = tween(200)
            ) + fadeOut(
                animationSpec = tween(200)
            ),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .zIndex(10f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Red.copy(alpha = 0.9f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.alert_circle),
                        contentDescription = "Error",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Fehler",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            selectedStart == "Start suchen..." || selectedDestination == "Ziel suchen..." ->
                                "Bitte wählen Sie sowohl einen Startpunkt als auch ein Ziel aus."
                            selectedStart == selectedDestination ->
                                "Startpunkt und Ziel dürfen nicht identisch sein."
                            else -> "Unbekannter Fehler"
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 412, heightDp = 917)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(navController = rememberNavController())
}

