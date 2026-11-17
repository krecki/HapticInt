
package com.kreckapps.hapticinterface.communication

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.kreckapps.hapticinterface.model.MachineStatus
import com.kreckapps.hapticinterface.model.MachineUnits
import java.math.BigDecimal
import kotlin.math.abs
import java.util.ArrayDeque

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
        WAITING_FOR_JOG_IDLE,
        WAITING_FOR_RELEASE
    }

    private enum class Operation {
        SELECT_WCS,
        ZERO_AXIS,
        SET_AXIS,
        HOME_MACHINE,
        JOG
    }
    private data class JogRequest(
        val axis: Char,
        val distance: Double,
        val negative: Boolean,
        val feedRate: Double,
        val units: MachineUnits
    )

    private val pendingJogRequests = ArrayDeque<JogRequest>()

    companion object {
        const val MAX_PENDING_JOG_REQUESTS = 64
        private val VALID_WCS = listOf(
            "G54", "G55", "G56",
            "G57", "G58", "G59"
        )

        private val VALID_AXES = listOf('X', 'Y', 'Z')

        private const val MPG_TOGGLE = 0x8B
        private const val JOG_CANCEL = 0x85

        private const val POSITION_TOLERANCE = 0.001

        private const val CONTINUOUS_SEGMENT_MM = 0.5

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

    // Jog state
    private var jogAxis: Char? = null
    private var jogSign = 1.0
    private var jogDistance = 0.0
    private var jogFeedRate = 0.0
    private var jogUnits = MachineUnits.MILLIMETERS

    private var continuousJog = false
    private var continuousJogActive = false
    private var jogStopRequested = false

    // Prevents a single early Idle report from being treated as
    // confirmation that a jog has finished.
    private var idleConfirmationPending = false

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
    // Incremental jog
    // ------------------------------------------------

    /**
     * Sends one incremental jog.
     *
     * distance and feedRate use the selected units:
     * mm and mm/min, or inches and inches/min.
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

        if (!distance.isFinite() || distance == 0.0) {
            onMessageChanged("Jog distance must be a nonzero number.")
            return
        }

        if (!validateJogDistance(distance, units)) return
        if (!validateFeedRate(feedRate, units)) return

        val request = JogRequest(
            axis = normalizedAxis,
            distance = abs(distance),
            negative = distance < 0.0,
            feedRate = feedRate,
            units = units
        )

        // Queue repeated keypad jog requests while a previous move is active.
        if (stage != Stage.IDLE) {
            val canQueue =
                operation == Operation.JOG &&
                        !continuousJog &&
                        !jogStopRequested &&
                        stage == Stage.WAITING_FOR_JOG_IDLE

            if (!canQueue) {
                onMessageChanged("Another pendant operation is in progress.")
                return
            }

            if (pendingJogRequests.size >= MAX_PENDING_JOG_REQUESTS) {
                onMessageChanged("Jog queue is full. Release the jog control and try again.")
                return
            }

            pendingJogRequests.addLast(request)
            onMessageChanged(
                "Jog queued: ${if (distance < 0.0) "-" else "+"}$normalizedAxis. " +
                        "${pendingJogRequests.size} movement(s) waiting."
            )
            return
        }

        if (!canStartOperation()) return

        operation = Operation.JOG
        continuousJog = false
        continuousJogActive = false
        jogStopRequested = false
        idleConfirmationPending = false

        pendingJogRequests.clear()

        loadJogRequest(request)

        // Keep MPG mode off so UART2 remains connected to the keypad plugin.
        onBusyChanged(true)
        onMessageChanged("Sending incremental keypad jog...")
        sendJogSegment()
    }

    private fun loadJogRequest(request: JogRequest) {
        jogAxis = request.axis
        jogSign = if (request.negative) -1.0 else 1.0
        jogDistance = request.distance
        jogFeedRate = request.feedRate
        jogUnits = request.units
    }

    private fun startNextQueuedIncrement() {
        val next = pendingJogRequests.pollFirst()

        if (next == null) {
            finish("Incremental jog completed.")
            return
        }

        loadJogRequest(next)

        onMessageChanged(
            "Sending next queued ${next.axis} jog. " +
                    "${pendingJogRequests.size} movement(s) remaining."
        )

        sendJogSegment()
    }

    // ------------------------------------------------
    // Continuous jog
    // ------------------------------------------------

    /**
     * Starts repeated bounded jog segments.
     *
     * direction examples: "+X", "-X", "+Y", "-Y", "+Z", "-Z".
     *
     * Each segment is 0.5 mm, or its equivalent in inches.
     */
    fun startContinuousJog(
        direction: String,
        feedRate: Double,
        units: MachineUnits
    ) {
        val normalized = direction.trim().uppercase()

        if (
            normalized.length != 2 ||
            (normalized[0] != '+' && normalized[0] != '-') ||
            normalized[1] !in VALID_AXES
        ) {
            onMessageChanged("Invalid jog direction.")
            return
        }

        if (!validateFeedRate(feedRate, units)) return
        if (!canStartOperation()) return

        // Direction is formatted as sign first, axis second.
        jogAxis = normalized[1]
        jogSign = if (normalized[0] == '+') 1.0 else -1.0

        jogDistance = if (units == MachineUnits.MILLIMETERS) {
            CONTINUOUS_SEGMENT_MM
        } else {
            CONTINUOUS_SEGMENT_MM / MM_PER_INCH
        }

        jogFeedRate = feedRate
        jogUnits = units

        operation = Operation.JOG
        continuousJog = true
        continuousJogActive = true
        jogStopRequested = false
        idleConfirmationPending = false

        // Keep MPG mode off so UART2 remains connected to the keypad plugin.
        onBusyChanged(true)
        onMessageChanged("Starting keypad continuous jog...")
        sendJogSegment()
    }

    /**
     * Stops continuous jogging or cancels an incremental jog
     * that is currently in progress.
     */
    fun stopJog() {
        if (operation != Operation.JOG) return

        continuousJogActive = false
        jogStopRequested = true

        when (stage) {
            Stage.WAITING_FOR_JOG_IDLE -> {
                requestJogCancellation("Jog stopped.")
            }

            else -> Unit
        }
    }

    /**
     * Sends the real-time jog-cancel byte and waits for an Idle report.
     * It deliberately does not release MPG control until the jog has
     * stopped or the controller state is declared uncertain.
     */
    private fun requestJogCancellation(message: String) {
        cancelTimeout()

        continuousJogActive = false
        jogStopRequested = true
        idleConfirmationPending = false

        if (currentStatus.pendantInControl != false) {
            markControlUncertain(
                "$message Keypad mode is not confirmed. " +
                        "Check the controller."
            )
            return
        }

        stage = Stage.WAITING_FOR_JOG_IDLE
        onMessageChanged("Cancelling jog...")

        if (!bluetooth.sendRealtime(JOG_CANCEL.toChar())) {
            markControlUncertain(
                "Could not send jog cancel. Check the controller."
            )
            return
        }

        bluetooth.sendRealtime('?')

        startTimeout(5000) {
            markControlUncertain(
                "Jog cancellation could not be confirmed. " +
                        "Check the controller before continuing."
            )
        }
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

        val maximum = if (units == MachineUnits.MILLIMETERS) {
            MAX_FEED_MM_MIN
        } else {
            MAX_FEED_MM_MIN / MM_PER_INCH
        }

        if (feedRate > maximum) {
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

            Stage.WAITING_FOR_JOG_IDLE -> {
                handleJogStatus()
            }

            Stage.WAITING_FOR_RELEASE -> {
                if (currentStatus.pendantInControl == false) {
                    finish(releaseMessage)
                }
            }

            else -> Unit
        }
    }

    private fun handleJogStatus() {
        if (currentStatus.pendantInControl != false) {
            markControlUncertain(
                "Keypad input mode was lost during jogging. " +
                        "Check the controller."
            )
            return
        }

        when (currentStatus.machineState) {
            "Alarm" -> {
                finish("Jog ended in an alarm state. Check the controller.")
            }

            "Idle" -> {
                // Require a second Idle observation after a fresh status
                // request. This helps avoid treating an early status report
                // as proof that a newly accepted jog has already finished.
                if (!idleConfirmationPending) {
                    idleConfirmationPending = true

                    handler.postDelayed({
                        if (stage == Stage.WAITING_FOR_JOG_IDLE) {
                            bluetooth.sendRealtime('?')
                        }
                    }, 100)

                    return
                }

                idleConfirmationPending = false
                cancelTimeout()

                if (jogStopRequested || (continuousJog && !continuousJogActive)) {
                    finish("Jog stopped.")
                } else if (continuousJog) {
                    // Only send the next bounded segment after the previous
                    // one has positively returned to Idle.
                    sendJogSegment()
                } else {
                    // Process the next MPG detent or queued incremental movement.
                    startNextQueuedIncrement()
                }
            }

            else -> {
                // The machine is still moving or changing state.
                idleConfirmationPending = false
            }
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

    // ------------------------------------------------
    // Send one bounded jog segment
    // ------------------------------------------------

    private fun sendJogSegment() {
        if (
            jogStopRequested ||
            (continuousJog && !continuousJogActive)
        ) {
            releaseControl("Jog cancelled.")
            return
        }

        if (currentStatus.pendantInControl != false) {
            markControlUncertain(
                "Cannot jog because keypad mode is not confirmed. " +
                        "MPG mode must be off."
            )
            return
        }

        val axis = jogAxis ?: run {
            releaseControl("No jog axis was selected.")
            return
        }

        val speedMmMin = toMillimeters(jogFeedRate, jogUnits).toFloat()
        val distanceMm = toMillimeters(jogDistance, jogUnits).toFloat()
        val settingIdSpeed = if (continuousJog) 3 else 1 // $52 or $50
        val settingIdDistance = if (continuousJog) 6 else 4 // $55 or $53

        val speedCached = if (continuousJog) runtimeFastSpeedMmMin else runtimeStepSpeedMmMin
        val distanceCached = if (continuousJog) runtimeFastDistanceMm else runtimeStepDistanceMm

        var settingPacketSent = false

        if (speedCached == null || speedCached != speedMmMin) {
            if (!sendRuntimeSetting(settingIdSpeed, speedMmMin)) {
                requestJogCancellation("Could not update the keypad jog speed.")
                return
            }
            if (continuousJog) runtimeFastSpeedMmMin = speedMmMin
            else runtimeStepSpeedMmMin = speedMmMin
            settingPacketSent = true
        }

        if (distanceCached == null || distanceCached != distanceMm) {
            // The firmware applies one packet at a time. Leave a short gap so
            // its foreground task can consume the previous update first.
            if (settingPacketSent) SystemClock.sleep(20)
            if (!sendRuntimeSetting(settingIdDistance, distanceMm)) {
                requestJogCancellation("Could not update the keypad jog distance.")
                return
            }
            if (continuousJog) runtimeFastDistanceMm = distanceMm
            else runtimeStepDistanceMm = distanceMm
            settingPacketSent = true
        }

        if (settingPacketSent) SystemClock.sleep(20)

        // Keypad mode 0 = Fast; mode 2 = Step.
        val mode = if (continuousJog) '0' else '2'
        val direction = when (axis) {
            'X' -> if (jogSign < 0.0) 'L' else 'R'
            'Y' -> if (jogSign < 0.0) 'B' else 'F'
            else -> if (jogSign < 0.0) 'D' else 'U'
        }

        stage = Stage.WAITING_FOR_JOG_IDLE
        idleConfirmationPending = false

        onMessageChanged(
            if (continuousJog) {
                "Jogging $axis in short bounded segments..."
            } else {
                "Sending incremental $axis jog..."
            }
        )

        if (!bluetooth.sendRealtime(mode) ||
            !bluetooth.sendRealtime(direction)
        ) {
            requestJogCancellation("Failed to send keypad jog command.")
            return
        }

        // Keypad direction bytes do not return an 'ok'. Completion is
        // determined from grblHAL status reports instead.
        if (!bluetooth.sendRealtime('?')) {
            requestJogCancellation("Could not verify keypad jog status.")
            return
        }

        startTimeout(5000) {
            requestJogCancellation("Timed out waiting for keypad jog completion.")
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

            Operation.JOG -> {
                cancelTimeout()

                if (continuousJog) {
                    // Keep feeding the controller while the jog button is held.
                    // Do not wait for the previous segment to reach Idle.
                    if (continuousJogActive && !jogStopRequested) {
                        sendJogSegment()
                    } else {
                        requestJogCancellation("Jog stopped.")
                    }
                } else {
                    // Incremental jogs still wait for confirmed completion.
                    stage = Stage.WAITING_FOR_JOG_IDLE
                    idleConfirmationPending = false

                    onMessageChanged("Jog accepted. Waiting for completion...")

                    if (!bluetooth.sendRealtime('?')) {
                        requestJogCancellation(
                            "Could not verify jog completion."
                        )
                        return
                    }

                    startTimeout(5000) {
                        requestJogCancellation(
                            "Timed out waiting for jog completion."
                        )
                    }
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

        if (operation == Operation.JOG) {
            requestJogCancellation(
                "Controller rejected the jog command ($error)."
            )
            return
        }

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

        jogAxis = null
        jogDistance = 0.0
        jogFeedRate = 0.0
        continuousJog = false
        continuousJogActive = false
        jogStopRequested = false
        idleConfirmationPending = false
        pendingJogRequests.clear()
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

        // Best-effort cleanup if the app is being destroyed while
        // the controller is still in pendant mode.
        if (currentStatus.pendantInControl == true) {
            if (operation == Operation.JOG) {
                bluetooth.sendRealtime(JOG_CANCEL.toChar())
            }

            bluetooth.sendRealtime(MPG_TOGGLE.toChar())
        }

        clearOperationState()
        stage = Stage.IDLE

        handler.removeCallbacksAndMessages(null)
    }
}
