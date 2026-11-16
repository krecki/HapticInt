
package com.kreckapps.hapticinterface.communication

import android.os.Handler
import android.os.Looper
import com.kreckapps.hapticinterface.model.MachineStatus
import java.math.BigDecimal
import kotlin.math.abs

class PendantCommandController(
    private val bluetooth: PendantBluetoothManager,
    private val onStatusChanged: (MachineStatus) -> Unit,
    private val onMessageChanged: (String) -> Unit,
    private val onBusyChanged: (Boolean) -> Unit
) {

    private enum class Stage {
        IDLE,
        WAITING_FOR_CONTROL,
        WAITING_FOR_COMMAND,
        WAITING_FOR_MODAL_REPORT,
        WAITING_FOR_POSITION_REPORT,
        WAITING_FOR_HOMING_REPORT,
        WAITING_FOR_RELEASE
    }

    private enum class Operation {
        SELECT_WCS,
        ZERO_AXIS,
        SET_AXIS,
        HOME_MACHINE
    }

    companion object {
        private val VALID_WCS = listOf(
            "G54", "G55", "G56",
            "G57", "G58", "G59"
        )

        private const val MPG_TOGGLE = 0x8B
        private const val POSITION_TOLERANCE = 0.001
    }

    private val handler = Handler(Looper.getMainLooper())

    private var stage = Stage.IDLE
    private var currentStatus = MachineStatus()

    private var operation: Operation? = null
    private var requestedWcs: String? = null
    private var requestedAxis: Char? = null
    private var requestedValue: Double? = null

    private var timeoutRunnable: Runnable? = null
    private var releaseMessage = ""

    val isBusy: Boolean
        get() = stage != Stage.IDLE

    fun updateConnection(connected: Boolean) {
        currentStatus = currentStatus.copy(
            connected = connected,
            machineState = if (connected) {
                currentStatus.machineState
            } else {
                "Disconnected"
            }
        )

        onStatusChanged(currentStatus)

        if (!connected && stage != Stage.IDLE) {
            cancelTimeout()
            requestedWcs = null
            requestedAxis = null
            requestedValue = null
            operation = null
            stage = Stage.IDLE

            onBusyChanged(false)

            onMessageChanged(
                "Connection lost during pendant operation. Check controller mode."
            )
        }
    }

    fun handleMessage(message: String) {
        when {
            message.startsWith("<") -> {
                handleStatusReport(message)
            }

            message.startsWith("[GC:") -> {
                handleModalReport(message)
            }

            message == "ok" -> {
                handleCommandAccepted()
            }

            message.startsWith("error:") -> {
                handleCommandError(message)
            }
        }
    }

    // ------------------------------------------------
    // Select active work coordinate system
    // ------------------------------------------------

    fun selectWorkCoordinateSystem(wcs: String) {
        if (wcs !in VALID_WCS) {
            onMessageChanged("Invalid coordinate system.")
            return
        }

        if (!canStartOperation()) return

        if (currentStatus.position.activeWcs == wcs) {
            onMessageChanged("$wcs is already active.")
            return
        }

        operation = Operation.SELECT_WCS
        requestedWcs = wcs
        requestedAxis = null
        requestedValue = null

        beginControlHandover("Requesting pendant control...")
    }

    // ------------------------------------------------
    // Zero work-coordinate axes
    // ------------------------------------------------

    fun zeroWorkX() {
        zeroWorkAxis('X')
    }

    fun zeroWorkY() {
        zeroWorkAxis('Y')
    }

    fun zeroWorkZ() {
        zeroWorkAxis('Z')
    }

    fun zeroWorkAxis(axis: Char) {
        val normalizedAxis = axis.uppercaseChar()

        if (normalizedAxis !in listOf('X', 'Y', 'Z')) {
            onMessageChanged("Invalid axis.")
            return
        }

        if (!canStartOperation()) return

        val wcs = currentStatus.position.activeWcs

        if (wcs !in VALID_WCS) {
            onMessageChanged("Active work coordinate system is invalid.")
            return
        }

        operation = Operation.ZERO_AXIS
        requestedWcs = wcs
        requestedAxis = normalizedAxis
        requestedValue = 0.0

        beginControlHandover(
            "Requesting pendant control to zero $normalizedAxis..."
        )
    }

    // ------------------------------------------------
    // Set work-coordinate axes to entered values
    // ------------------------------------------------

    fun setWorkAxis(axis: Char, value: Double) {
        val normalizedAxis = axis.uppercaseChar()

        if (normalizedAxis !in listOf('X', 'Y', 'Z')) {
            onMessageChanged("Invalid axis.")
            return
        }

        if (!value.isFinite()) {
            onMessageChanged("Please enter a valid coordinate value.")
            return
        }

        if (!canStartOperation()) return

        val wcs = currentStatus.position.activeWcs

        if (wcs !in VALID_WCS) {
            onMessageChanged("Active work coordinate system is invalid.")
            return
        }

        operation = Operation.SET_AXIS
        requestedWcs = wcs
        requestedAxis = normalizedAxis
        requestedValue = value

        beginControlHandover(
            "Requesting pendant control to set $normalizedAxis..."
        )
    }

    // ------------------------------------------------
    // Home machine
    // ------------------------------------------------

    fun homeMachine() {
        if (!canStartOperation()) return

        operation = Operation.HOME_MACHINE
        requestedWcs = null
        requestedAxis = null
        requestedValue = null

        beginControlHandover(
            "Requesting pendant control to start homing..."
        )
    }

    // ------------------------------------------------
    // Common operation checks
    // ------------------------------------------------

    private fun canStartOperation(): Boolean {
        if (stage != Stage.IDLE) {
            onMessageChanged("Another pendant operation is in progress.")
            return false
        }

        if (!currentStatus.connected) {
            onMessageChanged("Pendant is not connected.")
            return false
        }

        if (currentStatus.machineState != "Idle") {
            onMessageChanged("Machine must be idle.")
            return false
        }

        if (currentStatus.pendantInControl != false) {
            onMessageChanged(
                "Controller is not confirmed to be in listening mode."
            )
            return false
        }

        return true
    }

    // ------------------------------------------------
    // Request MPG control
    // ------------------------------------------------

    private fun beginControlHandover(message: String) {
        stage = Stage.WAITING_FOR_CONTROL
        onBusyChanged(true)
        onMessageChanged(message)

        if (!bluetooth.sendRealtime(MPG_TOGGLE.toChar())) {
            finish("Could not request pendant control.")
            return
        }

        startTimeout(4000) {
            finish(
                "Controller did not grant pendant control. Check machine state."
            )
        }
    }

    // ------------------------------------------------
    // Incoming status report
    // ------------------------------------------------

    private fun handleStatusReport(report: String) {
        val updated = GrblStatusParser.parseStatusReport(
            report,
            currentStatus
        ) ?: return

        currentStatus = updated
        onStatusChanged(currentStatus)

        when (stage) {
            Stage.WAITING_FOR_CONTROL -> {
                if (currentStatus.pendantInControl == true) {
                    sendPendingCommand()
                }
            }

            Stage.WAITING_FOR_MODAL_REPORT -> {
                if (currentStatus.position.activeWcs == requestedWcs) {
                    releaseControl(
                        "$requestedWcs selected successfully."
                    )
                }
            }

            Stage.WAITING_FOR_POSITION_REPORT -> {
                verifyRequestedAxis()
            }

            Stage.WAITING_FOR_HOMING_REPORT -> {
                when (currentStatus.machineState) {
                    "Idle" -> {
                        releaseControl(
                            "Homing cycle completed."
                        )
                    }

                    "Alarm" -> {
                        releaseControl(
                            "Homing ended in an alarm state. Check the controller."
                        )
                    }
                }
            }

            Stage.WAITING_FOR_RELEASE -> {
                if (currentStatus.pendantInControl == false) {
                    finish(releaseMessage)
                }
            }

            else -> Unit
        }
    }

    // ------------------------------------------------
    // Incoming modal report
    // ------------------------------------------------

    private fun handleModalReport(report: String) {
        currentStatus = GrblStatusParser.parseModalReport(
            report,
            currentStatus
        )

        onStatusChanged(currentStatus)

        if (
            stage == Stage.WAITING_FOR_MODAL_REPORT &&
            currentStatus.position.activeWcs == requestedWcs
        ) {
            releaseControl(
                "$requestedWcs selected successfully."
            )
        }
    }

    // ------------------------------------------------
    // Send the requested operation
    // ------------------------------------------------

    private fun sendPendingCommand() {
        stage = Stage.WAITING_FOR_COMMAND

        val command = when (operation) {

            Operation.SELECT_WCS -> {
                val wcs = requestedWcs ?: return
                onMessageChanged(
                    "Pendant has control. Selecting $wcs..."
                )
                wcs
            }

            Operation.ZERO_AXIS,
            Operation.SET_AXIS -> {
                val wcs = requestedWcs ?: return

                val pNumber = when (wcs) {
                    "G54" -> 1
                    "G55" -> 2
                    "G56" -> 3
                    "G57" -> 4
                    "G58" -> 5
                    "G59" -> 6
                    else -> {
                        releaseControl("Invalid work coordinate system.")
                        return
                    }
                }

                val axis = requestedAxis ?: run {
                    releaseControl("No axis was selected.")
                    return
                }

                val value = requestedValue ?: run {
                    releaseControl("No coordinate value was supplied.")
                    return
                }

                val formattedValue = BigDecimal.valueOf(value)
                    .stripTrailingZeros()
                    .toPlainString()

                if (operation == Operation.ZERO_AXIS) {
                    onMessageChanged(
                        "Pendant has control. Zeroing $wcs $axis..."
                    )
                } else {
                    onMessageChanged(
                        "Pendant has control. Setting $wcs $axis to $formattedValue..."
                    )
                }

                "G10 L20 P$pNumber $axis$formattedValue"
            }

            Operation.HOME_MACHINE -> {
                onMessageChanged(
                    "Pendant has control. Homing machine..."
                )
                "\$H"
            }

            else -> return
        }

        if (!bluetooth.sendCommand(command)) {
            releaseControl("Failed to send controller command.")
            return
        }

        startTimeout(5000) {
            releaseControl(
                "Timed out waiting for the controller response."
            )
        }
    }

    // ------------------------------------------------
    // Command acknowledgement
    // ------------------------------------------------

    private fun handleCommandAccepted() {
        if (stage != Stage.WAITING_FOR_COMMAND) return

        when (operation) {

            Operation.SELECT_WCS -> {
                stage = Stage.WAITING_FOR_MODAL_REPORT

                onMessageChanged(
                    "Command accepted. Verifying coordinate system..."
                )

                if (!bluetooth.sendCommand("\$G")) {
                    releaseControl(
                        "Could not verify the active coordinate system."
                    )
                    return
                }

                startTimeout(4000) {
                    releaseControl(
                        "Timed out verifying the active coordinate system."
                    )
                }
            }

            Operation.ZERO_AXIS,
            Operation.SET_AXIS -> {
                stage = Stage.WAITING_FOR_POSITION_REPORT

                val axis = requestedAxis ?: '?'

                onMessageChanged(
                    "Coordinate command accepted. Verifying $axis position..."
                )

                if (!bluetooth.sendRealtime('?')) {
                    releaseControl(
                        "Could not request position verification."
                    )
                    return
                }

                startTimeout(4000) {
                    releaseControl(
                        "Timed out verifying the $axis coordinate."
                    )
                }
            }

            Operation.HOME_MACHINE -> {
                stage = Stage.WAITING_FOR_HOMING_REPORT

                onMessageChanged(
                    "Homing command accepted. Verifying machine state..."
                )

                if (!bluetooth.sendRealtime('?')) {
                    releaseControl(
                        "Could not request homing status."
                    )
                    return
                }

                startTimeout(10000) {
                    releaseControl(
                        "Homing command was accepted, but the final machine state could not be confirmed."
                    )
                }
            }

            else -> Unit
        }
    }

    // ------------------------------------------------
    // Verify the requested work coordinate
    // ------------------------------------------------

    private fun verifyRequestedAxis() {
        if (stage != Stage.WAITING_FOR_POSITION_REPORT) return

        if (currentStatus.position.activeWcs != requestedWcs) {
            return
        }

        val axis = requestedAxis ?: return
        val expectedValue = requestedValue ?: return

        val actualValue = when (axis) {
            'X' -> currentStatus.position.workX
            'Y' -> currentStatus.position.workY
            'Z' -> currentStatus.position.workZ
            else -> return
        }

        if (abs(actualValue - expectedValue) <= POSITION_TOLERANCE) {
            val message = if (operation == Operation.ZERO_AXIS) {
                "$requestedWcs $axis successfully set to zero."
            } else {
                "$requestedWcs $axis successfully set to $expectedValue."
            }

            releaseControl(message)
        }
    }

    // ------------------------------------------------
    // Release MPG control
    // ------------------------------------------------

    private fun releaseControl(message: String) {
        cancelTimeout()

        releaseMessage = message

        if (currentStatus.pendantInControl != true) {
            finish(
                "$message Controller control state could not be confirmed."
            )
            return
        }

        stage = Stage.WAITING_FOR_RELEASE
        onMessageChanged("Releasing pendant control...")

        if (!bluetooth.sendRealtime(MPG_TOGGLE.toChar())) {
            finish(
                "$message Failed to send the release request. Check controller mode."
            )
            return
        }

        startTimeout(4000) {
            finish(
                "$message Could not confirm return to listening mode. Check controller before using UGS."
            )
        }
    }

    // ------------------------------------------------
    // Controller error
    // ------------------------------------------------

    private fun handleCommandError(error: String) {
        if (stage == Stage.IDLE) return

        if (currentStatus.pendantInControl == true) {
            releaseControl(
                "Controller rejected the command ($error)."
            )
        } else {
            finish(
                "Controller rejected the command ($error)."
            )
        }
    }

    // ------------------------------------------------
    // Timeout handling
    // ------------------------------------------------

    private fun startTimeout(
        milliseconds: Long,
        action: () -> Unit
    ) {
        cancelTimeout()

        timeoutRunnable = Runnable {
            action()
        }

        handler.postDelayed(
            timeoutRunnable!!,
            milliseconds
        )
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let {
            handler.removeCallbacks(it)
        }

        timeoutRunnable = null
    }

    // ------------------------------------------------
    // Finish operation
    // ------------------------------------------------

    private fun finish(message: String) {
        cancelTimeout()

        requestedWcs = null
        requestedAxis = null
        requestedValue = null
        operation = null
        stage = Stage.IDLE

        onBusyChanged(false)
        onMessageChanged(message)
    }

    fun releaseResources() {
        cancelTimeout()

        requestedWcs = null
        requestedAxis = null
        requestedValue = null
        operation = null
        stage = Stage.IDLE

        handler.removeCallbacksAndMessages(null)
    }
}
