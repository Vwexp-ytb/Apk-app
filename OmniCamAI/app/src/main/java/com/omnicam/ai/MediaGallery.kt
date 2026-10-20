package com.omnicam.ai

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

private data class OmniMediaItem(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val dateAdded: Long
)

private fun queryOmniMedia(context: Context): List<OmniMediaItem> {
    val resolver = context.contentResolver
    val collection = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.DATE_ADDED
    )
    val result = mutableListOf<OmniMediaItem>()
    resolver.query(
        collection,
        projection,
        "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
        arrayOf("OmniCam%"),
        "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        while (cursor.moveToNext()) {
            val mime = cursor.getString(mimeColumn) ?: continue
            if (!mime.startsWith("image/") && !mime.startsWith("video/")) continue
            result += OmniMediaItem(
                ContentUris.withAppendedId(collection, cursor.getLong(idColumn)),
                cursor.getString(nameColumn) ?: "Média OmniCam",
                mime,
                cursor.getLong(dateColumn)
            )
        }
    }
    return result
}

@Composable
internal fun MediaGalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }
    var selectedForDelete by remember { mutableStateOf<OmniMediaItem?>(null) }
    var selectedForDetails by remember { mutableStateOf<OmniMediaItem?>(null) }
    val media = remember(refreshKey) { runCatching { queryOmniMedia(context) }.getOrDefault(emptyList()) }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Galerie OmniCam", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Text(
            "Les médias affichés sont les fichiers dont le nom commence par OmniCam. Les photos personnelles des autres applications ne sont pas listées.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
        if (media.isEmpty()) {
            Text("Aucun média OmniCam trouvé. Prenez une photo ou une vidéo, puis actualisez.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(media, key = { it.uri.toString() }) { item ->
                    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                        Text(
                            "${if (item.mimeType.startsWith("video/")) "Vidéo" else "Photo"} · ${DateFormat.getDateTimeInstance().format(Date(item.dateAdded * 1000))}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val view = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(item.uri, item.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(view) }
                            }) { Text("Ouvrir") }
                            OutlinedButton(onClick = { selectedForDetails = item }) { Text("Détails") }
                            OutlinedButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = item.mimeType
                                    putExtra(Intent.EXTRA_STREAM, item.uri)
                                    clipData = android.content.ClipData.newUri(context.contentResolver, item.name, item.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(Intent.createChooser(send, "Partager le média")) }
                            }) { Text("Partager") }
                            OutlinedButton(onClick = { selectedForDelete = item }) { Text("Supprimer") }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refreshKey++ }) { Text("Actualiser") }
            Button(onClick = onBack) { Text("Retour") }
        }
    }

    selectedForDetails?.let { item ->
        val details = remember(item.uri) {
            runCatching {
                context.contentResolver.openInputStream(item.uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val tags = listOf(
                        ExifInterface.TAG_DATETIME to "Date EXIF",
                        ExifInterface.TAG_MAKE to "Fabricant",
                        ExifInterface.TAG_MODEL to "Modèle",
                        ExifInterface.TAG_IMAGE_WIDTH to "Largeur",
                        ExifInterface.TAG_IMAGE_LENGTH to "Hauteur",
                        ExifInterface.TAG_EXPOSURE_TIME to "Temps de pose",
                        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY to "ISO",
                        ExifInterface.TAG_F_NUMBER to "Ouverture",
                        ExifInterface.TAG_FOCAL_LENGTH to "Focale"
                    )
                    tags.mapNotNull { (tag, label) -> exif.getAttribute(tag)?.let { "$label : $it" } }
                        .ifEmpty { listOf("Aucune métadonnée EXIF lisible dans ce fichier.") }
                        .joinToString("\\n")
                } ?: "Impossible d’ouvrir le fichier."
            }.getOrElse { "Métadonnées indisponibles : ${it.localizedMessage ?: "erreur de lecture"}" }
        }
        AlertDialog(
            onDismissRequest = { selectedForDetails = null },
            title = { Text(item.name) },
            text = { Text("Type : ${item.mimeType}\\nDate du fichier : ${DateFormat.getDateTimeInstance().format(Date(item.dateAdded * 1000))}\\n\\n$details") },
            confirmButton = { TextButton(onClick = { selectedForDetails = null }) { Text("Fermer") } }
        )
    }

    selectedForDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedForDelete = null },
            title = { Text("Supprimer ce média ?") },
            text = { Text("${item.name}\nCette action supprime le fichier de la bibliothèque multimédia du téléphone.") },
            confirmButton = {
                TextButton(onClick = {
                    val deleted = runCatching { context.contentResolver.delete(item.uri, null, null) > 0 }.getOrDefault(false)
                    selectedForDelete = null
                    if (deleted) refreshKey++
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { selectedForDelete = null }) { Text("Annuler") } }
        )
    }
}
