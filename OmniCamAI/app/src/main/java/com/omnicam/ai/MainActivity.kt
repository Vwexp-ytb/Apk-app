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
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
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
    var showGrid by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var zoomLabel by remember { mutableStateOf("1×") }
    var exposureIndex by remember { mutableStateOf(0) }
    var capabilityText by remember { mutableStateOf("Lecture des capacités caméra…") }
    var aiStatus by remember { mutableStateOf("Les photos sont analysées localement après la capture.") }

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
        Header(page = page, onBack = { page = AppPage.CAMERA }, onSettings = { page = AppPage.SETTINGS })
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
                            lifecycleOwner = lifecycleOwner,
                            flashEnabled = flashEnabled,
                            selectedMode = selectedMode,
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
                        Text(
                            "OMNICAM  •  ${selectedMode}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                                .clip(RoundedCornerShape(8.dp)).background(Color(0x99090B0E)).padding(horizontal = 10.dp, vertical = 7.dp)
                        )
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
                                else capturePhoto(context, capture, boundCamera!!, selectedMode, cameraExecutor) { summary ->
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
                            isRecording = false
                        } else {
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
                    RoundAction(Icons.Default.Settings, "Réglages") { page = AppPage.SETTINGS }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                    listOf("HDR", "NUIT", "PORTRAIT", "PHOTO", "PRO").forEach { mode ->
                        ModeLabel(mode, selectedMode == mode) { selectedMode = mode }
                    }
                }
                if (selectedMode == "PRO") {
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
                    SettingTile("Flash / lampe", if (flashEnabled) "Activé" else "Désactivé", "Basculer") { flashEnabled = !flashEnabled }
                    SettingTile("Grille de composition", if (showGrid) "Visible" else "Masquée", "Basculer") { showGrid = !showGrid }
                    SettingTile("Caméra active", if (lensFacing == CameraSelector.LENS_FACING_BACK) "Arrière" else "Avant", "Changer d’objectif") {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                        zoomLabel = "1×"
                    }
                    SettingTile("Correction d’exposition", "Réglage EV disponible en mode Pro", "Ouvrir le mode Pro") { selectedMode = "PRO"; page = AppPage.CAMERA }
                    SectionTitle("APPAREIL", "Compatibilité dépendante du constructeur et du capteur.")
                    SettingTile("RAW / DNG et réglages manuels", "Capture Camera2 RAW avec ISO, temps de pose et mise au point", "Ouvrir") {
                        if (hasPermission) context.startActivity(Intent(context, RawCaptureActivity::class.java))
                        else permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    SettingTile("Capteurs et résolutions", "Inspecter les capacités Camera2", "Ouvrir") { page = AppPage.SENSORS }
                    SettingTile("Galerie", "Photos enregistrées dans Pictures/OmniCam", "Ouvrir") { openGallery(context) }
                    SectionTitle("INTELLIGENCE ARTIFICIELLE", "Traitement local sur le téléphone.")
                    SettingTile("Analyse IA", "Étiquetage d’image ML Kit après capture", "Détails") { page = AppPage.AI }
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
                    SettingTile("Mode nuit", "Capture priorisant la qualité, sans fusion multi-images", "Activer") { selectedMode = "NUIT"; page = AppPage.CAMERA }
                    SettingTile("Mode portrait", "Détourage du sujet et flou de fond par IA locale", "Activer") { selectedMode = "PORTRAIT"; page = AppPage.CAMERA }
                    Text("HDR et mode nuit utilisent une fusion multi-images. Le mode portrait utilise la segmentation locale ML Kit et un flou d’arrière-plan. La retouche générative et les commandes RAW restent à développer.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
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


}

@Composable
private fun Header(page: AppPage, onBack: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("OmniCam", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(when (page) {
                AppPage.CAMERA -> "CAMÉRA"
                AppPage.SETTINGS -> "PARAMÈTRES"
                AppPage.SENSORS -> "CAPTEURS"
                AppPage.AI -> "OMNICAM IA"
            }, color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
        }
        if (page == AppPage.CAMERA) {
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
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    flashEnabled: Boolean,
    selectedMode: String,
    onCaptureReady: (ImageCapture, Camera, VideoCapture<Recorder>) -> Unit,
    onTapToFocus: (PreviewView, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    } }
    DisposableEffect(lifecycleOwner, lensFacing, flashEnabled, selectedMode) {
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val captureMode = if (selectedMode == "PRO" || selectedMode == "NUIT") ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            val capture = ImageCapture.Builder().setCaptureMode(captureMode).build()
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build()
            val video = VideoCapture.withOutput(recorder)
            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.Builder().requireLensFacing(lensFacing).build(), preview, capture, video)
                camera.cameraControl.enableTorch(flashEnabled && lensFacing == CameraSelector.LENS_FACING_BACK)
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
    onAnalysis: (String) -> Unit
) {
    fun analyze(uri: android.net.Uri) {
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
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "OmniCam_$timestamp.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmniCam")
    }
    val output = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
    imageCapture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Photo enregistrée dans Pictures/OmniCam", Toast.LENGTH_SHORT).show()
                val saved = outputFileResults.savedUri
                if (saved == null) {
                    onAnalysis("Photo enregistrée, mais son URI est indisponible.")
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
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "Aucune application galerie disponible.", Toast.LENGTH_SHORT).show()
    }
}
