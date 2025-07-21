package com.example.arwalking.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.arwalking.R

@Composable
fun LocationDropdown(
    selectedText: String,
    options: List<String>,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    iconResource: Int? = null,
    iconTint: Color = Color.White,
    dropdownOffset: Dp = 0.dp
) {
    // Haupt-Feld
    Box(
        modifier = modifier
            .offset(y = dropdownOffset)
            .requiredWidth(350.dp)
            .requiredHeight(50.dp)
            .zIndex(2f)
    ) {
        // Hintergrund, Border, Shadow & Klick
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
                .clickable { onExpandedChange(!isExpanded) }
        )

        // Blur‑Effekt
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

        // Icon oder blauer Punkt
        if (iconResource != null) {
            Icon(
                painter = painterResource(id = iconResource),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 20.dp)
                    .requiredSize(20.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 20.dp)
                    .requiredSize(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xff00a8e8))
            )
        }

        // Ausgewählter Text
        Text(
            text = selectedText,
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
                .offset(x = if (iconResource != null) 50.dp else 45.dp)
        )

        // Pfeil-Icon
        Icon(
            painter = painterResource(
                id = if (iconResource != null) R.drawable.chevrondown2 else R.drawable.chevrondown1
            ),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.95f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-20).dp)
        )
    }

    // Dropdown-Liste
    if (isExpanded) {
        Box(
            modifier = modifier
                .offset(y = dropdownOffset + 120.dp)
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
                options.forEach { option ->
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
                                onOptionSelected(option)
                                onExpandedChange(false)
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                    )
                }
            }
        }
    }
}
