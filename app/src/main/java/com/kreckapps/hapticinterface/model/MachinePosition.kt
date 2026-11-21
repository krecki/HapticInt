package com.kreckapps.hapticinterface.model

enum class CoordinateDisplayMode {
    MACHINE,
    WORK
}

enum class MachineUnits {
    INCHES,
    MILLIMETERS
}

enum class DistanceMode {
    ABSOLUTE,
    INCREMENTAL
}

data class MachinePosition(
    val machineX: Double = 0.0,
    val machineY: Double = 0.0,
    val machineZ: Double = 0.0,

    val workX: Double = 0.0,
    val workY: Double = 0.0,
    val workZ: Double = 0.0,

    // Last known work-coordinate offset, in millimeters. Null until a
    // fresh offset has been received for the active WCS.
    val wcoX: Double? = null,
    val wcoY: Double? = null,
    val wcoZ: Double? = null,

    val activeWcs: String = "G54",

    val units: MachineUnits = MachineUnits.INCHES,

    // Null until the controller reports G90 or G91.
    val distanceMode: DistanceMode? = null,

    // True only after GRBL has reported G20 or G21.
    val modalStateKnown: Boolean = false
)