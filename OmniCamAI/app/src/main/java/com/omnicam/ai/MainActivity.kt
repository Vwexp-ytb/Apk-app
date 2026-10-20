package com.omnicam.ai

import android.Manifest
import android.content.ContentValues
import android.content.Context
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
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.camera2.interop.Camera2CameraInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.graphics.ImageFormat
import android.util.Range
import android.util.Size
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private val Ink = Color(0xFF090B0E)
private val Panel = Color(0xFF171A20)
private val Accent = Color(0xFFC7F36B)
private val Muted = Color(0xFF9DA3AD)

private enum class AppPage { CAMERA, SETTINGS, SENSORS, AI }
private data class LensOption(val id: String, val facing: Int, val label: String)

class MainActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 11, 14)
        window.navigationBarColor = android.graphics.Color.rgb(9, 11, 14)
        setContent {
            MaterialTheme {
                Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
                    OmniCamApp(cameraExecutor)
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
private fun OmniCamApp(cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var page by remember { mutableStateOf(AppPage.CAMERA) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var selectedMode by remember { mutableStateOf("PHOTO") }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var selectedCameraId by remember { mutableStateOf<String?>(null) }
    var showLensDialog by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var zoomLabel by remember { mutableStateOf("1×") }
    var exposureIndex by remember { mutableStateOf(0) }
    var outputFormat by remember { mutableStateOf("JPEG") }
    var aiEnabled by remember { mutableStateOf(true) }
    var manualControls by remember { mutableStateOf(false) }
    var manualIso by remember { mutableStateOf(400) }
    var manualExposureNs by remember { mutableStateOf(10_000_000L) }
    var whiteBalance by remember { mutableStateOf("Auto") }
    var videoQuality by remember { mutableStateOf("HD") }
    var videoFps by remember { mutableStateOf(30) }
    var photoResolution by remember { mutableStateOf("Maximale") }
    var capabilityText by remember { mutableStateOf("Lecture des capacités caméra…") }
    var aiStatus by remember { mutableStateOf("Les photos sont analysées localement après la capture.") }
    val lensOptions = remember(context) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        manager.cameraIdList.mapNotNull { id ->
            runCatching {
                val chars = manager.getCameraCharacteristics(id)
                val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> CameraSelector.LENS_FACING_BACK
                    CameraCharacteristics.LENS_FACING_FRONT -> CameraSelector.LENS_FACING_FRONT
                    else -> return@mapNotNull null
                }
                val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
                val jpeg = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.JPEG)
                    ?.maxByOrNull { it.width.toLong() * it.height }
                val mp = jpeg?.let { String.format(Locale.US, "%.1f MP", it.width.toLong() * it.height / 1_000_000.0) } ?: "JPEG n/d"
                LensOption(id, facing, "ID $id · ${if (facing == CameraSelector.LENS_FACING_BACK) "Arrière" else "Avant"} · ${focal?.let { String.format(Locale.US, "%.1f mm", it) } ?: "focale n/d"} · $mp")
            }.getOrNull()
        }
    }

    val availableVideoFps = remember(selectedCameraId, lensFacing) {
        runCatching {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = selectedCameraId ?: manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT }
            val ranges = id?.let { manager.getCameraCharacteristics(it).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList() }.orEmpty()
            ranges.map { it.upper }.distinct().sorted().ifEmpty { listOf(30) }
        }.getOrDefault(listOf(30))
    }

    val availableVideoQuality = remember(selectedCameraId, lensFacing) {
        runCatching {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = selectedCameraId ?: manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT }
            val sizes = id?.let { manager.getCameraCharacteristics(it).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(android.media.MediaRecorder::class.java) }.orEmpty()
            val maxDimension = sizes.maxOfOrNull { maxOf(it.width, it.height) } ?: 1280
            buildList {
                if (maxDimension >= 640) add("SD")
                if (maxDimension >= 1280) add("HD")
                if (maxDimension >= 1920) add("FHD")
                if (maxDimension >= 3840) add("UHD")
            }.ifEmpty { listOf("SD") }
        }.getOrDefault(listOf("SD"))
    }
    LaunchedEffect(availableVideoQuality) { if (videoQuality !in availableVideoQuality) videoQuality = availableVideoQuality.last() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) Toast.makeText(context, "L’autorisation caméra est nécessaire pour photographier.", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(page) {
        if (page == AppPage.SENSORS) capabilityText = describeCameraHardware(context)
    }

    Column(Modifier.fillMaxSize().background(Ink).padding(horizontal = 16.dp)) {
        Header(page = page, flashEnabled = flashEnabled, onFlash = { flashEnabled = !flashEnabled }, onBack = { page = AppPage.CAMERA }, onSettings = { page = AppPage.SETTINGS })
        Spacer(Modifier.height(10.dp))

        when (page) {
            AppPage.CAMERA -> {
                // Keep the preview dominant; advanced controls live on separate screens.
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel)
                ) {
                    if (hasPermission) {
                        CameraPreview(
                            lensFacing = lensFacing,
                            selectedCameraId = selectedCameraId,
                            lifecycleOwner = lifecycleOwner,
                            flashEnabled = flashEnabled,
                            selectedMode = selectedMode,
                            manualControls = manualControls,
                            manualIso = manualIso,
                            manualExposureNs = manualExposureNs,
                            whiteBalance = whiteBalance,
                            videoQuality = videoQuality,
                            videoFps = videoFps,
                            availableVideoFps = availableVideoFps,
                            availableVideoQuality = availableVideoQuality,
                            photoResolution = photoResolution,
                            onCaptureReady = { capture, camera, video ->
                                imageCapture = capture
                                videoCapture = video
                                boundCamera = camera
                                exposureIndex = camera.cameraInfo.exposureState.exposureCompensationIndex
                            },
                            onTapToFocus = { view, point ->
                                boundCamera?.let { camera ->
                                    val p = view.meteringPointFactory.createPoint(point.x, point.y)
                                    camera.cameraControl.startFocusAndMetering(
                                        FocusMeteringAction.Builder(p).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (showGrid) CompositionGrid(Modifier.fillMaxSize())
                        Row(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf("0.5×", "1×", "2×").forEach { zoom ->
                                val selected = zoomLabel == zoom
                                Box(
                                    Modifier.size(width = 54.dp, height = 38.dp).clip(CircleShape)
                                        .background(if (selected) Accent else Color(0xCC090B0E))
                                        .clickable {
                                            val ratio = zoom.removeSuffix("×").toFloat()
                                            val state = boundCamera?.cameraInfo?.zoomState?.value
                                            if (state != null && ratio >= state.minZoomRatio && ratio <= state.maxZoomRatio) {
                                                boundCamera?.cameraControl?.setZoomRatio(ratio)?.addListener(
                                                    { zoomLabel = zoom }, ContextCompat.getMainExecutor(context)
                                                )
                                            } else Toast.makeText(context, "Ce niveau de zoom n’est pas disponible sur cet objectif.", Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Text(zoom, color = if (selected) Ink else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    } else {
                        Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Autorisation caméra requise", color = Color.White, fontSize = 18.sp)
                            Spacer(Modifier.height(10.dp))
                            Text("Autorisez l’accès à la caméra pour utiliser OmniCam AI.", color = Muted)
                            Spacer(Modifier.height(12.dp))
                            ActionRow("Autoriser la caméra", "Ouvrir l’autorisation") { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Minimal shutter strip.
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    RoundAction(Icons.Default.PhotoLibrary, "Galerie") { openGallery(context) }
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).background(Color.White).padding(5.dp)
                            .clip(CircleShape).background(Accent).clickable {
                                val capture = imageCapture
                                if (capture == null) Toast.makeText(context, "Caméra en cours d’initialisation", Toast.LENGTH_SHORT).show()
                                else if (boundCamera == null) Toast.makeText(context, "Caméra indisponible", Toast.LENGTH_SHORT).show()
                                else if (outputFormat == "DNG") { context.startActivity(Intent(context, RawCaptureActivity::class.java)) } else capturePhoto(context, capture, boundCamera!!, selectedMode, cameraExecutor, outputFormat, aiEnabled) { summary ->
                                    aiStatus = summary
                                    page = AppPage.AI
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.CameraAlt, contentDescription = "Prendre une photo", tint = Ink, modifier = Modifier.size(28.dp)) }
                    RoundAction(
                        if (isRecording) Icons.Default.StopCircle else Icons.Default.Videocam,
                        if (isRecording) "Arrêter" else "Vidéo"
                    ) {
                        if (isRecording) {
                            activeRecording?.stop()
                            boundCamera?.cameraControl?.enableTorch(false)
                            isRecording = false
                        } else {
                            boundCamera?.cameraControl?.enableTorch(flashEnabled)
                            val recorder = videoCapture
                            if (recorder == null) {
                                Toast.makeText(context, "Vidéo indisponible sur cette caméra.", Toast.LENGTH_SHORT).show()
                            } else {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                val values = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, "OmniCam_${stamp}.mp4")
                                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OmniCam")
                                }
                                val options = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                                    .setContentValues(values).build()
                                activeRecording = recorder.output.prepareRecording(context, options)
                                    .start(ContextCompat.getMainExecutor(context)) { event ->
                                        if (event is VideoRecordEvent.Finalize) {
                                            activeRecording = null
                                            isRecording = false
                                            if (event.error == VideoRecordEvent.Finalize.ERROR_NONE) {
                                                Toast.makeText(context, "Vidéo enregistrée dans Movies/OmniCam", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Échec vidéo : ${event.error}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                isRecording = true
                            }
                        }
                    }

                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                    listOf("HDR", "NUIT", "PORTRAIT", "PHOTO", "PRO").forEach { mode ->
                        ModeLabel(mode, selectedMode == mode) { selectedMode = mode }
                    }
                }
                if (selectedMode == "PRO") {
                    val sensorRange = remember(selectedCameraId, lensFacing) { runCatching { val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager; val id = selectedCameraId ?: mgr.cameraIdList.firstOrNull { mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT }; id?.let { mgr.getCameraCharacteristics(it).get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) } }.getOrNull() }
                    val timeRange = remember(selectedCameraId, lensFacing) { runCatching { val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager; val id = selectedCameraId ?: mgr.cameraIdList.firstOrNull { mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT }; id?.let { mgr.getCameraCharacteristics(it).get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) } }.getOrNull() }
                    Column(Modifier.fillMaxWidth().height(230.dp).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Contrôle manuel en direct", color = Accent, fontSize = 11.sp); TextButton(onClick = { manualControls = !manualControls }) { Text(if (manualControls) "MANUEL" else "AUTO", color = if (manualControls) Accent else Muted) } }
                        if (sensorRange != null && sensorRange.upper > sensorRange.lower) { Text("ISO ${manualIso.coerceIn(sensorRange.lower, sensorRange.upper)} · ${sensorRange.lower}–${sensorRange.upper}", color = Color.White, fontSize = 11.sp); Slider(value = manualIso.coerceIn(sensorRange.lower, sensorRange.upper).toFloat(), onValueChange = { manualIso = it.roundToInt(); manualControls = true }, valueRange = sensorRange.lower.toFloat()..sensorRange.upper.toFloat()) }
                        if (timeRange != null && timeRange.upper > timeRange.lower) { Text("Temps de pose ${(manualExposureNs.coerceIn(timeRange.lower, timeRange.upper) / 1_000_000.0).let { String.format(Locale.US, "%.2f", it) }} ms", color = Color.White, fontSize = 11.sp); Slider(value = manualExposureNs.coerceIn(timeRange.lower, timeRange.upper).toDouble().toFloat(), onValueChange = { manualExposureNs = it.toLong(); manualControls = true }, valueRange = timeRange.lower.toDouble().toFloat()..timeRange.upper.toDouble().toFloat()) }
                        Text("Balance des blancs / température", color = Color.White, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("Auto", "Chaud", "Jour", "Froid").forEach { wb -> TextButton(onClick = { whiteBalance = wb }) { Text(wb, color = if (whiteBalance == wb) Accent else Muted) } } }
                        Text("Format photo", color = Color.White, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("JPEG", "JPG", "PNG", "DNG").forEach { fmt -> TextButton(onClick = { outputFormat = fmt }) { Text(fmt, color = if (outputFormat == fmt) Accent else Muted) } } }
                        Text("Résolution : $photoResolution", color = Color.White, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("Maximale", "HD", "4K").forEach { res -> TextButton(onClick = { photoResolution = res }) { Text(res, color = if (photoResolution == res) Accent else Muted) } } }
                        Text("Vidéo Pro · qualité $videoQuality · ${videoFps} fps", color = Color.White, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { availableVideoQuality.forEach { q -> TextButton(onClick = { videoQuality = q }) { Text(q, color = if (videoQuality == q) Accent else Muted) } } }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { availableVideoFps.forEach { fps -> TextButton(onClick = { videoFps = fps }) { Text("$fps fps", color = if (videoFps == fps) Accent else Muted) } } }
                    }
                    val range = boundCamera?.cameraInfo?.exposureState?.exposureCompensationRange
                    if (range != null && range.lower < range.upper) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                            Text("Exposition  ·  EV $exposureIndex", color = Accent, fontSize = 11.sp)
                            Slider(
                                value = exposureIndex.toFloat().coerceIn(range.lower.toFloat(), range.upper.toFloat()),
                                onValueChange = { value ->
                                    exposureIndex = value.roundToInt()
                                    boundCamera?.cameraControl?.setExposureCompensationIndex(exposureIndex)
                                },
                                valueRange = range.lower.toFloat()..range.upper.toFloat(),
                                steps = (range.upper - range.lower - 1).coerceAtLeast(0)
                            )
                        }
                    } else {
                        Text("Correction EV non prise en charge", color = Muted, fontSize = 10.sp)
                    }
                }
            }
            AppPage.SETTINGS -> {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("PRISE DE VUE", "Les réglages sont séparés de l’aperçu pour conserver une image large.")
                    SettingTile("Mode de capture", "Actuel : $selectedMode", "Choisir un mode") {
                        page = AppPage.CAMERA
                        Toast.makeText(context, "Choisissez le mode dans la barre sous le déclencheur.", Toast.LENGTH_SHORT).show()
                    }
                    SettingTile("Grille de composition", if (showGrid) "Visible" else "Masquée", "Basculer") { showGrid = !showGrid }
                    SettingTile("Caméra active", selectedCameraId?.let { id -> lensOptions.firstOrNull { it.id == id }?.label } ?: if (lensFacing == CameraSelector.LENS_FACING_BACK) "Arrière · automatique" else "Avant · automatique", "Choisir") {
                        showLensDialog = true
                    }
                    SettingTile("Correction d’exposition", "Réglage EV disponible en mode Pro", "Ouvrir le mode Pro") { selectedMode = "PRO"; page = AppPage.CAMERA }
                    SectionTitle("APPAREIL", "Compatibilité dépendante du constructeur et du capteur.")
                    SettingTile("RAW / DNG et réglages manuels", "Capture Camera2 RAW avec ISO, temps de pose et mise au point", "Ouvrir") {
                        if (hasPermission) context.startActivity(Intent(context, RawCaptureActivity::class.java))
                        else permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    SettingTile("Capteurs et résolutions", "Inspecter les capacités Camera2", "Ouvrir") { page = AppPage.SENSORS }
                    SettingTile("Format photo", "Actuel : $outputFormat", "Changer") { outputFormat = when (outputFormat) { "JPEG" -> "PNG"; "PNG" -> "JPG"; "JPG" -> "DNG"; else -> "JPEG" } }
                    SettingTile("Galerie", "Ouvrir directement la dernière photo", "Ouvrir") { openGallery(context) }
                    SectionTitle("INTELLIGENCE ARTIFICIELLE", "Traitement local sur le téléphone.")
                    SettingTile("Analyse IA", if (aiEnabled) "Analyse locale activée après capture" else "Analyse désactivée", if (aiEnabled) "Désactiver" else "Activer") { aiEnabled = !aiEnabled }
                    SettingTile("Mise au point tactile", "Touchez l’aperçu pour faire le point", "Retour caméra") { page = AppPage.CAMERA }
                    Text("Les fonctions non prises en charge par l’API caméra du téléphone ne sont pas activées artificiellement.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            AppPage.SENSORS -> {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
                    Text("Capteurs disponibles", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text(capabilityText, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Remarque : une taille annoncée par Camera2 n’assure pas que le constructeur autorise cette sortie en combinaison avec les autres flux. La sélection explicite d’un objectif physique et le DNG nécessitent des chemins de capture dédiés.", color = Color.White, fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(16.dp))
                    ActionRow("Actualiser les capacités", "Relire Camera2") { capabilityText = describeCameraHardware(context) }
                }
            }
            AppPage.AI -> {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("OMNICAM IA", "Outils disponibles dans cette version.")
                    SettingTile("Analyse de scène", "Classification locale de la photo après capture", "Ouvrir la galerie") { openGallery(context) }
                    SettingTile("Dernier résultat", aiStatus, "Réinitialiser") { aiStatus = "Les photos sont analysées localement après la capture." }
                    SettingTile("Mode nuit", "Fusion multi-images pour réduire le bruit", "Activer") { selectedMode = "NUIT"; page = AppPage.CAMERA }
                    SettingTile("Mode portrait", "Détourage du sujet et flou de fond par IA locale", "Activer") { selectedMode = "PORTRAIT"; page = AppPage.CAMERA }
                    Text("HDR et mode nuit fusionnent plusieurs images. Le mode portrait segmente le sujet et applique un arrière-plan légèrement pixelisé. L’analyse de scène est désactivable dans les paramètres.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
        if (page != AppPage.CAMERA) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), horizontalArrangement = Arrangement.Center) {
                Text("CAMÉRA", color = Muted, fontSize = 11.sp, modifier = Modifier.clip(CircleShape).clickable { page = AppPage.CAMERA }.padding(horizontal = 16.dp, vertical = 10.dp))
                Text("PARAMÈTRES", color = if (page == AppPage.SETTINGS) Accent else Muted, fontSize = 11.sp, modifier = Modifier.clip(CircleShape).clickable { page = AppPage.SETTINGS }.padding(horizontal = 16.dp, vertical = 10.dp))
                Text("IA", color = if (page == AppPage.AI) Accent else Muted, fontSize = 11.sp, modifier = Modifier.clip(CircleShape).clickable { page = AppPage.AI }.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }

    if (showLensDialog) {
        AlertDialog(
            onDismissRequest = { showLensDialog = false },
            title = { Text("Choisir un objectif exposé par Android") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("La liste contient les identifiants caméra accessibles à l’application. Les sous-capteurs d’un module logique peuvent ne pas être exposés séparément.", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    lensOptions.forEach { option ->
                        TextButton(onClick = {
                            selectedCameraId = option.id
                            lensFacing = option.facing
                            zoomLabel = "1×"
                            showLensDialog = false
                        }) { Text(option.label, color = Color.White) }
                    }
                    TextButton(onClick = {
                        selectedCameraId = null
                        showLensDialog = false
                    }) { Text("Automatique") }
                }
            },
            confirmButton = { TextButton(onClick = { showLensDialog = false }) { Text("Fermer") } }
        )
    }
}

@Composable
private fun Header(page: AppPage, flashEnabled: Boolean, onFlash: () -> Unit, onBack: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        if (page == AppPage.CAMERA) {
            RoundAction(if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff, if (flashEnabled) "Flash activé" else "Flash désactivé", onFlash)
            Spacer(Modifier.width(8.dp))
            RoundAction(Icons.Default.Settings, "Paramètres", onSettings)
        } else {
            RoundAction(Icons.Default.ArrowBack, "Retour", onBack)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text(title, color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SettingTile(title: String, subtitle: String, actionLabel: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).clickable(onClick = onClick).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(actionLabel, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    SettingTile(title, subtitle, "Ouvrir", onClick)
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(label, color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun ModeLabel(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = if (selected) Accent else Muted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(if (selected) 14.dp else 4.dp).height(2.dp).clip(CircleShape).background(if (selected) Accent else Color.Transparent))
    }
}

@Composable
private fun CompositionGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width / 3f
        val h = size.height / 3f
        val c = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            drawLine(c, Offset(w * i, 0f), Offset(w * i, size.height), strokeWidth = 1f)
            drawLine(c, Offset(0f, h * i), Offset(size.width, h * i), strokeWidth = 1f)
        }
    }
}

@Composable
private fun CameraPreview(
    lensFacing: Int,
    selectedCameraId: String?,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    flashEnabled: Boolean,
    selectedMode: String,
    manualControls: Boolean,
    manualIso: Int,
    manualExposureNs: Long,
    whiteBalance: String,
    videoQuality: String,
    videoFps: Int,
    availableVideoFps: List<Int>,
    availableVideoQuality: List<String>,
    photoResolution: String,
    onCaptureReady: (ImageCapture, Camera, VideoCapture<Recorder>) -> Unit,
    onTapToFocus: (PreviewView, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    } }
    DisposableEffect(lifecycleOwner, lensFacing, selectedCameraId, flashEnabled, selectedMode, manualControls, manualIso, manualExposureNs, whiteBalance, videoQuality, videoFps, photoResolution) {
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = future.get()
            val previewBuilder = Preview.Builder()
            val captureMode = if (selectedMode == "PRO" || selectedMode == "NUIT" || selectedMode == "HDR") ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            val targetQuality = when (videoQuality) { "SD" -> Quality.SD; "FHD" -> Quality.FHD; "UHD" -> Quality.UHD; else -> Quality.HD }
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(targetQuality, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build()
            val fpsRange = (availableVideoFps.ifEmpty { listOf(30) }).minByOrNull { kotlin.math.abs(it - videoFps) } ?: 30
            val videoBuilder = VideoCapture.Builder(recorder)
            val supportedRange = runCatching {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val id = selectedCameraId ?: manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT }
                id?.let { manager.getCameraCharacteristics(it).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.firstOrNull { range -> range.upper == fpsRange } }
            }.getOrNull()
            if (supportedRange != null) runCatching { androidx.camera.camera2.interop.Camera2Interop.Extender(videoBuilder).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, supportedRange) }
            val video = videoBuilder.build()
            val resolutionStrategy = when (photoResolution) {
                "HD" -> ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                "4K" -> ResolutionStrategy(Size(3840, 2160), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                else -> ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
            }
            val highestResolution = ResolutionSelector.Builder().setResolutionStrategy(resolutionStrategy).build()
            val captureBuilder = ImageCapture.Builder().setCaptureMode(captureMode).setResolutionSelector(highestResolution)
            if (manualControls) {
                val manualIsoSafe = manualIso.coerceAtLeast(50)
                val exposureSafe = manualExposureNs.coerceAtLeast(100_000L)
                runCatching { androidx.camera.camera2.interop.Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF).setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, manualIsoSafe).setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureSafe) }
                runCatching { androidx.camera.camera2.interop.Camera2Interop.Extender(captureBuilder).setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF).setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, manualIsoSafe).setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureSafe) }
            }
            val wbMode = when (whiteBalance) { "Chaud" -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT; "Jour" -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT; "Froid" -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT; else -> CaptureRequest.CONTROL_AWB_MODE_AUTO }
            runCatching { androidx.camera.camera2.interop.Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, wbMode) }
            runCatching { androidx.camera.camera2.interop.Camera2Interop.Extender(captureBuilder).setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, wbMode) }
            runCatching { androidx.camera.camera2.interop.Camera2Interop.Extender(videoBuilder).setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, wbMode) }
            var capture = captureBuilder.build()
            val preview = previewBuilder.build().also { it.surfaceProvider = previewView.surfaceProvider }
            val selectorBuilder = CameraSelector.Builder().requireLensFacing(lensFacing)
            if (selectedCameraId != null) {
                selectorBuilder.addCameraFilter { infos ->
                    infos.filter { info -> runCatching { Camera2CameraInfo.from(info).cameraId == selectedCameraId }.getOrDefault(false) }
                }
            }
            val selector = selectorBuilder.build()
            try {
                provider.unbindAll()
                val camera = try {
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, video)
                } catch (_: Exception) {
                    // Some HALs cannot combine their maximum still size with preview + video; fall back safely.
                    provider.unbindAll()
                    capture = ImageCapture.Builder().setCaptureMode(captureMode).build()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, video)
                }
                capture.flashMode = if (flashEnabled && lensFacing == CameraSelector.LENS_FACING_BACK) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                camera.cameraControl.enableTorch(false)
                onCaptureReady(capture, camera, video)
            } catch (_: Exception) {
                Toast.makeText(context, "Impossible de démarrer cette caméra", Toast.LENGTH_SHORT).show()
            }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose { if (future.isDone) runCatching { future.get().unbindAll() } }
    }
    AndroidView(factory = { previewView }, modifier = modifier.pointerInput(previewView) {
        detectTapGestures { point -> onTapToFocus(previewView, point) }
    })
}

private fun capturePhoto(
    context: android.content.Context,
    imageCapture: ImageCapture,
    camera: Camera,
    mode: String,
    cameraExecutor: ExecutorService,
    outputFormat: String,
    aiEnabled: Boolean,
    onAnalysis: (String) -> Unit
) {
    fun analyze(uri: android.net.Uri) {
        if (!aiEnabled) { onAnalysis("Photo enregistrée · analyse IA désactivée"); return }
        try {
            ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS).process(InputImage.fromFilePath(context, uri))
                .addOnSuccessListener { labels ->
                    val summary = labels.sortedByDescending { it.confidence }.take(3)
                        .joinToString { it.text + " " + (it.confidence * 100).toInt() + "%" }
                    val message = if (summary.isBlank()) "Aucun objet reconnu sur la dernière photo." else "Résultat : $summary"
                    Toast.makeText(context, "OmniCam IA : $summary", Toast.LENGTH_LONG).show()
                    onAnalysis(message)
                }
                .addOnFailureListener { onAnalysis("Photo enregistrée, mais l’analyse IA a échoué.") }
        } catch (_: Exception) { onAnalysis("Photo enregistrée. Analyse IA indisponible.") }
    }

    if (mode == "HDR" || mode == "NUIT") {
        Toast.makeText(context, if (mode == "HDR") "Capture HDR multi-images…" else "Rafale nuit : réduction du bruit…", Toast.LENGTH_SHORT).show()
        BurstCapture.capture(
            context = context,
            imageCapture = imageCapture,
            camera = camera,
            mode = mode,
            ioExecutor = cameraExecutor,
            onComplete = { uri ->
                Toast.makeText(context, if (mode == "HDR") "Fusion HDR enregistrée" else "Fusion nuit enregistrée", Toast.LENGTH_SHORT).show()
                analyze(uri)
            },
            onError = { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
        )
        return
    }

    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val wantsPng = outputFormat == "PNG"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "OmniCam_$timestamp.${if (wantsPng) "png" else "jpg"}")
        put(MediaStore.MediaColumns.MIME_TYPE, if (wantsPng) "image/png" else "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmniCam")
    }
    val output = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
    imageCapture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val saved = outputFileResults.savedUri
                if (saved == null) {
                    onAnalysis("Photo enregistrée, mais son URI est indisponible.")
                } else if (wantsPng) {
                    try {
                        val bitmap = context.contentResolver.openInputStream(saved).use { android.graphics.BitmapFactory.decodeStream(it) } ?: error("JPEG temporaire illisible")
                        val pngValues = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, "OmniCam_$timestamp.png"); put(MediaStore.MediaColumns.MIME_TYPE, "image/png"); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmniCam") }
                        val pngUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pngValues) ?: error("Création PNG impossible")
                        context.contentResolver.openOutputStream(pngUri).use { stream -> if (stream == null || !bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) error("Écriture PNG impossible") }
                        bitmap.recycle(); context.contentResolver.delete(saved, null, null)
                        Toast.makeText(context, "PNG enregistré dans Pictures/OmniCam", Toast.LENGTH_SHORT).show()
                        analyze(pngUri)
                    } catch (e: Exception) { Toast.makeText(context, "Conversion PNG impossible : ${e.message}", Toast.LENGTH_LONG).show(); analyze(saved) }
                } else if (mode == "PORTRAIT") {
                    Toast.makeText(context, "Détourage du sujet par IA…", Toast.LENGTH_SHORT).show()
                    PortraitProcessor.process(context, saved) { portraitUri ->
                        if (portraitUri != null) {
                            Toast.makeText(context, "Portrait avec flou d’arrière-plan enregistré", Toast.LENGTH_LONG).show()
                            analyze(portraitUri)
                        } else {
                            Toast.makeText(context, "Détourage indisponible ; photo originale conservée.", Toast.LENGTH_LONG).show()
                            analyze(saved)
                        }
                    }
                } else {
                    Toast.makeText(context, "Photo enregistrée dans Pictures/OmniCam", Toast.LENGTH_SHORT).show()
                    analyze(saved)
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
    val latest = runCatching {
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
            if (cursor.moveToFirst()) android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)) else null
        }
    }.getOrNull()
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(latest ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "Aucune application galerie disponible.", Toast.LENGTH_SHORT).show()
    }
}
