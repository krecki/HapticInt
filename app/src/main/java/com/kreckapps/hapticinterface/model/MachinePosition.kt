
package com.kreckapps.hapticinterface.model

enum class CoordinateDisplayMode {
    MACHINE,
    WORK
}

enum class MachineUnits {
    INCHES,
    MILLIMETERS
}

data class MachinePosition(
    val machineX: Double = 0.0,
    val machineY: Double = 0.0,
    val machineZ: Double = 0.0,

    val workX: Double = 0.0,
    val workY: Double = 0.0,
    val workZ: Double = 0.0,

    val activeWcs: String = "G54",
    val units: MachineUnits = MachineUnits.INCHES
)
