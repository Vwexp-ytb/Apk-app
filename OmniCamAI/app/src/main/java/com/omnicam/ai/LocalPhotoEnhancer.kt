package com.omnicam.ai

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import java.util.Arrays
import android.util.Log
import java.util.concurrent.Executor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Offline, conventional bitmap enhancement. This is not neural super-resolution.
 * The source photo is never edited; output dimensions and temporary memory are bounded.
 */
internal object LocalPhotoEnhancer {
    private const val MAX_PIXELS = 8_000_000L
    private const val MAX_DIMENSION = 4096
    private const val BAND_ROWS = 96

    fun process(
        context: Context,
        source: Uri,
        upscale: Boolean,
        zoomEnhance: Boolean,
        upscaleFactor: Int,
        upscaleMethod: String,
        noiseMethod: String,
        smoothing: Int,
        sharpening: Int,
        executor: Executor,
        onComplete: (Uri?) -> Unit
    ) {
        if (!upscale && !zoomEnhance) {
            onComplete(source)
            return
        }
        try {
            executor.execute {
                val output = try {
                    enhanceAndSave(context, source, upscale, zoomEnhance, upscaleFactor,
                        upscaleMethod, noiseMethod, smoothing, sharpening)
                } catch (error: OutOfMemoryError) {
                    Log.e("OmniCamEnhance", "Not enough memory while enhancing image", error)
                    null
                } catch (error: Exception) {
                    Log.e("OmniCamEnhance", "Local image enhancement failed", error)
                    null
                }
                Handler(Looper.getMainLooper()).post { onComplete(output) }
            }
        } catch (_: Exception) {
            Handler(Looper.getMainLooper()).post { onComplete(null) }
        }
    }

