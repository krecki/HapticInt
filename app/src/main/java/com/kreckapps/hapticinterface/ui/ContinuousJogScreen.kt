package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreckapps.hapticinterface.model.MachineUnits

private const val MAX_CONTINUOUS_FEED_MM_MIN = 2000f
private const val MM_PER_INCH = 25.4f

@Composable
fun ContinuousJogScreen(
    activeJog: String?,
    onJogChanged: (String?) -> Unit,
    speed: Float,
    onSpeedChanged: (Float) -> Unit,
    holdToRun: Boolean,
    onHoldToRunChanged: (Boolean) -> Unit,
    displayUnits: MachineUnits,
    onJogRequest: (String, Double, MachineUnits) -> Unit,
    onStopJog: () -> Unit
) {
    val unitLabel = if (displayUnits == MachineUnits.MILLIMETERS) {
        "mm/min"
    } else {
        "in/min"
    }

    val maxFeedRate = if (displayUnits == MachineUnits.MILLIMETERS) {
        MAX_CONTINUOUS_FEED_MM_MIN
    } else {
        MAX_CONTINUOUS_FEED_MM_MIN / MM_PER_INCH
    }

    val safeSpeed = speed.coerceIn(1f, maxFeedRate)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionTitle("Continuous Jogging")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (holdToRun) {
                "Press and hold a direction to jog"
            } else {
                "Tap a direction to start or stop jogging"
            },
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        OptionButtons(
            options = listOf("Hold to Run", "Tap to Toggle"),
            selected = if (holdToRun) "Hold to Run" else "Tap to Toggle",
            onSelected = {
                if (activeJog != null) {
                    onStopJog()
                    onJogChanged(null)
                }

                onHoldToRunChanged(it == "Hold to Run")
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Shared Jog Feed Rate",
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "${safeSpeed.toInt()} $unitLabel",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Slider(
            value = safeSpeed,
            onValueChange = onSpeedChanged,
            valueRange = 1f..maxFeedRate
        )

        Text(
            text = "Maximum: ${maxFeedRate.toInt()} $unitLabel",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        JogPad(
            activeJog = activeJog,
            onJogChanged = { direction ->
                onJogChanged(direction)

                if (direction == null) {
                    onStopJog()
                } else {
                    onJogRequest(
                        direction,
                        safeSpeed.toDouble(),
                        displayUnits
                    )
                }
            },
            holdToRun = holdToRun
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (activeJog == null) {
                "Jog inactive"
            } else {
                "Currently jogging: $activeJog"
            },
            fontWeight = FontWeight.Bold,
            color = if (activeJog == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            }
        )

        Text(
            text = "Feed rate: ${safeSpeed.toInt()} $unitLabel",
            fontSize = 14.sp
        )
    }
}