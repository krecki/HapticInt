
package com.kreckapps.hapticinterface

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kreckapps.hapticinterface.ui.PendantApp
import com.kreckapps.hapticinterface.ui.PendantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PendantTheme {
                PendantApp()
            }
        }
    }
}
