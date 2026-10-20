package com.omnicam.ai

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

/**
 * Lists the camera capabilities Android exposes on this device.
 * The camera HAL still decides which stream combinations can run together.
 */
fun describeCameraHardware(context: Context): String {
    return try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = manager.cameraIdList
        if (cameraIds.isEmpty()) return "Android n’a exposé aucun capteur caméra à l’application."

        cameraIds.joinToString(separator = "\n\n━━━━━━━━━━━━━━━━━━━━\n\n") { id ->
            val c = manager.getCameraCharacteristics(id)
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "Arrière"
                CameraCharacteristics.LENS_FACING_FRONT -> "Avant"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "Externe"
                else -> "Inconnu"
            }
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpeg = map?.getOutputSizes(ImageFormat.JPEG).orEmpty()
            val videoSizes = map?.getOutputSizes(android.media.MediaRecorder::class.java).orEmpty()
            val fpsRanges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
            val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                map?.getOutputSizes(ImageFormat.RAW_SENSOR).orEmpty()
            } else emptyArray()
            val maxJpeg = jpeg.maxByOrNull { it.width.toLong() * it.height.toLong() }
            val maxRaw = raw.maxByOrNull { it.width.toLong() * it.height.toLong() }
            val sensor = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.joinToString { "%.1f mm".format(java.util.Locale.US, it) } ?: "Non communiqué"
            val afModes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                ?.filter { mode ->
                    mode == CameraCharacteristics.CONTROL_AF_MODE_OFF ||
                        mode == CameraCharacteristics.CONTROL_AF_MODE_AUTO ||
                        mode == CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE ||
                        mode == CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO ||
                        mode == CameraCharacteristics.CONTROL_AF_MODE_EDOF
                }?.distinct()?.joinToString { mode ->
                    when (mode) {
                        CameraCharacteristics.CONTROL_AF_MODE_OFF -> "manuel"
                        CameraCharacteristics.CONTROL_AF_MODE_AUTO -> "autofocus"
                        CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "AF photo continu"
                        CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "AF vidéo continu"
                        CameraCharacteristics.CONTROL_AF_MODE_EDOF -> "EDOF"
                        else -> "autre"
                    }
                } ?: "Non communiqué"
            val exposure = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val iso = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL 3"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                else -> "Inconnu"
            }
            val physical = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { c.physicalCameraIds.joinToString().ifBlank { "Aucun déclaré" } }
                    .getOrDefault("Non disponible")
            } else "API Android 9+ requise"
            val jpegMp = maxJpeg?.let { "%.1f MP".format(java.util.Locale.US, it.width.toLong() * it.height / 1_000_000.0) }
                ?: "Non disponible"
            val rawMp = maxRaw?.let { "%.1f MP".format(java.util.Locale.US, it.width.toLong() * it.height / 1_000_000.0) }
                ?: "RAW non exposé"
            buildString {
                append("Caméra ID $id · $facing\n")
                append("Niveau Camera2 : $level\n")
                append("Capteur déclaré : ${sensor?.width ?: "?"} × ${sensor?.height ?: "?"} px")
                if (sensor != null) append(" (%.1f MP)".format(java.util.Locale.US, sensor.width.toLong() * sensor.height / 1_000_000.0))
                append("\nJPEG maximal exposé : $jpegMp")
                maxJpeg?.let { append(" (${it.width} × ${it.height})") }
                append("\nRAW/DNG possible via Camera2 : $rawMp")
                maxRaw?.let { append(" (${it.width} × ${it.height})") }
                append("\nFocale(s) : $focal")
                append("\nFlash : ${if (c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) "oui" else "non / non exposé"}")
                append("\nAutofocus : $afModes")
                append("\nISO : ${iso?.lower ?: "?"}–${iso?.upper ?: "?"}")
                append("\nExposition : ${exposure?.lower ?: "?"}–${exposure?.upper ?: "?"} ns")
                append("\nStabilisation optique : ${c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON) == true}")
                append("\nTailles JPEG disponibles : ${jpeg.size}")
                append("\nRésolutions JPEG : ${jpeg.sortedByDescending { it.width.toLong() * it.height }.take(12).joinToString { "${it.width}×${it.height}" }.ifBlank { "Non communiquées" }}")
                append("\nTailles vidéo exposées : ${videoSizes.size}")
                append("\nRésolutions vidéo : ${videoSizes.sortedByDescending { it.width.toLong() * it.height }.take(10).joinToString { "${it.width}×${it.height}" }.ifBlank { "Non communiquées" }}")
                append("\nPlages FPS annoncées : ${fpsRanges.joinToString { "${it.lower}–${it.upper} fps" }.ifBlank { "Non communiquées" }}")
                append("\nModules physiques annoncés : $physical")
            }
        }
    } catch (e: Exception) {
        "Impossible de lire les capacités Camera2 : ${e.localizedMessage ?: e.javaClass.simpleName}"
    }
}
