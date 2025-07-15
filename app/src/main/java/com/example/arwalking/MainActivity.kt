import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.arwalking.R

@Composable
fun AndroidCompact2(modifier: Modifier = Modifier) {
    var startDropdownExpanded by remember { mutableStateOf(false) }
    var destinationDropdownExpanded by remember { mutableStateOf(false) }
    var selectedStart by remember { mutableStateOf("Start suchen...") }
    var selectedDestination by remember { mutableStateOf("Ziel suchen...") }

    val startOptions = listOf(
        "Büro Prof. Dr. Wolff (PT 3.0.60E)",
        "Bibliothek",
        "Labor (PT 3.0.28)",
        "Informatik"
    )

    val destinationOptions = listOf(
        "Mensa",
        "Hörsaal A",
        "Hörsaal B",
        "Parkplatz"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
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
                .requiredWidth(412.dp)
                .requiredHeight(200.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
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

        // Vertical dots connection (mehr Punkte, größerer Abstand)

        val dotCount = 30
        val totalHeight = 250.dp

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

        // Start Location Search Field
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-150).dp)
                .requiredWidth(350.dp)
                .requiredHeight(50.dp)
                .zIndex(2f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(25.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.25f)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.6f),
                                Color.White.copy(alpha = 0.2f),
                                Color.White.copy(alpha = 0.4f)
                            )
                        ),
                        shape = RoundedCornerShape(25.dp)
                    )
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(25.dp),
                        ambientColor = Color.Black.copy(alpha = 0.1f),
                        spotColor = Color.Black.copy(alpha = 0.1f)
                    )
                    .clickable { startDropdownExpanded = !startDropdownExpanded }
            )

            // Verstärkter Blur-Effekt
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(25.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            radius = 150f
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 20.dp)
                    .requiredSize(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xff00a8e8))
            )

            Text(
                text = selectedStart,
                color = Color.White.copy(alpha = 0.95f),
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        offset = Offset(1f, 1f),
                        blurRadius = 3f
                    )
                ),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 45.dp)
            )

            Icon(
                painter = painterResource(id = R.drawable.chevrondown1),
                contentDescription = "dropdown",
                tint = Color.White.copy(alpha = 0.95f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-20).dp)
            )
        }

        if (startDropdownExpanded) {
            // Dropdown unterhalb der Suchleiste mit Abstand
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-29).dp)
                    .requiredWidth(350.dp)
                    .zIndex(3f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.4f)
                                )

                            )
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.7f),
                                    Color.White.copy(alpha = 0.3f),
                                    Color.White.copy(alpha = 0.5f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )

                ) {
                    startOptions.forEach { option ->
                        Text(
                            text = option,
                            color = Color.White.copy(alpha = 0.95f),
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    offset = Offset(1f, 1f),
                                    blurRadius = 2f
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedStart = option
                                    startDropdownExpanded = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp)
                        )
                    }
                }
            }
        }

        // Destination Search Field
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 150.dp)
                .requiredWidth(350.dp)
                .requiredHeight(50.dp)
                .zIndex(2f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(25.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.25f)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.6f),
                                Color.White.copy(alpha = 0.2f),
                                Color.White.copy(alpha = 0.4f)
                            )
                        ),
                        shape = RoundedCornerShape(25.dp)
                    )
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(25.dp),
                        ambientColor = Color.Black.copy(alpha = 0.1f),
                        spotColor = Color.Black.copy(alpha = 0.1f)
                    )
                    .clickable { destinationDropdownExpanded = !destinationDropdownExpanded }
            )

            // Verstärkter Blur-Effekt
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(25.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            radius = 150f
                        )
                    )
            )

            Icon(
                painter = painterResource(id = R.drawable.mappin1),
                contentDescription = "location pin",
                tint = Color(0xffff4757),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 20.dp)
                    .requiredSize(20.dp)
            )

            Text(
                text = selectedDestination,
                color = Color.White.copy(alpha = 0.95f),
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        offset = Offset(1f, 1f),
                        blurRadius = 3f
                    )
                ),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 50.dp)
            )

            Icon(
                painter = painterResource(id = R.drawable.chevrondown2),
                contentDescription = "dropdown",
                tint = Color.White.copy(alpha = 0.95f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-20).dp)
            )
        }

        if (destinationDropdownExpanded) {
            // Dropdown unterhalb der Suchleiste mit Abstand
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 272.dp)
                    .requiredWidth(350.dp)
                    .zIndex(3f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.4f)
                                )
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.7f),
                                    Color.White.copy(alpha = 0.3f),
                                    Color.White.copy(alpha = 0.5f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )

                ) {
                    destinationOptions.forEach { option ->
                        Text(
                            text = option,
                            color = Color.White.copy(alpha = 0.95f),
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    offset = Offset(1f, 1f),
                                    blurRadius = 2f
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedDestination = option
                                    destinationDropdownExpanded = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp)
                        )
                    }
                }
            }
        }

        // Start Button nun unten mittig
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
private fun AndroidCompact2Preview() {
    AndroidCompact2(Modifier)
}