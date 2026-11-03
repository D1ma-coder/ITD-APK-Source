package com.itd.app.data.repository

import android.content.Context
import android.net.Uri
import com.itd.app.data.media.MediaPreparer
import com.itd.app.data.media.OptimizationConfig
import com.itd.app.data.model.FileUploadResponse
import com.itd.app.data.remote.FilesApi
import com.itd.app.data.remote.UploadContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(context: Context, private val api: FilesApi) {
    private val context = context.applicationContext

    /** Prepares the media (re-encoding, metadata stripping, transcoding), then uploads the result. */
    suspend fun upload(
        uri: Uri,
        uploadContext: UploadContext,
        config: OptimizationConfig = OptimizationConfig.POST,
        onProgress: (Long, Long?) -> Unit,
    ): FileUploadResponse {
        val prepared = MediaPreparer.prepare(context, uri, config)
        try {
            val bytes = withContext(Dispatchers.IO) { prepared.tempFile.readBytes() }
            return api.upload(prepared.filename, bytes, prepared.mime, uploadContext, onProgress)
        } finally {
            prepared.tempFile.delete()
        }
    }

    /** Uploads bytes that need no preparation, such as a PNG drawing. */
    suspend fun upload(name: String, bytes: ByteArray, mime: String, uploadContext: UploadContext, onProgress: (Long, Long?) -> Unit): FileUploadResponse =
        api.upload(name, bytes, mime, uploadContext, onProgress)
}
