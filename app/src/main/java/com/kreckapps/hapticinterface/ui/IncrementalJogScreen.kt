
package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import java.util.Locale
import kotlin.math.roundToInt
import com.kreckapps.hapticinterface.model.MachineUnits
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color

private const val MAX_FEED_MM_MIN = 2500f
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
    onJogRequest: (Char, Double, Double, MachineUnits) -> Unit,
    onJogCancel: () -> Unit
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

    val isMetric = displayUnits == MachineUnits.MILLIMETERS
    val minFeedRate = if (isMetric) 25f else 1f
    val maxFeedRate = if (isMetric) MAX_FEED_MM_MIN else 98f
    val safeFeedRate = feedRate.roundToInt().toFloat().coerceIn(minFeedRate, maxFeedRate)
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(88.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.Start
            ) {
                PanelSectionTitle("Step Distance")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
            for (axis in listOf("X", "Y", "Z")) {

                var fieldValue by remember(axis) {
                    mutableStateOf(
                        TextFieldValue(
                            axisSteps[axis]
                                ?: if (displayUnits == MachineUnits.MILLIMETERS)
                                    "10"
                                else
                                    "0.375"
                        )
                    )
                }

                // Track focus so selection can happen after the next frame
                var hasFocus by remember(axis) {
                    mutableStateOf(false)
                }

                // Synchronize with external value changes
                LaunchedEffect(axisSteps[axis]) {
                    val externalValue = axisSteps[axis]

                    if (externalValue != null &&
                        externalValue != fieldValue.text
                    ) {
                        fieldValue = TextFieldValue(externalValue)
                    }
                }

                // Select all text after the focus event has completed
                LaunchedEffect(hasFocus) {
                    if (hasFocus) {
                        withFrameNanos { }

                        fieldValue = fieldValue.copy(
                            selection = TextRange(
                                0,
                                fieldValue.text.length
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { value ->
                        if (value.text.matches(
                                Regex("[0-9]*\\.?[0-9]*")
                            )
                        ) {
                            fieldValue = value
                            onStepChanged(axis, value.text)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .onFocusChanged { state ->
                            hasFocus = state.isFocused
                        },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    label = {
                        Text("$axis ($unitLabel)", fontSize = 11.sp)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                        }
                    )
                )
            }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            contentAlignment = Alignment.Center
        ) {
        Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelSectionTitle("Feed Rate")
            Text(
                text = "${String.format(Locale.US, "%.0f", safeFeedRate)} $feedUnit",
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp
            )
        }

        Slider(
            value = safeFeedRate,
            onValueChange = { value ->
                focusManager.clearFocus()
                onFeedRateChanged(value)
            },
            valueRange = minFeedRate..maxFeedRate,
        )
        }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            JogPad(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = 0.90f, scaleY = 0.90f),
                incremental = true,
                onIncrementalJog = { direction ->
                    focusManager.clearFocus()

                    val axis = direction.last()
                    val step = axisSteps[axis.toString()]
                        ?: if (displayUnits == MachineUnits.MILLIMETERS)
                            "10"
                        else
                            "0.375"

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
                    focusManager.clearFocus()
                    onJogCancel()
                    onCommand("Jog cancel requested")
                }
            )
        }
    }
}

@Composable
fun AxisSettingsRow(
    axis: String,
    step: String,
    unitLabel: String,
    onStepChanged: (String) -> Unit
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(step))
    }

    var hasFocus by remember {
        mutableStateOf(false)
    }

    // Keep the text field synchronized with external changes
    LaunchedEffect(step) {
        if (step != fieldValue.text) {
            fieldValue = TextFieldValue(step)
        }
    }

    // Select all text after the focus event has completed
    LaunchedEffect(hasFocus) {
        if (hasFocus) {
            withFrameNanos { }

            fieldValue = fieldValue.copy(
                selection = TextRange(
                    0,
                    fieldValue.text.length
                )
            )
        }
    }

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
                value = fieldValue,
                onValueChange = { newValue ->
                    if (newValue.text.matches(
                            Regex("[0-9]*\\.?[0-9]*")
                        )
                    ) {
                        fieldValue = newValue
                        onStepChanged(newValue.text)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        hasFocus = state.isFocused
                    },
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
