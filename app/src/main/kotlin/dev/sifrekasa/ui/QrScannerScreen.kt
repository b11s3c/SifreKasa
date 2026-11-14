package dev.sifrekasa.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sifrekasa.core.qr.QrDecoder
import java.util.concurrent.Executors

/**
 * Kamerayla QR tarar. Kareler yalnızca bellekte, cihazda çözülür; hiçbir yere kaydedilmez.
 * [onScanned] her okunan QR için ana iş parçacığında çağrılır; çağıran içeriği beğenmezse
 * [error] ile bildirir ve tarama sürer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(title: String, error: String?, onScanned: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
    }
    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    BackHandler(onBack = onCancel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri") }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (granted) {
                CameraPreview(onScanned)
                Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    Text(
                        error ?: "QR kodunu kameraya göster.",
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("QR taramak için kamera izni gerekiyor. Görüntü yalnızca cihazda işlenir.")
                    if (denied) {
                        Text(
                            "İzin verilmedi. Sistem ayarlarından kamera iznini açabilir ya da anahtarı elle girebilirsin.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("İzin ver") }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        val cameraProvider = ProcessCameraProvider.awaitInstance(context)
        provider = cameraProvider
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val decoder = QrDecoder()
        val mainHandler = Handler(Looper.getMainLooper())
        var lastText: String? = null
        var lastTime = 0L
        analysis.setAnalyzer(executor) { image ->
            try {
                val plane = image.planes[0]
                val luminance = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
                val text = decoder.decode(luminance, plane.rowStride, image.width, image.height)
                val now = System.currentTimeMillis()
                // Aynı QR'ı saniyede onlarca kez bildirme.
                if (text != null && (text != lastText || now - lastTime > 2000)) {
                    lastText = text
                    lastTime = now
                    mainHandler.post { currentOnScanned(text) }
                }
            } finally {
                image.close()
            }
        }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
    }
    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
