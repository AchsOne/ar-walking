package com.example.arwalking.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
    iconTint: Color = Color(0xFF4285F4),
    dropdownOffset: Dp = 8.dp,
    expandUpward: Boolean = false
) {
    val density = LocalDensity.current

    // Suchfunktionalität
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var searchText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var shouldFocusSearch by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    val filteredOptions = if (searchText.isEmpty()) options else options.filter {
        it.contains(searchText, ignoreCase = true)
    }

    // Dropdown-Dimensionen
    val dropdownMinHeight = 120.dp
    val dropdownMaxHeight = 250.dp // Reduziert von 300.dp auf 200.dp
    val scrollStateDropdown = rememberScrollState()

    // Suchfeld fokussieren nur wenn über Textfeld-Click geöffnet wird
    LaunchedEffect(isExpanded, shouldFocusSearch) {
        if (isExpanded && shouldFocusSearch) {
            focusRequester.requestFocus()
        }
        // State zurücksetzen wenn Dropdown geschlossen wird
        if (!isExpanded) {
            shouldFocusSearch = true // Default wieder auf true setzen
            searchText = "" // Suchtext zurücksetzen
        }
    }

    // Refined animations with Apple-style easing
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 400f
        )
    )

    // Height animation with smooth Google Maps-style expansion
    val dropdownHeight = remember { Animatable(0f) }
    val calculatedHeight = with(density) {
        val contentHeight = if (filteredOptions.isEmpty()) {
            // Minimale Höhe für "Keine Ergebnisse" Text (Apple-Stil)
            64.dp
        } else {
            (filteredOptions.size * 44 + 8).dp // Apple-typische Item-Höhe
        }
        contentHeight.coerceIn(dropdownMinHeight, dropdownMaxHeight).toPx()
    }
    LaunchedEffect(isExpanded, filteredOptions.size) {
        if (isExpanded) {
            dropdownHeight.animateTo(
                calculatedHeight,
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 300f
                )
            )
        } else {
            dropdownHeight.animateTo(0f, animationSpec = tween(250))
        }
    }

    // Apple-style fade and scale
    val dropdownAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = if (isExpanded) tween(350, delayMillis = 50) else tween(200)
    )
    val dropdownScale by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 350f
        )
    )

    // Button scale animation (Apple-style press effect)
    val buttonScale by animateFloatAsState(
        targetValue = if (isExpanded) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 500f
        )
    )

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .requiredWidth(350.dp)
            .requiredHeight(50.dp)
    ) {
        // Apple-Style Hintergrund
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(buttonScale)
                .clip(RoundedCornerShape(16.dp)) // Apple-typische Rundung
                .background(Color.White)
                .border(
                    width = 0.5.dp,
                    color = Color(0xFFD1D1D6), // Apple's separator color
                    RoundedCornerShape(16.dp)
                )
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.Black.copy(alpha = 0.04f),
                    spotColor = Color.Black.copy(alpha = 0.08f)
                )
        )

        // Apple-Style Icon
        if (iconResource != null) {
            Icon(
                painter = painterResource(iconResource),
                contentDescription = null,
                tint = Color(0xFFE31B0D), // Apple Blue
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 16.dp)
                    .requiredSize(20.dp)
            )
        } else {
            // Apple-Style Location Indicator
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 18.dp)
                    .requiredSize(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF007AFF)) // Apple Blue
            )
        }

        // Suchfeld anstatt Text
        androidx.compose.material3.TextField(
            value = searchText,
            onValueChange = { newValue ->
                searchText = newValue
                if (newValue.isNotEmpty() && !isExpanded) {
                    shouldFocusSearch = true
                    onExpandedChange(true)
                }
            },
            placeholder = {
                Text(
                    text = selectedText,
                    color = Color(0xFF8E8E93), // Apple's tertiary label color
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && !isExpanded) {
                        shouldFocusSearch = true
                        onExpandedChange(true)
                    }
                }
                .padding(
                    start = if (iconResource != null) 44.dp else 38.dp,
                ),
            singleLine = true,
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color(0xFF007AFF) // Apple Blue
            ),
            textStyle = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF000000) // Apple's primary label color
            )
        )

        // Apple-Style Chevron
        Icon(
            painter = painterResource(
                id = if (iconResource != null) R.drawable.chevrondown2 else R.drawable.chevrondown1
            ),
            contentDescription = null,
            tint = Color(0xFF8E8E93), // Apple's tertiary label color
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-16).dp)
                .rotate(chevronRotation)
                .requiredSize(16.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    shouldFocusSearch = false // Kein Fokus beim Chevron-Click
                    onExpandedChange(!isExpanded)
                }
        )

        // Dropdown-Implementierung mit Suchfeld und Pressed-Effekt

        if (dropdownHeight.value > 0f) {
            val yOffset = with(density) {
                if (expandUpward) {
                    // Nach oben expandieren - Dropdown oberhalb des Eingabefelds
                    val dropdownHeightPx = calculatedHeight
                    -(dropdownHeightPx + dropdownOffset.toPx()).toInt()
                } else {
                    // Nach unten expandieren (Standard)
                    (50.dp + dropdownOffset).roundToPx()
                }
            }
            Popup(
                offset = IntOffset(0, yOffset),
                properties = PopupProperties(
                    clippingEnabled = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                ),
                onDismissRequest = {
                    // Dropdown schließen
                    onExpandedChange(false)
                }
            ) {
                Box(
                    modifier = Modifier
                        .requiredWidth(350.dp)
                        .height(
                            with(density) {
                                val contentHeight = if (filteredOptions.isEmpty()) {
                                    64.dp // Apple-Stil
                                } else {
                                    (filteredOptions.size * 44 + 8).dp // Apple-typische Item-Höhe
                                }
                                contentHeight.coerceIn(dropdownMinHeight, dropdownMaxHeight)
                            }
                        )
                        .alpha(dropdownAlpha)
                        .scale(dropdownScale)
                ) {
                    // Apple-Style Dropdown
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp)) // Apple-typische Rundung
                            .background(Color.White)
                            .border(
                                width = 0.5.dp,
                                color = Color(0xFFD1D1D6), // Apple's separator color
                                RoundedCornerShape(16.dp)
                            )
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(16.dp),
                                ambientColor = Color.Black.copy(alpha = 0.04f),
                                spotColor = Color.Black.copy(alpha = 0.08f)
                            )
                            .padding(vertical = 4.dp)
                            .verticalScroll(scrollStateDropdown)
                    ) {
                        if (filteredOptions.isEmpty()) {
                            // Apple-Style "Keine Ergebnisse"
                            Text(
                                text = "Keine Ergebnisse gefunden",
                                color = Color(0xFF8E8E93), // Apple's tertiary label color
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 16.dp)
                            )
                        } else {
                            filteredOptions.forEach { option ->
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isPressed) Color(0xFFF2F2F7) // Apple's quaternary system fill
                                            else Color.Transparent
                                        )
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) {
                                            onOptionSelected(option)
                                            onExpandedChange(false)
                                            searchText = option // Suchtext auf ausgewählte Option setzen
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = option,
                                        color = Color(0xFF000000), // Apple's primary label color
                                        style = TextStyle(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp, horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}