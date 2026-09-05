package me.rerere.rikkahub.data.files

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.uuid.Uuid

data class SafEntry(val uri: Uri, val name: String, val isDir: Boolean)

object FileUtils {
    private const val TAG = "FileUtils"

    suspend fun collectSafFiles(
        context: Context,
        dir: DocumentFile,
        relativePath: String = "",
        dispatcher: CoroutineDispatcher,
    ): Map<String, ByteArray> = coroutineScope {
        listSafChildren(context, dir).map { entry ->
            async(dispatcher) {
                val childPath = if (relativePath.isBlank()) entry.name else "$relativePath/${entry.name}"
                if (entry.isDir) {
                    val childDir = DocumentFile.fromTreeUri(context, entry.uri) ?: return@async emptyMap()
                    collectSafFiles(context, childDir, childPath, dispatcher)
                } else {
                    val bytes = runCatching {
                        context.contentResolver.openInputStream(entry.uri)?.use { it.readBytes() }
                    }.getOrNull() ?: return@async emptyMap()
                    mapOf(childPath to bytes)
                }
            }
        }.awaitAll().fold(LinkedHashMap()) { acc, map -> acc.putAll(map); acc }
    }

    fun listSafChildren(context: Context, dir: DocumentFile): List<SafEntry> {
        val childrenUri = runCatching {
            val documentId = DocumentsContract.getDocumentId(dir.uri)
            DocumentsContract.buildChildDocumentsUriUsingTree(dir.uri, documentId)
        }.getOrNull() ?: return emptyList()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        )
        return runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        if (nameIndex < 0 || idIndex < 0) continue
                        val name = cursor.getString(nameIndex) ?: continue
                        val documentId = cursor.getString(idIndex) ?: continue
                        val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) else ""
                        val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                        add(SafEntry(DocumentsContract.buildDocumentUriUsingTree(dir.uri, documentId), name, isDir))
                    }
                }
            }
        }.onFailure {
            Log.w(TAG, "listSafChildren: Failed to list ${dir.uri}", it)
        }.getOrNull() ?: emptyList()
    }

    fun buildUuidFileName(displayName: String?, mimeType: String?): String {
        val extFromName = displayName
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() && it != displayName }
            ?.lowercase()
        val extFromMime = mimeType
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it.lowercase()) }
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
        val ext = extFromName ?: extFromMime ?: "bin"
        return "${Uuid.random()}.$ext"
    }

    fun buildRelativePath(folder: String, file: File): String =
        "$folder/${file.name}"

    fun getRelativePathInFilesDir(filesDir: File, file: File): String? {
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return null
        val canonicalFilesDir = runCatching { filesDir.canonicalFile }.getOrNull() ?: return null
        val basePath = canonicalFilesDir.path
        val filePath = canonicalFile.path
        if (!filePath.startsWith("$basePath${File.separator}")) {
            return null
        }
        return canonicalFile.relativeTo(canonicalFilesDir).path.replace(File.separatorChar, '/')
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            var fileName: String? = null
            val projection = arrayOf(
                OpenableColumns.DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val documentDisplayNameIndex =
                        cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (documentDisplayNameIndex != -1) {
                        fileName = cursor.getString(documentDisplayNameIndex)
                    } else {
                        val openableDisplayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (openableDisplayNameIndex != -1) {
                            fileName = cursor.getString(openableDisplayNameIndex)
                        }
                    }
                }
            }
            fileName
        }.onFailure {
            Log.w(TAG, "getFileNameFromUri: Failed to query display name for $uri", it)
        }.getOrNull()
    }

    fun getFileMimeType(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> runCatching {
                context.contentResolver.getType(uri)
            }.onFailure {
                Log.w(TAG, "getFileMimeType: Failed to resolve MIME for $uri", it)
            }.getOrNull()
            else -> null
        }
    }

    fun guessMimeType(file: File, fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty()) {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"
        }
        return sniffMimeType(file)
    }

    fun compressBitmapToPng(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        it.toByteArray()
    }

    private fun sniffMimeType(file: File): String {
        val header = ByteArray(16)
        val read = runCatching {
            FileInputStream(file).use { input ->
                input.read(header)
            }
        }.getOrDefault(-1)

        if (read <= 0) return "application/octet-stream"

        if (header.startsWithBytes(0x89, 0x50, 0x4E, 0x47)) return "image/png"
        if (header.startsWithBytes(0xFF, 0xD8, 0xFF)) return "image/jpeg"
        if (header.startsWithBytes(0x47, 0x49, 0x46, 0x38)) return "image/gif"
        if (header.startsWithBytes(0x25, 0x50, 0x44, 0x46)) return "application/pdf"
        if (header.startsWithBytes(0x50, 0x4B, 0x03, 0x04)) return "application/zip"
        if (header.startsWithBytes(0x50, 0x4B, 0x05, 0x06)) return "application/zip"
        if (header.startsWithBytes(0x50, 0x4B, 0x07, 0x08)) return "application/zip"
        if (header.startsWithBytes(0x52, 0x49, 0x46, 0x46) && header.sliceArray(8..11)
                .contentEquals(byteArrayOf(0x57, 0x45, 0x42, 0x50))
        ) {
            return "image/webp"
        }

        val textSample = runCatching {
            val sample = ByteArray(512)
            FileInputStream(file).use { input ->
                val len = input.read(sample)
                if (len <= 0) return@runCatching null
                sample.copyOf(len)
            }
        }.getOrNull()
        if (textSample != null && isLikelyText(textSample)) {
            return "text/plain"
        }

        return "application/octet-stream"
    }

    private fun isLikelyText(bytes: ByteArray): Boolean {
        var printable = 0
        var total = 0
        bytes.forEach { b ->
            val c = b.toInt() and 0xFF
            total += 1
            if (c == 0x09 || c == 0x0A || c == 0x0D) {
                printable += 1
            } else if (c in 0x20..0x7E) {
                printable += 1
            }
        }
        return total > 0 && printable.toDouble() / total >= 0.8
    }

    private fun ByteArray.startsWithBytes(vararg values: Int): Boolean {
        if (this.size < values.size) return false
        for (i in values.indices) {
            if ((this[i].toInt() and 0xFF) != values[i]) return false
        }
        return true
    }
}
