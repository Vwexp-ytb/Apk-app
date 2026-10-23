@file:androidx.annotation.OptIn(markerClass = [androidx.camera.camera2.interop.ExperimentalCamera2Interop::class])

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
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import android.graphics.ImageFormat
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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

private fun temperatureGains(kelvin: Int): RggbChannelVector {
    val t = kelvin.coerceIn(2500, 8000) / 100.0
    val red = if (t <= 66.0) 255.0 else (329.698727446 * Math.pow(t - 60.0, -0.1332047592)).coerceIn(0.0, 255.0)
    val green = if (t <= 66.0) (99.4708025861 * kotlin.math.ln(t) - 161.1195681661).coerceIn(0.0, 255.0) else (288.1221695283 * Math.pow(t - 60.0, -0.0755148492)).coerceIn(0.0, 255.0)
    val blue = when { t >= 66.0 -> 255.0; t <= 19.0 -> 0.0; else -> (138.5177312231 * kotlin.math.ln(t - 10.0) - 305.0447927307).coerceIn(0.0, 255.0) }
    val rGain = (green / red.coerceAtLeast(1.0)).toFloat().coerceIn(0.5f, 8f)
    val bGain = (green / blue.coerceAtLeast(1.0)).toFloat().coerceIn(0.5f, 8f)
    return RggbChannelVector(rGain, 1f, 1f, bGain)
}

private enum class AppPage { CAMERA, SETTINGS, SENSORS, AI, GALLERY, PRIVACY, ABOUT }
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

