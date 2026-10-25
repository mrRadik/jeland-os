package ru.jeland.os

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.jeland.os.ui.JelandApp
import ru.jeland.os.ui.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onResume(){super.onResume();ru.jeland.os.data.StartupVisibility.resumed=true}
    override fun onPause(){ru.jeland.os.data.StartupVisibility.resumed=false;super.onPause()}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowInsetsControllerCompat(window,window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val container=(application as JelandApplication).container
        setContent { val model: MainViewModel=viewModel(factory=MainViewModel.Factory(container)); JelandApp(model) }
    }
}
