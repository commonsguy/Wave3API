package com.commonsware.wave3api

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.commonsware.wave3api.ui.theme.Wave3APITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            Wave3APITheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Wave3(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun Wave3(modifier: Modifier = Modifier) {
    val eco: EcoFlowWave3Manager = remember { EcoFlowWave3ManagerImpl() }
    var testApiSig by remember { mutableStateOf<String?>("") }
    var devices by remember { mutableStateOf<List<EcoFlowDevice>>(emptyList()) }
    var fullState by remember { mutableStateOf<Map<String, String>?>(emptyMap()) }
    var powerOnSuccess by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(eco) {
        testApiSig = eco.testCall()
        devices = eco.listDevices()
        fullState = eco.getFullState()

        try {
            eco.changePowerState(powerState = PowerState.On)
            powerOnSuccess = true
        } catch (t: Throwable) {
            Log.e("Wave3 API", "Exception powering on Wave3", t)
            powerOnSuccess = false
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Text("Test API Signature: $testApiSig")
        Text("Test API Check: ${testApiSig == "07c13b65e037faf3b153d51613638fa80003c4c38d2407379a7f52851af1473e"}")

        HorizontalDivider(thickness = 2.dp)

        Text("Device Count: ${devices.size}")

        devices.forEach { Text("• ${it.serialNumber} ${if (it.isOnline) "online" else "offline"}") }

        HorizontalDivider(thickness = 2.dp)

        Text("Test Device Details")

        fullState?.let { state ->
            state.toSortedMap().forEach { (key, value) -> Text("• $key = $value") }
        } ?: run {
            Text("No state reported!")
        }

        HorizontalDivider()

        when (powerOnSuccess) {
            null -> Text("Waiting for power-on request...")
            true -> Text("Power-on succeeded!")
            false -> Text("Power-on failed!")
        }
    }
}
