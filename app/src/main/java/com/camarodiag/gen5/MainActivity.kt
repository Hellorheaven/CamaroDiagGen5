package com.camarodiag.gen5

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.camarodiag.gen5.ui.CamaroDiagApp
import com.camarodiag.gen5.ui.MainViewModel
import com.camarodiag.gen5.ui.theme.CamaroDiagTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CamaroDiagTheme {
                val vm: MainViewModel = viewModel()
                CamaroDiagApp(viewModel = vm)
            }
        }
    }
}
