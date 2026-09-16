/*
 * Copyright 2025 - 2026. All rights reserved.
 */
package com.pico.spatial.sample.hotlinechamber

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.Stage
import com.pico.spatial.sample.hotlinechamber.ui.ChamberStage
import com.pico.spatial.sample.hotlinechamber.ui.HomePanel

const val STAGE_ID = "HOTLINE_CHAMBER"

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        // 启动后的悬浮窗：提供进入玻璃房间的入口
        DefaultWindowContainer {
            PicoTheme {
                HomePanel()
            }
        }

        // 全空间沉浸 Stage：玻璃立方体房间
        Stage(id = STAGE_ID) {
            PicoTheme {
                ChamberStage()
            }
        }
    }
