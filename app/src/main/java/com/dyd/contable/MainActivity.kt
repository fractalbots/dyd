package com.dyd.contable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dyd.contable.ui.AppRoot
import com.dyd.contable.ui.theme.DydTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DydTheme {
                AppRoot()
            }
        }
    }
}
