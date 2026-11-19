
package com.kreckapps.hapticinterface.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
    onSettingsClick: () -> Unit
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

    var currentStatus by remember {
        mutableStateOf(MachineStatus())
    }

    var commandBusy by remember {
        mutableStateOf(false)
    }

    // Keep the connection controls out of the way while operating the pendant.
    var detailsExpanded by remember {
        mutableStateOf(false)
    }
    
    val commandController = remember(bluetoothManager) {
        PendantCommandController(
            bluetooth = bluetoothManager,

            onStatusChanged = { newStatus ->
                currentStatus = newStatus
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
                            detailsExpanded = false
                            commandController.updateConnection(true)

                            // Synchronize units/modal state through the
                            // normal protocol stream, then return to keypad mode.
                            commandController.initializeControllerState()
                        }

                        PendantBluetoothManager.ConnectionState.DISCONNECTED,
                        PendantBluetoothManager.ConnectionState.ERROR -> {
                            detailsExpanded = true
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
            .padding(bottom = 8.dp)
        .pointerInput(detailsExpanded) {
            detectVerticalDragGestures { _, dragAmount ->
                if (dragAmount > 8f) detailsExpanded = true
                if (dragAmount < -8f) detailsExpanded = false
            }
        }
    ) {
        if (detailsExpanded) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 44.dp)
            ) {
                Text(
                    text = connectionMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = when {
                        !currentStatus.connected ->
                            "Pendant mode: Not connected"
                        currentStatus.pendantInControl == true ->
                            "Pendant mode: In control"
                        currentStatus.pendantInControl == false ->
                            "Pendant mode: Listening"
                        else ->
                            "Pendant mode: Waiting for controller..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (connectionState) {
                PendantBluetoothManager.ConnectionState.CONNECTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                PendantBluetoothManager.ConnectionState.CONNECTED -> {
                    OutlinedButton(
                        onClick = {
                            connectOrDisconnect()
                        }
                    ) {
                        Text("Disconnect")
                    }
                }

                else -> {
                    Button(
                        onClick = {
                            connectOrDisconnect()
                        }
                    ) {
                        Text("Connect")
                    }
                }
                }
                TextButton(
                    onClick = { detailsExpanded = !detailsExpanded }
                ) {
                    Text(if (detailsExpanded) "Hide" else "Controls")
                }
                TextButton(onClick = onSettingsClick) {
                    Text("Settings")
                }
            }
        }
        } else {
            Text(
                "Swipe down · Connection controls",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }


        if (detailsExpanded) {
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                commandController.homeMachine()
            },
            enabled = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Home Machine")
        }

        if (commandBusy) {
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Pendant operation in progress...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        }
    }
}
