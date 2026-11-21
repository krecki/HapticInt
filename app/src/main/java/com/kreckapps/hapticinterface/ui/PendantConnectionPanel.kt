
package com.kreckapps.hapticinterface.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kreckapps.hapticinterface.communication.PendantBluetoothManager
import com.kreckapps.hapticinterface.communication.PendantCommandController
import com.kreckapps.hapticinterface.model.MachineStatus
import com.kreckapps.hapticinterface.model.CoordinateDisplayMode
import com.kreckapps.hapticinterface.model.MachineUnits
import kotlinx.coroutines.delay

@Composable
fun PendantConnectionPanel(
    modifier: Modifier = Modifier,
    onMachineStatusChanged: (MachineStatus) -> Unit,
    onZeroAxisActionAvailable: (((Char) -> Unit)?) -> Unit,
    onSetAxisActionAvailable: (((Char, Double) -> Unit)?) -> Unit,
    onMoveAxisActionAvailable: (((Char, Double, CoordinateDisplayMode, MachineUnits) -> Unit)?) -> Unit,
    onSelectWcsActionAvailable: (((String) -> Unit)?) -> Unit,
    onIncrementalJogActionAvailable:
        (((Char, Double, Double, MachineUnits) -> Unit)?) -> Unit,
    onContinuousJogStartActionAvailable:
        (((String, Double, MachineUnits) -> Unit)?) -> Unit,
    onStopJogActionAvailable: ((() -> Unit)?) -> Unit,
    onJogModeConfigurationAvailable: (((Int, Double, Double) -> Unit)?) -> Unit,
    onCommandBusyChanged: (Boolean) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val latestStatusCallback = rememberUpdatedState(onMachineStatusChanged)
    val latestZeroAxisCallback = rememberUpdatedState(onZeroAxisActionAvailable)
    val latestSetAxisCallback = rememberUpdatedState(onSetAxisActionAvailable)
    val latestMoveAxisCallback = rememberUpdatedState(onMoveAxisActionAvailable)
    val latestSelectWcsCallback = rememberUpdatedState(onSelectWcsActionAvailable)
    val latestIncrementalJogCallback =
        rememberUpdatedState(onIncrementalJogActionAvailable)
    val latestContinuousJogCallback =
        rememberUpdatedState(onContinuousJogStartActionAvailable)
    val latestStopJogCallback =
        rememberUpdatedState(onStopJogActionAvailable)
    val latestBusyCallback = rememberUpdatedState(onCommandBusyChanged)

    val bluetoothManager = remember {
        PendantBluetoothManager(context.applicationContext)
    }

    var connectionState by remember {
        mutableStateOf(
            PendantBluetoothManager.ConnectionState.DISCONNECTED
        )
    }

    var connectionMessage by remember {
        mutableStateOf("Not connected")
    }

    var commandBusy by remember {
        mutableStateOf(false)
    }

    val commandController = remember(bluetoothManager) {
        PendantCommandController(
            bluetooth = bluetoothManager,

            onStatusChanged = { newStatus ->
                latestStatusCallback.value(newStatus)
            },

            onMessageChanged = { message ->
                connectionMessage = message
            },

            onBusyChanged = { busy ->
                commandBusy = busy
            }
        )
    }

    // Publish the existing coordinate actions.
    val zeroAxisAction = remember(commandController) {
        { axis: Char ->
            commandController.zeroWorkAxis(axis)
        }
    }

    val setAxisAction = remember(commandController) {
        { axis: Char, value: Double ->
            commandController.setWorkAxis(axis, value)
        }
    }

    val moveAxisAction = remember(commandController) {
        { axis: Char, target: Double, mode: CoordinateDisplayMode, units: MachineUnits ->
            commandController.moveAxisToPosition(axis, target, mode, units)
        }
    }

    val selectWcsAction = remember(commandController) {
        { wcs: String -> commandController.selectWorkCoordinateSystem(wcs) }
    }

    // Publish the incremental jog action.
    val incrementalJogAction = remember(commandController) {
        { axis: Char, distance: Double, feedRate: Double, units: MachineUnits ->
            commandController.jogIncrement(
                axis,
                distance,
                feedRate,
                units
            )
        }
    }

    // Publish the continuous jog start action.
    val continuousJogStartAction = remember(commandController) {
        { direction: String, feedRate: Double, units: MachineUnits ->
            commandController.startContinuousJog(
                direction,
                feedRate,
                units
            )
        }
    }

    val jogModeConfigurationAction = remember(commandController) {
        { mode: Int, stepMm: Double, feedMmMin: Double ->
            commandController.configureJogMode(mode, stepMm, feedMmMin)
        }
    }

    // Publish the jog stop action.
    val stopJogAction = remember(commandController) {
        {
            commandController.stopJog()
        }
    }

    SideEffect {
        latestZeroAxisCallback.value(zeroAxisAction)
        latestSetAxisCallback.value(setAxisAction)
        latestMoveAxisCallback.value(moveAxisAction)
        latestSelectWcsCallback.value(selectWcsAction)
        latestIncrementalJogCallback.value(incrementalJogAction)
        latestContinuousJogCallback.value(continuousJogStartAction)
        latestStopJogCallback.value(stopJogAction)
        onJogModeConfigurationAvailable(jogModeConfigurationAction)
        latestBusyCallback.value(commandBusy)
    }

    // Request Bluetooth permission when needed on Android 12+.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            bluetoothManager.connect()
        } else {
            connectionMessage = "Bluetooth permission was denied."
        }
    }

    // Listen for Bluetooth connection changes and incoming messages.
    DisposableEffect(bluetoothManager) {
        bluetoothManager.listener =
            object : PendantBluetoothManager.Listener {

                override fun onConnectionStateChanged(
                    state: PendantBluetoothManager.ConnectionState,
                    message: String
                ) {
                    connectionState = state
                    connectionMessage = message

                    when (state) {
                        PendantBluetoothManager.ConnectionState.CONNECTED -> {
                            onExpandedChange(false)
                            commandController.updateConnection(true)

                            // Synchronize units/modal state through the
                            // normal protocol stream, then return to keypad mode.
                            commandController.initializeControllerState()
                        }

                        PendantBluetoothManager.ConnectionState.DISCONNECTED,
                        PendantBluetoothManager.ConnectionState.ERROR -> {
                            onExpandedChange(false)
                            commandController.updateConnection(false)
                        }

                        PendantBluetoothManager.ConnectionState.CONNECTING -> {
                            // No additional action needed.
                        }
                    }
                }

                override fun onMessageReceived(message: String) {
                    commandController.handleMessage(message)
                }
            }

        onDispose {
            latestZeroAxisCallback.value(null)
            latestSetAxisCallback.value(null)
            latestMoveAxisCallback.value(null)
            latestSelectWcsCallback.value(null)
            latestIncrementalJogCallback.value(null)
            latestContinuousJogCallback.value(null)
            latestStopJogCallback.value(null)
            onJogModeConfigurationAvailable(null)
            latestBusyCallback.value(false)

            commandController.releaseResources()
            bluetoothManager.release()
            bluetoothManager.listener = null
        }
    }

    // Position polling is independent of modal-state synchronization.
    // $G is requested only during the controlled connection/WCS handshakes,
    // when the normal protocol stream is active.
    LaunchedEffect(connectionState) {
        if (
            connectionState ==
            PendantBluetoothManager.ConnectionState.CONNECTED
        ) {
            while (true) {
                bluetoothManager.sendRealtime('?')
                delay(100)
            }
        }
    }

    fun connectOrDisconnect() {
        if (
            connectionState ==
            PendantBluetoothManager.ConnectionState.CONNECTED
        ) {
            bluetoothManager.disconnect()
            return
        }

        if (
            connectionState ==
            PendantBluetoothManager.ConnectionState.CONNECTING
        ) {
            return
        }

        val permissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    context.checkSelfPermission(
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            bluetoothManager.connect()
        } else {
            permissionLauncher.launch(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(bottom = if (expanded) 8.dp else 0.dp)
            .pointerInput(expanded) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 8f) onExpandedChange(true)
                    if (dragAmount < -8f) onExpandedChange(false)
                }
            }
    ) {
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (connectionState) {
                    PendantBluetoothManager.ConnectionState.CONNECTING -> {
                        PanelActionChip(
                            text = "Connecting…",
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f),
                            containerColor = PendantColors.actionNeutralContainer,
                            labelColor = PendantColors.onActionNeutralContainer,
                            minHeight = 48.dp
                        )
                    }
                    PendantBluetoothManager.ConnectionState.CONNECTED -> {
                        PanelActionChip(
                            text = "Disconnect",
                            onClick = { connectOrDisconnect() },
                            modifier = Modifier.weight(1f),
                            containerColor = PendantColors.actionDangerContainer,
                            labelColor = PendantColors.onActionDangerContainer,
                            minHeight = 48.dp
                        )
                    }
                    else -> {
                        PanelActionChip(
                            text = "Connect",
                            onClick = { connectOrDisconnect() },
                            modifier = Modifier.weight(1f),
                            containerColor = PendantColors.actionPrimaryContainer,
                            labelColor = PendantColors.onActionPrimaryContainer,
                            minHeight = 48.dp
                        )
                    }
                }
                PanelActionChip(
                    text = "Home Machine",
                    onClick = { commandController.homeMachine() },
                    modifier = Modifier.weight(1f),
                    containerColor = PendantColors.actionSecondaryContainer,
                    labelColor = PendantColors.onActionSecondaryContainer,
                    minHeight = 48.dp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Connection: $connectionMessage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (commandBusy) {
                    Text(
                        "Pendant operation in progress…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clickable { onExpandedChange(true) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Swipe down · Connection controls",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}