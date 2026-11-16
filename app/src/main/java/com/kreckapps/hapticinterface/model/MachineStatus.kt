
package com.kreckapps.hapticinterface.model

data class MachineStatus(
    val machineState: String = "Disconnected",
    val connected: Boolean = false,
    val position: MachinePosition = MachinePosition(),

    // null means the controller hasn't reported this information yet.
    val pendantInControl: Boolean? = null
)
