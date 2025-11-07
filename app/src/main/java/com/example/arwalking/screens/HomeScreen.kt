package com.example.arwalking.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.arwalking.R
import com.example.arwalking.RouteViewModel
import com.example.arwalking.components.LocationDropdown
import com.example.arwalking.components.MenuOverlay
import com.example.arwalking.ui.theme.GradientUtils
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val routeViewModel: RouteViewModel = viewModel()

    var startDropdownExpanded by remember { mutableStateOf(false) }
    var destinationDropdownExpanded by remember { mutableStateOf(false) }
    var selectedStart by remember { mutableStateOf("Search start...") }
    var selectedDestination by remember { mutableStateOf("Search destination...") }
    var showMenuOverlay by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val startOptions = listOf(
        "Office Prof. Dr. Ludwig",
        "Office Prof. Dr. Wolff (PT 3.0.60) (coming soon)",
        "Cafeteria (coming soon)",
        "Parking (coming soon)",
        "Lecture Hall A (coming soon)",
        "Lecture Hall B (coming soon)",
        "Lab 1 (coming soon)",
        "Lab 2 (coming soon)",
        "Library (coming soon)",
        "Student Office (coming soon)"
    )

    val destinationOptions = listOf(
        "Office Prof. Dr. Ludwig (PT 3.0.84C)",
        "Office Prof. Dr. Wolff (PT 3.0.60) (coming soon)",
        "Cafeteria (coming soon)",
        "Parking (coming soon)",
        "Lecture Hall A (coming soon)",
        "Lecture Hall B (coming soon)",
        "Lab 1 (coming soon)",
        "Lab 2 (coming soon)",
        "Library (coming soon)",
        "Student Office (coming soon)"
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            navigateToCamera(
                navController = navController,
                start = selectedStart,
                destination = selectedDestination,
                onError = { message ->
                    errorMessage = message
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
        }
    }

    fun startNavigation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            navigateToCamera(
                navController = navController,
                start = selectedStart,
                destination = selectedDestination,
                onError = { message ->
                    errorMessage = message
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
        } else {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            delay(3000)
            errorMessage = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        BackgroundImage()
        TopGradient()
        BottomGradient(
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        Logo(modifier = Modifier.align(Alignment.TopCenter))

        MenuButton(
            onClick = {
                showMenuOverlay = true
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            modifier = Modifier.align(Alignment.TopEnd)
        )

        ConnectionDots(modifier = Modifier.align(Alignment.Center))

        LocationDropdown(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-170).dp),
            selectedText = selectedStart,
            options = startOptions,
            isExpanded = startDropdownExpanded,
            onExpandedChange = { expanded ->
                startDropdownExpanded = expanded
                if (expanded) destinationDropdownExpanded = false
            },
            onOptionSelected = { selectedStart = it },
            onTextChange = { selectedStart = it },
            iconResource = null
        )

        LocationDropdown(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 170.dp),
            selectedText = selectedDestination,
            options = destinationOptions,
            isExpanded = destinationDropdownExpanded,
            onExpandedChange = { expanded ->
                destinationDropdownExpanded = expanded
                if (expanded) startDropdownExpanded = false
            },
            onOptionSelected = { selectedDestination = it },
            onTextChange = { selectedDestination = it },
            iconResource = R.drawable.mappin1,
            iconTint = Color(0xFFD31526),
            expandUpward = true
        )

        StartButton(
            onClick = { startNavigation() },
            hapticFeedback = hapticFeedback,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        LocationButton(
            onClick = { /* TODO: Add location functionality */ },
            hapticFeedback = hapticFeedback,
            modifier = Modifier.align(Alignment.BottomEnd)
        )

        MenuOverlay(
            isVisible = showMenuOverlay,
            onDismiss = { showMenuOverlay = false },
            onFavoriteSelected = { favorite ->
                // Apply favorite selection to both fields and collapse any dropdowns
                selectedStart = favorite.startLocation
                selectedDestination = favorite.destination
                startDropdownExpanded = false
                destinationDropdownExpanded = false
            }
        )

        errorMessage?.let { message ->
            ErrorMessage(
                message = message,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun BackgroundImage() {
    Image(
        painter = painterResource(id = R.drawable.background),
        contentDescription = "Background",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun TopGradient() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(200.dp)
            .background(
                brush = GradientUtils.safeVerticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.6f),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
private fun BottomGradient(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(
                brush = GradientUtils.safeVerticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.8f)
                    )
                )
            )
    )
}

@Composable
private fun Logo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.logo),
        contentDescription = "AR Walking Logo",
        modifier = modifier
            .offset(y = 120.dp)
            .requiredWidth(280.dp)
            .requiredHeight(80.dp)
    )
}

@Composable
private fun MenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "menuScale"
    )

    Icon(
        painter = painterResource(id = R.drawable.menu_1),
        contentDescription = "Menu",
        tint = Color.White,
        modifier = modifier
            .offset(x = (-20).dp, y = 60.dp)
            .size(28.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    )
}

@Composable
private fun ConnectionDots(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .requiredHeight(266.dp)
            .requiredWidth(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(30) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.8f))
                )
            }
        }
    }
}

@Composable
private fun StartButton(
    onClick: () -> Unit,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "buttonScale"
    )

    val elevation by animateFloatAsState(
        targetValue = if (isPressed) 4f else 12f,
        animationSpec = tween(100),
        label = "buttonElevation"
    )

    Card(
        modifier = modifier
            .padding(bottom = 180.dp)
            .width(140.dp)
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF94AC0B)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation.dp
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
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Start",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                style = TextStyle(letterSpacing = 0.5.sp)
            )
        }
    }
}

@Composable
private fun LocationButton(
    onClick: () -> Unit,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "locationScale"
    )

    Box(
        modifier = modifier
            .offset(x = (-20).dp, y = (-50).dp)
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(0.5.dp, Color(0xFF0F0F86), CircleShape)
            .shadow(8.dp, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.location_searching),
            contentDescription = "Find location",
            tint = Color(0xFF94AD0C),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ErrorMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(animationSpec = tween(300)),
        exit = scaleOut(targetScale = 0.8f) + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .zIndex(10f),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Color(0xFF007AFF).copy(alpha = 0.1f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.alert_circle),
                        contentDescription = null,
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Error",
                    color = Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = message,
                    color = Color.Gray,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

private fun navigateToCamera(
    navController: NavController,
    start: String,
    destination: String,
    onError: (String) -> Unit
) {
    when {
        start == "Search start..." || destination == "Search destination..." -> {
            onError("Please select both start and destination.")
        }
        start == destination -> {
            onError("Start and destination must be different.")
        }
        !isRouteAvailable(start, destination) -> {
            onError("This route is not yet available.\n\nWe're working on adding more routes.")
        }
        else -> {
            val encodedDestination = URLEncoder.encode(destination, StandardCharsets.UTF_8.toString())
            val encodedStart = URLEncoder.encode(start, StandardCharsets.UTF_8.toString())
            navController.navigate("camera_navigation/$encodedDestination/$encodedStart")
        }
    }
}

private fun isRouteAvailable(start: String, destination: String): Boolean {
    val cleanStart = start.replace(" (coming soon)", "")
    val cleanDestination = destination.replace(" (coming soon)", "")

    return cleanStart == "Office Prof. Dr. Ludwig"
            && cleanDestination == "Office Prof. Dr. Ludwig (PT 3.0.84C)"
}

@Preview(widthDp = 412, heightDp = 917)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(navController = rememberNavController())
}