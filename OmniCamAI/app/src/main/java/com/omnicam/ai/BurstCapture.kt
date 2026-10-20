package com.omnicam.ai

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Small computational burst pipeline. HDR brackets exposure where the device supports EV
 * compensation; night stacks several frames to reduce uncorrelated noise. It does not perform
 * optical-flow alignment, so scenes with movement can show ghosting.
 */
internal object BurstCapture {
    fun capture(
        context: Context,
        imageCapture: ImageCapture,
        camera: Camera,
        mode: String,
        ioExecutor: Executor,
        onComplete: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val main = Handler(Looper.getMainLooper())
        val state = camera.cameraInfo.exposureState
        val originalIndex = state.exposureCompensationIndex
        val range = state.exposureCompensationRange
        val step = state.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: 1f
        val offsets = if (mode == "HDR" && range.lower < range.upper) {
            listOf((-2f / step).roundToInt(), 0, (2f / step).roundToInt())
        } else if (mode == "NUIT") listOf(0, 0, 0, 0)
        else listOf(0)
        val levels = offsets.map { (originalIndex + it).coerceIn(range.lower, range.upper) }
        val files = mutableListOf<File>()

        fun finishWithError(message: String) {
            camera.cameraControl.setExposureCompensationIndex(originalIndex)
            files.forEach { it.delete() }
            main.post { onError(message) }
        }

        fun captureAt(index: Int) {
            if (index >= levels.size) {
                camera.cameraControl.setExposureCompensationIndex(originalIndex).addListener({
                    ioExecutor.execute {
                        try {
                            val merged = merge(files, mode == "HDR")
                            val uri = saveJpeg(context, merged)
                            merged.recycle()
                            files.forEach { it.delete() }
                            if (uri == null) main.post { onError("Impossible d’enregistrer l’image fusionnée.") }
                            else main.post { onComplete(uri) }
                        } catch (e: Exception) {
                            files.forEach { it.delete() }
                            main.post { onError("Fusion multi-images impossible : ${e.localizedMessage ?: "erreur inconnue"}") }
                        }
                    }
                }, ioExecutor)
                return
            }

            camera.cameraControl.setExposureCompensationIndex(levels[index]).addListener({
                val file = File(context.cacheDir, "omnicam_burst_${System.nanoTime()}_${index}.jpg")
                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture.takePicture(options, ioExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        files.add(file)
                        captureAt(index + 1)
                    }
                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        finishWithError("Échec d’une image de la rafale : ${exception.message}")
                    }
                })
            }, ioExecutor)
        }
        captureAt(0)
    }

    private fun merge(files: List<File>, hdr: Boolean): Bitmap {
        require(files.isNotEmpty()) { "Aucune image capturée" }
        val bitmaps = files.map { file ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while ((bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > 8_000_000L) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1); inPreferredConfig = Bitmap.Config.ARGB_8888 }
            BitmapFactory.decodeFile(file.absolutePath, options) ?: error("JPEG illisible")
        }
        try {
            val width = bitmaps.minOf { it.width }
            val height = bitmaps.minOf { it.height }
            val scaled = bitmaps.map { if (it.width == width && it.height == height) it else Bitmap.createScaledBitmap(it, width, height, true) }
            try {
                val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val rows = scaled.map { IntArray(width) }
                val outRow = IntArray(width)
                for (y in 0 until height) {
                    scaled.forEachIndexed { index, bitmap -> bitmap.getPixels(rows[index], 0, width, 0, y, width, 1) }
                    for (x in 0 until width) {
                        var red = 0.0
                        var green = 0.0
                        var blue = 0.0
                        for (frame in rows.indices) {
                            val color = rows[frame][x]
                            var r = ((color shr 16) and 255) / 255.0
                            var g = ((color shr 8) and 255) / 255.0
                            var b = (color and 255) / 255.0
                            if (hdr) {
                                val ev = when (frame) { 0 -> -2.0; rows.lastIndex -> 2.0; else -> 0.0 }
                                val scale = 2.0.pow(-ev)
                                r = (r.pow(2.2) * scale).coerceAtMost(4.0)
                                g = (g.pow(2.2) * scale).coerceAtMost(4.0)
                                b = (b.pow(2.2) * scale).coerceAtMost(4.0)
                            }
                            red += r; green += g; blue += b
                        }
                        val n = rows.size.toDouble()
                        if (hdr) {
                            red = red / n; green = green / n; blue = blue / n
                            red = red / (1.0 + red); green = green / (1.0 + green); blue = blue / (1.0 + blue)
                            red = red.pow(1.0 / 2.2); green = green.pow(1.0 / 2.2); blue = blue.pow(1.0 / 2.2)
                        } else {
                            red /= n; green /= n; blue /= n
                        }
                        val r8 = (red * 255).roundToInt().coerceIn(0, 255)
                        val g8 = (green * 255).roundToInt().coerceIn(0, 255)
                        val b8 = (blue * 255).roundToInt().coerceIn(0, 255)
                        outRow[x] = (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
                    }
                    output.setPixels(outRow, 0, width, 0, y, width, 1)
                }
                return output
            } finally {
                scaled.forEachIndexed { index, bitmap -> if (bitmap !== bitmaps[index]) bitmap.recycle() }
            }
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    private fun saveJpeg(context: Context, bitmap: Bitmap): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "OmniCam_${System.currentTimeMillis()}_${if (Build.VERSION.SDK_INT >= 29) "fusion" else "photo"}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmniCam")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) error("Compression JPEG échouée")
            } ?: error("Flux de sortie indisponible")
            uri
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }
}
