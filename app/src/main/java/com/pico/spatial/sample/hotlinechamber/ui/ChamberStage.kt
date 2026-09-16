/*
 * Copyright 2025 - 2026. All rights reserved.
 */
package com.pico.spatial.sample.hotlinechamber.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.foundation.content.SpatialView

/**
 * 全空间沉浸 Stage 内容：加载并呈现玻璃立方体房间。
 *
 * room（静态）+ chair/sidetable/phone（已挂 InteractableComponent，可被手势直接抓取移动）。
 * 四个 GLB 按同一世界坐标导出，根实体施加相同落地偏移即可精确拼合。
 */
@Composable
fun ChamberStage() {
    val context = LocalContext.current
    var chamber by remember { mutableStateOf<ChamberModels.Chamber?>(null) }

    LaunchedEffect(Unit) {
        if (chamber == null) {
            chamber = ChamberModels.load(context)
        }
    }

    val current = chamber ?: return

    SpatialView(
        initial = { content, _ ->
            content.addEntity(current.room)
            current.movables.forEach { content.addEntity(it) }
        }
    )
}
