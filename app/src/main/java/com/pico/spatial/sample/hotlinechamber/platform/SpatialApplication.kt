/*
 * Copyright 2025 - 2026. All rights reserved.
 */
package com.pico.spatial.sample.hotlinechamber.platform

import android.app.Application
import com.pico.spatial.sample.hotlinechamber.mainApp
import com.pico.spatial.ui.foundation.dsl.launch

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
