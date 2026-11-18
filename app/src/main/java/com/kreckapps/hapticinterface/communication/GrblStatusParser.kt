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
        if (!trimmed.startsWith("<") || !trimmed.endsWith(">")) return null

        val contents = trimmed.removePrefix("<").removeSuffix(">")
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

        val old = previous.position
        val machinePosition = parseCoordinates(values["MPos"])
        val workPosition = parseCoordinates(values["WPos"])
        val reportedOffset = parseCoordinates(values["WCO"])

        val reportedWcs = values["WCS"]?.takeIf { it in validWcs }
        val activeWcs = reportedWcs ?: old.activeWcs
        val wcsChanged = activeWcs != old.activeWcs

        // Never apply an offset from a different work coordinate system.
        var offsetX = if (wcsChanged) null else old.wcoX
        var offsetY = if (wcsChanged) null else old.wcoY
        var offsetZ = if (wcsChanged) null else old.wcoZ

        if (reportedOffset != null) {
            offsetX = reportedOffset[0]
            offsetY = reportedOffset[1]
            offsetZ = reportedOffset[2]
        } else if (machinePosition != null && workPosition != null) {
            // MPos and WPos together also provide a valid offset.
            offsetX = machinePosition[0] - workPosition[0]
            offsetY = machinePosition[1] - workPosition[1]
            offsetZ = machinePosition[2] - workPosition[2]
        }

        var machineX = old.machineX
        var machineY = old.machineY
        var machineZ = old.machineZ
        var workX = old.workX
        var workY = old.workY
        var workZ = old.workZ

        if (machinePosition != null) {
            machineX = machinePosition[0]
            machineY = machinePosition[1]
            machineZ = machinePosition[2]

            // Recalculate WPos on every MPos update using the last known WCO.
            if (offsetX != null && offsetY != null && offsetZ != null) {
                workX = machineX - offsetX
                workY = machineY - offsetY
                workZ = machineZ - offsetZ
            }
        }

        if (workPosition != null) {
            workX = workPosition[0]
            workY = workPosition[1]
            workZ = workPosition[2]

            if (machinePosition == null &&
                offsetX != null && offsetY != null && offsetZ != null
            ) {
                machineX = workX + offsetX
                machineY = workY + offsetY
                machineZ = workZ + offsetZ
            }
        }

        val newPosition = old.copy(
            machineX = machineX,
            machineY = machineY,
            machineZ = machineZ,
            workX = workX,
            workY = workY,
            workZ = workZ,
            wcoX = offsetX,
            wcoY = offsetY,
            wcoZ = offsetZ,
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

        val contents = trimmed.removePrefix("[GC:").removeSuffix("]")
        val words = contents.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        var activeWcs = previous.position.activeWcs
        var units = previous.position.units
        var unitsReported = false

        for (word in words) {
            when (word) {
                in validWcs -> activeWcs = word
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

        val wcsChanged = activeWcs != previous.position.activeWcs
        val newPosition = previous.position.copy(
            activeWcs = activeWcs,
            units = units,
            modalStateKnown = previous.position.modalStateKnown || unitsReported,
            // A modal report can reveal a WCS change before the next status
            // report. Discard the old offset until the new WCO arrives.
            wcoX = if (wcsChanged) null else previous.position.wcoX,
            wcoY = if (wcsChanged) null else previous.position.wcoY,
            wcoZ = if (wcsChanged) null else previous.position.wcoZ
        )

        return previous.copy(position = newPosition)
    }

    private fun parseCoordinates(value: String?): DoubleArray? {
        if (value == null) return null
        val coordinates = value.split(",")
        if (coordinates.size != 3) return null
        val parsed = coordinates.map { it.trim().toDoubleOrNull() }
        if (parsed.any { it == null || !it.isFinite() }) return null
        return doubleArrayOf(parsed[0]!!, parsed[1]!!, parsed[2]!!)
    }
}