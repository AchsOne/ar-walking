package com.example.arwalking.components

import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.arwalking.R
import android.graphics.RenderEffect as AndroidRenderEffect

@RequiresApi(Build.VERSION_CODES.S)
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
    dropdownOffset: Dp = 8.dp
) {
    val density = LocalDensity.current

    // 1. Chevron‑Rotation
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    // 2. Höhe animieren
    val dropdownHeight = remember { Animatable(0f) }
    val maxDropdownHeight = with(density) { (options.size * 44 + 8).dp.toPx() }
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            dropdownHeight.animateTo(
                maxDropdownHeight,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
        } else {
            dropdownHeight.animateTo(0f, animationSpec = tween(200))
        }
    }

    // 3. Alpha & Scale
    val dropdownAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = if (isExpanded) tween(300, delayMillis = 100) else tween(150)
    )
    val dropdownScale by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        )
    )

    // 4. Scroll‑State
    val scrollState = rememberScrollState()

    // 5. Trigger‑Box
    Box(
        modifier = modifier
            .requiredWidth(350.dp)
            .requiredHeight(50.dp)
    ) {
        // 5a. Hintergrund‑Box (Feld)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(25.dp))
                // Blur‑Effekt über graphicsLayer
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = AndroidRenderEffect.createBlurEffect(
                            20f,
                            20f,
                            Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    }
                }
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.25f)
                        )
                    )
                )
                .border(
                    1.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.2f),
                            Color.White.copy(alpha = 0.4f)
                        )
                    ),
                    RoundedCornerShape(25.dp)
                )
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(25.dp),
                    ambientColor = Color.Black.copy(alpha = 0.1f),
                    spotColor = Color.Black.copy(alpha = 0.1f)
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onExpandedChange(!isExpanded) }
        ) {
            // hier kommt dein eigentlicher Content rein (Icon, Text o.Ä.)
        }

        // 5b. Icon oder Dot
        if (iconResource != null) {
            Icon(
                painter = painterResource(iconResource),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 20.dp)
                    .requiredWidth(20.dp)
                    .requiredHeight(20.dp)
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

        // 5c. Text
        Text(
            text = selectedText,
            color = Color.White.copy(alpha = 0.95f),
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                shadow = Shadow(
                    Color.Black.copy(alpha = 0.5f),
                    Offset(1f, 1f),
                    blurRadius = 3f
                )
            ),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = if (iconResource != null) 50.dp else 45.dp)
        )

        // 5d. Chevron
        Icon(
            painter = painterResource(
                id = if (iconResource != null) R.drawable.chevrondown2 else R.drawable.chevrondown1
            ),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.95f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-20).dp)
                .rotate(chevronRotation)
        )

        // 6. Dropdown im Popup
        if (dropdownHeight.value > 0f) {
            val yOffset = with(density) { (50.dp + dropdownOffset).roundToPx() }
            Popup(
                offset = IntOffset(0, yOffset),
                properties = PopupProperties(clippingEnabled = false)
            ) {
                Box(
                    modifier = Modifier
                        .requiredWidth(350.dp)
                        .height(with(density) { dropdownHeight.value.toDp() })
                        .alpha(dropdownAlpha)
                        .scale(dropdownScale)
                ) {
                    // Blur-Hintergrund (unterste Ebene)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .graphicsLayer {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    renderEffect = AndroidRenderEffect.createBlurEffect(
                                        25f,
                                        25f,
                                        Shader.TileMode.CLAMP
                                    ).asComposeRenderEffect()
                                }
                            }
                    )

                    // Dropdown-Inhalt (oberste Ebene)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Black.copy(alpha = 0.25f),
                                        Color.Black.copy(alpha = 0.4f)
                                    )
                                )
                            )
                            .border(
                                1.5.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.7f),
                                        Color.White.copy(alpha = 0.3f),
                                        Color.White.copy(alpha = 0.5f)
                                    )
                                ),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        options.forEachIndexed { index, option ->
                            val optionAlpha by animateFloatAsState(
                                targetValue = if (isExpanded) 1f else 0f,
                                animationSpec = if (isExpanded)
                                    tween(200, delayMillis = index * 30 + 150)
                                else tween(100)
                            )
                            val optionTranslateY by animateFloatAsState(
                                targetValue = if (isExpanded) 0f else -10f,
                                animationSpec = if (isExpanded)
                                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                else tween(150)
                            )
                            Text(
                                text = option,
                                color = Color.White.copy(alpha = 0.95f),
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    shadow = Shadow(
                                        Color.Black.copy(alpha = 0.4f),
                                        Offset(1f, 1f),
                                        blurRadius = 2f
                                    )
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(optionAlpha)
                                    .graphicsLayer { translationY = optionTranslateY }
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
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
    }
}