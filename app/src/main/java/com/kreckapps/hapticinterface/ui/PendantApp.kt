
package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreckapps.hapticinterface.model.CoordinateDisplayMode
import com.kreckapps.hapticinterface.model.MachineStatus

@Composable
fun PendantApp() {
    var selectedMode by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }

    // MPG settings and state
    var pulsesPerRev by remember { mutableIntStateOf(50) }
    var mpgPosition by remember { mutableIntStateOf(0) }
    var mpgResolution by remember { mutableStateOf("0.001") }
    var mpgAxis by remember { mutableStateOf("X") }

    // Continuous jogging settings
    var jogSpeed by remember { mutableFloatStateOf(50f) }
    var holdToRun by remember { mutableStateOf(true) }
    var activeJog by remember { mutableStateOf<String?>(null) }

    // Coordinate display selection
    var coordinateDisplayMode by remember {
        mutableStateOf(CoordinateDisplayMode.WORK)
    }

    // Live machine information
    var machineStatus by remember {
        mutableStateOf(MachineStatus())
    }

    // Zero X action supplied by the connection panel
    var zeroAxisAction by remember {
        mutableStateOf<((Char) -> Unit)?>(null)
    }

    var setAxisAction by remember {
        mutableStateOf<((Char, Double) -> Unit)?>(null)
    }

    var coordinateCommandBusy by remember {
        mutableStateOf(false)
    }

    // Incremental jogging settings
    val axisSteps = remember {
        mutableStateMapOf(
            "X" to "0.01",
            "Y" to "0.01",
            "Z" to "0.01"
        )
    }

    val axisSpeeds = remember {
        mutableStateMapOf(
            "X" to 50f,
            "Y" to 50f,
            "Z" to 50f
        )
    }

    var lastIncrementalCommand by remember {
        mutableStateOf("No jog command yet")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CNC Pendant",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )

            TextButton(
                onClick = {
                    settingsOpen = !settingsOpen
                    activeJog = null
                }
            ) {
                Text(if (settingsOpen) "Back" else "Settings")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (settingsOpen) {
            SettingsScreen(
                pulsesPerRev = pulsesPerRev,
                onPulsesPerRevChanged = {
                    pulsesPerRev = it
                }
            )
        } else {
            PendantConnectionPanel(
                onMachineStatusChanged = { newStatus ->
                    machineStatus = newStatus
                },
                onZeroAxisActionAvailable = { action ->
                    zeroAxisAction = action
                },
                onSetAxisActionAvailable = { action ->
                    setAxisAction = action
                },
                onCommandBusyChanged = { busy ->
                    coordinateCommandBusy = busy
                }
            )

            PositionDisplay(
                status = machineStatus,
                displayMode = coordinateDisplayMode,
                onDisplayModeChanged = {
                    coordinateDisplayMode = it
                },
                zeroXEnabled =
                machineStatus.connected &&
                        machineStatus.machineState == "Idle" &&
                        machineStatus.pendantInControl == false &&
                        !coordinateCommandBusy,
                onZeroAxis = { axis ->
                    zeroAxisAction?.invoke(axis)
                },
                onSetAxis = { axis, value ->
                    setAxisAction?.invoke(axis, value)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModeTab(
                    text = "MPG",
                    selected = selectedMode == 0,
                    modifier = Modifier.weight(1f)
                ) {
                    selectedMode = 0
                    activeJog = null
                }

                ModeTab(
                    text = "Continuous",
                    selected = selectedMode == 1,
                    modifier = Modifier.weight(1f)
                ) {
                    selectedMode = 1
                    activeJog = null
                }

                ModeTab(
                    text = "Incremental",
                    selected = selectedMode == 2,
                    modifier = Modifier.weight(1f)
                ) {
                    selectedMode = 2
                    activeJog = null
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (selectedMode) {
                0 -> MPGScreen(
                    position = mpgPosition,
                    onPositionChanged = {
                        mpgPosition = it
                    },
                    pulsesPerRev = pulsesPerRev,
                    resolution = mpgResolution,
                    onResolutionChanged = {
                        mpgResolution = it
                    },
                    axis = mpgAxis,
                    onAxisChanged = {
                        mpgAxis = it
                    }
                )

                1 -> ContinuousJogScreen(
                    activeJog = activeJog,
                    onJogChanged = {
                        activeJog = it
                    },
                    speed = jogSpeed,
                    onSpeedChanged = {
                        jogSpeed = it
                    },
                    holdToRun = holdToRun,
                    onHoldToRunChanged = {
                        holdToRun = it
                        activeJog = null
                    }
                )

                2 -> IncrementalJogScreen(
                    axisSteps = axisSteps,
                    axisSpeeds = axisSpeeds,
                    lastCommand = lastIncrementalCommand,
                    onStepChanged = { axis, value ->
                        axisSteps[axis] = value
                    },
                    onSpeedChanged = { axis, value ->
                        axisSpeeds[axis] = value
                    },
                    onCommand = { command ->
                        lastIncrementalCommand = command
                    }
                )
            }
        }
    }
}
