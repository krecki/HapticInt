
package com.kreckapps.hapticinterface.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.kreckapps.hapticinterface.model.MachineUnits
import kotlinx.coroutines.delay

@Composable
fun PendantConnectionPanel(
    onMachineStatusChanged: (MachineStatus) -> Unit,
    onZeroAxisActionAvailable: (((Char) -> Unit)?) -> Unit,
    onSetAxisActionAvailable: (((Char, Double) -> Unit)?) -> Unit,
    onIncrementalJogActionAvailable:
        (((Char, Double, Double, MachineUnits) -> Unit)?) -> Unit,
    onContinuousJogStartActionAvailable:
        (((String, Double, MachineUnits) -> Unit)?) -> Unit,
    onStopJogActionAvailable: ((() -> Unit)?) -> Unit,
    onCommandBusyChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val latestStatusCallback = rememberUpdatedState(onMachineStatusChanged)
    val latestZeroAxisCallback = rememberUpdatedState(onZeroAxisActionAvailable)
    val latestSetAxisCallback = rememberUpdatedState(onSetAxisActionAvailable)
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
    
    val latestCommandBusy = rememberUpdatedState(commandBusy)

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

    // Publish the jog stop action.
    val stopJogAction = remember(commandController) {
        {
            commandController.stopJog()
        }
    }

    SideEffect {
        latestZeroAxisCallback.value(zeroAxisAction)
        latestSetAxisCallback.value(setAxisAction)
        latestIncrementalJogCallback.value(incrementalJogAction)
        latestContinuousJogCallback.value(continuousJogStartAction)
        latestStopJogCallback.value(stopJogAction)
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
                            commandController.updateConnection(true)

                            // Request a complete grblHAL status report.
                            bluetoothManager.sendRealtime(0x87.toChar())

                            // Request the active G-code modal state.
                            bluetoothManager.sendCommand("\$G")
                        }

                        PendantBluetoothManager.ConnectionState.DISCONNECTED,
                        PendantBluetoothManager.ConnectionState.ERROR -> {
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
            latestIncrementalJogCallback.value(null)
            latestContinuousJogCallback.value(null)
            latestStopJogCallback.value(null)
            latestBusyCallback.value(false)

            commandController.releaseResources()
            bluetoothManager.release()
            bluetoothManager.listener = null
        }
    }

    // Request position every 100 ms and modal state every 1 second.
    LaunchedEffect(connectionState) {
        if (
            connectionState ==
            PendantBluetoothManager.ConnectionState.CONNECTED
        ) {
            var pollCount = 0

            while (true) {
                bluetoothManager.sendRealtime('?')

                if (pollCount % 10 == 0 && !latestCommandBusy.value) {
                    bluetoothManager.sendCommand("\$G")
                }

                pollCount++

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
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
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
        }

        // Work Coordinate System Selector
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Active Work Coordinate System",
            style = MaterialTheme.typography.titleSmall
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("G54", "G55", "G56", "G57", "G58", "G59").forEach { wcs ->
                FilterChip(
                    selected = currentStatus.position.activeWcs == wcs,
                    enabled =
                    currentStatus.connected &&
                            currentStatus.machineState == "Idle" &&
                            currentStatus.pendantInControl == false &&
                            !commandBusy,
                    onClick = {
                        commandController.selectWorkCoordinateSystem(wcs)
                    },
                    label = {
                        Text(wcs)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                commandController.homeMachine()
            },
            enabled =
            currentStatus.connected &&
                    currentStatus.machineState == "Idle" &&
                    currentStatus.pendantInControl == false &&
                    !commandBusy,
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
