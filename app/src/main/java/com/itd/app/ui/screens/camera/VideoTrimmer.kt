package com.itd.app.ui.screens.camera

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import com.itd.app.util.LogTag
import java.io.File
import java.nio.ByteBuffer

/** Cuts a recorded video without re-encoding: samples between two times are copied into a new MP4. */
object VideoTrimmer {
    fun durationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (failure: Throwable) {
            LogTag.CAMERA.tree().e(failure, "duration of %s", file.name)
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun rotation(file: File): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        } catch (_: Throwable) {
            0
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
            }
        }
    }

    fun trim(source: File, startMs: Long, endMs: Long, target: File) {
        val startUs = startMs * 1000
        val endUs = endMs * 1000
        val extractor = MediaExtractor()
        extractor.setDataSource(source.absolutePath)
        val muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val tracks = LinkedHashMap<Int, Int>()
            val isVideo = LinkedHashMap<Int, Boolean>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString("mime") ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    tracks[i] = muxer.addTrack(format)
                    isVideo[i] = mime.startsWith("video/")
                }
            }
            if (tracks.isEmpty()) throw IllegalStateException("No video/audio tracks in source")
            val rotation = rotation(source)
            if (rotation != 0) muxer.setOrientationHint(rotation)
            muxer.start()
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            for ((track, muxerTrack) in tracks) {
                extractor.selectTrack(track)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                var samples = 0
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val time = extractor.sampleTime
                    if (time > endUs) break
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = time - startUs
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    if (info.presentationTimeUs >= 0) {
                        muxer.writeSampleData(muxerTrack, buffer, info)
                        samples++
                    }
                    extractor.advance()
                }
                extractor.unselectTrack(track)
                LogTag.CAMERA.tree().d("trim track %d video=%s samples=%d", track, isVideo[track] == true, samples)
            }
            try {
                muxer.stop()
            } catch (_: Throwable) {
            }
            muxer.release()
            extractor.release()
            LogTag.CAMERA.tree().d("trimmed %s (%d bytes)", target.name, target.length())
        } catch (failure: Throwable) {
            LogTag.CAMERA.tree().e(failure, "trim failed")
            try {
                muxer.release()
            } catch (_: Throwable) {
            }
            extractor.release()
            target.delete()
            throw failure
        }
    }
}
