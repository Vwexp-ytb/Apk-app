package com.omnicam.ai

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

private val Ink = Color(0xFF090B0E)
private val Panel = Color(0xFF171A20)
private val Accent = Color(0xFFC7F36B)
private val Muted = Color(0xFF9DA3AD)

class MainActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 11, 14)
        window.navigationBarColor = android.graphics.Color.rgb(9, 11, 14)
        setContent {
            MaterialTheme {
                Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
                    CameraScreen(cameraExecutor)
                }
            }
        }
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun CameraScreen(cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var selectedMode by remember { mutableStateOf("PHOTO") }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var showGrid by remember { mutableStateOf(true) }
    var flashEnabled by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var zoomLabel by remember { mutableStateOf("1×") }
    var showCapabilities by remember { mutableStateOf(false) }
    var exposureIndex by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Ink).padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopBar()
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Panel)
        ) {
            if (hasPermission) {
                CameraPreview(
                    lensFacing = lensFacing,
                    lifecycleOwner = lifecycleOwner,
                    cameraExecutor = cameraExecutor,
                    flashEnabled = flashEnabled,
                    selectedMode = selectedMode,
                    onCaptureReady = { capture, camera -> imageCapture = capture; boundCamera = camera; exposureIndex = camera.cameraInfo.exposureState.exposureCompensationIndex },
                    onTapToFocus = { view, point ->
                        val camera = boundCamera
                        if (camera != null) {
                            val meterPoint = view.meteringPointFactory.createPoint(point.x, point.y)
                            val action = FocusMeteringAction.Builder(meterPoint).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
                            camera.cameraControl.startFocusAndMetering(action)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (showGrid) CompositionGrid(Modifier.fillMaxSize())
                Column(
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoChip("IA PHOTO", "Analyse après capture")
                    InfoChip("CAPTEURS", "Capacités du téléphone")
                }
                Text(
                    text = "●  LIVE",
                    color = Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(18.dp)
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("0.5×", "1×", "2×").forEach { zoom ->
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(if (zoomLabel == zoom) Accent else Color(0x99090B0E))
                                .clickable {
                                    val ratio = zoom.removeSuffix("×").toFloat()
                                    val state = boundCamera?.cameraInfo?.zoomState?.value
                                    if (state != null && ratio >= state.minZoomRatio && ratio <= state.maxZoomRatio) {
                                        boundCamera?.cameraControl?.setZoomRatio(ratio)?.addListener({ zoomLabel = zoom }, ContextCompat.getMainExecutor(context))
                                    } else {
                                        Toast.makeText(context, "Grossissement non pris en charge par cet objectif.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                zoom, color = if (zoomLabel == zoom) Ink else Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Autorisation caméra requise", color = Color.White, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Autoriser l’accès à la caméra pour démarrer OmniCam AI.",
                        color = Muted
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Autoriser",
                        color = Accent,
                        modifier = Modifier.clickable {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }.padding(12.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton("Flash", Icons.Default.FlashOff, flashEnabled) {
                flashEnabled = !flashEnabled
            }
            ToolButton("Grille", Icons.Default.GridOn, showGrid) { showGrid = !showGrid }
            ToolButton("Capteurs", Icons.Default.Tune, showCapabilities) {
                showCapabilities = true
            }
            ToolButton("Retourner", Icons.Default.FlipCameraAndroid, false) {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeLabel("NUIT", selectedMode == "NUIT") { selectedMode = "NUIT" }
            ModeLabel("PORTRAIT", selectedMode == "PORTRAIT") { selectedMode = "PORTRAIT" }
            ModeLabel("PHOTO", selectedMode == "PHOTO") { selectedMode = "PHOTO" }
            ModeLabel("PRO", selectedMode == "PRO") { selectedMode = "PRO" }
        }
        if (selectedMode == "PRO") {
            val range = boundCamera?.cameraInfo?.exposureState?.exposureCompensationRange
            if (range != null && range.lower < range.upper) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text("Exposition : EV $exposureIndex", color = Accent, fontSize = 11.sp)
                    Slider(
                        value = exposureIndex.toFloat().coerceIn(range.lower.toFloat(), range.upper.toFloat()),
                        onValueChange = { value ->
                            val index = value.roundToInt()
                            exposureIndex = index
                            boundCamera?.cameraControl?.setExposureCompensationIndex(index)
                        },
                        valueRange = range.lower.toFloat()..range.upper.toFloat(),
                        steps = (range.upper - range.lower - 1).coerceAtLeast(0)
                    )
                }
            } else {
                Text("Correction EV non exposée par cette caméra", color = Muted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(16.dp))
                    .background(Panel).clickable {
                        openGallery(context)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("▧", color = Color.White, fontSize = 26.sp)
            }
            Box(
                modifier = Modifier.size(82.dp).clip(CircleShape)
                    .background(Color.Transparent).padding(5.dp)
                    .clip(CircleShape).background(Accent)
                    .clickable {
                        val capture = imageCapture
                        if (capture == null) {
                            Toast.makeText(context, "Caméra en cours d’initialisation", Toast.LENGTH_SHORT).show()
                        } else {
                            capturePhoto(context, capture, cameraExecutor)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Accent))
            }
            Box(
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(16.dp))
                    .background(Panel).clickable {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Changer de caméra", tint = Color.White)
            }
        }
    }

    if (showCapabilities) {
        AlertDialog(
            onDismissRequest = { showCapabilities = false },
            title = { Text("Capacités des capteurs") },
            text = {
                Text(
                    describeCameraHardware(context),
                    modifier = Modifier.height(420.dp).verticalScroll(rememberScrollState()),
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showCapabilities = false }) { Text("Fermer") }
            },
            containerColor = Panel,
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("OmniCam", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("AI  /  COMPUTATIONAL CAMERA", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
        }
        Box(
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Panel).padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text("STUDIO  •  01", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoChip(title: String, subtitle: String) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xCC090B0E))
            .padding(horizontal = 11.dp, vertical = 8.dp)
    ) {
        Text(title, color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun CompositionGrid(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val thirdW = size.width / 3f
        val thirdH = size.height / 3f
        val gridColor = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            drawLine(gridColor, androidx.compose.ui.geometry.Offset(thirdW * i, 0f),
                androidx.compose.ui.geometry.Offset(thirdW * i, size.height), strokeWidth = 1f)
            drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, thirdH * i),
                androidx.compose.ui.geometry.Offset(size.width, thirdH * i), strokeWidth = 1f)
        }
    }
}

@Composable
private fun ToolButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                .background(if (active) Accent.copy(alpha = 0.15f) else Panel),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = if (active) Accent else Color.White)
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun ModeLabel(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = if (selected) Accent else Muted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier.width(if (selected) 16.dp else 4.dp).height(3.dp)
                .clip(CircleShape).background(if (selected) Accent else Color.Transparent)
        )
    }
}

@Composable
private fun CameraPreview(
    lensFacing: Int,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    cameraExecutor: ExecutorService,
    flashEnabled: Boolean,
    selectedMode: String,
    onCaptureReady: (ImageCapture, Camera) -> Unit,
    onTapToFocus: (PreviewView, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    } }

    DisposableEffect(lifecycleOwner, lensFacing, flashEnabled, selectedMode) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val captureMode = if (selectedMode == "PRO" || selectedMode == "NUIT") ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            val capture = ImageCapture.Builder().setCaptureMode(captureMode).build()
            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    preview,
                    capture
                )
                camera.cameraControl.enableTorch(flashEnabled && lensFacing == CameraSelector.LENS_FACING_BACK)
                onCaptureReady(capture, camera)
            } catch (_: Exception) {
                Toast.makeText(context, "Impossible de démarrer cette caméra", Toast.LENGTH_SHORT).show()
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.pointerInput(previewView) {
            detectTapGestures { point -> onTapToFocus(previewView, point) }
        }
    )
}

private fun capturePhoto(
    context: android.content.Context,
    imageCapture: ImageCapture,
    cameraExecutor: ExecutorService
) {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "OmniCam_$timestamp.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmniCam")
        }
    }
    val output = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values
    ).build()

    imageCapture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Photo enregistrée dans Pictures/OmniCam", Toast.LENGTH_SHORT).show()
                outputFileResults.savedUri?.let { uri ->
                    try {
                        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                            .process(InputImage.fromFilePath(context, uri))
                            .addOnSuccessListener { labels ->
                                val summary = labels.sortedByDescending { it.confidence }.take(3)
                                    .joinToString { label -> label.text + " " + (label.confidence * 100).toInt() + "%" }
                                if (summary.isNotBlank()) Toast.makeText(context, "OmniCam IA : " + summary, Toast.LENGTH_LONG).show()
                            }
                    } catch (_: Exception) {
                        Toast.makeText(context, "Analyse IA indisponible.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        override fun onError(exception: ImageCaptureException) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Échec de la capture : ${exception.message}", Toast.LENGTH_LONG).show()
            }
        }
    })
}

 
private fun openGallery(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "Aucune application galerie disponible.", Toast.LENGTH_SHORT).show()
    }
}
