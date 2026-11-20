
package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color

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
            letterSpacing = 0.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PanelSectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun OptionButtons(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true
) {
    if (compact) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(9.dp)
                )
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable(enabled = enabled) { onSelected(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        fontSize = 12.sp,
                        letterSpacing = 0.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        else if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { option ->
                val buttonModifier = Modifier
                    .weight(1f)
                    .height(48.dp)

                if (option == selected) {
                    Button(
                        onClick = { onSelected(option) },
                        modifier = buttonModifier,
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        Text(option, fontSize = 12.sp, letterSpacing = 0.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelected(option) },
                        modifier = buttonModifier,
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        Text(option, fontSize = 12.sp, letterSpacing = 0.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun PanelActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = PendantColors.actionSecondaryContainer,
    labelColor: Color = PendantColors.onActionSecondaryContainer
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
            selectedContainerColor = containerColor,
            selectedLabelColor = labelColor,
            disabledContainerColor = PendantColors.actionNeutralContainer.copy(alpha = 0.55f),
            disabledLabelColor = PendantColors.onActionNeutralContainer.copy(alpha = 0.55f)
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = false,
            borderColor = PendantColors.outlineVariant,
            selectedBorderColor = PendantColors.outlineVariant
        ),
        label = {
            Text(
                text = text,
                fontSize = 12.sp,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center
            )
        }
    )
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
