package com.omnicam.ai

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** On-device portrait rendering. The person mask and background treatment are independent controls. */
internal object PortraitProcessor {
    fun process(
        context: Context,
        sourceUri: Uri,
        pixelSize: Int = 12,
        shape: String = "Carré",
        edgeSoftness: Int = 55,
        mosaicSmoothing: Int = 35,
        onResult: (Uri?) -> Unit
    ) {
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
            var original: Bitmap? = null
            var oriented: Bitmap? = null
            var background: Bitmap? = null
            var result: Bitmap? = null
            var outputUri: Uri? = null
            try {
                original = context.contentResolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it) }
                    ?: error("Image source illisible")
                oriented = rotateFromExif(context, sourceUri, original!!)
                if (oriented !== original) original?.recycle()
                val bitmap = oriented!!
                val maskWidth = segmentation.width
                val maskHeight = segmentation.height
                val mask = FloatArray(maskWidth * maskHeight)
                segmentation.buffer.asFloatBuffer().get(mask)

                // Build a compact mosaic once. Pixel size is expressed in source-image pixels.
                val cell = pixelSize.coerceIn(4, 48)
                val smallW = max(1, (bitmap.width + cell - 1) / cell)
                val smallH = max(1, (bitmap.height + cell - 1) / cell)
                val smoothing = mosaicSmoothing.coerceIn(0, 100) / 100f
                val lowResolution = Bitmap.createScaledBitmap(bitmap, smallW, smallH, false)
                background = Bitmap.createScaledBitmap(lowResolution, bitmap.width, bitmap.height, smoothing > 0.55f)
                if (lowResolution !== background) lowResolution.recycle()

                result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                val srcRow = IntArray(bitmap.width)
                val bgRow = IntArray(bitmap.width)
                val outRow = IntArray(bitmap.width)
                val softness = edgeSoftness.coerceIn(0, 100) / 100f
                val transition = 0.08f + softness * 0.42f
                for (y in 0 until bitmap.height) {
                    bitmap.getPixels(srcRow, 0, bitmap.width, 0, y, bitmap.width, 1)
                    background!!.getPixels(bgRow, 0, bitmap.width, 0, y, bitmap.width, 1)
                    val my = min(maskHeight - 1, y * maskHeight / bitmap.height)
                    for (x in 0 until bitmap.width) {
                        val mx = min(maskWidth - 1, x * maskWidth / bitmap.width)
                        val confidence = mask[my * maskWidth + mx].coerceIn(0f, 1f)
                        // Smoothstep mask transition reduces hard cut-out halos around hair and shoulders.
                        val lower = 0.42f - transition * 0.5f
                        val upper = 0.42f + transition * 0.5f
                        val t = ((confidence - lower) / (upper - lower).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                        val subjectAlpha = t * t * (3f - 2f * t)
                        val cellX = x % cell
                        val cellY = y % cell
                        val half = cell / 2f
                        val nx = abs((cellX + 0.5f - half) / half.coerceAtLeast(1f))
                        val ny = abs((cellY + 0.5f - half) / half.coerceAtLeast(1f))
                        val shapeAlpha = when (shape) {
                            "Arrondi" -> {
                                val distance = kotlin.math.sqrt(nx * nx + ny * ny)
                                ((1.18f - distance) * (1f + smoothing * 2f)).coerceIn(0f, 1f)
                            }
                            "Diamant" -> ((1.35f - (nx + ny)) * (1f + smoothing * 2f)).coerceIn(0f, 1f)
                            else -> 1f
                        }
                        val foreground = srcRow[x]
                        val mosaicBackground = bgRow[x]
                        // Apply the selected cell shape to the background first: opaque inside each cell,
                        // original background outside it, with a soft transition at rounded/diamond edges.
                        val shapedBackground = blendColor(mosaicBackground, foreground, shapeAlpha)
                        // The subject mask then restores the person over the shaped background.
                        outRow[x] = blendColor(foreground, shapedBackground, subjectAlpha)
                    }
                    result!!.setPixels(outRow, 0, bitmap.width, 0, y, bitmap.width, 1)
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "OmniCam_Portrait_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmniCam")
                }
                outputUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Impossible de créer l’image portrait")
                context.contentResolver.openOutputStream(outputUri!!)?.use {
                    if (!result!!.compress(Bitmap.CompressFormat.JPEG, 94, it)) error("Écriture JPEG échouée")
                } ?: error("Flux de sortie indisponible")
                onResult(outputUri)
            } catch (_: Exception) {
                outputUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
                onResult(null)
            } finally {
                if (original != null && original !== oriented) original?.recycle()
                oriented?.recycle()
                background?.recycle()
                result?.recycle()
                segmenter.close()
            }
        }.addOnFailureListener {
            segmenter.close()
            onResult(null)
        }
    }

    private fun blend(fg: Int, bg: Int, alpha: Float): Int =
        (fg * alpha + bg * (1f - alpha)).roundToInt().coerceIn(0, 255)

    private fun blendColor(first: Int, second: Int, alpha: Float): Int {
        val a = alpha.coerceIn(0f, 1f)
        return (0xFF shl 24) or
            (blend((first shr 16) and 255, (second shr 16) and 255, a) shl 16) or
            (blend((first shr 8) and 255, (second shr 8) and 255, a) shl 8) or
            blend(first and 255, second and 255, a)
    }

    private fun rotateFromExif(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
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
