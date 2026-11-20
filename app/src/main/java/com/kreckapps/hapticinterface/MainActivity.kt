
package com.kreckapps.hapticinterface

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kreckapps.hapticinterface.ui.PendantApp
import com.kreckapps.hapticinterface.ui.PendantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the pendant display awake while this activity is in the foreground.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            PendantTheme {
                PendantApp()
            }
        }
    }
}
