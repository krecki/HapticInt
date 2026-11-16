
package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreckapps.hapticinterface.model.CoordinateDisplayMode
import com.kreckapps.hapticinterface.model.MachineStatus
import com.kreckapps.hapticinterface.model.MachineUnits
import java.util.Locale

@Composable
fun PositionDisplay(
    status: MachineStatus,
    displayMode: CoordinateDisplayMode,
    onDisplayModeChanged: (CoordinateDisplayMode) -> Unit,
    zeroXEnabled: Boolean,
    onZeroAxis: (Char) -> Unit,
    onSetAxis: (Char, Double) -> Unit
) {
    val position = status.position

    val coordinates = if (displayMode == CoordinateDisplayMode.MACHINE) {
        listOf(position.machineX, position.machineY, position.machineZ)
    } else {
        listOf(position.workX, position.workY, position.workZ)
    }

    val unitLabel = if (position.units == MachineUnits.INCHES) {
        "in"
    } else {
        "mm"
    }

    var xValue by remember { mutableStateOf("") }
    var yValue by remember { mutableStateOf("") }
    var zValue by remember { mutableStateOf("") }

    val enteredValues = listOf(
        'X' to xValue,
        'Y' to yValue,
        'Z' to zValue
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Machine Position",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (status.connected) {
                    status.machineState
                } else {
                    "DEMO • NOT CONNECTED"
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (status.connected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OptionButtons(
            options = listOf("Machine", "Work"),
            selected = if (
                displayMode == CoordinateDisplayMode.MACHINE
            ) {
                "Machine"
            } else {
                "Work"
            },
            onSelected = {
                onDisplayModeChanged(
                    if (it == "Machine") {
                        CoordinateDisplayMode.MACHINE
                    } else {
                        CoordinateDisplayMode.WORK
                    }
                )
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("X", "Y", "Z").forEachIndexed { index, axis ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = axis,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = String.format(
                            Locale.US,
                            "%.3f",
                            coordinates[index]
                        ),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = unitLabel,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (displayMode == CoordinateDisplayMode.WORK) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Set Work Coordinates",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            enteredValues.forEach { (axis, enteredText) ->
                val parsedValue = enteredText.toDoubleOrNull()
                val validValue = parsedValue != null &&
                        parsedValue.isFinite()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = axis.toString(),
                        modifier = Modifier.width(20.dp),
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = enteredText,
                        onValueChange = { newValue ->
                            when (axis) {
                                'X' -> xValue = newValue
                                'Y' -> yValue = newValue
                                'Z' -> zValue = newValue
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = {
                            Text("New value")
                        },
                        suffix = {
                            Text(unitLabel)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        )
                    )

                    OutlinedButton(
                        onClick = {
                            onSetAxis(axis, parsedValue!!)
                        },
                        enabled = zeroXEnabled && validValue,
                        contentPadding = PaddingValues(
                            horizontal = 10.dp,
                            vertical = 8.dp
                        )
                    ) {
                        Text("Set")
                    }

                    OutlinedButton(
                        onClick = {
                            onZeroAxis(axis)
                        },
                        enabled = zeroXEnabled,
                        contentPadding = PaddingValues(
                            horizontal = 10.dp,
                            vertical = 8.dp
                        )
                    ) {
                        Text("Zero")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Coordinate system: ${position.activeWcs}",
                fontSize = 12.sp
            )

            Text(
                text = if (
                    displayMode == CoordinateDisplayMode.MACHINE
                ) {
                    "MPos"
                } else {
                    "WPos"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
