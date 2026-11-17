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
import com.kreckapps.hapticinterface.model.MachineUnits

private const val MAX_FEED_MM_MIN = 2000f
private const val MM_PER_INCH = 25.4f

@Composable
fun IncrementalJogScreen(
    axisSteps: Map<String, String>,
    lastCommand: String,
    onStepChanged: (String, String) -> Unit,
    onCommand: (String) -> Unit,
    displayUnits: MachineUnits,
    feedRate: Float,
    onFeedRateChanged: (Float) -> Unit,
    onJogRequest: (Char, Double, Double, MachineUnits) -> Unit
) {
    val unitLabel = if (displayUnits == MachineUnits.MILLIMETERS) {
        "mm"
    } else {
        "in"
    }

    val feedUnit = if (displayUnits == MachineUnits.MILLIMETERS) {
        "mm/min"
    } else {
        "in/min"
    }

    val maxFeedRate = if (displayUnits == MachineUnits.MILLIMETERS) {
        MAX_FEED_MM_MIN
    } else {
        MAX_FEED_MM_MIN / MM_PER_INCH
    }

    val safeFeedRate = feedRate.coerceIn(1f, maxFeedRate)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionTitle("Incremental Jogging")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Set the step size for each axis.",
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        for (axis in listOf("X", "Y", "Z")) {
            AxisSettingsRow(
                axis = axis,
                step = axisSteps[axis] ?: "0.01",
                unitLabel = unitLabel,
                onStepChanged = {
                    onStepChanged(axis, it)
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Shared Jog Feed Rate",
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "${safeFeedRate.toInt()} $feedUnit",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Slider(
            value = safeFeedRate,
            onValueChange = onFeedRateChanged,
            valueRange = 1f..maxFeedRate
        )

        Text(
            text = "Maximum: ${maxFeedRate.toInt()} $feedUnit",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        JogPad(
            incremental = true,
            onIncrementalJog = { direction ->
                val axis = direction.last()
                val step = axisSteps[axis.toString()] ?: "0.01"
                val stepValue = step.toDoubleOrNull()

                if (
                    stepValue != null &&
                    stepValue.isFinite() &&
                    stepValue > 0.0
                ) {
                    val signedDistance =
                        if (direction.startsWith("-")) {
                            -stepValue
                        } else {
                            stepValue
                        }

                    onCommand(
                        "Jog $direction: $step $unitLabel at " +
                                "${safeFeedRate.toInt()} $feedUnit"
                    )

                    onJogRequest(
                        axis,
                        signedDistance,
                        safeFeedRate.toDouble(),
                        displayUnits
                    )
                } else {
                    onCommand("Invalid step size for axis $axis")
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
    unitLabel: String,
    onStepChanged: (String) -> Unit
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
                label = {
                    Text("Step size ($unitLabel)")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                )
            )
        }
    }
}