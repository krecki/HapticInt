
package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ModeTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            Text(text, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            Text(text, fontSize = 12.sp)
        }
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
