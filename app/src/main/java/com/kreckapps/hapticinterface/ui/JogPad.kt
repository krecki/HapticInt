
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JogPad(
    activeJog: String? = null,
    onJogChanged: (String?) -> Unit = {},
    holdToRun: Boolean = false,
    incremental: Boolean = false,
    onIncrementalJog: (String) -> Unit = {},
    onStop: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // XY joystick layout
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row {
                    Spacer(modifier = Modifier.width(62.dp))

                    JogDirectionButton(
                        label = "+Y",
                        active = activeJog == "+Y",
                        width = 62.dp,
                        holdToRun = holdToRun,
                        incremental = incremental,
                        onJogChanged = onJogChanged,
                        onIncrementalJog = onIncrementalJog
                    )

                    Spacer(modifier = Modifier.width(74.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    JogDirectionButton(
                        label = "-X",
                        active = activeJog == "-X",
                        width = 74.dp,
                        holdToRun = holdToRun,
                        incremental = incremental,
                        onJogChanged = onJogChanged,
                        onIncrementalJog = onIncrementalJog
                    )

                    Button(
                        onClick = {
                            if (onStop != null) {
                                onStop()
                            } else {
                                onJogChanged(null)
                            }
                        },
                        modifier = Modifier.size(62.dp, 60.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        Text(
                            "STOP",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    JogDirectionButton(
                        label = "+X",
                        active = activeJog == "+X",
                        width = 74.dp,
                        holdToRun = holdToRun,
                        incremental = incremental,
                        onJogChanged = onJogChanged,
                        onIncrementalJog = onIncrementalJog
                    )
                }

                Row {
                    Spacer(modifier = Modifier.width(74.dp))

                    JogDirectionButton(
                        label = "-Y",
                        active = activeJog == "-Y",
                        width = 74.dp,
                        holdToRun = holdToRun,
                        incremental = incremental,
                        onJogChanged = onJogChanged,
                        onIncrementalJog = onIncrementalJog
                    )

                    Spacer(modifier = Modifier.width(74.dp))
                }
            }

            // Z buttons stacked vertically to the right
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                JogDirectionButton(
                    label = "+Z",
                    active = activeJog == "+Z",
                    width = 56.dp,
                    holdToRun = holdToRun,
                    incremental = incremental,
                    onJogChanged = onJogChanged,
                    onIncrementalJog = onIncrementalJog
                )

                JogDirectionButton(
                    label = "-Z",
                    active = activeJog == "-Z",
                    width = 66.dp,
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
    width: androidx.compose.ui.unit.Dp = 74.dp,
    holdToRun: Boolean,
    incremental: Boolean,
    onJogChanged: (String?) -> Unit,
    onIncrementalJog: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

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
            .size(width, 60.dp)
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
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress
                            )

                            latestOnIncrementalJog(label)

                        } else if (holdToRun) {
                            latestOnJogChanged(label)

                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress
                            )

                            try {
                                tryAwaitRelease()
                            } finally {
                                // Always request a stop when the press ends
                                // or its pointer interaction is cancelled.
                                latestOnJogChanged(null)
                            }

                        } else {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress
                            )

                            if (latestActive) {
                                latestOnJogChanged(null)
                            } else {
                                latestOnJogChanged(label)
                            }
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