    private fun enhanceAndSave(
        context: Context,
        source: Uri,
        upscale: Boolean,
        zoomEnhance: Boolean,
        upscaleFactor: Int,
        upscaleMethod: String,
        noiseMethod: String,
        smoothing: Int,
        sharpening: Int
    ): Uri? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val requestedFactor = if (upscale) upscaleFactor.coerceIn(2, 8) else 1
        val decodePixelLimit = MAX_PIXELS / (requestedFactor.toLong() * requestedFactor)
        var sample = 1
        while ((bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > decodePixelLimit ||
            bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val original = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        var scaled: Bitmap? = null
        var finalBitmap: Bitmap? = null
        var outputUri: Uri? = null
        try {
            val pixelLimitedFactor = sqrt(MAX_PIXELS.toDouble() / (original.width.toLong() * original.height))
                .toInt().coerceAtLeast(1)
            val factor = minOf(requestedFactor, pixelLimitedFactor).coerceAtLeast(1)
            val width = (original.width.toLong() * factor).coerceAtMost(MAX_DIMENSION.toLong() * 2).toInt()
            val height = (original.height.toLong() * factor).coerceAtMost(MAX_DIMENSION.toLong() * 2).toInt()
            // Re-check pixel count after dimension caps; never allocate an unbounded bitmap.
            val safeScale = minOf(1.0, sqrt(MAX_PIXELS.toDouble() / (width.toLong() * height)))
            val targetWidth = max(1, (width * safeScale).toInt())
            val targetHeight = max(1, (height * safeScale).toInt())
            scaled = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = upscaleMethod != "Voisin proche"
                isDither = true
            }
            Canvas(scaled!!).drawBitmap(original, null, Rect(0, 0, targetWidth, targetHeight), paint)

            if (zoomEnhance && (noiseMethod != "Désactivé" || smoothing > 0 || sharpening > 0)) {
                // Work in bounded horizontal bands rather than full-image pixel arrays.
                val smooth = smoothing.coerceIn(0, 100) / 100f
                if (noiseMethod != "Désactivé" || smoothing > 0) {
                    processBands(scaled!!, targetWidth, targetHeight, BAND_ROWS, noiseMethod, smooth, 0f)
                }
                if (sharpening > 0) {
                    processBands(scaled!!, targetWidth, targetHeight, BAND_ROWS, "Désactivé", 0f,
                        sharpening.coerceIn(0, 100) / 100f)
                }
            }
            finalBitmap = scaled
            scaled = null

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "OmniCam_Enhanced_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmniCam")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            outputUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            val bitmap = finalBitmap ?: return null
            val wrote = context.contentResolver.openOutputStream(outputUri!!)?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it)
            } ?: false
            check(wrote) { "Image JPEG non écrite" }
            if (Build.VERSION.SDK_INT >= 29) {
                val updated = context.contentResolver.update(outputUri!!,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                check(updated > 0) { "Impossible de finaliser la photo" }
            }
            return outputUri
        } catch (_: OutOfMemoryError) {
            outputUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            return null
        } catch (_: Exception) {
            outputUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            return null
        } finally {
            original.recycle()
            scaled?.recycle()
            finalBitmap?.recycle()
        }
    }

    /**
     * Bounded band processing with a one-row halo for neighborhood filters.
     * This reduces scratch arrays from image-sized to width * (bandRows + 2).
     */
    private fun processBands(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        bandRows: Int,
        noiseMethod: String,
        smoothing: Float,
        sharpening: Float
    ) {
        if (width < 3 || height < 3) return
        var coreTop = 1
        while (coreTop < height - 1) {
            val coreBottom = minOf(height - 1, coreTop + bandRows)
            val readTop = coreTop - 1
            val bandHeight = coreBottom - readTop + 1
            val input = IntArray(width * bandHeight)
            bitmap.getPixels(input, 0, width, 0, readTop, width, bandHeight)
            val output = IntArray(width * (coreBottom - coreTop))
            val window = IntArray(9)
            for (y in coreTop until coreBottom) {
                val localY = y - readTop
                for (x in 1 until width - 1) {
                    val index = localY * width + x
                    val pixel = input[index]
                    fun channel(shift: Int): Int {
                        if (sharpening > 0f) {
                            val center = (pixel ushr shift) and 255
                            val left = (input[index - 1] ushr shift) and 255
                            val right = (input[index + 1] ushr shift) and 255
                            val top = (input[index - width] ushr shift) and 255
                            val bottom = (input[index + width] ushr shift) and 255
                            val average = (left + right + top + bottom) / 4
                            val delta = center - average
                            return if (kotlin.math.abs(delta) < 4) center
                                else (center + delta * sharpening).toInt().coerceIn(0, 255)
                        }
                        return filteredChannel(input, index, width, shift, noiseMethod, smoothing, window)
                    }
                    val outputIndex = (y - coreTop) * width + x
                    output[outputIndex] = (pixel and -0x1000000) or
                        (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                }
            }
            bitmap.setPixels(output, 0, width, 0, coreTop, width, coreBottom - coreTop)
            coreTop = coreBottom
        }
    }

    private fun filteredChannel(
        pixels: IntArray,
        index: Int,
        width: Int,
        shift: Int,
        noiseMethod: String,
        smoothing: Float,
        window: IntArray
    ): Int {
        val center = (pixels[index] ushr shift) and 255
        if (noiseMethod == "Désactivé" && smoothing <= 0f) return center
        var n = 0
        var sum = 0
        for (dy in -1..1) for (dx in -1..1) {
            val value = (pixels[index + dy * width + dx] ushr shift) and 255
            window[n++] = value
            sum += value
        }
        val target = when (noiseMethod) {
            "Médian" -> {
                Arrays.sort(window)
                window[4]
            }
            "Gaussien" -> {
                val c = index
                (((pixels[c - width - 1] ushr shift) and 255) +
                    2 * ((pixels[c - width] ushr shift) and 255) +
                    ((pixels[c - width + 1] ushr shift) and 255) +
                    2 * ((pixels[c - 1] ushr shift) and 255) +
                    4 * center +
                    2 * ((pixels[c + 1] ushr shift) and 255) +
                    ((pixels[c + width - 1] ushr shift) and 255) +
                    2 * ((pixels[c + width] ushr shift) and 255) +
                    ((pixels[c + width + 1] ushr shift) and 255)) / 16
            }
            else -> sum / 9
        }
        val mix = if (noiseMethod == "Désactivé") smoothing
            else (0.35f + smoothing * 0.65f).coerceIn(0f, 1f)
        return (center * (1f - mix) + target * mix).toInt().coerceIn(0, 255)
    }
}
