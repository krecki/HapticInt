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
import com.kreckapps.hapticinterface.model.MachineUnits

private const val MM_PER_INCH = 25.4f
private const val MAX_FEED_MM_MIN = 2000f

@Composable
fun PendantApp() {
    var selectedMode by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }

    // MPG settings and state
    var pulsesPerRev by remember { mutableIntStateOf(50) }
    var mpgPosition by remember { mutableIntStateOf(0) }
    var mpgResolution by remember { mutableStateOf("0.01") }
    var mpgAxis by remember { mutableStateOf("X") }

    // One shared feed rate, always stored internally in mm/min.
    var jogFeedRateMmMin by remember {
        mutableFloatStateOf(50f)
    }

    // Continuous jogging settings
    var holdToRun by remember { mutableStateOf(true) }
    var activeJog by remember { mutableStateOf<String?>(null) }

    // Coordinate display selection
    var coordinateDisplayMode by remember {
        mutableStateOf(CoordinateDisplayMode.WORK)
    }

    // Display units are initialized from the first valid modal report.
    var displayUnits by remember {
        mutableStateOf(MachineUnits.INCHES)
    }

    var displayUnitsInitialized by remember {
        mutableStateOf(false)
    }

    // Live machine information
    var machineStatus by remember {
        mutableStateOf(MachineStatus())
    }

    // Actions supplied by the connection panel
    var zeroAxisAction by remember {
        mutableStateOf<((Char) -> Unit)?>(null)
    }

    var setAxisAction by remember {
        mutableStateOf<((Char, Double) -> Unit)?>(null)
    }

    var incrementalJogAction by remember {
        mutableStateOf<
                ((Char, Double, Double, MachineUnits) -> Unit)?
                >(null)
    }

    var continuousJogStartAction by remember {
        mutableStateOf<
                ((String, Double, MachineUnits) -> Unit)?
                >(null)
    }

    var stopJogAction by remember {
        mutableStateOf<(() -> Unit)?>(null)
    }

    var jogModeConfigurationAction by remember {
        mutableStateOf<((Int, Double, Double) -> Unit)?>(null)
    }

    var wasConnected by remember { mutableStateOf(false) }

    var coordinateCommandBusy by remember {
        mutableStateOf(false)
    }

    // Incremental jogging step sizes remain independent per axis.
    val axisSteps = remember {
        mutableStateMapOf(
            "X" to "0.01",
            "Y" to "0.01",
            "Z" to "0.01"
        )
    }

    var lastIncrementalCommand by remember {
        mutableStateOf("No jog command yet")
    }

    // Convert the shared internal feed rate to the selected display units.
    val displayedFeedRate = if (
        displayUnits == MachineUnits.MILLIMETERS
    ) {
        jogFeedRateMmMin
    } else {
        jogFeedRateMmMin / MM_PER_INCH
    }

    val maximumDisplayedFeedRate = if (
        displayUnits == MachineUnits.MILLIMETERS
    ) {
        MAX_FEED_MM_MIN
    } else {
        MAX_FEED_MM_MIN / MM_PER_INCH
    }

    fun updateDisplayedFeedRate(value: Float) {
        val safeValue = value.coerceIn(
            1f,
            maximumDisplayedFeedRate
        )

        jogFeedRateMmMin = if (
            displayUnits == MachineUnits.MILLIMETERS
        ) {
            safeValue
        } else {
            safeValue * MM_PER_INCH
        }
    }

    fun configureSelectedJogMode(mode: Int) {
        val stepMm = if (mode == 0) {
            (mpgResolution.toDoubleOrNull() ?: 0.01) *
                    if (displayUnits == MachineUnits.MILLIMETERS) 1.0 else 25.4
        } else {
            (axisSteps["X"]?.toDoubleOrNull() ?: 0.01) *
                    if (displayUnits == MachineUnits.MILLIMETERS) 1.0 else 25.4
        }
        val feedMmMin = if (mode == 0) 2000.0 else jogFeedRateMmMin.toDouble()
        jogModeConfigurationAction?.invoke(mode, stepMm, feedMmMin)
    }

    LaunchedEffect(
        jogModeConfigurationAction,
        selectedMode,
        mpgResolution,
        jogFeedRateMmMin,
        displayUnits,
        machineStatus.connected,
        machineStatus.pendantInControl
    ) {
        if (machineStatus.connected && machineStatus.pendantInControl == false) {
            configureSelectedJogMode(selectedMode)
        }
    }

    fun stopActiveJog() {
        if (activeJog != null) {
            stopJogAction?.invoke()
            activeJog = null
        }
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
                    stopActiveJog()
                    settingsOpen = !settingsOpen
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

                    if (!newStatus.connected) {
                        wasConnected = false
                        displayUnitsInitialized = false
                    } else {
                        if (!wasConnected) {
                            wasConnected = true
                            displayUnitsInitialized = false
                        }
                        if (newStatus.position.modalStateKnown) {
                            displayUnits = newStatus.position.units
                            displayUnitsInitialized = true
                        }
                    }
                },

                onZeroAxisActionAvailable = { action ->
                    zeroAxisAction = action
                },

                onSetAxisActionAvailable = { action ->
                    setAxisAction = action
                },

                onIncrementalJogActionAvailable = { action ->
                    incrementalJogAction = action
                },

                onContinuousJogStartActionAvailable = { action ->
                    continuousJogStartAction = action
                },

                onStopJogActionAvailable = { action ->
                    stopJogAction = action
                },

                onJogModeConfigurationAvailable = { action ->
                    jogModeConfigurationAction = action
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
                displayUnits = displayUnits,
                onDisplayUnitsChanged = {
                    displayUnits = it
                    displayUnitsInitialized = true
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
                    stopActiveJog()
                    selectedMode = 0
                    configureSelectedJogMode(0)
                }

                ModeTab(
                    text = "Continuous",
                    selected = selectedMode == 1,
                    modifier = Modifier.weight(1f)
                ) {
                    stopActiveJog()
                    selectedMode = 1
                    configureSelectedJogMode(1)
                }

                ModeTab(
                    text = "Incremental",
                    selected = selectedMode == 2,
                    modifier = Modifier.weight(1f)
                ) {
                    stopActiveJog()
                    selectedMode = 2
                    configureSelectedJogMode(2)
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
                        if (selectedMode == 0) configureSelectedJogMode(0)
                    },
                    axis = mpgAxis,
                    onAxisChanged = {
                        mpgAxis = it
                    },
                    displayUnits = displayUnits,
                    onJogRequest = { axis, distance, feedRate, units ->
                        lastIncrementalCommand =
                            "MPG $axis detent: $distance at $feedRate ($units)"

                        incrementalJogAction?.invoke(
                            axis,
                            distance,
                            feedRate,
                            units
                        )
                    }
                )

                1 -> ContinuousJogScreen(
                    activeJog = activeJog,
                    onJogChanged = {
                        activeJog = it
                    },
                    speed = displayedFeedRate,
                    onSpeedChanged = {
                        updateDisplayedFeedRate(it)
                        if (selectedMode == 1) configureSelectedJogMode(1)
                    },
                    holdToRun = holdToRun,
                    onHoldToRunChanged = {
                        holdToRun = it
                        activeJog = null
                    },
                    displayUnits = displayUnits,
                    onJogRequest = { direction, feedRate, units ->
                        lastIncrementalCommand =
                            "Continuous jog requested: $direction at $feedRate ($units)"

                        continuousJogStartAction?.invoke(
                            direction,
                            feedRate,
                            units
                        )
                    },
                    onStopJog = {
                        lastIncrementalCommand =
                            "Continuous jog stop requested"

                        stopJogAction?.invoke()
                    }
                )

                2 -> IncrementalJogScreen(
                    axisSteps = axisSteps,
                    lastCommand = lastIncrementalCommand,
                    onStepChanged = { axis, value ->
                        axisSteps[axis] = value
                        if (selectedMode == 2 && axis == "X") configureSelectedJogMode(2)
                    },
                    onCommand = { command ->
                        lastIncrementalCommand = command
                    },
                    displayUnits = displayUnits,
                    feedRate = displayedFeedRate,
                    onFeedRateChanged = {
                        updateDisplayedFeedRate(it)
                        if (selectedMode == 2) configureSelectedJogMode(2)
                    },
                    onJogRequest = { axis, distance, feedRate, units ->
                        lastIncrementalCommand =
                            "Requested $axis move: $distance at $feedRate ($units)"

                        incrementalJogAction?.invoke(
                            axis,
                            distance,
                            feedRate,
                            units
                        )
                    }
                )
            }
        }
    }
}