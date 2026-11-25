package com.lumiposture.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumiposture.app.data.local.prefs.UserCalibrationDataStore
import com.lumiposture.app.presentation.navigation.LumiNavGraph
import com.lumiposture.app.presentation.theme.LumiPostureTheme
import com.lumiposture.app.presentation.theme.LumiTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userDataStore: UserCalibrationDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LumiPostureTheme {
                val isCalibratedFlow = remember(userDataStore.preferencesFlow) {
                    userDataStore.preferencesFlow.map { it.isCalibrated }
                }
                val isCalibratedState by isCalibratedFlow.collectAsStateWithLifecycle(initialValue = null)

                val calibrated = isCalibratedState
                if (calibrated == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LumiTheme.colors.canvas),
                    )
                } else {
                    LumiNavGraph(isCalibrated = calibrated)
                }
            }
        }
    }
}
