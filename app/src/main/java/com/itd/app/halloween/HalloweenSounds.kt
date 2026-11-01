package com.itd.app.halloween

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HalloweenSound { WINGS, FEATHER, DOOR, HOWL, CANDY }

/** Short event sound effects downloaded once into the cache and played through a small SoundPool. */
object HalloweenSounds {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pool: SoundPool? = null
    var context: Context? = null
    private val soundIds = HashMap<HalloweenSound, Int>()
    private val loaded = HashSet<Int>()
    private val loading = HashSet<HalloweenSound>()
    private val lastAmbientAt = HashMap<HalloweenSound, Long>()

    @Volatile
    var enabled = false
        private set

    /** Notification-triggered sounds play with a 25% chance and at most once per 30 s each; [forced] sounds always play. */
    fun play(sound: HalloweenSound, forced: Boolean) {
        if (!enabled) return
        if (!forced) {
            val now = System.currentTimeMillis()
            val last = lastAmbientAt[sound]
            if ((last != null && now - last < 30_000) || Math.random() >= 0.25) return
            lastAmbientAt[sound] = now
        }
        val id = soundIds[sound]
        if (id == null || id !in loaded) {
            preload()
            return
        }
        pool?.play(id, 0.5f, 0.5f, 1, 0, 1.0f)
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (enabled) {
            preload()
            return
        }
        pool?.release()
        pool = null
        soundIds.clear()
        loaded.clear()
        loading.clear()
    }

    private fun preload() {
        val context = context ?: return
        val soundPool = pool ?: SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
            .also { created ->
                created.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded.add(sampleId) }
                pool = created
            }
        for (sound in HalloweenSound.entries) {
            if (soundIds.containsKey(sound) || !loading.add(sound)) continue
            scope.launch {
                val file = withContext(Dispatchers.IO) { download(context, sound) }
                if (file != null && pool === soundPool) soundIds[sound] = soundPool.load(file.path, 1)
                loading.remove(sound)
            }
        }
    }

    private fun download(context: Context, sound: HalloweenSound): File? = try {
        val name = sound.name.lowercase(Locale.ROOT)
        val file = File(context.cacheDir, "halloween-sfx-$name.mp3")
        if (file.length() > 0) {
            file
        } else {
            val part = File(file.path + ".part")
            URL("https://xn--d1ah4a.com/public/events/halloween/sfx/$name.mp3").openStream().use { input ->
                FileOutputStream(part).use { output -> input.copyTo(output) }
            }
            if (part.renameTo(file)) file else null
        }
    } catch (_: Throwable) {
        null
    }
}
