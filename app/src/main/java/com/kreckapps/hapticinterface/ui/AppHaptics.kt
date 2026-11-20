package com.kreckapps.hapticinterface.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Requests a short vibration directly from Android rather than using Compose's
 * system touch-feedback effect. This keeps pendant haptics separate from
 * navigation-bar and general touch-feedback settings on supported devices.
 */
fun performAppHaptic(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        ?: return

    if (vibrator.hasVibrator()) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                18L,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }
}
