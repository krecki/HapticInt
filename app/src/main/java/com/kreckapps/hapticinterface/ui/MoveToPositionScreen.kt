package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    val targets = remember { mutableStateMapOf('X' to "", 'Y' to "", 'Z' to "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(coordinateLabel, style = MaterialTheme.typography.titleSmall)
        Text(
            "Enter an absolute target for one axis. Moves use the machine's rapid rate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        listOf('X', 'Y', 'Z').forEachIndexed { index, axis ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(axis.toString(), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = targets[axis] ?: "",
                    onValueChange = { targets[axis] = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("$axis target ($unitLabel)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = enabled
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val target = targets[axis]?.trim()?.toDoubleOrNull()
                            if (target != null && target.isFinite()) {
                                onMove(axis, target)
                            }
                        },
                        enabled = enabled && (targets[axis]?.trim()?.toDoubleOrNull()?.isFinite() == true),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Go to Position")
                    }
                    OutlinedButton(
                        onClick = { onMoveToZero(axis) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Go to Zero")
                    }
                }
            }
            if (index < 2) HorizontalDivider()
        }
    }
}
