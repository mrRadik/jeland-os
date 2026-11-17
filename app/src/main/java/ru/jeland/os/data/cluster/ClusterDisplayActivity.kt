package ru.jeland.os.data.cluster

import android.graphics.PixelFormat
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ru.jeland.os.JelandApplication
import ru.jeland.os.core.*
import ru.jeland.os.localization.AppLocalization
import ru.jeland.os.ui.MainViewModel
import ru.jeland.os.ui.cluster.ClusterStage
import ru.jeland.os.ui.cluster.rememberClusterPanelSnapshot
import ru.jeland.os.ui.theme.JelandTheme
import java.util.Date

/** Independent task on the OEM secondary display; no overlay or separate telemetry connection. */
class ClusterDisplayActivity:ComponentActivity(),ClusterOutputHost {
    override val closing get()=isFinishing||isDestroyed
    override val clusterDisplayId get()=display?.displayId
    override fun rebuildOutput(){recreate()}
    override fun finishOutput(){finish()}
    private val dependencies get()=(application as JelandApplication).container
    private lateinit var model:MainViewModel
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setFormat(PixelFormat.TRANSLUCENT)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility=5894
        if(dependencies.clusterProjection.applied.value==null){finish();return}
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dependencies.clusterProjection.restoreStock()
            }
        })
        setContent {
            val output by dependencies.clusterProjection.applied.collectAsStateWithLifecycle()
            val selected=output
            if(selected==null){LaunchedEffect(Unit){finish()};return@setContent}
            val vm:MainViewModel=viewModel(factory=MainViewModel.Factory(dependencies))
            SideEffect {model=vm}
            val state by vm.state.collectAsStateWithLifecycle()
            val trips by dependencies.trips.state.collectAsStateWithLifecycle()
            val language by dependencies.firstRun.state.collectAsStateWithLifecycle()
            var elapsed by remember {mutableLongStateOf(SystemClock.elapsedRealtime())}
            var clock by remember {mutableStateOf("")}
            LaunchedEffect(Unit) {
                while(true) {
                    elapsed=SystemClock.elapsedRealtime()
                    clock=android.text.format.DateFormat.getTimeFormat(this@ClusterDisplayActivity).format(Date())
                    delay(250)
                }
            }
            DisposableEffect(vm) {vm.resume();onDispose {vm.pause()}}
            val panel=rememberClusterPanelSnapshot(state,trips.current,trips.speed,elapsed,selected.tripPeriod)
            AppLocalization(language.language.tag) {JelandTheme(state.dark,state.preferences.style) {
                BoxWithConstraints(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                    val width=minOf(maxWidth,maxHeight*(1920f/720f))
                    ClusterStage(panel,state.media,selected.design,state.dark,clock,vm,
                        Modifier.width(width).aspectRatio(1920f/720f).align(Alignment.Center),
                        selected.accent,projection=true,colorMode=selected.colorMode)
                }
            }}
        }
        dependencies.clusterProjection.attached(this)
        dependencies.car.recordIntegration("CLUSTER_ACTIVITY_CREATED display=${display?.displayId}")
    }
    override fun onConfigurationChanged(newConfig:android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        dependencies.car.recordIntegration("CLUSTER_CONFIGURATION_CHANGED uiMode=${newConfig.uiMode}; scene retained")
    }
    override fun onResume(){super.onResume();if(::model.isInitialized)model.resume();dependencies.clusterProjection.resumed(this)}
    override fun onPause(){if(::model.isInitialized)model.pause();super.onPause()}
    override fun onStop(){dependencies.clusterProjection.stopped(this);super.onStop()}
    override fun onDestroy(){dependencies.clusterProjection.detached(this);super.onDestroy()}
}
