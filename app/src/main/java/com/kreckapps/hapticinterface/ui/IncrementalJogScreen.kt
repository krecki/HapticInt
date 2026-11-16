
package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IncrementalJogScreen(
    axisSteps: Map<String, String>,
    axisSpeeds: Map<String, Float>,
    lastCommand: String,
    onStepChanged: (String, String) -> Unit,
    onSpeedChanged: (String, Float) -> Unit,
    onCommand: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionTitle("Incremental Jogging")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Set the step size and speed for each axis.",
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        for (axis in listOf("X", "Y", "Z")) {
            AxisSettingsRow(
                axis = axis,
                step = axisSteps[axis] ?: "0.01",
                speed = axisSpeeds[axis] ?: 50f,
                onStepChanged = {
                    onStepChanged(axis, it)
                },
                onSpeedChanged = {
                    onSpeedChanged(axis, it)
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        JogPad(
            incremental = true,
            onIncrementalJog = { direction ->
                val axis = direction.last().toString()
                val step = axisSteps[axis] ?: "0.01"
                val speed = axisSpeeds[axis] ?: 50f

                val stepValue = step.toDoubleOrNull()

                if (stepValue != null && stepValue > 0.0) {
                    onCommand(
                        "$direction: Move $step in at ${speed.toInt()}% speed"
                    )
                } else {
                    onCommand(
                        "Invalid step size for axis $axis"
                    )
                }
            },
            onStop = {
                onCommand("Jog canceled")
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Last command:",
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = lastCommand,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun AxisSettingsRow(
    axis: String,
    step: String,
    speed: Float,
    onStepChanged: (String) -> Unit,
    onSpeedChanged: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$axis axis",
                modifier = Modifier.width(65.dp),
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = step,
                onValueChange = { newValue ->
                    if (newValue.matches(Regex("[0-9]*\\.?[0-9]*"))) {
                        onStepChanged(newValue)
                    }
                },
                modifier = Modifier.weight(1f),
                label = { Text("Step size (in)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                )
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Speed",
                modifier = Modifier.width(65.dp),
                fontSize = 12.sp
            )

            Slider(
                value = speed,
                onValueChange = onSpeedChanged,
                valueRange = 1f..100f,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "${speed.toInt()}%",
                modifier = Modifier.width(42.dp),
                fontSize = 12.sp
            )
        }
    }
}
