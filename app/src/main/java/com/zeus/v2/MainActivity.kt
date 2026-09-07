package com.zeus.v2

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var audioService: AudioEngineService? = null
    private var bound = false
    private var pendingPcmStart = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioEngineService.LocalBinder
            audioService = binder.getService()
            bound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            bound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { p ->
        if (!p.values.all { it }) {
            Toast.makeText(this, "Se necesitan permisos de audio para el procesamiento real", Toast.LENGTH_LONG).show()
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null && pendingPcmStart) {
            pendingPcmStart = false
            startZeusService(data)
        } else if (pendingPcmStart) {
            pendingPcmStart = false
            Toast.makeText(this, "Captura PCM cancelada; Zeus no se inició", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            requestNeededPermissions()
            setContent { ComposeRoot() }
        } catch (e: Throwable) {
            android.util.Log.e("ZeusMain", "onCreate fatal: ${android.util.Log.getStackTraceString(e)}")
            Toast.makeText(this, "Error: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    private fun ComposeRoot() {
        val vm: EqViewModel = viewModel(factory = EqViewModel.Factory)
        val punch: PunchViewModel = viewModel()
        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(vm.toSettings().toJson().toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this@MainActivity, "Configuración exportada", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, "No se pudo exportar: " + it.message, Toast.LENGTH_LONG).show()
            }
        }

        LaunchedEffect(Unit) { vm.loadSavedIfAny(); punch.loadSaved() }
        LaunchedEffect(Unit) {
            while (true) {
                audioService?.audioEngine?.let { e ->
                    vm.spectrum = e.spectrumData.copyOf()
                    vm.isEngineRunning = e.isEnabled
                }
                delay(50)
            }
        }
        LaunchedEffect(audioService) {
            audioService?.audioEngine?.let { e ->
                e.settings = vm.toSettings()
                e.setPunch(punch.amount)
                e.applyAll()
            }
            audioService?.configurePcm(vm.toSettings())
        }
        LaunchedEffect(vm.bands.toList(), vm.subBoost, punch.amount) {
            audioService?.audioEngine?.setBands(vm.bands.toList())
            audioService?.audioEngine?.setSubBoost(vm.subBoost)
            audioService?.audioEngine?.setPunch(punch.amount)
            audioService?.configurePcm(vm.toSettings())
        }
        LaunchedEffect(vm.preamp, vm.headroomTrim) {
            audioService?.audioEngine?.setPreGain(vm.preamp + vm.headroomTrim)
            audioService?.configurePcm(vm.toSettings())
        }
        LaunchedEffect(vm.pipelineEnabled, vm.lowShelfEnabled, vm.peakBandsEnabled, vm.highShelfEnabled) {
            audioService?.audioEngine?.setPipelineTags(vm.pipelineEnabled, vm.lowShelfEnabled, vm.peakBandsEnabled, vm.highShelfEnabled)
            audioService?.configurePcm(vm.toSettings())
        }
        LaunchedEffect(vm.limiterEnabled, vm.limiterThreshold, vm.limiterAttack, vm.limiterRelease, vm.limiterRatio, vm.limiterPostGain) {
            audioService?.audioEngine?.setLimiter(vm.limiterEnabled, vm.limiterThreshold, vm.limiterAttack, vm.limiterRelease, vm.limiterRatio, vm.limiterPostGain)
            audioService?.configurePcm(vm.toSettings())
        }
        LaunchedEffect(
            vm.compressorMultibandEnabled, vm.crossoverFrequencies.toList(),
            vm.compMbThLow, vm.compMbThLoMid, vm.compMbThHiMid, vm.compMbThHigh,
            vm.compMbRatioLow, vm.compMbRatioLoMid, vm.compMbRatioHiMid, vm.compMbRatioHigh,
            vm.compMbKneeLow, vm.compMbKneeLoMid, vm.compMbKneeHiMid, vm.compMbKneeHigh,
            vm.compMbAttackLow, vm.compMbAttackLoMid, vm.compMbAttackHiMid, vm.compMbAttackHigh,
            vm.compMbReleaseLow, vm.compMbReleaseLoMid, vm.compMbReleaseHiMid, vm.compMbReleaseHigh,
            vm.compMbPostGainLow, vm.compMbPostGainLoMid, vm.compMbPostGainHiMid, vm.compMbPostGainHigh
        ) {
            audioService?.audioEngine?.setCompressor(
                enabled = vm.compressorMultibandEnabled,
                cross1 = vm.crossoverFrequencies.getOrElse(0) { 180f },
                cross2 = vm.crossoverFrequencies.getOrElse(1) { 1800f },
                cross3 = vm.crossoverFrequencies.getOrElse(2) { 8000f },
                thLow = vm.compMbThLow, thLoMid = vm.compMbThLoMid, thHiMid = vm.compMbThHiMid, thHigh = vm.compMbThHigh,
                ratioLow = vm.compMbRatioLow, ratioLoMid = vm.compMbRatioLoMid, ratioHiMid = vm.compMbRatioHiMid, ratioHigh = vm.compMbRatioHigh,
                kneeLow = vm.compMbKneeLow, kneeLoMid = vm.compMbKneeLoMid, kneeHiMid = vm.compMbKneeHiMid, kneeHigh = vm.compMbKneeHigh,
                attackLow = vm.compMbAttackLow, attackLoMid = vm.compMbAttackLoMid, attackHiMid = vm.compMbAttackHiMid, attackHigh = vm.compMbAttackHigh,
                releaseLow = vm.compMbReleaseLow, releaseLoMid = vm.compMbReleaseLoMid, releaseHiMid = vm.compMbReleaseHiMid, releaseHigh = vm.compMbReleaseHigh,
                postGainLow = vm.compMbPostGainLow, postGainLoMid = vm.compMbPostGainLoMid, postGainHiMid = vm.compMbPostGainHiMid, postGainHigh = vm.compMbPostGainHigh
            )
            audioService?.configurePcm(vm.toSettings())
        }

        MaterialTheme(colorScheme = darkColorScheme(
            primary = Color(0xFFFF6B9E), secondary = Color(0xFF9B59B6),
            background = Color(0xFF0D0D12), surface = Color(0xFF0D0D12),
            onPrimary = Color.White, onBackground = Color(0xFFECECEE), onSurface = Color(0xFFECECEE)
        )) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF0D0D12)) {
                Box(Modifier.fillMaxSize()) {
                    ZeusStudioScreenV2(
                        vm, punch,
                        { toggleEngine(vm) },
                        {
                            vm.saveSettings(); punch.save()
                            Toast.makeText(this@MainActivity, "Configuración guardada", Toast.LENGTH_SHORT).show()
                        },
                        { exportLauncher.launch(ConfigFileRepository.fileNameForExport()) }
                    )
                }
            }
        }
    }

    private fun toggleEngine(vm: EqViewModel) {
        if (vm.isEngineRunning) {
            try { audioService?.audioEngine?.setEnabled(false) } catch (_: Exception) {}
            try { stopService(Intent(this, AudioEngineService::class.java)) } catch (_: Exception) {}
            if (bound) {
                try { unbindService(connection) } catch (_: Exception) {}
                bound = false
            }
            audioService = null
            vm.isEngineRunning = false
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projectionManager = getSystemService(MediaProjectionManager::class.java)
                pendingPcmStart = true
                mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            } else {
                startZeusService(null)
            }
        }
    }

    private fun startZeusService(projectionData: Intent?) {
        val intent = Intent(this, AudioEngineService::class.java).apply {
            if (projectionData != null) {
                action = AudioEngineService.ACTION_START_PCM
                putExtra(AudioEngineService.EXTRA_MEDIA_PROJECTION_DATA, projectionData)
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
            Toast.makeText(this, if (projectionData != null) "Zeus PCM experimental activado" else "Zeus EQ Pro18 activado", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo iniciar el motor: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val toRequest = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (toRequest.isNotEmpty()) requestPermissionLauncher.launch(toRequest.toTypedArray())
    }

    override fun onStart() { super.onStart() }

    override fun onStop() {
        super.onStop()
        if (bound) {
            try { unbindService(connection) } catch (_: Exception) {}
            bound = false
        }
    }
}
