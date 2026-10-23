package com.omnicam.ai

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import java.util.concurrent.Executor
import kotlin.math.min

/**
 * Offline post-capture enhancement. This is conventional image processing, NOT neural
 * super-resolution. The original MediaStore entry is never modified.
 */
internal object LocalPhotoEnhancer {
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
        if (!upscale && !zoomEnhance) { onComplete(source); return }
        executor.execute {
            var result: Uri? = null
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(source).use { BitmapFactory.decodeStream(it, null, bounds) }
                require(bounds.outWidth > 0 && bounds.outHeight > 0)
                // Keep processing bounded on memory-constrained phones.
                var sample = 1
                while (bounds.outWidth / sample > 3000 || bounds.outHeight / sample > 3000) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val original = context.contentResolver.openInputStream(source).use { BitmapFactory.decodeStream(it, null, options) }
                    ?: error("Image illisible")
                val factor = if (upscale) upscaleFactor.coerceIn(2,16) else 1
                val width = original.width * factor
                val height = original.height * factor
                require(width.toLong() * height <= 24_000_000L)
                val scaled = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Canvas(scaled).drawBitmap(original, null, android.graphics.Rect(0, 0, width, height),
                    Paint(Paint.ANTI_ALIAS_FLAG or if (upscaleMethod == "Voisin proche") 0 else Paint.FILTER_BITMAP_FLAG))
                if (scaled !== original) original.recycle()
                if (zoomEnhance && (noiseMethod != "Désactivé" || smoothing > 0)) {
                    val src = IntArray(width * height)
                    scaled.getPixels(src, 0, width, 0, 0, width, height)
                    val dst = src.copyOf()
                    for (y in 1 until height - 1) for (x in 1 until width - 1) {
                        val i = y * width + x
                        val neighborhood = IntArray(9) { n -> src[(y + n / 3 - 1) * width + x + n % 3 - 1] }
                        fun channel(shift: Int): Int {
                            val center = (src[i] ushr shift) and 255
                            val values = neighborhood.map { (it ushr shift) and 255 }
                            val target = when (noiseMethod) {
                                "Médian" -> values.sorted()[4]
                                "Gaussien" -> values.zip(listOf(1,2,1,2,4,2,1,2,1)).sumOf { it.first * it.second } / 16
                                else -> values.sum() / 9
                            }
                            val mix = if (noiseMethod == "Désactivé") smoothing.coerceIn(0,100) / 100f else (0.5f + smoothing.coerceIn(0,100) / 200f)
                            return (center * (1 - mix) + target * mix).toInt().coerceIn(0,255)
                        }
                        dst[i] = (src[i] and -0x1000000) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                    }
                    scaled.setPixels(dst, 0, width, 0, 0, width, height)
                }
                val finalBitmap = if (zoomEnhance) {
                    // Local 3x3 unsharp mask with a small threshold to avoid boosting flat-area noise.
                    val pixels = IntArray(width * height)
                    scaled.getPixels(pixels, 0, width, 0, 0, width, height)
                    val output = pixels.copyOf()
                    for (y in 1 until height - 1) for (x in 1 until width - 1) {
                        val i = y * width + x
                        val center = pixels[i]
                        val neighbors = intArrayOf(pixels[i-1], pixels[i+1], pixels[i-width], pixels[i+width])
                        fun channel(shift: Int): Int {
                            val c = (center ushr shift) and 255
                            val avg = neighbors.sumOf { (it ushr shift) and 255 } / 4
                            val delta = c - avg
                            return if (kotlin.math.abs(delta) < 5) c else (c + delta * (sharpening.coerceIn(0,100)/100f)).toInt().coerceIn(0,255)
                        }
                        output[i] = (center and -0x1000000) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                    }
                    Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888).also { scaled.recycle() }
                } else scaled
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "OmniCam_Enhanced_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmniCam")
                    if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Impossible de créer la copie")
                try {
                    context.contentResolver.openOutputStream(uri)?.use {
                        check(finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it))
                    } ?: error("Flux indisponible")
                    if (Build.VERSION.SDK_INT >= 29) {
                        context.contentResolver.update(uri, ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        }, null, null)
                    }
                    result = uri
                } catch (e: Exception) {
                    context.contentResolver.delete(uri, null, null)
                    throw e
                } finally { finalBitmap.recycle() }
            } catch (_: Exception) {
                // Original remains available even when enhancement fails.
            }
            Handler(Looper.getMainLooper()).post { onComplete(result) }
        }
    }
}
