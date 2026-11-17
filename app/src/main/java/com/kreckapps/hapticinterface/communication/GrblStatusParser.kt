package com.kreckapps.hapticinterface.communication

import com.kreckapps.hapticinterface.model.MachinePosition
import com.kreckapps.hapticinterface.model.MachineStatus
import com.kreckapps.hapticinterface.model.MachineUnits

object GrblStatusParser {

    private val validWcs = setOf(
        "G54", "G55", "G56",
        "G57", "G58", "G59"
    )

    fun parseStatusReport(
        report: String,
        previous: MachineStatus
    ): MachineStatus? {

        val trimmed = report.trim()

        if (!trimmed.startsWith("<") || !trimmed.endsWith(">")) {
            return null
        }

        val contents = trimmed
            .removePrefix("<")
            .removeSuffix(">")

        val fields = contents.split("|")

        if (fields.isEmpty()) return null

        val machineState = fields[0].trim()

        if (machineState.isBlank()) return null

        val values = mutableMapOf<String, String>()

        for (field in fields.drop(1)) {
            val separator = field.indexOf(':')

            if (separator > 0) {
                val key = field.substring(0, separator).trim()
                val value = field.substring(separator + 1).trim()

                values[key] = value
            }
        }

        val oldPosition = previous.position

        val machinePosition = parseCoordinates(values["MPos"])
        val workPosition = parseCoordinates(values["WPos"])
        val workCoordinateOffset = parseCoordinates(values["WCO"])

        var machineX = oldPosition.machineX
        var machineY = oldPosition.machineY
        var machineZ = oldPosition.machineZ

        var workX = oldPosition.workX
        var workY = oldPosition.workY
        var workZ = oldPosition.workZ

        if (machinePosition != null) {
            machineX = machinePosition[0]
            machineY = machinePosition[1]
            machineZ = machinePosition[2]
        }

        if (workPosition != null) {
            workX = workPosition[0]
            workY = workPosition[1]
            workZ = workPosition[2]
        }

        if (workCoordinateOffset != null) {

            if (machinePosition != null && workPosition == null) {
                workX = machineX - workCoordinateOffset[0]
                workY = machineY - workCoordinateOffset[1]
                workZ = machineZ - workCoordinateOffset[2]
            }

            if (workPosition != null && machinePosition == null) {
                machineX = workX + workCoordinateOffset[0]
                machineY = workY + workCoordinateOffset[1]
                machineZ = workZ + workCoordinateOffset[2]
            }
        }

        val reportedWcs = values["WCS"]

        val activeWcs = if (reportedWcs in validWcs) {
            reportedWcs!!
        } else {
            oldPosition.activeWcs
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
            "1" -> true
            "0" -> false
            else -> previous.pendantInControl
        }

        return previous.copy(
            machineState = machineState,
            position = newPosition,
            pendantInControl = pendantInControl
        )
    }

    fun parseModalReport(
        report: String,
        previous: MachineStatus
    ): MachineStatus {

        val trimmed = report.trim()

        if (!trimmed.startsWith("[GC:") || !trimmed.endsWith("]")) {
            return previous
        }

        val contents = trimmed
            .removePrefix("[GC:")
            .removeSuffix("]")

        val words = contents
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        var activeWcs = previous.position.activeWcs
        var units = previous.position.units
        var unitsReported = false

        for (word in words) {
            when (word) {
                in validWcs -> {
                    activeWcs = word
                }

                "G20" -> {
                    units = MachineUnits.INCHES
                    unitsReported = true
                }

                "G21" -> {
                    units = MachineUnits.MILLIMETERS
                    unitsReported = true
                }
            }
        }

        val newPosition = previous.position.copy(
            activeWcs = activeWcs,
            units = units,
            modalStateKnown =
            previous.position.modalStateKnown || unitsReported
        )

        return previous.copy(
            position = newPosition
        )
    }

    private fun parseCoordinates(
        value: String?
    ): DoubleArray? {

        if (value == null) return null

        val coordinates = value.split(",")

        if (coordinates.size != 3) return null

        val parsed = coordinates.map {
            it.trim().toDoubleOrNull()
        }

        if (parsed.any { it == null || !it.isFinite() }) {
            return null
        }

        return doubleArrayOf(
            parsed[0]!!,
            parsed[1]!!,
            parsed[2]!!
        )
    }
}