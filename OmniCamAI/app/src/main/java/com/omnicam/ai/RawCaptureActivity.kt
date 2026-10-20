package com.omnicam.ai

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.DngCreator
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import android.util.Size
import android.hardware.camera2.params.StreamConfigurationMap
import java.io.OutputStream
import java.util.Locale
import kotlin.math.max

/**
 * Dedicated Camera2 RAW screen. It is separate from CameraX because DNG requires the original
 * RAW_SENSOR Image plus TotalCaptureResult/CameraCharacteristics for DngCreator.
 */
class RawCaptureActivity : Activity() {
    private lateinit var manager: CameraManager
    private lateinit var texture: TextureView
    private lateinit var cameraSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var isoLabel: TextView
    private lateinit var exposureLabel: TextView
    private lateinit var focusLabel: TextView
    private lateinit var isoSeek: SeekBar
    private lateinit var exposureSeek: SeekBar
    private lateinit var focusSeek: SeekBar
    private var cameraIds: List<String> = emptyList()
    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var characteristics: CameraCharacteristics? = null
    private var rawSize: Size? = null
    private var previewSize: Size? = null
    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    private var pendingImage: Image? = null
    private var pendingResult: TotalCaptureResult? = null
    private var selectedIso = 400
    private var selectedExposureNs = 10_000_000L
    private var selectedFocus = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            setBackgroundColor(android.graphics.Color.rgb(9, 11, 14))
        }
        root.addView(TextView(this).apply {
            text = "RAW / DNG · MODE MANUEL"
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Le DNG dépend de la prise en charge RAW_SENSOR du module sélectionné."
            textSize = 12f
            setTextColor(android.graphics.Color.LTGRAY)
        })
        cameraSpinner = Spinner(this)
        root.addView(cameraSpinner)
        texture = TextureView(this)
        root.addView(texture, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = label("Préparation Camera2…")
        root.addView(status)
        isoLabel = label("ISO")
        root.addView(isoLabel)
        isoSeek = SeekBar(this).apply { max = 1000; progress = 1000 * (selectedIso - 100) / 3100 }
        root.addView(isoSeek)
        exposureLabel = label("Temps de pose")
        root.addView(exposureLabel)
        exposureSeek = SeekBar(this).apply { max = 1000; progress = 100 }
        root.addView(exposureSeek)
        focusLabel = label("Mise au point")
        root.addView(focusLabel)
        focusSeek = SeekBar(this).apply { max = 1000; progress = 0 }
        root.addView(focusSeek)
        root.addView(Button(this).apply {
            text = "Capturer le fichier DNG"
            setOnClickListener { captureRaw() }
        })
        root.addView(Button(this).apply {
            text = "Retour à OmniCam"
            setOnClickListener { finish() }
        })
        setContentView(root)

        cameraIds = manager.cameraIdList.filter { id ->
            runCatching {
                val c = manager.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                    c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.RAW_SENSOR)?.isNotEmpty() == true
            }.getOrDefault(false)
        }
        if (cameraIds.isEmpty()) {
            status.text = "Aucun module arrière n’expose RAW_SENSOR à l’application."
            cameraSpinner.isEnabled = false
        } else {
            cameraSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cameraIds.map { id ->
                val c = manager.getCameraCharacteristics(id)
                val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
                if (focal == null) "Caméra $id" else "Caméra $id · ${String.format(Locale.US, "%.1f", focal)} mm"
            })
            cameraSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    closeCamera()
                    if (texture.isAvailable) openCamera()
                }
            }
        }
        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) { if (cameraIds.isNotEmpty()) openCamera() }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { closeCamera(); return true }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
        isoSeek.setOnSeekBarChangeListener(slider { progress ->
            val range = characteristics?.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            selectedIso = if (range == null) 400 else range.lower + ((range.upper - range.lower) * progress / 1000)
            isoLabel.text = "ISO manuel : $selectedIso"
        })
        exposureSeek.setOnSeekBarChangeListener(slider { progress ->
            val range = characteristics?.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            selectedExposureNs = if (range == null) 10_000_000L else range.lower + ((range.upper - range.lower) * progress / 1000)
            exposureLabel.text = "Temps de pose : ${(selectedExposureNs / 1_000_000.0).let { String.format(Locale.US, "%.2f", it) }} ms"
        })
        focusSeek.setOnSeekBarChangeListener(slider { progress ->
            val maxDiopters = characteristics?.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            selectedFocus = maxDiopters * progress / 1000f
            focusLabel.text = "Mise au point : ${String.format(Locale.US, "%.2f", selectedFocus)} dioptries"
        })
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        setTextColor(android.graphics.Color.LTGRAY)
    }

    private fun slider(onProgress: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onProgress(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    override fun onResume() {
        super.onResume()
        worker = HandlerThread("OmniCamCamera2Raw").also { it.start() }
        handler = Handler(worker!!.looper)
        if (::texture.isInitialized && texture.isAvailable && cameraIds.isNotEmpty()) openCamera()
    }

    override fun onPause() {
        closeCamera()
        worker?.quitSafely()
        runCatching { worker?.join() }
        worker = null
        handler = null
        super.onPause()
    }

    private fun openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Autorisation caméra nécessaire pour le mode RAW."
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 702)
            return
        }
        if (cameraIds.isEmpty() || cameraDevice != null) return
        val id = cameraIds.getOrNull(cameraSpinner.selectedItemPosition) ?: return
        try {
            characteristics = manager.getCameraCharacteristics(id)
            val map = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            rawSize = map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width.toLong() * it.height }
            previewSize = map?.getOutputSizes(SurfaceTexture::class.java)?.minByOrNull {
                kotlin.math.abs(it.width.toFloat() / it.height - texture.width.toFloat() / max(1, texture.height))
            } ?: Size(1280, 720)
            val raw = rawSize ?: run { status.text = "RAW_SENSOR indisponible"; return }
            val c = characteristics
            val isoRange = c?.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            if (isoRange != null) {
                selectedIso = selectedIso.coerceIn(isoRange.lower, isoRange.upper)
                if (isoRange.upper > isoRange.lower) isoSeek.progress = (selectedIso - isoRange.lower) * 1000 / (isoRange.upper - isoRange.lower)
            }
            isoLabel.text = "ISO manuel : $selectedIso (${isoRange?.lower ?: "?"}–${isoRange?.upper ?: "?"})"
            val exposureRange = c?.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            if (exposureRange != null) {
                selectedExposureNs = selectedExposureNs.coerceIn(exposureRange.lower, exposureRange.upper)
                if (exposureRange.upper > exposureRange.lower) exposureSeek.progress = (((selectedExposureNs - exposureRange.lower) * 1000) / (exposureRange.upper - exposureRange.lower)).toInt()
            }
            exposureLabel.text = "Temps de pose : ${selectedExposureNs / 1_000_000} ms"
            val maxFocus = c?.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            selectedFocus = selectedFocus.coerceIn(0f, maxFocus)
            if (maxFocus > 0f) focusSeek.progress = (selectedFocus / maxFocus * 1000f).toInt()
            focusLabel.text = "Mise au point : ${String.format(Locale.US, "%.2f", selectedFocus)} dioptries"
            imageReader?.close()
            imageReader = ImageReader.newInstance(raw.width, raw.height, ImageFormat.RAW_SENSOR, 2).also { reader ->
                reader.setOnImageAvailableListener({ source ->
                    pendingImage?.close()
                    pendingImage = source.acquireNextImage()
                    writeDngWhenReady()
                }, handler)
            }
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createPreviewSession()
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); cameraDevice = null }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close(); cameraDevice = null
                    runOnUiThread { status.text = "Erreur Camera2 : $error" }
                }
            }, handler)
        } catch (e: Exception) {
            status.text = "Ouverture RAW impossible : ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    @Deprecated("Deprecated in Android API; retained for minSdk compatibility")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 702 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && texture.isAvailable) {
            openCamera()
        } else if (requestCode == 702) {
            status.text = "Autorisation caméra refusée : le mode RAW est indisponible."
        }
    }

    private fun createPreviewSession() {
        val device = cameraDevice ?: return
        val textureSurface = texture.surfaceTexture ?: return
        val size = previewSize ?: Size(1280, 720)
        textureSurface.setDefaultBufferSize(size.width, size.height)
        val previewSurface = Surface(textureSurface)
        val rawSurface = imageReader?.surface ?: return
        try {
            device.createCaptureSession(listOf(previewSurface, rawSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    session = configured
                    try {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewSurface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }.build()
                        configured.setRepeatingRequest(request, null, handler)
                        runOnUiThread { status.text = "RAW prêt · ${rawSize?.width} × ${rawSize?.height}" }
                    } catch (e: Exception) { runOnUiThread { status.text = "Aperçu indisponible : ${e.message}" } }
                }
                override fun onConfigureFailed(configured: CameraCaptureSession) {
                    runOnUiThread { status.text = "Le fabricant refuse le flux RAW avec cet aperçu." }
                }
            }, handler)
        } catch (e: Exception) {
            runOnUiThread { status.text = "Session RAW impossible : ${e.localizedMessage}" }
        }
    }

    private fun captureRaw() {
        val device = cameraDevice
        val activeSession = session
        val rawSurface = imageReader?.surface
        if (device == null || activeSession == null || rawSurface == null) {
            Toast.makeText(this, "Caméra RAW non prête.", Toast.LENGTH_SHORT).show()
            return
        }
        pendingResult = null
        pendingImage?.close()
        pendingImage = null
        try {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(rawSurface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                set(CaptureRequest.SENSOR_SENSITIVITY, selectedIso)
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, selectedExposureNs)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                set(CaptureRequest.LENS_FOCUS_DISTANCE, selectedFocus)
            }.build()
            activeSession.capture(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    pendingResult = result
                    writeDngWhenReady()
                }
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: android.hardware.camera2.CaptureFailure) {
                    runOnUiThread { Toast.makeText(this@RawCaptureActivity, "Capture RAW échouée : ${failure.reason}", Toast.LENGTH_LONG).show() }
                }
            }, handler)
            status.text = "Capture RAW en cours…"
        } catch (e: Exception) {
            Toast.makeText(this, "Contrôles manuels non acceptés : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    @Synchronized
    private fun writeDngWhenReady() {
        val image = pendingImage ?: return
        val result = pendingResult ?: return
        val chars = characteristics ?: return
        pendingImage = null
        pendingResult = null
        handler?.post {
            var uri: android.net.Uri? = null
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "OmniCam_RAW_${System.currentTimeMillis()}.dng")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmniCam/RAW")
                }
                uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Impossible de créer le fichier DNG")
                contentResolver.openOutputStream(uri)?.use { stream ->
                    DngCreator(chars, result).use { creator -> creator.writeImage(stream, image) }
                } ?: error("Flux DNG indisponible")
                runOnUiThread { status.text = "DNG enregistré · ISO $selectedIso · ${selectedExposureNs / 1_000_000} ms"; Toast.makeText(this, "DNG enregistré dans Pictures/OmniCam/RAW", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                uri?.let { contentResolver.delete(it, null, null) }
                runOnUiThread { status.text = "Écriture DNG échouée : ${e.localizedMessage}" }
            } finally {
                image.close()
            }
        }
    }

    private fun closeCamera() {
        session?.close(); session = null
        cameraDevice?.close(); cameraDevice = null
        imageReader?.close(); imageReader = null
        pendingImage?.close(); pendingImage = null
        pendingResult = null
    }
}
