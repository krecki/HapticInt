package com.kreckapps.hapticinterface.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import com.kreckapps.hapticinterface.model.CoordinateDisplayMode
import com.kreckapps.hapticinterface.model.MachineStatus
import com.kreckapps.hapticinterface.model.MachineUnits
import java.util.Locale

@Composable
fun PositionDisplay(
    status: MachineStatus,
    displayMode: CoordinateDisplayMode,
    onDisplayModeChanged: (CoordinateDisplayMode) -> Unit,
    displayUnits: MachineUnits,
    onDisplayUnitsChanged: (MachineUnits) -> Unit,
    zeroXEnabled: Boolean,
    onZeroAxis: (Char) -> Unit,
    onSetAxis: (Char, Double) -> Unit
) {
    val position = status.position
    val isWorkDisplay = displayMode == CoordinateDisplayMode.WORK

    // Status coordinates arrive in millimeters because $13=0.
    val coordinatesMm = if (isWorkDisplay) {
        listOf(position.workX, position.workY, position.workZ)
    } else {
        listOf(position.machineX, position.machineY, position.machineZ)
    }
    val coordinates = coordinatesMm.map { value ->
        if (displayUnits == MachineUnits.INCHES) value / 25.4 else value
    }
    val unitLabel = if (displayUnits == MachineUnits.INCHES) "in" else "mm"

    var editingAxis by remember { mutableStateOf<Char?>(null) }
    var editText by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime

    fun finishEditing() {
        val axis = editingAxis ?: return
        val entered = editText.text.toDoubleOrNull()
        if (entered != null && entered.isFinite()) {
            // Convert display units to millimeters, then to the controller's
            // current G20/G21 units because G10 L20 uses modal units.
            val valueMm = if (displayUnits == MachineUnits.INCHES) {
                entered * 25.4
            } else {
                entered
            }
            val controllerValue = if (position.units == MachineUnits.INCHES) {
                valueMm / 25.4
            } else {
                valueMm
            }
            onSetAxis(axis, controllerValue)
        }
        editingAxis = null
        keyboard?.hide()
        focusManager.clearFocus()
    }

    BackHandler(enabled = editingAxis != null) {
        finishEditing()
    }

    LaunchedEffect(editingAxis) {
        if (editingAxis != null) {
            focusRequester.requestFocus()
            keyboard?.show()

            // The Android keyboard's Back button can hide the IME without
            // clearing field focus. Treat that dismissal as Set as well.
            var keyboardWasVisible = false
            snapshotFlow { imeInsets.getBottom(density) > 0 }
                .distinctUntilChanged()
                .collect { keyboardVisible ->
                    if (keyboardVisible) {
                        keyboardWasVisible = true
                    } else if (keyboardWasVisible && editingAxis != null) {
                        finishEditing()
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Machine Position",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val controllerStatus = when {
                !status.connected -> "Disconnected"
                status.pendantInControl == true -> "In control"
                status.pendantInControl == false -> "Listening"
                else -> "Waiting"
            }
            val statusColor = when {
                !status.connected -> MaterialTheme.colorScheme.onSurfaceVariant
                status.pendantInControl == true -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.tertiary
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(statusColor, RoundedCornerShape(50))
                )
                Text(
                    text = controllerStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OptionButtons(
                options = listOf("MPos", "WPos"),
                selected = if (isWorkDisplay) "WPos" else "MPos",
                onSelected = {
                    onDisplayModeChanged(
                        if (it == "MPos") CoordinateDisplayMode.MACHINE
                        else CoordinateDisplayMode.WORK
                    )
                },
                modifier = Modifier.weight(1f)
            )
            OptionButtons(
                options = listOf("Inch", "mm"),
                selected = if (displayUnits == MachineUnits.INCHES) "Inch" else "mm",
                onSelected = {
                    onDisplayUnitsChanged(
                        if (it == "Inch") MachineUnits.INCHES
                        else MachineUnits.MILLIMETERS
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            listOf('X', 'Y', 'Z').forEachIndexed { index, axis ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = axis.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (editingAxis == axis) {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            modifier = Modifier
                                .widthIn(min = 88.dp, max = 112.dp)
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            suffix = { Text(unitLabel) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onDone = { finishEditing() }
                            )
                        )
                    } else {
                        Text(
                            text = String.format(Locale.US, "%.3f", coordinates[index]),
                            modifier = Modifier
                                .clickable(enabled = isWorkDisplay && zeroXEnabled) {
                                    editingAxis = axis
                                    val initialText = String.format(Locale.US, "%.3f", coordinates[index])
                                    editText = TextFieldValue(initialText, selection = TextRange(0, initialText.length))
                                }
                                .padding(vertical = 4.dp),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWorkDisplay) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }

                    if (isWorkDisplay) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onZeroAxis(axis) },
                                enabled = zeroXEnabled,
                                contentPadding = PaddingValues(
                                    horizontal = 7.dp, vertical = 0.dp
                                )
                            ) {
                                Text("Zero", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isWorkDisplay) position.activeWcs else "Machine coordinates",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
