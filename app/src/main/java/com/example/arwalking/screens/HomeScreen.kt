package com.example.arwalking.screens
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

    val startOptions = listOf(
        "Büro Prof. Dr. Wolff (PT 3.0.60)",
        "Büro Prof. Dr. Ludwig (PT 3.0.84C) ",
        "Mensa (coming soon)",
        "Parkplatz (coming soon)",
        "Parkplatz (coming soon)",
        "Büro Prof. Dr. Wolff (PT 3.0.60)",
        "Büro Prof. Dr. Ludwig (PT 3.0.84C) ",
        "Mensa (coming soon)",
        "Parkplatz (coming soon)"
    )

    val destinationOptions = listOf(
        "Büro Prof. Dr. Wolff (PT 3.0.60)",
        "Büro Prof. Dr. Ludwig (PT 3.0.84C) ",
        "Mensa (coming soon)",
        "Parkplatz (coming soon)",
        "Büro Prof. Dr. Wolff (PT 3.0.60)",
        "Büro Prof. Dr. Ludwig (PT 3.0.84C) ",
        "Mensa (coming soon)",
        "Parkplatz (coming soon)",
        "Büro Prof. Dr. Wolff (PT 3.0.60)",
        "Büro Prof. Dr. Ludwig (PT 3.0.84C) ",
        "Mensa (coming soon)",
        "Parkplatz (coming soon)"
    )

    val context = LocalContext.current
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val destination = if (selectedDestination != "Ziel suchen...") selectedDestination else "Unbekanntes Ziel"
            val encodedDestination = URLEncoder.encode(destination, StandardCharsets.UTF_8.toString())
            navController.navigate("camera_navigation/$encodedDestination")
        }
    }

    fun navigateWithPermission() {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val destination = if (selectedDestination != "Ziel suchen...") selectedDestination else "Unbekanntes Ziel"
            val encodedDestination = URLEncoder.encode(destination, StandardCharsets.UTF_8.toString())
            navController.navigate("camera_navigation/$encodedDestination")
        } else {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(startDropdownExpanded, destinationDropdownExpanded) {
                detectTapGestures { offset ->
                    // Bereich für Start-Dropdown (oben)
                    val startDropdownArea = Rect(
                        offset = Offset(
                            x = (size.width - 350.dp.toPx()) / 2,
                            y = size.height / 2 - 150.dp.toPx() - 25.dp.toPx()
                        ),
                        size = Size(350.dp.toPx(), 250.dp.toPx()) // Feld + Dropdown-Bereich
                    )

                    // Bereich für Destination-Dropdown (unten)
                    val destinationDropdownArea = Rect(
                        offset = Offset(
                            x = (size.width - 350.dp.toPx()) / 2,
                            y = size.height / 2 - 90.dp.toPx() - 25.dp.toPx() - 250.dp.toPx()
                        ),
                        size = Size(350.dp.toPx(), 300.dp.toPx()) // Feld + Dropdown-Bereich (nach oben)
                    )

                    // Wenn außerhalb der Dropdown-Bereiche geklickt wird, schließen
                    if (startDropdownExpanded && !startDropdownArea.contains(offset)) {
                        startDropdownExpanded = false
                    }
                    if (destinationDropdownExpanded && !destinationDropdownArea.contains(offset)) {
                        destinationDropdownExpanded = false
                    }
                }
            }
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
            onExpandedChange = {
                startDropdownExpanded = it

                // Schließe das andere Dropdown wenn dieses geöffnet wird
                if (it && destinationDropdownExpanded) {
                    destinationDropdownExpanded = false
                }
            },
            onOptionSelected = { selectedStart = it },
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
            onExpandedChange = {
                destinationDropdownExpanded = it
                // Schließe das andere Dropdown wenn dieses geöffnet wird
                if (it && startDropdownExpanded) {
                    startDropdownExpanded = false
                }
            },
            onOptionSelected = { selectedDestination = it },
            iconResource = R.drawable.mappin1,
            iconTint = Color(0xFFD31526),

        )

        // Start Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 180.dp)
                .requiredWidth(200.dp)
                .requiredHeight(50.dp)
                .zIndex(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(25.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xff94ad0c),
                                Color(0xff7a9208),
                                Color(0xff94ad0c)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xffa8c40f),
                                Color(0xff6d7f06)
                            )
                        ),
                        shape = RoundedCornerShape(25.dp)
                    )
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(25.dp),
                        ambientColor = Color.Black.copy(alpha = 0.3f),
                        spotColor = Color.Black.copy(alpha = 0.3f)
                    )
                    .clickable {
                        // Navigation mit Kamera-Permission Check
                        navigateWithPermission()
                    }
            )

            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.navigation21),
                    contentDescription = "navigation",
                    tint = Color.White,
                    modifier = Modifier.requiredSize(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Starten",
                    color = Color.White,
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}

@Preview(widthDp = 412, heightDp = 917)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(navController = rememberNavController())
}