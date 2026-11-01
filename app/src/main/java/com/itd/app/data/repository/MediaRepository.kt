package com.itd.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.itd.app.data.model.FileUploadResponse
import com.itd.app.data.remote.FilesApi
import com.itd.app.data.remote.UploadContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(context: Context, private val api: FilesApi) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun upload(uri: Uri, context: UploadContext, onProgress: (Long, Long?) -> Unit): FileUploadResponse {
        val file = withContext(Dispatchers.IO) {
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: "attachment"
            val bytes = requireNotNull(resolver.openInputStream(uri)) { "Не удалось открыть файл" }.use { it.readBytes() }
            Triple(name, mime, bytes)
        }
        return api.upload(file.first, file.third, file.second, context, onProgress)
    }
}
