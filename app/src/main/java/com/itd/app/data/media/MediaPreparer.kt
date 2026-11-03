package com.itd.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.itd.app.util.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

enum class MediaKind { PHOTO, VIDEO, DOCUMENT }

/** A file ready for upload, in the app cache; delete [tempFile] after use. */
data class PreparedMediaItem(
    val id: String,
    val kind: MediaKind,
    val tempFile: File,
    val filename: String,
    val mime: String,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
)

data class PhotoConfig(val reEncode: Boolean = true, val quality: Int = 90, val maxDimension: Int? = 1024)

data class VideoConfig(val transcode: Boolean = true)

data class SanitizationConfig(val stripGps: Boolean = true, val stripCamera: Boolean = true, val stripUserComment: Boolean = true)

data class OptimizationConfig(
    val photo: PhotoConfig = PhotoConfig(),
    val video: VideoConfig = VideoConfig(),
    val sanitization: SanitizationConfig = SanitizationConfig(),
) {
    companion object {
        /** Post attachments: photos re-encoded at quality 80 at their full size, videos transcoded, metadata stripped. */
        val POST = OptimizationConfig(PhotoConfig(reEncode = true, quality = 80, maxDimension = null))
    }
}

/** Shrinks and cleans media before upload: JPEG re-encoding, EXIF sanitising, H.264/AAC transcoding. */
object MediaPreparer {
    private val GPS = arrayOf(
        "GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef", "GPSAltitude", "GPSAltitudeRef", "GPSTimeStamp",
        "GPSDateStamp", "GPSProcessingMethod", "GPSAreaInformation", "GPSDestLatitude", "GPSDestLatitudeRef", "GPSDestLongitude",
        "GPSDestLongitudeRef", "GPSImgDirection", "GPSImgDirectionRef", "GPSSpeed", "GPSSpeedRef", "GPSTrack", "GPSTrackRef",
        "GPSMapDatum", "GPSVersionID", "GPSStatus", "GPSMeasureMode", "GPSDOP",
    )
    private val CAMERA = arrayOf("Make", "Model", "Software", "LensMake", "LensModel", "LensSerialNumber", "LensSpecification", "BodySerialNumber", "CameraOwnerName")
    private val USER_COMMENT = arrayOf("UserComment", "Artist", "Copyright", "ImageDescription", "Xmp")

    suspend fun prepare(context: Context, uri: Uri, config: OptimizationConfig): PreparedMediaItem {
        val type = context.contentResolver.getType(uri) ?: mimeFromExtension(uri)
        val kind = when {
            type == null || type.startsWith("image/") -> MediaKind.PHOTO
            type.startsWith("video/") -> MediaKind.VIDEO
            else -> MediaKind.DOCUMENT
        }
        return when (kind) {
            MediaKind.PHOTO -> withContext(Dispatchers.IO) { preparePhoto(context, uri, config) }
            MediaKind.VIDEO -> prepareVideo(context, uri, config.video)
            MediaKind.DOCUMENT -> withContext(Dispatchers.IO) { prepareDocument(context, uri) }
        }
    }

    private fun mimeFromExtension(uri: Uri): String? {
        val segment = uri.lastPathSegment ?: uri.path ?: return null
        val extension = segment.substringAfterLast('.', "")
        if (extension.isEmpty()) return null
        return when (extension.lowercase(Locale.ROOT)) {
            "jpg", "jpeg" -> "image/jpeg"
            "m4a" -> "audio/mp4"
            "m4v", "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "mov" -> "video/quicktime"
            "ogg" -> "audio/ogg"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "heic", "heif" -> "image/heic"
            "webm" -> "video/webm"
            "webp" -> "image/webp"
            else -> null
        }
    }

    private fun optimizedDir(context: Context) = File(context.cacheDir, "media-optimized").apply { if (!exists()) mkdirs() }

