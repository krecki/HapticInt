package com.kreckapps.hapticinterface.ui

import android.content.Context
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Short, crisp app haptics. MPG detents are rate-limited so rapid wheel turns
 * do not become one continuous-feeling vibration.
 */
private object AppHapticLimiter {
    private var lastMpgPulseAt = 0L

    @Synchronized
    fun allowMpgPulse(now: Long): Boolean {
        if (now - lastMpgPulseAt < 22L) return false
        lastMpgPulseAt = now
        return true
    }
}

fun performAppHaptic(context: Context, mpgDetent: Boolean = false) {
    val now = SystemClock.elapsedRealtime()
    if (mpgDetent && !AppHapticLimiter.allowMpgPulse(now)) return

    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        ?: return

    if (vibrator.hasVibrator()) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                if (mpgDetent) 22L else 40L,
                255
            )
        )
    }
}
