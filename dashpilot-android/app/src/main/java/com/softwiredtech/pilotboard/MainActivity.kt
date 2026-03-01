package com.softwiredtech.pilotboard

import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.startup.AppInitializer
import app.rive.runtime.kotlin.RiveInitializer
import com.softwiredtech.pilotboard.datasource.CommaDataSource
import com.softwiredtech.pilotboard.datasource.IDataSource
import com.softwiredtech.pilotboard.ui.RiveDashView
import com.softwiredtech.pilotboard.ui.WebDashView
import com.softwiredtech.pilotboard.ui.theme.PilotBoardTheme
import com.softwiredtech.pilotboard.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val dashboardServerAddress = "http://192.168.1.105:3000"

class MainActivity : ComponentActivity() {

    private lateinit var dataSource: IDataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppInitializer.getInstance(applicationContext)
            .initializeComponent(RiveInitializer::class.java)

        lifecycleScope.launch(Dispatchers.IO) {
            dataSource =
                CommaDataSource(FileUtils.copyAssetToFile(this@MainActivity, "tesla_model3_party.dbc"))
            dataSource.connect("192.168.1.105")
        }

        // Enable edge-to-edge for Compose
        enableEdgeToEdge()
        hideSystemBars()

        setContent {
            // TODO : Handle switching between the two
            /*RiveDashView(dataSource, lifecycleScope, { error ->
                Log.d("Rive", error)
            })*/

            PilotBoardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    WebDashView(
                        modifier = Modifier.fillMaxSize(),
                        dashboardServerAddress,
                        lifecycleScope,
                        dataSource
                    )
                }
            }
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // fallback for pre-R devices
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
        }

        // Keep screen on
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // Re-apply when focus changes
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }
}
