package com.kreckapps.hapticinterface.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JogPad(
    modifier: Modifier = Modifier,
    activeJog: String? = null,
    onJogChanged: (String?) -> Unit = {},
    holdToRun: Boolean = false,
    incremental: Boolean = false,
    onIncrementalJog: (String) -> Unit = {},
    onStop: (() -> Unit)? = null
) {
    val context = LocalContext.current

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val horizontalGap = 6.dp
        val verticalGap = 6.dp
        val availableCellWidth = (maxWidth - horizontalGap * 3) / 4
        val availableCellHeight = (maxHeight - verticalGap * 2) / 3
        val cellSize = minOf(availableCellWidth, availableCellHeight)

        Column(
            verticalArrangement = Arrangement.spacedBy(verticalGap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
                Spacer(Modifier.size(cellSize))
                JogDirectionButton(
                    label = "+Y",
                    active = activeJog == "+Y",
                    size = cellSize,
                    holdToRun = holdToRun,
                    incremental = incremental,
                    onJogChanged = onJogChanged,
                    onIncrementalJog = onIncrementalJog
                )
                Spacer(Modifier.size(cellSize))
                JogDirectionButton(
                    label = "+Z",
                    active = activeJog == "+Z",
                    size = cellSize,
                    holdToRun = holdToRun,
                    incremental = incremental,
                    onJogChanged = onJogChanged,
                    onIncrementalJog = onIncrementalJog
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
                JogDirectionButton(
                    label = "-X",
                    active = activeJog == "-X",
                    size = cellSize,
                    holdToRun = holdToRun,
                    incremental = incremental,
                    onJogChanged = onJogChanged,
                    onIncrementalJog = onIncrementalJog
                )

                Button(
                    onClick = {
                        performAppHaptic(context)
                        if (onStop != null) onStop() else onJogChanged(null)
                    },
                    modifier = Modifier.size(cellSize),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    Text("STOP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                JogDirectionButton(
                    label = "+X",
                    active = activeJog == "+X",
                    size = cellSize,
                    holdToRun = holdToRun,
                    incremental = incremental,
                    onJogChanged = onJogChanged,
                    onIncrementalJog = onIncrementalJog
                )
                Spacer(Modifier.size(cellSize))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
                Spacer(Modifier.size(cellSize))
                JogDirectionButton(
                    label = "-Y",
                    active = activeJog == "-Y",
                    size = cellSize,
                    holdToRun = holdToRun,
                    incremental = incremental,
                    onJogChanged = onJogChanged,
                    onIncrementalJog = onIncrementalJog
                )
                Spacer(Modifier.size(cellSize))
                JogDirectionButton(
                    label = "-Z",
                    active = activeJog == "-Z",
                    size = cellSize,
                    holdToRun = holdToRun,
                    incremental = incremental,
                    onJogChanged = onJogChanged,
                    onIncrementalJog = onIncrementalJog
                )
            }
        }
    }
}

@Composable
fun JogDirectionButton(
    label: String,
    active: Boolean,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    holdToRun: Boolean,
    incremental: Boolean,
    onJogChanged: (String?) -> Unit,
    onIncrementalJog: (String) -> Unit
) {
    val context = LocalContext.current

    val latestActive by rememberUpdatedState(active)
    val latestOnJogChanged by rememberUpdatedState(onJogChanged)
    val latestOnIncrementalJog by rememberUpdatedState(onIncrementalJog)

    val backgroundColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(label, holdToRun, incremental) {
                detectTapGestures(
                    onPress = {
                        if (incremental) {
                            performAppHaptic(context)
                            latestOnIncrementalJog(label)
                        } else if (holdToRun) {
                            latestOnJogChanged(label)
                            performAppHaptic(context)
                            try {
                                tryAwaitRelease()
                            } finally {
                                latestOnJogChanged(null)
                            }
                        } else {
                            performAppHaptic(context)
                            if (latestActive) latestOnJogChanged(null)
                            else latestOnJogChanged(label)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}
