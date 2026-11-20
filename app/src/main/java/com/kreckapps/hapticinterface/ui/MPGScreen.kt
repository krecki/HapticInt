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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val mpgFeedRate = if (displayUnits == MachineUnits.MILLIMETERS) {
        2500.0
    } else {
        2500.0 / 25.4
    }

    LaunchedEffect(displayUnits) {
        if (resolution !in resolutionOptions) onResolutionChanged(resolutionOptions.first())
    }

    val displayedResolution = resolution.takeIf { it in resolutionOptions } ?: resolutionOptions.first()
    val unitLabel = if (displayUnits == MachineUnits.MILLIMETERS) "mm" else "in"

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
                PanelSectionTitle("Selected Axis")
                OptionButtons(
                    options = listOf("X", "Y", "Z"),
                    selected = axis,
                    onSelected = onAxisChanged
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.Start
            ) {
                PanelSectionTitle("Distance per detent ($unitLabel)")
                OptionButtons(
                    options = resolutionOptions,
                    selected = displayedResolution,
                    onSelected = onResolutionChanged
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        RotaryWheel(
            position = position,
            onPositionChanged = onPositionChanged,
            onDetent = { direction ->
                val distance = displayedResolution.toDoubleOrNull()
                if (distance != null && distance > 0.0) {
                    onJogRequest(axis.first(), distance * direction, mpgFeedRate, displayUnits)
                }
            },
            onDragFinished = onJogCancel,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}

@Composable
fun RotaryWheel(
    position: Int,
    onPositionChanged: (Int) -> Unit,
    onDetent: (Int) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val latestPosition by rememberUpdatedState(position)
    val latestPositionCallback by rememberUpdatedState(onPositionChanged)
    val latestDetentCallback by rememberUpdatedState(onDetent)
    val latestDragFinished by rememberUpdatedState(onDragFinished)

    var accumulatedAngle by remember { mutableFloatStateOf(0f) }

    val safePulsesPerRev = 20
    val detentAngle = (2f * PI.toFloat()) / safePulsesPerRev

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Use 90% of the available space to leave a margin.
        val wheelSize = minOf(maxWidth, maxHeight) * 0.90f

        Box(
            modifier = Modifier
                .size(wheelSize)
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
                            if (sentJogDuringDrag) {
                                latestDragFinished()
                            }
                            accumulatedAngle = 0f
                        },
                        onDragCancel = {
                            if (sentJogDuringDrag) {
                                latestDragFinished()
                            }
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
            val markerColor = MaterialTheme.colorScheme.primary

            // Draw tick marks and numbered positions.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = position * (360f / safePulsesPerRev)
                    }
            ) {
                val center = Offset(
                    size.width / 2f,
                    size.height / 2f
                )

                val radius = size.minDimension * 0.43f

                val numberPaint = android.graphics.Paint().apply {
                    color = tickColor.toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = size.minDimension * 0.035f
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT,
                        android.graphics.Typeface.NORMAL
                    )
                }

                val fontMetrics = numberPaint.fontMetrics
                val textOffset =
                    (fontMetrics.ascent + fontMetrics.descent) / 2f

                for (i in 0 until safePulsesPerRev) {
                    val angle =
                        (2.0 * PI * i / safePulsesPerRev) - PI / 2.0

                    val innerRadius =
                        if (i % 5 == 0) radius * 0.82f
                        else radius * 0.88f

                    val outerRadius = radius

                    val start = Offset(
                        center.x + cos(angle).toFloat() * innerRadius,
                        center.y + sin(angle).toFloat() * innerRadius
                    )

                    val end = Offset(
                        center.x + cos(angle).toFloat() * outerRadius,
                        center.y + sin(angle).toFloat() * outerRadius
                    )

                    // Draw tick mark.
                    drawLine(
                        color = tickColor,
                        start = start,
                        end = end,
                        strokeWidth =
                        if (i % 5 == 0) 3.dp.toPx() else 1.5.dp.toPx()
                    )

                    // Calculate number position.
                    val numberRadius = radius * 0.70f

                    val numberX =
                        center.x + cos(angle).toFloat() * numberRadius

                    val numberY =
                        center.y + sin(angle).toFloat() * numberRadius

                    // Rotate the number to align with its tick.
                    drawContext.canvas.nativeCanvas.save()

                    drawContext.canvas.nativeCanvas.rotate(
                        Math.toDegrees(angle).toFloat() + 90f,
                        numberX,
                        numberY
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        i.toString(),
                        numberX,
                        numberY - textOffset,
                        numberPaint
                    )

                    drawContext.canvas.nativeCanvas.restore()
                }
            }

            // Center label.
            Text(
                text = "MPG",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            // Fixed pointer at the top of the dial.
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
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

                drawPath(
                    marker,
                    color = markerColor
                )
            }
        }
    }
}
