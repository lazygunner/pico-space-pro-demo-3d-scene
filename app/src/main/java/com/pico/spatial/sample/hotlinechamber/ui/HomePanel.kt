/*
 * Copyright 2025 - 2026. All rights reserved.
 */
package com.pico.spatial.sample.hotlinechamber.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.sample.hotlinechamber.STAGE_ID
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.vibrant.Vibrant
import com.pico.spatial.ui.foundation.vibrant.vibrantEffect
import com.pico.spatial.ui.graphics.Vibrant
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.StageStyle
import com.pico.spatial.ui.platform.containers.openStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 启动悬浮窗：毛玻璃卡片 + 标题/副标题 + 进入房间按钮。
 *
 * 窗口主题是 Translucent，若内容直接铺透明底，文字会与背后的浅色 passthrough 叠在一起
 * 几乎不可读。因此整体用一张 Vibrant 材质卡片（系统合成器保证与背景的对比），
 * 文字走 Vibrant 色板，按钮用 design 包的 Button（IconButton 会把文字裁断）。
 */
@Composable
fun HomePanel() {
    val spatialNavigator = LocalSpatialNavigator.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp)
            // Vibrant 毛玻璃卡片：圆角 + 材质，passthrough 上自动保证对比度
            .clip(RoundedCornerShape(32.dp))
            .vibrantEffect(Vibrant.Neutral)
            .background(Color.Vibrant)
            .padding(horizontal = 56.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Hotline Chamber",
            modifier = Modifier.vibrantEffect(Vibrant.Semidark),
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Vibrant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Control 风格复古未来主义 · 玻璃立方体热线房间",
            modifier = Modifier.vibrantEffect(Vibrant.Darker),
            fontSize = 22.sp,
            color = Color.Vibrant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(44.dp))
        Button(
            colors = ButtonDefaults.buttonColors(
                containerColor = PicoTheme.colorScheme.fillPrimary,
                contentColor = Color.White,
            ),
            onClick = {
                coroutineScope.launch(Dispatchers.Main.immediate) {
                    // Full 全沉浸：房间以脚底原点世界锁定，不随头部/视角移动。
                    // Mixed 是透视 MR，模拟器无真实空间追踪会导致内容半跟随头部。
                    spatialNavigator.openStage(STAGE_ID, StageStyle.Full)
                }
            },
        ) {
            Text(
                text = "进入房间",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "进入后用手势捏合：可抓取并移动电话、圆几、皮椅",
            modifier = Modifier.vibrantEffect(Vibrant.Darker),
            fontSize = 17.sp,
            color = Color.Vibrant,
            textAlign = TextAlign.Center,
        )
    }
}
