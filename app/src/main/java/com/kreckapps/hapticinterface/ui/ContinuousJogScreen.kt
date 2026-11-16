
package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ContinuousJogScreen(
    activeJog: String?,
    onJogChanged: (String?) -> Unit,
    speed: Float,
    onSpeedChanged: (Float) -> Unit,
    holdToRun: Boolean,
    onHoldToRunChanged: (Boolean) -> Unit
) {
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
                onHoldToRunChanged(it == "Hold to Run")
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SpeedControl(
            label = "Jog Speed",
            speed = speed,
            onSpeedChanged = onSpeedChanged
        )

        Spacer(modifier = Modifier.height(18.dp))

        JogPad(
            activeJog = activeJog,
            onJogChanged = onJogChanged,
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
            text = "Speed: ${speed.toInt()}%",
            fontSize = 14.sp
        )
    }
}
