package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
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
        Spacer(modifier = Modifier.height(6.dp))

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

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Shared Jog Feed Rate",
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "${String.format(Locale.US, "%.1f", safeSpeed)} $unitLabel",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Slider(
            value = safeSpeed,
            onValueChange = onSpeedChanged,
            valueRange = 1f..maxFeedRate
        )

        Text(
            text = "Maximum: ${String.format(Locale.US, "%.1f", maxFeedRate)} $unitLabel",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

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

    }
}