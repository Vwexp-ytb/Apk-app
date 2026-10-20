package com.omnicam.ai

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

/** Sequential JPEG burst saved directly to MediaStore; avoids holding several full-size bitmaps. */
internal object PhotoBurst {
    fun capture(
        context: Context,
        imageCapture: ImageCapture,
        executor: Executor,
        count: Int,
        onComplete: (List<Uri>) -> Unit,
        onError: (String) -> Unit
    ) {
        val main = Handler(Looper.getMainLooper())
        val saved = mutableListOf<Uri>()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        fun captureNext(index: Int) {
            if (index >= count) {
                main.post { onComplete(saved.toList()) }
                return
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "OmniCam_Burst_${stamp}_${index + 1}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmniCam")
                }
            }
            val options = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ).build()
            imageCapture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    outputFileResults.savedUri?.let(saved::add)
                    captureNext(index + 1)
                }

                override fun onError(exception: ImageCaptureException) {
                    main.post {
                        onError("Rafale interrompue après ${saved.size}/$count photo(s) : ${exception.message ?: "erreur caméra"}")
                    }
                }
            })
        }

        captureNext(0)
    }
}
