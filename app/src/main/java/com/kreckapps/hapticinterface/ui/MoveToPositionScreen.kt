
package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.font.FontWeight
import com.kreckapps.hapticinterface.model.CoordinateDisplayMode
import com.kreckapps.hapticinterface.model.MachineStatus
import com.kreckapps.hapticinterface.model.MachineUnits

@Composable
fun MoveToPositionScreen(
    status: MachineStatus,
    coordinateMode: CoordinateDisplayMode,
    displayUnits: MachineUnits,
    enabled: Boolean,
    onMove: (Char, Double) -> Unit,
    onMoveToZero: (Char) -> Unit
) {
    val unitLabel = if (displayUnits == MachineUnits.MILLIMETERS) "mm" else "in"

    val coordinateLabel = if (coordinateMode == CoordinateDisplayMode.MACHINE) {
        "Machine coordinates (MPos)"
    } else {
        "Work coordinates (WPos · ${status.position.activeWcs})"
    }

    val focusManager = LocalFocusManager.current

    val targets = remember {
        mutableStateMapOf('X' to "0", 'Y' to "0", 'Z' to "0")
    }

    val targetFields = remember {
        mutableStateMapOf<Char, TextFieldValue>()
    }

    // Track focus separately for each axis
    val focusedAxes = remember {
        mutableStateMapOf<Char, Boolean>()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            coordinateLabel,
//            style = MaterialTheme.typography.titleSmall
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        listOf('X', 'Y', 'Z').forEachIndexed { index, axis ->

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {

                // Select all text after the focus event has completed
                LaunchedEffect(focusedAxes[axis]) {
                    if (focusedAxes[axis] == true) {
                        withFrameNanos { }

                        val current = targets[axis] ?: "0"

                        targetFields[axis] = TextFieldValue(
                            text = current,
                            selection = TextRange(0, current.length)
                        )
                    }
                }

                OutlinedTextField(
                    value = targetFields[axis]
                        ?: TextFieldValue(targets[axis] ?: "0"),

                    onValueChange = {
                        targetFields[axis] = it
                        targets[axis] = it.text
                    },

                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            focusedAxes[axis] = state.isFocused
                        },

                    singleLine = true,

                    label = {
                        Text("$axis ($unitLabel)")
                    },

                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),

                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                        }
                    ),

                    enabled = enabled
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PanelActionChip(
                        text = "Go to Position",
                        onClick = {
                            focusManager.clearFocus()

                            val target = targets[axis]
                                ?.trim()
                                ?.toDoubleOrNull()

                            if (target != null && target.isFinite()) {
                                onMove(axis, target)
                            }
                        },
                        enabled = enabled && (
                                targets[axis]
                                    ?.trim()
                                    ?.toDoubleOrNull()
                                    ?.isFinite() == true
                                ),
                        modifier = Modifier.weight(1f),
                        containerColor = PendantColors.actionPrimaryContainer,
                        labelColor = PendantColors.onActionPrimaryContainer
                    )

                    PanelActionChip(
                        text = "Go to Zero",
                        onClick = {
                            focusManager.clearFocus()
                            onMoveToZero(axis)
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        containerColor = PendantColors.actionNeutralContainer,
                        labelColor = PendantColors.onActionNeutralContainer
                    )
                }
            }

            if (index < 2) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}
