package com.zeus.v2

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var audioService: AudioEngineService? = null
    private var bound = false

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
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(
                this,
                "Se necesitan permisos de audio para el procesamiento real",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            requestNeededPermissions()
            setContent {
                ComposeRoot()
            }
        } catch (e: Throwable) {
            android.util.Log.e("ZeusMain", "onCreate fatal: ${android.util.Log.getStackTraceString(e)}")
            android.widget.Toast.makeText(this, "Error: ${e.javaClass.simpleName}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    private fun ComposeRoot() {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFFFF6B9E),
                secondary = Color(0xFF9B59B6),
                background = Color(0xFF0D0D12),
                surface = Color(0xFF0D0D12),
                onPrimary = Color.White,
                onBackground = Color(0xFFECECEE),
                onSurface = Color(0xFFECECEE)
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0D0D12)
            ) {
                // Interfaz temporal muy simple para diagnosticar
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Zeus-V2 cargó correctamente",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }
    }

    private fun toggleEngine(viewModel: EqViewModel) {
        if (viewModel.isEngineRunning) {
            try {
                audioService?.audioEngine?.setEnabled(false)
            } catch (_: Exception) {
            }
            try {
                stopService(Intent(this, AudioEngineService::class.java))
            } catch (_: Exception) {
            }
            if (bound) {
                try {
                    unbindService(connection)
                } catch (_: Exception) {
                }
                bound = false
            }
            audioService = null
            viewModel.isEngineRunning = false
        } else {
            val intent = Intent(this, AudioEngineService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                bindService(intent, connection, Context.BIND_AUTO_CREATE)
                viewModel.isEngineRunning = true
                Toast.makeText(this, "Zeus EQ Pro18 activado", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                viewModel.isEngineRunning = false
                Toast.makeText(
                    this,
                    "No se pudo iniciar el motor: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    override fun onStart() {
        super.onStart()
        // No conectar el servicio al abrir: evita crash en algunos dispositivos.
        // El motor solo arranca al pulsar el botón de power.
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            try {
                unbindService(connection)
            } catch (_: Exception) {
            }
            bound = false
        }
    }
}
