package com.example.arwalking.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class NavigationStepData(
    val stepNumber: Int,
    val instruction: String,
    val distance: Double,
    val isCompleted: Boolean = false
)

@Composable
fun NavigationDrawer(
    steps: List<NavigationStepData>,
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Navigation Steps",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            steps.forEach { step ->
                NavigationStepItem(
                    step = step,
                    isCurrent = step.stepNumber == currentStep
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun NavigationStepItem(
    step: NavigationStepData,
    isCurrent: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "${step.stepNumber}.",
            style = if (isCurrent) MaterialTheme.typography.bodyLarge 
                   else MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary 
                   else MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column {
            Text(
                text = step.instruction,
                style = if (isCurrent) MaterialTheme.typography.bodyLarge 
                       else MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "${step.distance.toInt()}m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}