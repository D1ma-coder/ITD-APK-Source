package com.itd.app.data.remote

import com.itd.app.data.model.FileUploadResponse
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod

enum class UploadContext(val value: String) { POST("post"), COMMENT("comment"), OTHER("other") }

class FilesApi(private val client: ApiClient) {
    suspend fun upload(filename: String, bytes: ByteArray, mimeType: String, context: UploadContext,
        onProgress: (Long, Long?) -> Unit = { _, _ -> }): FileUploadResponse {
        val safeFilename = filename.replace("\"", "%22").replace("\r", "").replace("\n", "")
        val body = MultiPartFormDataContent(formData {
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentType, mimeType)
                append(HttpHeaders.ContentDisposition, "filename=\"$safeFilename\"")
            })
            append("context", context.value)
        })
        val response = client.send(HttpMethod.Post, "/api/files/upload", body, onProgress = onProgress)
        return client.json.decodeFromString(FileUploadResponse.serializer(), response.bodyAsText())
    }
}
