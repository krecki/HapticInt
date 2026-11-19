package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreckapps.hapticinterface.model.MachineUnits
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MPGScreen(
    position: Int,
    onPositionChanged: (Int) -> Unit,
    resolution: String,
    onResolutionChanged: (String) -> Unit,
    axis: String,
    onAxisChanged: (String) -> Unit,
    displayUnits: MachineUnits,
    onJogRequest: (Char, Double, Double, MachineUnits) -> Unit,
    onJogCancel: () -> Unit
) {
    val resolutionOptions = if (displayUnits == MachineUnits.MILLIMETERS) {
        listOf("0.01", "0.1", "1.0")
    } else {
        listOf("0.0005", "0.005", "0.05")
    }

    // MPG uses a fixed, high step feed rate. Turning the wheel faster
    // increases the rate of detents rather than changing the step feed.
    val mpgFeedRate = if (displayUnits == MachineUnits.MILLIMETERS) {
        2000.0
    } else {
        2000.0 / 25.4
    }

    LaunchedEffect(displayUnits) {
        if (resolution !in resolutionOptions) {
            onResolutionChanged(resolutionOptions.first())
        }
    }

    val displayedResolution =
        resolution.takeIf { it in resolutionOptions }
            ?: resolutionOptions.first()

    val unitLabel = if (displayUnits == MachineUnits.MILLIMETERS) {
        "mm"
    } else {
        "in"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        OptionButtons(
            options = listOf("X", "Y", "Z"),
            selected = axis,
            onSelected = onAxisChanged
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Distance per detent ($unitLabel)",
            fontWeight = FontWeight.SemiBold
        )

        OptionButtons(
            options = resolutionOptions,
            selected = displayedResolution,
            onSelected = onResolutionChanged
        )

        Spacer(modifier = Modifier.height(6.dp))

        RotaryWheel(
            position = position,
            onPositionChanged = onPositionChanged,
            onDetent = { direction ->
                val distance = displayedResolution.toDoubleOrNull()

                if (distance != null && distance > 0.0) {
                    val signedDistance = distance * direction

                    onJogRequest(
                        axis.first(),
                        signedDistance,
                        mpgFeedRate,
                        displayUnits
                    )
                }
            },
            onDragFinished = onJogCancel
        )

    }
}

@Composable
fun RotaryWheel(
    position: Int,
    onPositionChanged: (Int) -> Unit,
    onDetent: (Int) -> Unit,
    onDragFinished: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val latestPosition by rememberUpdatedState(position)
    val latestPositionCallback by rememberUpdatedState(onPositionChanged)
    val latestDetentCallback by rememberUpdatedState(onDetent)
    val latestDragFinished by rememberUpdatedState(onDragFinished)

    var accumulatedAngle by remember {
        mutableFloatStateOf(0f)
    }

    val safePulsesPerRev = 20
    val detentAngle = (2f * PI.toFloat()) / safePulsesPerRev

    Box(
        modifier = Modifier
            .size(150.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 3.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            )
            .pointerInput(safePulsesPerRev) {
                var dragPosition = latestPosition
                var sentJogDuringDrag = false

                detectDragGestures(
                    onDragStart = {
                        dragPosition = latestPosition
                        accumulatedAngle = 0f
                        sentJogDuringDrag = false
                    },
                    onDragEnd = {
                        if (sentJogDuringDrag) latestDragFinished()
                        accumulatedAngle = 0f
                    },
                    onDragCancel = {
                        if (sentJogDuringDrag) latestDragFinished()
                        accumulatedAngle = 0f
                    }
                ) { change, _ ->
                    val center = Offset(
                        size.width / 2f,
                        size.height / 2f
                    )

                    val current = change.position - center
                    val previous = change.previousPosition - center

                    val currentAngle = atan2(current.y, current.x)
                    val previousAngle = atan2(previous.y, previous.x)

                    var delta = currentAngle - previousAngle

                    if (delta > PI) {
                        delta -= (2.0 * PI).toFloat()
                    }

                    if (delta < -PI) {
                        delta += (2.0 * PI).toFloat()
                    }

                    accumulatedAngle += delta

                    while (accumulatedAngle >= detentAngle) {
                        dragPosition += 1
                        latestPositionCallback(dragPosition)
                        latestDetentCallback(1)
                        sentJogDuringDrag = true

                        accumulatedAngle -= detentAngle

                        haptic.performHapticFeedback(
                            HapticFeedbackType.LongPress
                        )
                    }

                    while (accumulatedAngle <= -detentAngle) {
                        dragPosition -= 1
                        latestPositionCallback(dragPosition)
                        latestDetentCallback(-1)
                        sentJogDuringDrag = true

                        accumulatedAngle += detentAngle

                        haptic.performHapticFeedback(
                            HapticFeedbackType.LongPress
                        )
                    }

                    change.consume()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val tickColor = MaterialTheme.colorScheme.onSurface

        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = position * (360f / safePulsesPerRev) }) {
            val center = Offset(
                size.width / 2f,
                size.height / 2f
            )

            val radius = size.minDimension * 0.42f

            for (i in 0 until safePulsesPerRev) {
                val angle =
                    (2.0 * PI * i / safePulsesPerRev) - PI / 2.0

                val outerRadius = radius

                val innerRadius = if (i % 5 == 0) {
                    radius * 0.78f
                } else {
                    radius * 0.88f
                }

                val start = Offset(
                    center.x + cos(angle).toFloat() * innerRadius,
                    center.y + sin(angle).toFloat() * innerRadius
                )

                val end = Offset(
                    center.x + cos(angle).toFloat() * outerRadius,
                    center.y + sin(angle).toFloat() * outerRadius
                )

                drawLine(
                    color = tickColor,
                    start = start,
                    end = end,
                    strokeWidth = if (i % 5 == 0) {
                        3.dp.toPx()
                    } else {
                        1.dp.toPx()
                    }
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val top = 2.dp.toPx()
            val bottom = 13.dp.toPx()
            val half = 6.dp.toPx()
            val marker = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, bottom)
                lineTo(cx - half, top)
                lineTo(cx + half, top)
                close()
            }
            drawPath(marker, color = MaterialTheme.colorScheme.primary)
        }
    }
}