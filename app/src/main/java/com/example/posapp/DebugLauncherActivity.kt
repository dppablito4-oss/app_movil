package com.example.posapp

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class DebugLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "SpaceSale - Debug Launcher\nLa app arranca correctamente. Pulsa atrás para cerrar."
            textSize = 18f
            setPadding(40, 40, 40, 40)
        }
        setContentView(tv)
    }
}
