package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.kreckapps.hapticinterface.model.CoordinateDisplayMode
import com.kreckapps.hapticinterface.model.MachineStatus
import com.kreckapps.hapticinterface.model.MachineUnits

private const val MM_PER_INCH = 25.4f
private const val MAX_FEED_MM_MIN = 2500f

@Composable
fun PendantApp() {
    var selectedMode by remember { mutableIntStateOf(0) }
    var wcsPanelExpanded by remember { mutableStateOf(false) }
    var topPanelExpanded by remember { mutableStateOf(false) }

    // MPG settings and state
    var mpgPosition by remember { mutableIntStateOf(0) }
    var mpgResolution by remember { mutableStateOf("0.01") }
    var mpgAxis by remember { mutableStateOf("X") }

    // One shared feed rate, always stored internally in mm/min.
    var jogFeedRateMmMin by remember {
        mutableFloatStateOf(1250f)
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

    var selectWcsAction by remember { mutableStateOf<((String) -> Unit)?>(null) }

    var moveAxisAction by remember {
        mutableStateOf<((Char, Double, CoordinateDisplayMode, MachineUnits) -> Unit)?>(null)
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
            "X" to "0.375",
            "Y" to "0.375",
            "Z" to "0.375"
        )
    }
    val customStepAxes = remember { mutableStateListOf<String>() }

    var lastIncrementalCommand by remember {
        mutableStateOf("No jog command yet")
    }

    // Convert the shared internal feed rate to the selected display units.
    val displayedFeedRate = if (
        displayUnits == MachineUnits.MILLIMETERS
    ) {
        (jogFeedRateMmMin / 25f).roundToInt() * 25f
    } else {
        (jogFeedRateMmMin / MM_PER_INCH).roundToInt().toFloat()
    }

    val maximumDisplayedFeedRate = if (
        displayUnits == MachineUnits.MILLIMETERS
    ) MAX_FEED_MM_MIN else 98f

    val minimumDisplayedFeedRate = if (
        displayUnits == MachineUnits.MILLIMETERS
    ) 25f else 1f

    fun updateDisplayedFeedRate(value: Float) {
        val safeValue = value.coerceIn(minimumDisplayedFeedRate, maximumDisplayedFeedRate)
            .roundToInt().toFloat()
        jogFeedRateMmMin = if (displayUnits == MachineUnits.MILLIMETERS) safeValue else safeValue * MM_PER_INCH
    }

    fun configureSelectedJogMode(mode: Int) {
        val stepMm = if (mode == 0) {
            (mpgResolution.toDoubleOrNull() ?: 0.01) *
                    if (displayUnits == MachineUnits.MILLIMETERS) 1.0 else 25.4
        } else {
            (axisSteps["X"]?.toDoubleOrNull() ?: 0.01) *
                    if (displayUnits == MachineUnits.MILLIMETERS) 1.0 else 25.4
        }
        val feedMmMin = if (mode == 0) 2500.0 else jogFeedRateMmMin.toDouble()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, top = 42.dp, bottom = 6.dp)
        ) {
            PositionDisplay(
                status = machineStatus,
                displayMode = coordinateDisplayMode,
                onDisplayModeChanged = {
                    coordinateDisplayMode = it
                },
                displayUnits = displayUnits,
                onDisplayUnitsChanged = {
                    if (it != displayUnits) {
                        listOf("X", "Y", "Z").forEach { axis ->
                            if (axis !in customStepAxes) axisSteps[axis] = if (it == MachineUnits.MILLIMETERS) "10" else "0.375"
                        }
                    }
                    displayUnits = it
                    displayUnitsInitialized = true
                },
                zeroXEnabled = !coordinateCommandBusy,
                onZeroAxis = { axis ->
                    zeroAxisAction?.invoke(axis)
                },
                onSetAxis = { axis, value ->
                    setAxisAction?.invoke(axis, value)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

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

                ModeTab(
                    text = "Position",
                    selected = selectedMode == 3,
                    modifier = Modifier.weight(1f)
                ) {
                    stopActiveJog()
                    selectedMode = 3
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(selectedMode) {
                        var dragTotal = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onHorizontalDrag = { change, amount ->
                                dragTotal += amount
                                change.consume()
                            },
                            onDragEnd = {
                                if (dragTotal < -70f) {
                                    stopActiveJog()
                                    selectedMode = (selectedMode + 1).coerceAtMost(3)
                                    configureSelectedJogMode(selectedMode)
                                } else if (dragTotal > 70f) {
                                    stopActiveJog()
                                    selectedMode = (selectedMode - 1).coerceAtLeast(0)
                                    configureSelectedJogMode(selectedMode)
                                }
                            }
                        )
                    }
            ) {
            when (selectedMode) {
                0 -> MPGScreen(
                    position = mpgPosition,
                    onPositionChanged = {
                        mpgPosition = it
                    },
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
                    },
                    onJogCancel = {
                        stopJogAction?.invoke()
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
                        if (axis !in customStepAxes) customStepAxes.add(axis)
                        if (selectedMode == 2) {
                            val step = value.toDoubleOrNull()
                            if (step != null && step > 0.0) {
                                val stepMm = step *
                                        if (displayUnits == MachineUnits.MILLIMETERS) 1.0 else 25.4
                                jogModeConfigurationAction?.invoke(
                                    2, stepMm, jogFeedRateMmMin.toDouble()
                                )
                            }
                        }
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
                    },
                    onJogCancel = {
                        stopJogAction?.invoke()
                    }
                )

                3 -> MoveToPositionScreen(
                    status = machineStatus,
                    coordinateMode = coordinateDisplayMode,
                    displayUnits = displayUnits,
                    enabled = !coordinateCommandBusy,
                    onMove = { axis, target ->
                        moveAxisAction?.invoke(
                            axis, target, coordinateDisplayMode, displayUnits
                        )
                    },
                    onMoveToZero = { axis ->
                        moveAxisAction?.invoke(
                            axis, 0.0, coordinateDisplayMode, displayUnits
                        )
                    }
                )
            }
            }
        }

        if (wcsPanelExpanded || topPanelExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable {
                        wcsPanelExpanded = false
                        topPanelExpanded = false
                    }
            )
        }

        PendantConnectionPanel(
            modifier = Modifier.align(Alignment.TopCenter),
            expanded = topPanelExpanded,
            onExpandedChange = { topPanelExpanded = it },
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
                        // Adopt controller units once after connecting.
                        // Thereafter the display-units selector is user-owned;
                        // ordinary status reports must not undo a manual choice.
                        if (
                            !displayUnitsInitialized &&
                            newStatus.position.modalStateKnown
                        ) {
                            val controllerUnits = newStatus.position.units
                            if (controllerUnits != displayUnits) {
                                listOf("X", "Y", "Z").forEach { axis ->
                                    if (axis !in customStepAxes) {
                                        axisSteps[axis] = if (controllerUnits == MachineUnits.MILLIMETERS) "10" else "0.375"
                                    }
                                }
                            }
                            displayUnits = controllerUnits
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

                onMoveAxisActionAvailable = { action ->
                    moveAxisAction = action
                },

                onSelectWcsActionAvailable = { action ->
                    selectWcsAction = action
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



        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(if (wcsPanelExpanded) 116.dp else 26.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(wcsPanelExpanded) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -5f) wcsPanelExpanded = true
                        if (dragAmount > 5f) wcsPanelExpanded = false
                    }
                }
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            if (wcsPanelExpanded) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Active Work Coordinate System", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { wcsPanelExpanded = false }) { Text("Hide") }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("G54", "G55", "G56", "G57", "G58", "G59").forEach { wcs ->
                            FilterChip(
                                selected = machineStatus.position.activeWcs == wcs,
                                enabled = !coordinateCommandBusy,
                                onClick = { selectWcsAction?.invoke(wcs) },
                                label = { Text(wcs) }
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Swipe up · Work Coordinate System",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}