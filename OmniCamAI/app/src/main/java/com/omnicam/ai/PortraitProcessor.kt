package com.omnicam.ai

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** On-device portrait effect: ML Kit person mask + soft low-resolution background blur. */
internal object PortraitProcessor {
    fun process(context: Context, sourceUri: Uri, onResult: (Uri?) -> Unit) {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(options)
        val task = try {
            segmenter.process(InputImage.fromFilePath(context, sourceUri))
        } catch (_: Exception) {
            segmenter.close()
            onResult(null)
            return
        }
        task.addOnSuccessListener { segmentation ->
            try {
                val original = context.contentResolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it) }
                    ?: error("Image source illisible")
                val bitmap = rotateFromExif(context, sourceUri, original)
                if (bitmap !== original) original.recycle()
                val maskBuffer = segmentation.buffer.asFloatBuffer()
                val maskWidth = segmentation.width
                val maskHeight = segmentation.height
                val mask = FloatArray(maskWidth * maskHeight)
                maskBuffer.get(mask)
                val smallW = max(1, bitmap.width / 12)
                val smallH = max(1, bitmap.height / 12)
                val small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)
                val blurred = Bitmap.createScaledBitmap(small, bitmap.width, bitmap.height, true)
                if (small !== blurred) small.recycle()
                val srcPixels = IntArray(bitmap.width * bitmap.height)
                val bgPixels = IntArray(srcPixels.size)
                bitmap.getPixels(srcPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                blurred.getPixels(bgPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val out = IntArray(srcPixels.size)
                for (y in 0 until bitmap.height) {
                    val my = min(maskHeight - 1, y * maskHeight / bitmap.height)
                    for (x in 0 until bitmap.width) {
                        val mx = min(maskWidth - 1, x * maskWidth / bitmap.width)
                        val confidence = mask[my * maskWidth + mx].coerceIn(0f, 1f)
                        val alpha = ((confidence - 0.25f) / 0.5f).coerceIn(0f, 1f)
                        val i = y * bitmap.width + x
                        val fg = srcPixels[i]
                        val bg = bgPixels[i]
                        val r = blend((fg shr 16) and 255, (bg shr 16) and 255, alpha)
                        val g = blend((fg shr 8) and 255, (bg shr 8) and 255, alpha)
                        val b = blend(fg and 255, bg and 255, alpha)
                        out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
                val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                result.setPixels(out, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "OmniCam_Portrait_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmniCam")
                }
                val outputUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Impossible de créer l’image portrait")
                try {
                    context.contentResolver.openOutputStream(outputUri)?.use {
                        if (!result.compress(Bitmap.CompressFormat.JPEG, 94, it)) error("Écriture JPEG échouée")
                    } ?: error("Flux de sortie indisponible")
                } catch (e: Exception) {
                    context.contentResolver.delete(outputUri, null, null)
                    throw e
                } finally {
                    result.recycle()
                    bitmap.recycle()
                    blurred.recycle()
                }
                onResult(outputUri)
            } catch (_: Exception) {
                onResult(null)
            } finally {
                segmenter.close()
            }
        }.addOnFailureListener {
            segmenter.close()
            onResult(null)
        }
    }

    private fun blend(fg: Int, bg: Int, alpha: Float): Int =
        (fg * alpha + bg * (1f - alpha)).roundToInt().coerceIn(0, 255)

    private fun rotateFromExif(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
