package com.example.arwalking.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.arwalking.R

/**
 * An elegant dropdown for location selection with intelligent search.
 * Supports fuzzy search and various matching strategies,
 * so users can quickly find what they’re looking for—even with typos.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun LocationDropdown(
    selectedText: String,
    options: List<String>,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOptionSelected: (String) -> Unit,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    iconResource: Int? = null,
    iconTint: Color = Color(0xFF4285F4),
    dropdownOffset: Dp = 8.dp,
    expandUpward: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    var searchText by remember { androidx.compose.runtime.mutableStateOf("") }

    // Intelligent fuzzy search: also finds terms with small typos
    // For example: "Cafetria" will still find "Cafeteria"
    // (Created with assistance from an LLM)
    fun fuzzyMatch(text: String, query: String): Boolean {
        if (query.length < 2) return false // Searches that are too short are not useful
        var textIndex = 0
        var queryIndex = 0
        var mismatches = 0
        val maxMismatches = 1 // Generous: one typo is okay

        while (textIndex < text.length && queryIndex < query.length) {
            if (text[textIndex] == query[queryIndex]) {
                queryIndex++
            } else {
                mismatches++
                if (mismatches > maxMismatches) return false
            }
            textIndex++
        }

        return queryIndex >= query.length - maxMismatches
    }

    // Multi-stage search algorithm: from exact to flexible
    // The more hits, the better for the user
    // (Created with assistance from an LLM)
    val filteredOptions = if (searchText.isEmpty()) {
        options // Keine Suche = alle Optionen anzeigen
    } else {
        val searchQuery = searchText.trim().lowercase()
        options.filter { option ->
            val optionLower = option.lowercase()

            // Strategy 1: Direct hit ("Office" finds "Office Prof.")
            optionLower.contains(searchQuery) ||

            // Strategy 2: Word-start matching ("Prof" finds "Office Prof. Dr.")
            option.split(" ", "(", ")", ".", "-").any { word ->
                word.lowercase().startsWith(searchQuery)
            } ||

            // Strategy 3: Acronym search ("odl" finds "Office Dr. Ludwig")
            option.split(" ", "(", ")", ".", "-")
                .mapNotNull { it.firstOrNull()?.lowercase() }
                .joinToString("")
                .contains(searchQuery) ||

            // Strategy 4: Fuzzy match for typos
            fuzzyMatch(optionLower, searchQuery)
        }.sortedWith(compareBy<String> { option ->
            val optionLower = option.lowercase()
            when {
                // Priority 1: Starts with search term
                optionLower.startsWith(searchQuery) -> 0
                // Priority 2: Contains search term at the beginning of a word
                option.split(" ").any { it.lowercase().startsWith(searchQuery) } -> 1
                // Priority 3: Contains search term somewhere
                optionLower.contains(searchQuery) -> 2
                // Priority 4: Other matches
                else -> 3
            }
        }.thenBy { it.length }) // With the same priority: shorter options first
    }

    // Helper for highlighting the search text
    // (Created with assistance from an LLM)
    fun highlightSearchText(text: String, searchQuery: String): AnnotatedString {
        if (searchQuery.isEmpty()) {
            return AnnotatedString(text)
        }

        return buildAnnotatedString {
            val searchQueryLower = searchQuery.lowercase()
            val textLower = text.lowercase()

            var lastIndex = 0
            var index = textLower.indexOf(searchQueryLower)

            while (index != -1) {
                // Text before the match
                append(text.substring(lastIndex, index))

                // Highlighted match
                withStyle(style = SpanStyle(
                    color = Color(0xFF007AFF), // Apple Blue
                    fontWeight = FontWeight.SemiBold
                )) {
                    append(text.substring(index, index + searchQuery.length))
                }

                lastIndex = index + searchQuery.length
                index = textLower.indexOf(searchQueryLower, lastIndex)
            }

            // Remaining text
            append(text.substring(lastIndex))
        }
    }

    // Dropdown dimensions
    val dropdownMinHeight = 120.dp
    val dropdownMaxHeight = 250.dp // Reduced from 300.dp to 200.dp
    val scrollStateDropdown = rememberScrollState()

    // Reset searchText when dropdown is closed and focus when opened
    androidx.compose.runtime.LaunchedEffect(isExpanded) {
        if (!isExpanded) {
            searchText = "" // Reset search text when closing
            focusManager.clearFocus() // Remove focus and close keyboard
        } else {
            // Focus the text field and open the keyboard
            focusRequester.requestFocus()
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
            // Minimum height for "No results" text (Apple style)
            64.dp
        } else {
            (filteredOptions.size * 44 + 8).dp // Apple-typical item height
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

    // FIXED: Separate handling for outside clicks - only when dropdown is expanded
    // and not interfering with text input
    Box(
        modifier = if (isExpanded) {
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // This will only capture clicks outside the dropdown area
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    // Only close if clicked outside the dropdown components
                    focusManager.clearFocus()
                    onExpandedChange(false)
                }
        } else {
            Modifier.fillMaxSize()
        }
    ) {
        Box(
            modifier = modifier
                .requiredWidth(350.dp)
                .requiredHeight(50.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    // Open dropdown when clicking anywhere on the container
                    onExpandedChange(true)
                }
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

            // FIXED: Use BasicTextField with custom click handling
            val textFieldInteractionSource = remember { MutableInteractionSource() }
            
            // Handle clicks on the text field
            LaunchedEffect(textFieldInteractionSource) {
                textFieldInteractionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is androidx.compose.foundation.interaction.PressInteraction.Press -> {
                            onExpandedChange(true)
                        }
                    }
                }
            }
            
            BasicTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    if (it.isNotEmpty() && !isExpanded) {
                        onExpandedChange(true)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = if (iconResource != null) 44.dp else 38.dp, end = 48.dp)
                    .padding(vertical = 12.dp)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF000000)
                ),
                singleLine = true,
                interactionSource = textFieldInteractionSource,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onExpandedChange(false)
                    }
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchText.isEmpty()) {
                            Text(
                                text = selectedText,
                                color = Color(0xFF8E8E93),
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Invisible overlay to catch all clicks when dropdown is closed
            if (!isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            onExpandedChange(true)
                        }
                )
            }

            // Apple-Style Chevron with larger clickable area
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-8).dp) // Adjusted to center the larger clickable area
                    .size(40.dp) // Larger clickable area (40dp instead of 16dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        onExpandedChange(!isExpanded)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        id = if (iconResource != null) R.drawable.chevrondown2 else R.drawable.chevrondown1
                    ),
                    contentDescription = null,
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier
                        .rotate(chevronRotation)
                        .requiredSize(16.dp) // Keep icon size same, just make clickable area larger
                )
            }

            if (dropdownHeight.value > 0f) {
                val yOffset = with(density) {
                    if (expandUpward) {
                        // Expand upward — dropdown above the input field
                        val dropdownHeightPx = calculatedHeight
                        -(dropdownHeightPx + dropdownOffset.toPx()).toInt()
                    } else {
                        // Expand downward (default)
                        (50.dp + dropdownOffset).roundToPx()
                    }
                }
                Popup(
                    offset = IntOffset(0, yOffset),
                    properties = PopupProperties(
                        clippingEnabled = false,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = false, // FIXED: Disable automatic dismiss
                        excludeFromSystemGesture = true,
                        usePlatformDefaultWidth = false,
                        focusable = false // FIXED: Don't steal focus from text field
                    ),
                    onDismissRequest = {
                        // Handle manual dismiss only
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
                            // FIXED: Prevent clicks from propagating to parent
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                // Consume clicks within dropdown - do nothing
                            }
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
                                // Apple-Style "Keine Ergebnisse" mit besserem Feedback
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp, horizontal = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Keine Ergebnisse",
                                        color = Color(0xFF8E8E93), // Apple's tertiary label color
                                        style = TextStyle(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "für \"${searchText.trim()}\"",
                                        color = Color(0xFFAEAEB2), // Lighter gray
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    )
                                }
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
                                                searchText = "" // Clear search when option is selected
                                                focusManager.clearFocus() // Close keyboard
                                                onExpandedChange(false)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = highlightSearchText(option, searchText.trim()),
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
}