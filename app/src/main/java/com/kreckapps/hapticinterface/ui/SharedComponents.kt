
package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ModeTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .padding(top = 3.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 9.dp,
                    topEnd = 9.dp
                )
            )
            .background(
                if (selected) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun OptionButtons(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            if (option == selected) {
                Button(
                    onClick = { onSelected(option) },
                    modifier = Modifier.padding(horizontal = 3.dp),
                    contentPadding = PaddingValues(
                        horizontal = 10.dp,
                        vertical = 8.dp
                    )
                ) {
                    Text(option, fontSize = 12.sp)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelected(option) },
                    modifier = Modifier.padding(horizontal = 3.dp),
                    contentPadding = PaddingValues(
                        horizontal = 10.dp,
                        vertical = 8.dp
                    )
                ) {
                    Text(option, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SpeedControl(
    label: String,
    speed: Float,
    onSpeedChanged: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold
            )

            Text("${speed.toInt()}%")
        }

        Slider(
            value = speed,
            onValueChange = onSpeedChanged,
            valueRange = 1f..100f
        )
    }
}