    private fun preparePhoto(context: Context, uri: Uri, config: OptimizationConfig): PreparedMediaItem {
        val target = File(optimizedDir(context), "${UUID.randomUUID()}.jpg")
        val photo = config.photo
        if (!photo.reEncode) {
            val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open input stream for $uri")
            input.use { source -> FileOutputStream(target).use { source.copyTo(it) } }
            sanitize(target, config.sanitization)
            return photoItem(target)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var width = bounds.outWidth
        var height = bounds.outHeight
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        var sample = 1
        val maxDimension = photo.maxDimension
        if (maxDimension != null && maxDimension > 0) {
            while (maxOf(width, height) / 2 >= maxDimension) {
                sample *= 2
                width /= 2
                height /= 2
            }
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (bitmap == null) {
            LogTag.MEDIA.tree().w("photo decode failed, copying %s as is", uri)
            val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open input stream for $uri")
            input.use { source -> FileOutputStream(target).use { source.copyTo(it) } }
        } else {
            if (rotation != 0) {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                if (rotated != bitmap) bitmap.recycle()
                bitmap = rotated
            }
            if (maxDimension != null) {
                val longest = maxOf(bitmap.width, bitmap.height)
                if (longest > maxDimension) {
                    val scale = maxDimension.toFloat() / longest
                    val scaled = Bitmap.createScaledBitmap(
                        bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1), (bitmap.height * scale).roundToInt().coerceAtLeast(1), true,
                    )
                    if (scaled != bitmap) bitmap.recycle()
                    bitmap = scaled
                }
            }
            FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, photo.quality, it) }
            bitmap.recycle()
            ExifInterface(target.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, "1")
                saveAttributes()
            }
        }
        sanitize(target, config.sanitization)
        return photoItem(target)
    }

    private fun photoItem(file: File): PreparedMediaItem {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return PreparedMediaItem(
            UUID.randomUUID().toString(), MediaKind.PHOTO, file, file.name, "image/jpeg", file.length(),
            bounds.outWidth.takeIf { it > 0 }, bounds.outHeight.takeIf { it > 0 }, null,
        )
    }

    /** Removes location, camera and author metadata, keeping the orientation. */
    fun sanitize(file: File, config: SanitizationConfig) {
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttribute(ExifInterface.TAG_ORIENTATION)
            if (config.stripGps) GPS.forEach { exif.setAttribute(it, null) }
            if (config.stripCamera) CAMERA.forEach { exif.setAttribute(it, null) }
            if (config.stripUserComment) USER_COMMENT.forEach { exif.setAttribute(it, null) }
            if (orientation != null) exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation)
            exif.saveAttributes()
        } catch (failure: Throwable) {
            LogTag.MEDIA.tree().w(failure, "metadata of %s not sanitized", file.name)
        }
    }

    private suspend fun prepareVideo(context: Context, uri: Uri, config: VideoConfig): PreparedMediaItem {
        val target = File(optimizedDir(context), "${UUID.randomUUID()}.mp4")
        if (config.transcode) {
            withContext(Dispatchers.Main) { transcode(context, uri, target) }
        } else {
            withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open input stream: $uri")
                input.use { source -> FileOutputStream(target).use { source.copyTo(it) } }
            }
        }
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(target.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            PreparedMediaItem(
                UUID.randomUUID().toString(), MediaKind.VIDEO, target, target.name, "video/mp4", target.length(),
                width?.takeIf { it > 0 }, height?.takeIf { it > 0 }, duration,
            )
        }
    }

    /** H.264 + AAC at a height of 1920, with media3 Transformer (it must run on a looper thread). */
    private suspend fun transcode(context: Context, uri: Uri, target: File) = suspendCancellableCoroutine { continuation ->
        val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(maxOf(1920, 1080)))))
            .build()
        val transformer = Transformer.Builder(context.applicationContext)
            .setLooper(Looper.myLooper() ?: Looper.getMainLooper())
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            })
            .build()
        try {
            transformer.start(item, target.absolutePath)
        } catch (failure: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(failure)
        }
        continuation.invokeOnCancellation { transformer.cancel() }
    }

    private fun prepareDocument(context: Context, uri: Uri): PreparedMediaItem {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val target = File(optimizedDir(context), "${UUID.randomUUID()}_$name")
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open input stream: $uri")
        input.use { source -> FileOutputStream(target).use { source.copyTo(it) } }
        val mime = context.contentResolver.getType(uri) ?: mimeFromExtension(uri) ?: "application/octet-stream"
        return PreparedMediaItem(UUID.randomUUID().toString(), MediaKind.DOCUMENT, target, name, mime, target.length(), null, null, null)
    }
}
