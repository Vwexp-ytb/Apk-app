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
                val output = runCatching {
                    enhanceAndSave(context, source, upscale, zoomEnhance, upscaleFactor,
                        upscaleMethod, noiseMethod, smoothing, sharpening)
                }.getOrNull()
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
                val count = targetWidth * targetHeight
                val src = IntArray(count)
                scaled!!.getPixels(src, 0, targetWidth, 0, 0, targetWidth, targetHeight)
                val dst = src.copyOf()
                val window = IntArray(9)
                val smooth = smoothing.coerceIn(0, 100) / 100f
                for (y in 1 until targetHeight - 1) {
                    var i = y * targetWidth + 1
                    for (x in 1 until targetWidth - 1) {
                        val pixel = src[i]
                        val r = filteredChannel(src, i, targetWidth, 16, noiseMethod, smooth, window)
                        val g = filteredChannel(src, i, targetWidth, 8, noiseMethod, smooth, window)
                        val b = filteredChannel(src, i, targetWidth, 0, noiseMethod, smooth, window)
                        dst[i] = (pixel and -0x1000000) or (r shl 16) or (g shl 8) or b
                        i++
                    }
                }
                scaled!!.setPixels(dst, 0, targetWidth, 0, 0, targetWidth, targetHeight)

                if (sharpening > 0) {
                    val input = dst
                    val sharpened = input.copyOf()
                    val amount = sharpening.coerceIn(0, 100) / 100f
                    for (y in 1 until targetHeight - 1) {
                        var i = y * targetWidth + 1
                        for (x in 1 until targetWidth - 1) {
                            val center = input[i]
                            val left = input[i - 1]
                            val right = input[i + 1]
                            val top = input[i - targetWidth]
                            val bottom = input[i + targetWidth]
                            fun channel(shift: Int): Int {
                                val c = (center ushr shift) and 255
                                val avg = (((left ushr shift) and 255) + ((right ushr shift) and 255) +
                                    ((top ushr shift) and 255) + ((bottom ushr shift) and 255)) / 4
                                val delta = c - avg
                                return if (kotlin.math.abs(delta) < 4) c
                                    else (c + delta * amount).toInt().coerceIn(0, 255)
                            }
                            sharpened[i] = (center and -0x1000000) or
                                (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                            i++
                        }
                    }
                    scaled!!.setPixels(sharpened, 0, targetWidth, 0, 0, targetWidth, targetHeight)
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
