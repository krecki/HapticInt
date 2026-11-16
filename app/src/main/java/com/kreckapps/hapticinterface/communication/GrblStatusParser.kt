
package com.kreckapps.hapticinterface.communication

import com.kreckapps.hapticinterface.model.MachinePosition
import com.kreckapps.hapticinterface.model.MachineStatus
import com.kreckapps.hapticinterface.model.MachineUnits

object GrblStatusParser {

    /**
     * Parses a grblHAL real-time status report.
     *
     * Example:
     * <Idle|MPos:12.345,6.789,2.500|WCO:11.095,4.289,1.750>
     *
     * Returns null if the message is not a valid status report.
     */
    fun parseStatusReport(
        report: String,
        previous: MachineStatus
    ): MachineStatus? {

        val cleaned = report.trim()

        if (!cleaned.startsWith("<") || !cleaned.endsWith(">")) {
            return null
        }

        val contents = cleaned.removePrefix("<").removeSuffix(">")
        val fields = contents.split("|")

        if (fields.isEmpty()) return null

        val machineState = fields[0].trim()

        if (machineState.isEmpty()) return null

        val values = mutableMapOf<String, String>()

        for (field in fields.drop(1)) {
            val separator = field.indexOf(':')

            if (separator > 0) {
                val key = field.substring(0, separator).trim()
                val value = field.substring(separator + 1).trim()
                values[key] = value
            }
        }

        val machineCoordinates = parseCoordinates(values["MPos"])
        val workCoordinates = parseCoordinates(values["WPos"])
        val workCoordinateOffset = parseCoordinates(values["WCO"])

        val oldPosition = previous.position

        var machineX = oldPosition.machineX
        var machineY = oldPosition.machineY
        var machineZ = oldPosition.machineZ

        var workX = oldPosition.workX
        var workY = oldPosition.workY
        var workZ = oldPosition.workZ

        // Update machine coordinates when MPos is available.
        if (machineCoordinates != null) {
            machineX = machineCoordinates[0]
            machineY = machineCoordinates[1]
            machineZ = machineCoordinates[2]
        }

        // Update work coordinates directly when WPos is available.
        if (workCoordinates != null) {
            workX = workCoordinates[0]
            workY = workCoordinates[1]
            workZ = workCoordinates[2]
        }

        // If WPos wasn't reported, calculate it from MPos and WCO.
        if (workCoordinates == null &&
            machineCoordinates != null &&
            workCoordinateOffset != null
        ) {
            workX = machineX - workCoordinateOffset[0]
            workY = machineY - workCoordinateOffset[1]
            workZ = machineZ - workCoordinateOffset[2]
        }

        // If MPos wasn't reported, calculate it from WPos and WCO.
        if (machineCoordinates == null &&
            workCoordinates != null &&
            workCoordinateOffset != null
        ) {
            machineX = workX + workCoordinateOffset[0]
            machineY = workY + workCoordinateOffset[1]
            machineZ = workZ + workCoordinateOffset[2]
        }


        val reportedWcs = values["WCS"]

        val activeWcs = when (reportedWcs) {
            "G54", "G55", "G56", "G57", "G58", "G59" ->
                reportedWcs

            else -> oldPosition.activeWcs
        }

        val newPosition = oldPosition.copy(
            machineX = machineX,
            machineY = machineY,
            machineZ = machineZ,
            workX = workX,
            workY = workY,
            workZ = workZ,
            activeWcs = activeWcs
        )

        val pendantInControl = when (values["MPG"]) {
            "0" -> false
            "1" -> true
            else -> previous.pendantInControl
        }

        return previous.copy(
            machineState = machineState,
            position = newPosition,
            pendantInControl = pendantInControl
        )

    }

    /**
     * Parses the response to the grblHAL $G command.
     *
     * Example:
     * [GC:G0 G54 G17 G21 G90 G94 M5 M9 T0 F0 S0]
     */
    fun parseModalReport(
        report: String,
        previous: MachineStatus
    ): MachineStatus {

        val cleaned = report.trim()

        if (!cleaned.startsWith("[GC:") || !cleaned.endsWith("]")) {
            return previous
        }

        val contents = cleaned
            .removePrefix("[GC:")
            .removeSuffix("]")

        val words = contents.split(Regex("\\s+"))

        var activeWcs = previous.position.activeWcs
        var units = previous.position.units

        for (word in words) {
            when (word) {
                "G54", "G55", "G56", "G57", "G58", "G59" -> {
                    activeWcs = word
                }

                "G20" -> {
                    units = MachineUnits.INCHES
                }

                "G21" -> {
                    units = MachineUnits.MILLIMETERS
                }
            }
        }

        return previous.copy(
            position = previous.position.copy(
                activeWcs = activeWcs,
                units = units
            )
        )
    }

    /**
     * Converts a coordinate string such as
     * "12.345,6.789,2.500" into three Double values.
     */
    private fun parseCoordinates(value: String?): List<Double>? {

        if (value == null) return null

        val coordinates = value.split(",")

        if (coordinates.size != 3) return null

        val parsed = coordinates.map {
            it.trim().toDoubleOrNull() ?: return null
        }

        return parsed
    }
}