@OptIn(ExperimentalCamera2Interop::class)
@Composable
private fun OmniCamApp(cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var page by remember { mutableStateOf(AppPage.CAMERA) }
    var preferencesLoaded by remember { mutableStateOf(false) }
    val preferences = remember(context) { context.getSharedPreferences("omnicam_preferences", Context.MODE_PRIVATE) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var selectedMode by remember { mutableStateOf("PHOTO") }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var selectedCameraId by remember { mutableStateOf<String?>(null) }
    var showLensDialog by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var openAfterPhoto by remember { mutableStateOf(false) }
    val systemGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    fun openSystemGallery() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        runCatching { systemGalleryLauncher.launch(intent) }
            .onFailure { Toast.makeText(context, "Aucune galerie disponible", Toast.LENGTH_SHORT).show() }
    }
    var flashEnabled by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var zoomLabel by remember { mutableStateOf("1×") }
    var exposureIndex by remember { mutableStateOf(0) }
    var outputFormat by remember { mutableStateOf("JPEG") }
    var aiEnabled by remember { mutableStateOf(true) }
    var localUpscale by remember { mutableStateOf(false) }
    var zoomEnhance by remember { mutableStateOf(false) }
    var manualControls by remember { mutableStateOf(false) }
    var manualIso by remember { mutableStateOf(400) }
    var manualExposureNs by remember { mutableStateOf(10_000_000L) }
    var temperatureKelvin by remember { mutableStateOf(5500) }
    var videoQuality by remember { mutableStateOf("HD") }
    var videoFps by remember { mutableStateOf(30) }
    var photoResolution by remember { mutableStateOf("Maximale") }
    var timerSeconds by remember { mutableStateOf(0) }
    var burstCount by remember { mutableStateOf(1) }
    var capabilityText by remember { mutableStateOf("Lecture des capacités caméra…") }
    var aiStatus by remember { mutableStateOf("Les photos sont analysées localement après la capture.") }
    var latestPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var showPortraitControls by remember { mutableStateOf(true) }
    var portraitPixelSize by remember { mutableStateOf(14) }
    var portraitShape by remember { mutableStateOf("Carré") }
    var portraitEdgeSoftness by remember { mutableStateOf(58) }
    var portraitMosaicSmoothing by remember { mutableStateOf(42) }
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
    LaunchedEffect(availableVideoFps) { if (videoFps !in availableVideoFps) videoFps = availableVideoFps.minByOrNull { kotlin.math.abs(it - 30) } ?: 30 }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) Toast.makeText(context, "L’autorisation caméra est nécessaire pour photographier.", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(Unit) {
        // Restore preferences before saving values, so defaults never overwrite saved settings.
        selectedMode = preferences.getString("selectedMode", "PHOTO") ?: "PHOTO"
        lensFacing = preferences.getInt("lensFacing", CameraSelector.LENS_FACING_BACK)
        selectedCameraId = preferences.getString("selectedCameraId", null)
            ?.takeIf { savedId -> lensOptions.any { it.id == savedId } }
        selectedCameraId?.let { savedId ->
            lensFacing = lensOptions.firstOrNull { it.id == savedId }?.facing ?: lensFacing
        }
        showGrid = preferences.getBoolean("showGrid", false)
        openAfterPhoto = preferences.getBoolean("openAfterPhoto", false)
        flashEnabled = preferences.getBoolean("flashEnabled", false)
        outputFormat = preferences.getString("outputFormat", "JPEG") ?: "JPEG"
        aiEnabled = preferences.getBoolean("aiEnabled", true)
        localUpscale = preferences.getBoolean("localUpscale", false)
        zoomEnhance = preferences.getBoolean("zoomEnhance", false)
        manualControls = preferences.getBoolean("manualControls", false)
        manualIso = preferences.getInt("manualIso", 400)
        manualExposureNs = preferences.getLong("manualExposureNs", 10_000_000L)
        temperatureKelvin = preferences.getInt("temperatureKelvin", 5500)
        videoQuality = preferences.getString("videoQuality", "HD") ?: "HD"
        videoFps = preferences.getInt("videoFps", 30)
        photoResolution = preferences.getString("photoResolution", "Maximale") ?: "Maximale"
        timerSeconds = preferences.getInt("timerSeconds", 0)
        burstCount = preferences.getInt("burstCount", 1)
        showPortraitControls = preferences.getBoolean("showPortraitControls", true)
        portraitPixelSize = preferences.getInt("portraitPixelSize", 14)
        portraitShape = preferences.getString("portraitShape", "Carré") ?: "Carré"
        portraitEdgeSoftness = preferences.getInt("portraitEdgeSoftness", 58)
        portraitMosaicSmoothing = preferences.getInt("portraitMosaicSmoothing", 42)
        preferencesLoaded = true
        latestPhoto = loadLatestPhotoBitmap(context)
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(
        preferencesLoaded, selectedMode, lensFacing, selectedCameraId, showGrid, openAfterPhoto, flashEnabled,
        outputFormat, aiEnabled, localUpscale, zoomEnhance, manualControls, manualIso, manualExposureNs, temperatureKelvin,
        videoQuality, videoFps, photoResolution, timerSeconds, burstCount, showPortraitControls, portraitPixelSize,
        portraitShape, portraitEdgeSoftness, portraitMosaicSmoothing
    ) {
        if (preferencesLoaded) preferences.edit()
            .putString("selectedMode", selectedMode)
            .putInt("lensFacing", lensFacing)
            .putString("selectedCameraId", selectedCameraId)
            .putBoolean("showGrid", showGrid)
            .putBoolean("openAfterPhoto", openAfterPhoto)
            .putBoolean("flashEnabled", flashEnabled)
            .putString("outputFormat", outputFormat)
            .putBoolean("aiEnabled", aiEnabled)
            .putBoolean("localUpscale", localUpscale)
            .putBoolean("zoomEnhance", zoomEnhance)
            .putBoolean("manualControls", manualControls)
            .putInt("manualIso", manualIso)
            .putLong("manualExposureNs", manualExposureNs)
            .putInt("temperatureKelvin", temperatureKelvin)
            .putString("videoQuality", videoQuality)
            .putInt("videoFps", videoFps)
            .putString("photoResolution", photoResolution)
            .putInt("timerSeconds", timerSeconds)
            .putInt("burstCount", burstCount)
            .putBoolean("showPortraitControls", showPortraitControls)
            .putInt("portraitPixelSize", portraitPixelSize)
            .putString("portraitShape", portraitShape)
            .putInt("portraitEdgeSoftness", portraitEdgeSoftness)
            .putInt("portraitMosaicSmoothing", portraitMosaicSmoothing)
            .apply()
    }
    LaunchedEffect(page) {
        if (page == AppPage.SENSORS) capabilityText = describeCameraHardware(context)
    }
    LaunchedEffect(boundCamera, manualControls, manualIso, manualExposureNs, temperatureKelvin) {
        val camera = boundCamera ?: return@LaunchedEffect
        runCatching {
            val chars = (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager).getCameraCharacteristics(selectedCameraId ?: Camera2CameraInfo.from(camera.cameraInfo).cameraId)
            val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val exposureRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val builder = CaptureRequestOptions.Builder()
            if (manualControls && isoRange != null && exposureRange != null) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, manualIso.coerceIn(isoRange.lower, isoRange.upper))
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, manualExposureNs.coerceIn(exposureRange.lower, exposureRange.upper))
            } else builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            if (temperatureKelvin == 5500) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_FAST)
                builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, temperatureGains(temperatureKelvin))
            }
            Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(builder.build())
        }
    }

    fun performPhotoCapture() {
        val capture = imageCapture
        if (burstCount > 1 && selectedMode == "PHOTO" && (outputFormat == "JPEG" || outputFormat == "JPG")) {
            if (capture == null) {
                Toast.makeText(context, "Caméra en cours d’initialisation", Toast.LENGTH_SHORT).show()
            } else {
                PhotoBurst.capture(
                    context, capture, cameraExecutor, burstCount,
                    onComplete = { uris ->
                        latestPhoto = loadLatestPhotoBitmap(context)
                    },
                    onError = { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
                )
            }
            return
        }
        if (outputFormat == "DNG") {
            context.startActivity(Intent(context, RawCaptureActivity::class.java))
        } else if (capture == null) {
            Toast.makeText(context, "Caméra en cours d’initialisation", Toast.LENGTH_SHORT).show()
        } else if (boundCamera == null) {
            Toast.makeText(context, "Caméra indisponible", Toast.LENGTH_SHORT).show()
        } else {
            capturePhoto(context, capture, boundCamera!!, selectedMode, cameraExecutor, outputFormat, aiEnabled, localUpscale, zoomEnhance, portraitPixelSize, portraitShape, portraitEdgeSoftness, portraitMosaicSmoothing) { summary ->
                aiStatus = summary
                latestPhoto = loadLatestPhotoBitmap(context)
                if (openAfterPhoto) page = AppPage.AI
            }
        }
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
                            onPreviewReady = { previewViewRef = it },
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
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Panel).clickable { openSystemGallery() }, contentAlignment = Alignment.Center) {
                        val thumbnail = latestPhoto
                        if (thumbnail != null && !thumbnail.isRecycled) {
                            Image(bitmap = thumbnail.asImageBitmap(), contentDescription = "Dernière photo : ouvrir la galerie système", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else Icon(Icons.Default.PhotoLibrary, contentDescription = "Ouvrir la galerie système", tint = Color.White)
                    }
                    RoundAction(Icons.Default.Tune, "Autofocus central") {
                        val camera = boundCamera
                        val view = previewViewRef
                        if (camera != null && view != null && view.width > 0 && view.height > 0) {
                            val point = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f, 0.18f)
                            val action = FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                            camera.cameraControl.startFocusAndMetering(action)
                            Toast.makeText(context, "Autofocus et mesure au centre", Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(context, "Aperçu en cours d’initialisation", Toast.LENGTH_SHORT).show()
                    }
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).background(Color.White).padding(5.dp)
                            .clip(CircleShape).background(Accent).clickable {
                                if (timerSeconds > 0) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ performPhotoCapture() }, timerSeconds * 1000L)
                                } else performPhotoCapture()
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
                            if (boundCamera?.cameraInfo?.hasFlashUnit() == true) boundCamera?.cameraControl?.enableTorch(flashEnabled)
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
                                            if (event.error != VideoRecordEvent.Finalize.ERROR_NONE) {
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
                        Text("Température de couleur : ${temperatureKelvin} K", color = Color.White, fontSize = 11.sp)
                        Slider(value = temperatureKelvin.toFloat(), onValueChange = { temperatureKelvin = it.roundToInt() }, valueRange = 2500f..8000f, steps = 54)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("Chaud" to 3000, "Neutre" to 5500, "Jour" to 6500, "Froid" to 8000).forEach { (label, kelvin) -> TextButton(onClick = { temperatureKelvin = kelvin }) { Text(label, color = if (temperatureKelvin == kelvin) Accent else Muted) } } }
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
                    SettingTile("Ouvrir le résultat après capture", if (openAfterPhoto) "Activé : ouvre l’analyse" else "Désactivé : reste sur l’appareil photo", if (openAfterPhoto) "Désactiver" else "Activer") { openAfterPhoto = !openAfterPhoto }
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
                    SettingTile("Retardateur photo", if (timerSeconds == 0) "Désactivé" else "$timerSeconds secondes", "Changer") { timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 } }
                    SettingTile("Rafale photo", if (burstCount == 1) "Une photo" else "$burstCount photos · JPEG en mode Photo", "Changer") { burstCount = when (burstCount) { 1 -> 3; 3 -> 5; else -> 1 } }
                    SettingTile("Galerie", "Ouvrir la galerie installée sur le téléphone", "Ouvrir") { openSystemGallery() }
                    SectionTitle("INTELLIGENCE ARTIFICIELLE", "Traitement local sur le téléphone.")
                    SettingTile("Analyse IA", if (aiEnabled) "Analyse locale activée après capture" else "Analyse désactivée", if (aiEnabled) "Désactiver" else "Activer") { aiEnabled = !aiEnabled }
                    SettingTile("Agrandissement local ×2", if (localUpscale) "Activé · copie agrandie après capture" else "Désactivé", if (localUpscale) "Désactiver" else "Activer") { localUpscale = !localUpscale }
                    SettingTile("Amélioration du zoom", if (zoomEnhance) "Activée · netteté et réduction du bruit après capture" else "Désactivée", if (zoomEnhance) "Désactiver" else "Activer") { zoomEnhance = !zoomEnhance }
                    Text("Traitement hors ligne, sans modèle neuronal pour le moment. La photo originale est conservée ; les détails absents ne peuvent pas être recréés fidèlement.", color = Muted, fontSize = 12.sp)
                    SettingTile("Mise au point tactile", "Touchez l’aperçu pour faire le point ; l’AF continu reste actif", "Retour caméra") { page = AppPage.CAMERA }
                    SectionTitle("PORTRAIT IA", "Réglages du détourage et de la mosaïque d’arrière-plan.")
                    SettingTile("Afficher les réglages portrait", if (showPortraitControls) "Panneau de réglage visible" else "Panneau de réglage masqué", if (showPortraitControls) "Masquer" else "Afficher") { showPortraitControls = !showPortraitControls }
                    if (showPortraitControls) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Taille des blocs : $portraitPixelSize px", color = Color.White, fontSize = 12.sp)
                            Slider(value = portraitPixelSize.toFloat(), onValueChange = { portraitPixelSize = it.roundToInt() }, valueRange = 4f..48f, steps = 43)
                            Text("Forme des cellules", color = Color.White, fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf("Carré", "Arrondi", "Diamant").forEach { shape -> TextButton(onClick = { portraitShape = shape }) { Text(shape, color = if (portraitShape == shape) Accent else Muted) } }
                            }
                            Text("Adoucissement des contours : $portraitEdgeSoftness %", color = Color.White, fontSize = 12.sp)
                            Slider(value = portraitEdgeSoftness.toFloat(), onValueChange = { portraitEdgeSoftness = it.roundToInt() }, valueRange = 0f..100f, steps = 9)
                            Text("Lissage visuel : $portraitMosaicSmoothing %", color = Color.White, fontSize = 12.sp)
                            Slider(value = portraitMosaicSmoothing.toFloat(), onValueChange = { portraitMosaicSmoothing = it.roundToInt() }, valueRange = 0f..100f, steps = 9)
                            Text("Le lissage adoucit les cellules et les contours sans supprimer le détourage du sujet.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                    SectionTitle("PRODUCTION", "Informations de transparence et version de l’application.")
                    SettingTile("Confidentialité", "Permissions, stockage et traitement IA", "Consulter") { page = AppPage.PRIVACY }
                    SettingTile("À propos d’OmniCam AI", "Version, appareil et informations d’assistance", "Consulter") { page = AppPage.ABOUT }
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
                    ActionRow("Partager le profil de compatibilité", "Exporter les capacités de ce téléphone") {
                        val report = "OmniCam AI — profil de compatibilité\\nFabricant : ${Build.MANUFACTURER}\\nModèle : ${Build.MODEL}\\nAndroid : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\\n\\n${describeCameraHardware(context)}"
                        val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, report) }
                        runCatching { context.startActivity(Intent.createChooser(share, "Partager le profil OmniCam")) }
                            .onFailure { Toast.makeText(context, "Impossible de partager le profil.", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            AppPage.PRIVACY -> {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("CONFIDENTIALITÉ", "Informations sur les données utilisées par cette version.")
                    Text("Caméra : l’autorisation caméra est nécessaire pour afficher l’aperçu et prendre des photos ou vidéos. Vous pouvez la retirer à tout moment dans les paramètres Android.", color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
                    Text("Traitement IA : l’analyse de scène et la segmentation portrait utilisent les bibliothèques ML Kit dans l’application. Le code actuel ne déclare pas la permission INTERNET et ne contient pas de SDK d’analyse/traçage ; les traitements d’image décrits sont réalisés sur l’appareil.", color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
                    Text("Stockage : les photos et vidéos que vous prenez sont enregistrées dans la bibliothèque multimédia Android, dans les dossiers Pictures/OmniCam et Movies/OmniCam. Les fichiers peuvent contenir des métadonnées EXIF selon le chemin de capture utilisé.", color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
                    Text("Partage : si vous partagez un fichier depuis Android, le destinataire et l’application choisie peuvent recevoir ce fichier et ses métadonnées. Vérifiez les métadonnées avant de publier une photo.", color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
                    Text("Contact et suppression : les fichiers peuvent être supprimés depuis la galerie du téléphone. L’adresse de contact du responsable doit être publiée avant toute diffusion commerciale ; elle n’est pas encore configurée dans cette version.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    Text("Cette page est un résumé intégré, pas un avis juridique ni la politique publique définitive requise pour une publication sur une boutique.", color = Accent, fontSize = 12.sp, lineHeight = 18.sp)
                    TextButton(onClick = { page = AppPage.SETTINGS }) { Text("Retour aux paramètres", color = Accent) }
                }
            }
            AppPage.ABOUT -> {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("À PROPOS", "Informations techniques utiles au support.")
                    Text("OmniCam AI", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Version : ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "inconnue"}", color = Color.White, fontSize = 13.sp)
                    Text("Android : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", color = Color.White, fontSize = 13.sp)
                    Text("Appareil : ${Build.MANUFACTURER} ${Build.MODEL}", color = Color.White, fontSize = 13.sp)
                    Text("Les fonctions caméra varient selon les pilotes, les capteurs et les versions Android. Un rapport de compatibilité peut inclure ces informations techniques, mais ne doit jamais inclure de photos privées ni de secrets.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    Text("Support : adresse à configurer avant publication.", color = Accent, fontSize = 12.sp)
                    TextButton(onClick = { page = AppPage.SETTINGS }) { Text("Retour aux paramètres", color = Accent) }
                }
            }
            AppPage.GALLERY -> {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    MediaGalleryScreen(onBack = { page = AppPage.CAMERA })
                }
            }
            AppPage.AI -> {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("OMNICAM IA", "Outils disponibles dans cette version.")
                    SettingTile("Analyse de scène", "Classification locale de la photo après capture", "Voir dernière photo") { openSystemGallery() }
                    SettingTile("Dernier résultat", aiStatus, "Réinitialiser") { aiStatus = "Les photos sont analysées localement après la capture." }
                    SettingTile("Mode nuit", "Fusion multi-images pour réduire le bruit", "Activer") { selectedMode = "NUIT"; page = AppPage.CAMERA }
                    SettingTile("Mode portrait", "Détourage du sujet et fond légèrement pixelisé par IA locale", "Activer") { selectedMode = "PORTRAIT"; page = AppPage.CAMERA }
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
    videoQuality: String,
    videoFps: Int,
    availableVideoFps: List<Int>,
    availableVideoQuality: List<String>,
    photoResolution: String,
    onCaptureReady: (ImageCapture, Camera, VideoCapture<Recorder>) -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onTapToFocus: (PreviewView, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    } }
    LaunchedEffect(previewView) { onPreviewReady(previewView) }
    DisposableEffect(lifecycleOwner, lensFacing, selectedCameraId, flashEnabled, selectedMode, videoQuality, videoFps, photoResolution) {
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
                capture.flashMode = if (flashEnabled && lensFacing == CameraSelector.LENS_FACING_BACK && camera.cameraInfo.hasFlashUnit()) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
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
    localUpscale: Boolean,
    zoomEnhance: Boolean,
    portraitPixelSize: Int,
    portraitShape: String,
    portraitEdgeSoftness: Int,
    portraitMosaicSmoothing: Int,
    onAnalysis: (String) -> Unit
) {
    fun analyze(uri: android.net.Uri) {
        if (localUpscale || zoomEnhance) LocalPhotoEnhancer.process(context, uri, localUpscale, zoomEnhance, cameraExecutor) { result ->
            if (result == null) Toast.makeText(context, "Amélioration indisponible ; original conservé", Toast.LENGTH_SHORT).show()
        }
        if (!aiEnabled) { onAnalysis("Photo enregistrée · analyse IA désactivée"); return }
        try {
            ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS).process(InputImage.fromFilePath(context, uri))
                .addOnSuccessListener { labels ->
                    val summary = labels.sortedByDescending { it.confidence }.take(3)
                        .joinToString { it.text + " " + (it.confidence * 100).toInt() + "%" }
                    val message = if (summary.isBlank()) "Aucun objet reconnu sur la dernière photo." else "Résultat : $summary"
                    onAnalysis(message)
                }
                .addOnFailureListener { onAnalysis("Photo enregistrée, mais l’analyse IA a échoué.") }
        } catch (_: Exception) { onAnalysis("Photo enregistrée. Analyse IA indisponible.") }
    }

    if (mode == "HDR" || mode == "NUIT") {
        BurstCapture.capture(
            context = context,
            imageCapture = imageCapture,
            camera = camera,
            mode = mode,
            ioExecutor = cameraExecutor,
            onComplete = { uri ->
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
                        if (mode == "PORTRAIT") PortraitProcessor.process(context, pngUri, portraitPixelSize, portraitShape, portraitEdgeSoftness, portraitMosaicSmoothing) { portraitUri -> analyze(portraitUri ?: pngUri) } else analyze(pngUri)
                    } catch (e: Exception) { Toast.makeText(context, "Conversion PNG impossible : ${e.message}", Toast.LENGTH_LONG).show(); analyze(saved) }
                } else if (mode == "PORTRAIT") {
                    PortraitProcessor.process(context, saved, portraitPixelSize, portraitShape, portraitEdgeSoftness, portraitMosaicSmoothing) { portraitUri ->
                        if (portraitUri != null) {
                            analyze(portraitUri)
                        } else {
                            Toast.makeText(context, "Détourage indisponible ; photo originale conservée.", Toast.LENGTH_LONG).show()
                            analyze(saved)
                        }
                    }
                } else {
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

private fun loadLatestPhotoBitmap(context: android.content.Context): Bitmap? {
    return runCatching {
        val uri = context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
            if (cursor.moveToFirst()) android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)) else null
        } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}
