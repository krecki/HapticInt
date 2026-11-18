
package com.kreckapps.hapticinterface.communication

import android.os.Handler
import android.os.Looper
import com.kreckapps.hapticinterface.model.MachineStatus
import com.kreckapps.hapticinterface.model.MachineUnits
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
        WAITING_FOR_INITIAL_LISTENING,
        WAITING_FOR_INITIAL_CONTROL,
        WAITING_FOR_INITIAL_MODAL,
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

        private val VALID_AXES = listOf('X', 'Y', 'Z')

        private const val MPG_TOGGLE = 0x8B
        private const val JOG_CANCEL = 0x85

        private const val POSITION_TOLERANCE = 0.001

        private const val CONTINUOUS_JOG_DISTANCE_MM = 220.0

        private const val MAX_FEED_MM_MIN = 2000.0
        private const val MM_PER_INCH = 25.4
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

    // True while the continuous direction key is held by the UI.
    private var continuousJogActive = false

    // Blocks new operations if the controller's MPG state is uncertain.
    private var controlStateUncertain = false

    // Runtime keypad settings are volatile on the controller. Cache only for
    // this connection and resend them after every reconnect.
    private var runtimeStepSpeedMmMin: Float? = null
    private var runtimeFastSpeedMmMin: Float? = null
    private var runtimeStepDistanceMm: Float? = null
    private var runtimeFastDistanceMm: Float? = null

    val isBusy: Boolean
        get() = stage != Stage.IDLE

    // ------------------------------------------------
    // Connection and incoming messages
    // ------------------------------------------------

    fun updateConnection(connected: Boolean) {
        currentStatus = currentStatus.copy(
            connected = connected,
            machineState = if (connected) {
                currentStatus.machineState
            } else {
                "Disconnected"
            },
            // Do not reuse the previous connection's G20/G21 state. The
            // fresh $G report must establish units for this connection.
            position = if (connected) {
                currentStatus.position.copy(
                    modalStateKnown = false,
                    wcoX = null,
                    wcoY = null,
                    wcoZ = null
                )
            } else {
                currentStatus.position
            }
        )

        onStatusChanged(currentStatus)

        if (connected) {
            runtimeStepSpeedMmMin = null
            runtimeFastSpeedMmMin = null
            runtimeStepDistanceMm = null
            runtimeFastDistanceMm = null
        }

        if (!connected && stage != Stage.IDLE) {
            cancelTimeout()
            clearOperationState()
            stage = Stage.IDLE
            controlStateUncertain = true
            onBusyChanged(false)

            onMessageChanged(
                "Connection lost during pendant operation. " +
                        "Controller mode is uncertain. Check the controller."
            )
        }
    }

    /**
     * Establish the controller's modal state on each Bluetooth connection.
     * The keypad plugin consumes ordinary G-code while MPG is off, so briefly
     * switch to the normal protocol stream to request $G, then return to
     * keypad/listening mode before enabling jog controls.
     */
    fun initializeControllerState() {
        if (!currentStatus.connected || stage != Stage.IDLE) return

        controlStateUncertain = true
        stage = Stage.WAITING_FOR_INITIAL_LISTENING
        onBusyChanged(true)
        onMessageChanged("Checking controller mode...")

        if (!bluetooth.sendRealtime(0x87.toChar())) {
            markControlUncertain("Could not request controller status.")
            return
        }

        startTimeout(4000) {
            markControlUncertain("Timed out checking controller mode.")
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
    // Work coordinate system selection
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

    fun zeroWorkX() = zeroWorkAxis('X')

    fun zeroWorkY() = zeroWorkAxis('Y')

    fun zeroWorkZ() = zeroWorkAxis('Z')

    fun zeroWorkAxis(axis: Char) {
        val normalizedAxis = axis.uppercaseChar()

        if (normalizedAxis !in VALID_AXES) {
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
    // Set work-coordinate axes
    // ------------------------------------------------

    fun setWorkAxis(axis: Char, value: Double) {
        val normalizedAxis = axis.uppercaseChar()

        if (normalizedAxis !in VALID_AXES) {
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
    // Keypad jogging - fire-and-forget from Android
    // ------------------------------------------------

    /**
     * Sends one keypad Step-mode jog immediately. No Android-side movement
     * queue is maintained and completion status is not awaited.
     */
    fun jogIncrement(
        axis: Char,
        distance: Double,
        feedRate: Double,
        units: MachineUnits
    ) {
        val normalizedAxis = axis.uppercaseChar()
        if (normalizedAxis !in VALID_AXES) {
            onMessageChanged("Invalid jog axis.")
            return
        }
        if (!distance.isFinite() || distance == 0.0 ||
            !validateJogDistance(distance, units) ||
            !validateFeedRate(feedRate, units)) return
        if (!canSendKeypadJog()) return

        sendKeypadJog(
            axis = normalizedAxis,
            negative = distance < 0.0,
            distanceMm = toMillimeters(abs(distance), units),
            feedRateMmMin = toMillimeters(feedRate, units)
                .coerceAtMost(MAX_FEED_MM_MIN),
            mode = '2',
            continuous = false
        )
    }

    /**
     * Continuous mode uses a long finite keypad jog. The physical key-up
     * event sends the keypad plugin's realtime jog-cancel byte.
     */
    fun startContinuousJog(
        direction: String,
        feedRate: Double,
        units: MachineUnits
    ) {
        val normalized = direction.trim().uppercase()
        if (normalized.length != 2 ||
            (normalized[0] != '+' && normalized[0] != '-') ||
            normalized[1] !in VALID_AXES) {
            onMessageChanged("Invalid jog direction.")
            return
        }
        if (!validateFeedRate(feedRate, units) || !canSendKeypadJog()) return

        continuousJogActive = true

        sendKeypadJog(
            axis = normalized[1],
            negative = normalized[0] == '-',
            distanceMm = CONTINUOUS_JOG_DISTANCE_MM,
            feedRateMmMin = toMillimeters(feedRate, units)
                .coerceAtMost(MAX_FEED_MM_MIN),
            mode = '0',
            continuous = true
        )
    }

    /**
     * Preloads the volatile keypad settings when the user enters a jog mode.
     * Values are supplied in mm and mm/min.
     */
    fun configureJogMode(mode: Int, stepDistanceMm: Double, feedRateMmMin: Double) {
        if (!canSendKeypadJog()) return
        val speed = feedRateMmMin.toFloat()
        if (!speed.isFinite() || speed !in 1f..10000f) return

        when (mode) {
            0, 2 -> {
                val distance = stepDistanceMm.toFloat()
                if (!distance.isFinite() || distance !in 0.001f..1000f) return
                if (runtimeStepSpeedMmMin != speed && sendRuntimeSetting(1, speed))
                    runtimeStepSpeedMmMin = speed
                if (runtimeStepDistanceMm != distance && sendRuntimeSetting(4, distance))
                    runtimeStepDistanceMm = distance
            }
            1 -> {
                val distance = CONTINUOUS_JOG_DISTANCE_MM.toFloat()
                if (runtimeFastSpeedMmMin != speed && sendRuntimeSetting(3, speed))
                    runtimeFastSpeedMmMin = speed
                if (runtimeFastDistanceMm != distance && sendRuntimeSetting(6, distance))
                    runtimeFastDistanceMm = distance
            }
        }
    }

    /**
     * A keypad key-up is sent immediately. The controller's status reports
     * are deliberately not part of the jog command pipeline.
     */
    fun stopJog() {
        // Jog cancel is valid for Step, Fast, and queued MPG detent moves.
        // Send it whenever connected, even if the latest status report is
        // stale or temporarily missing the MPG field.
        continuousJogActive = false
        if (!currentStatus.connected) {
            onMessageChanged("Cannot send jog cancel: pendant is disconnected.")
            return
        }

        if (bluetooth.sendRealtime(JOG_CANCEL.toChar())) {
            onMessageChanged("Jog cancel sent.")
        } else {
            onMessageChanged("Could not send jog cancel.")
        }
    }

    private fun canSendKeypadJog(): Boolean {
        if (!currentStatus.connected) {
            onMessageChanged("Pendant is not connected.")
            return false
        }
        if (controlStateUncertain || currentStatus.pendantInControl != false) {
            onMessageChanged("Controller must be in keypad/listening mode (MPG off).")
            return false
        }
        if (stage != Stage.IDLE || operation != null) {
            onMessageChanged("Another pendant operation is in progress.")
            return false
        }
        return true
    }

    private fun sendKeypadJog(
        axis: Char,
        negative: Boolean,
        distanceMm: Double,
        feedRateMmMin: Double,
        mode: Char,
        continuous: Boolean
    ) {
        val speedId = if (continuous) 3 else 1
        val distanceId = if (continuous) 6 else 4
        val speed = feedRateMmMin.toFloat()
        val distance = distanceMm.toFloat()

        if (!speed.isFinite() || !distance.isFinite()) return

        // Send only changed runtime settings. The keypad firmware stores these
        // in RAM; no $ settings command or NVS write is performed.
        if (continuous) {
            if (runtimeFastSpeedMmMin != speed) {
                if (!sendRuntimeSetting(speedId, speed)) {
                    onMessageChanged("Could not update keypad jog feed rate.")
                    return
                }
                runtimeFastSpeedMmMin = speed
            }
            if (runtimeFastDistanceMm != distance) {
                if (!sendRuntimeSetting(distanceId, distance)) {
                    onMessageChanged("Could not update keypad jog distance.")
                    return
                }
                runtimeFastDistanceMm = distance
            }
        } else {
            if (runtimeStepSpeedMmMin != speed) {
                if (!sendRuntimeSetting(speedId, speed)) {
                    onMessageChanged("Could not update keypad jog feed rate.")
                    return
                }
                runtimeStepSpeedMmMin = speed
            }
            if (runtimeStepDistanceMm != distance) {
                if (!sendRuntimeSetting(distanceId, distance)) {
                    onMessageChanged("Could not update keypad jog distance.")
                    return
                }
                runtimeStepDistanceMm = distance
            }
        }

        val direction = when (axis) {
            'X' -> if (negative) 'L' else 'R'
            'Y' -> if (negative) 'B' else 'F'
            else -> if (negative) 'D' else 'U'
        }

        if (!bluetooth.sendRealtime(mode) || !bluetooth.sendRealtime(direction)) {
            onMessageChanged("Failed to send keypad jog command.")
            return
        }
        onMessageChanged(if (continuous) "Continuous $axis jog started." else "$axis step jog sent.")
    }

    // ------------------------------------------------
    // Common operation checks
    // ------------------------------------------------

    private fun canStartOperation(): Boolean {
        if (stage != Stage.IDLE) {
            onMessageChanged("Another pendant operation is in progress.")
            return false
        }

        if (controlStateUncertain) {
            onMessageChanged(
                "Controller mode is uncertain. Verify that MPG mode is off " +
                        "before starting another operation."
            )
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

    private fun validateJogDistance(
        distance: Double,
        units: MachineUnits
    ): Boolean {
        val distanceMm = toMillimeters(abs(distance), units)
        if (distanceMm < 0.001 || distanceMm > 1000.0) {
            onMessageChanged(
                "Jog distance must be between 0.001 and 1000 mm " +
                        "(0.00004 and 39.37 inches)."
            )
            return false
        }
        return true
    }

    private fun validateFeedRate(
        feedRate: Double,
        units: MachineUnits
    ): Boolean {
        if (!feedRate.isFinite() || feedRate <= 0.0) {
            onMessageChanged("Feed rate must be greater than zero.")
            return false
        }

        // Compare in the controller's internal mm/min units. The inch slider
        // endpoint is a Float, so its Double conversion can exceed the exact
        // 2000/25.4 boundary by a few millionths.
        val feedRateMmMin = toMillimeters(feedRate, units)
        if (feedRateMmMin > MAX_FEED_MM_MIN + 0.001) {
            val maximum = if (units == MachineUnits.MILLIMETERS) {
                MAX_FEED_MM_MIN
            } else {
                MAX_FEED_MM_MIN / MM_PER_INCH
            }
            onMessageChanged(
                "Feed rate exceeds the pendant limit of " +
                        "${formatNumber(maximum)} ${unitName(units)}/min."
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
            markControlUncertain(
                "Could not request pendant control. Check controller mode."
            )
            return
        }

        startTimeout(4000) {
            markControlUncertain(
                "Controller did not confirm pendant control. " +
                        "Check controller mode before continuing."
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

        // A confirmed MPG:0 report clears the uncertainty lockout.
        if (currentStatus.pendantInControl == false) {
            controlStateUncertain = false
        }

        when (stage) {
            Stage.WAITING_FOR_INITIAL_LISTENING -> {
                if (currentStatus.pendantInControl == false) {
                    cancelTimeout()
                    stage = Stage.WAITING_FOR_INITIAL_CONTROL
                    if (!bluetooth.sendRealtime(MPG_TOGGLE.toChar())) {
                        markControlUncertain("Could not temporarily enable the protocol stream.")
                        return
                    }
                    startTimeout(4000) {
                        markControlUncertain("Controller did not confirm protocol-stream mode.")
                    }
                }
            }

            Stage.WAITING_FOR_INITIAL_CONTROL -> {
                if (currentStatus.pendantInControl == true) {
                    cancelTimeout()
                    stage = Stage.WAITING_FOR_INITIAL_MODAL
                    if (!bluetooth.sendCommand("\$G")) {
                        releaseControl("Could not request controller modal state.")
                        return
                    }
                    startTimeout(4000) {
                        releaseControl("Timed out waiting for controller modal state.")
                    }
                }
            }

            Stage.WAITING_FOR_CONTROL -> {
                if (currentStatus.pendantInControl == true) {
                    cancelTimeout()
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
                        releaseControl("Homing cycle completed.")
                    }

                    "Alarm" -> {
                        releaseControl(
                            "Homing ended in an alarm state. " +
                                    "Check the controller."
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

        if (stage == Stage.WAITING_FOR_INITIAL_MODAL &&
            currentStatus.position.modalStateKnown
        ) {
            releaseControl("Controller units synchronized.")
            return
        }

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
    // Send coordinate / homing command
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
                        "Pendant has control. Setting $wcs $axis " +
                                "to $formattedValue..."
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

    private fun toMillimeters(value: Double, units: MachineUnits): Double =
        if (units == MachineUnits.MILLIMETERS) value else value * MM_PER_INCH

    private fun sendRuntimeSetting(settingId: Int, value: Float): Boolean {
        if (!value.isFinite() || value <= 0f) return false

        val bits = java.lang.Float.floatToIntBits(value)
        val packet = byteArrayOf(
            0xF0.toByte(),
            0x01,
            settingId.toByte(),
            bits.toByte(),
            (bits ushr 8).toByte(),
            (bits ushr 16).toByte(),
            (bits ushr 24).toByte(),
            0
        )

        var crc = 0
        for (index in 1..6) {
            crc = crc xor (packet[index].toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 0x80) != 0) {
                    ((crc shl 1) xor 0x07) and 0xFF
                } else {
                    (crc shl 1) and 0xFF
                }
            }
        }
        packet[7] = crc.toByte()
        return bluetooth.sendBytes(packet)
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

                startTimeout(120000) {
                    releaseControl(
                        "Homing was accepted, but its final state " +
                                "could not be confirmed."
                    )
                }
            }

            else -> Unit
        }
    }

    // ------------------------------------------------
    // Verify requested work coordinate
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
            markControlUncertain(
                "$message Controller control state could not be confirmed."
            )
            return
        }

        stage = Stage.WAITING_FOR_RELEASE
        continuousJogActive = false

        onMessageChanged("Releasing pendant control...")

        if (!bluetooth.sendRealtime(MPG_TOGGLE.toChar())) {
            markControlUncertain(
                "$message Failed to send the release request. " +
                        "Check controller mode."
            )
            return
        }

        startTimeout(4000) {
            markControlUncertain(
                "$message Could not confirm return to listening mode. " +
                        "Check controller before using UGS."
            )
        }
    }

    // ------------------------------------------------
    // Controller errors
    // ------------------------------------------------

    private fun handleCommandError(error: String) {
        if (stage == Stage.IDLE) return

        if (currentStatus.pendantInControl == true) {
            releaseControl(
                "Controller rejected the command ($error)."
            )
        } else {
            markControlUncertain(
                "Controller rejected the command ($error). " +
                        "Verify controller mode."
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
    // Finish / uncertain state
    // ------------------------------------------------

    private fun finish(message: String) {
        cancelTimeout()
        clearOperationState()
        stage = Stage.IDLE

        onBusyChanged(false)
        onMessageChanged(message)
    }

    private fun markControlUncertain(message: String) {
        cancelTimeout()

        controlStateUncertain = true
        continuousJogActive = false

        clearOperationState()
        stage = Stage.IDLE

        onBusyChanged(false)
        onMessageChanged(message)
    }

    private fun clearOperationState() {
        requestedWcs = null
        requestedAxis = null
        requestedValue = null
        operation = null
        continuousJogActive = false
    }

    // ------------------------------------------------
    // Formatting helpers
    // ------------------------------------------------

    private fun formatNumber(value: Double): String {
        return BigDecimal.valueOf(value)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun unitName(units: MachineUnits): String {
        return if (units == MachineUnits.MILLIMETERS) "mm" else "in"
    }

    // ------------------------------------------------
    // Resource cleanup
    // ------------------------------------------------

    fun releaseResources() {
        cancelTimeout()

        // Best-effort cleanup if the app is destroyed during a jog or
        // while a coordinate/homing operation has MPG control.
        if (continuousJogActive) bluetooth.sendRealtime(JOG_CANCEL.toChar())

        if (currentStatus.pendantInControl == true) {
            bluetooth.sendRealtime(MPG_TOGGLE.toChar())
        }

        clearOperationState()
        stage = Stage.IDLE

        handler.removeCallbacksAndMessages(null)
    }
}
