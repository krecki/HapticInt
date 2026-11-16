
package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    pulsesPerRev: Int,
    onPulsesPerRevChanged: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle("Pendant Settings")

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "MPG Detents per Revolution",
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Controls how many individual steps are generated during one full wheel rotation.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "$pulsesPerRev detents per revolution",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Slider(
            value = pulsesPerRev.toFloat(),
            onValueChange = {
                onPulsesPerRevChanged(it.toInt())
            },
            valueRange = 10f..200f,
            steps = 37
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("10")
            Text("200")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "The setting is adjustable in increments of five detents.",
            fontSize = 13.sp
        )
    }
}
