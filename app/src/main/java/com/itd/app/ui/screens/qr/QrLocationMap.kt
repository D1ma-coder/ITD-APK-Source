package com.itd.app.ui.screens.qr

import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import kotlin.math.cos
import kotlin.math.max

@Composable
fun QrLocationMap(latitude: Double, longitude: Double, accuracyKm: Double, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val dark = isSystemInDarkTheme()
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }
    DisposableEffect(mapView, owner) {
        var started = false
        var resumed = false
        fun sync() {
            val state = owner.lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.STARTED) && !started) { mapView.onStart(); started = true }
            if (state.isAtLeast(Lifecycle.State.RESUMED) && !resumed) { mapView.onResume(); resumed = true }
            if (!state.isAtLeast(Lifecycle.State.RESUMED) && resumed) { mapView.onPause(); resumed = false }
            if (!state.isAtLeast(Lifecycle.State.STARTED) && started) { mapView.onStop(); started = false }
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        owner.lifecycle.addObserver(observer)
        sync()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(mapView, latitude, longitude, accuracyKm, dark) {
        mapView.getMapAsync { map ->
            map.setStyle("https://tiles.openfreemap.org/styles/${if (dark) "dark" else "positron"}") {
                map.uiSettings.apply {
                    setAllGesturesEnabled(false)
                    isCompassEnabled = false
                    isLogoEnabled = false
                    isAttributionEnabled = false
                }
                val latRadius = accuracyKm / 111.32
                val lonRadius = accuracyKm / (max(cos(Math.toRadians(latitude)), 0.01) * 111.32)
                val bounds = LatLngBounds.Builder()
                    .include(LatLng((latitude - latRadius).coerceIn(-90.0, 90.0), longitude - lonRadius))
                    .include(LatLng((latitude + latRadius).coerceIn(-90.0, 90.0), longitude + lonRadius)).build()
                mapView.post { map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0)) }
            }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}
