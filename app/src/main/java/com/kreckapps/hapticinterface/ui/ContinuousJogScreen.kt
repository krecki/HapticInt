package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt
import com.kreckapps.hapticinterface.model.MachineUnits

private const val MAX_CONTINUOUS_FEED_MM_MIN = 2500f

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
    val unitLabel = if (displayUnits == MachineUnits.MILLIMETERS) "mm/min" else "in/min"
    val isMetric = displayUnits == MachineUnits.MILLIMETERS
    val minFeedRate = if (isMetric) 25f else 1f
    val maxFeedRate = if (isMetric) MAX_CONTINUOUS_FEED_MM_MIN else 98f
    val safeSpeed = speed.roundToInt().toFloat().coerceIn(minFeedRate, maxFeedRate)

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
                PanelSectionTitle("Jog Mode")
                OptionButtons(
                    options = listOf("Hold to Run", "Tap to Toggle"),
                    selected = if (holdToRun) "Hold to Run" else "Tap to Toggle",
                    onSelected = {
                        if (activeJog != null) {
                            onStopJog()
                            onJogChanged(null)
                        }
                        onHoldToRunChanged(it == "Hold to Run")
                    },
                    compact = true
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            contentAlignment = Alignment.Center
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PanelSectionTitle("Feed Rate")
                    Text(
                        text = "${String.format(Locale.US, "%.0f", safeSpeed)} $unitLabel",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.sp
                    )
                }
                Slider(
                    value = safeSpeed,
                    onValueChange = onSpeedChanged,
                    valueRange = minFeedRate..maxFeedRate
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            JogPad(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = 0.90f, scaleY = 0.90f),
                activeJog = activeJog,
                onJogChanged = { direction ->
                    onJogChanged(direction)
                    if (direction == null) onStopJog()
                    else onJogRequest(direction, safeSpeed.toDouble(), displayUnits)
                },
                holdToRun = holdToRun
            )
        }
    }
}