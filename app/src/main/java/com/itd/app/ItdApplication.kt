package com.itd.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import com.itd.app.di.AppContainer
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import io.sentry.android.core.SentryAndroid
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.itd.app.halloween.HalloweenPortalManager

class ItdApplication : Application(), ImageLoaderFactory {
    val container by lazy { AppContainer(this) }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this).components {
        if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        add(VideoFrameDecoder.Factory())
        add(SvgDecoder.Factory())
    }.build()

    override fun onCreate() {
        super.onCreate()
        timber.log.Timber.plant(com.itd.app.util.DiagnosticsTree())
        SentryAndroid.init(this) { options ->
            options.dsn = "https://0ef659a7bb1d05e0b1c03546baa9fa7e@sentry.xn--d1ah4a.com/3"
            options.environment = "release"
            options.release = "com.itd.app@1.1.0+28"
        }
        AppMetrica.activate(this, AppMetricaConfig.newConfigBuilder("880189b6-adc7-43b5-b71b-02cce1f8877e").build())
        AnnouncementStore.init(this)
        com.itd.app.halloween.HalloweenFlags.init(this)
        com.itd.app.halloween.HalloweenSounds.context = applicationContext
        com.itd.app.ui.components.RemoteIcons.refresh(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                com.itd.app.ui.components.RemoteIcons.refresh(this@ItdApplication)
                HalloweenPortalManager.setForeground(true)
                com.itd.app.halloween.HalloweenNotices.setForeground(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                HalloweenPortalManager.setForeground(false)
                com.itd.app.halloween.HalloweenNotices.setForeground(false)
            }
        })
    }
}

/** Remembers dismissed platform announcements. */
object AnnouncementStore {
    var preferences: SharedPreferences? = null
        private set

    fun init(context: Context) {
        if (preferences == null) preferences = context.applicationContext.getSharedPreferences("announcements", Context.MODE_PRIVATE)
    }
}
