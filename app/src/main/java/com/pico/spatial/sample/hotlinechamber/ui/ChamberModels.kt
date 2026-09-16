/*
 * Copyright 2025 - 2026. All rights reserved.
 */
package com.pico.spatial.sample.hotlinechamber.ui

import android.content.Context
import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从 assets 根目录加载 Blender 导出的 4 个 PBR GLB。
 *
 * 加载方式严格对齐官方示例（welcomespace / animation 0.10.7）：
 * 资源放 src/main/assets 根目录，用 `Entity.load("asset://xxx.glb")` 字符串 URI 加载，
 * 而不是 assets.open() 的 InputStream 重载——后者不会正确注册渲染资源，
 * 在 SpatialView 里会报 “createSpatialView batch, entity invalid” 并导致渲染进程被杀。
 *
 * 坐标约定（实测 4 个 GLB 的包围盒/根节点 TRS 得出）：
 *  - 4 个 GLB 在 Blender 中按【同一世界坐标】导出：room 地板顶面约 y=0（整体 y∈[-0.64, 3.59]），
 *    椅子根节点 (-1.15, 0, -0.55)、椅脚 y≈0.016；边桌 (1.12, 0, -0.55)；电话 (1.12, 0.755, -0.55)
 *    正好落在桌面上。它们天然拼合，【绝不能】用 setPosition(常量) 整体覆盖根节点，
 *    否则 GLB 自带摆放被抹掉、全部叠到原点（历史 bug：椅桌悬空、天花板压脸、灯板乱飞）。
 *  - Stage 坐标系：原点 = 用户脚下，+Z 朝向用户，前方是 -Z，地面 y=0（SDK 无相机 API，
 *    官方 welcomespace 的做法是“挪场景不挪人”：把房间根平移到 (0.15, 0, -3.6)）。
 *  - 所以这里只在每个实体【现有的 GLB 坐标】上叠加同一个前向偏移 SCENE_Z_OFFSET，
 *    保留各自的 x/y 与朝向：家具区被送到用户正前约 3.5m，地板仍与用户脚平齐。
 */
object ChamberModels {
    private const val TAG = "HotlineChamber"

    // 整体把场景沿前方 -Z 平移的距离（米）。家具 GLB 内 z≈-0.55，故最终落在约 -3.55m。
    private const val SCENE_Z_OFFSET = -3.0f

    data class Chamber(
        val room: Entity,
        val movables: List<Entity>,
        val failures: List<String>,
    )

    private data class Spec(val assetUri: String, val movable: Boolean)

    private val SPECS =
        listOf(
            Spec("asset://room.glb", movable = false),
            Spec("asset://chair.glb", movable = true),
            Spec("asset://sidetable.glb", movable = true),
            Spec("asset://phone.glb", movable = true),
        )

    suspend fun load(@Suppress("UNUSED_PARAMETER") context: Context): Chamber {
        val movables = mutableListOf<Entity>()
        val failures = mutableListOf<String>()
        var room: Entity? = null

        for (spec in SPECS) {
            try {
                // 文件 IO / 解码放后台线程
                val entity = withContext(Dispatchers.IO) { Entity.load(spec.assetUri) }

                // 组件访问必须在 Android 主线程（SDK 强制约束）。
                withContext(Dispatchers.Main) {
                    // 在 GLB 自带坐标上叠加前向偏移，而不是覆盖。
                    // 有 TransformComponent（椅/桌/电话根节点带 t）→ 读出原位置仅改 z；
                    // 无组件（多根场景的虚拟根）→ 新建一个只含前向平移的组件。
                    val existing = entity.components[TransformComponent::class.java]
                    if (existing != null) {
                        val p = existing.position
                        existing.setPosition(Vector3(p.x, p.y, p.z + SCENE_Z_OFFSET))
                    } else {
                        entity.components.set(
                            TransformComponent(
                                Vector3(0f, 0f, SCENE_Z_OFFSET),
                                EulerAngles(0f, 0f, 0f),
                                Vector3(1f, 1f, 1f),
                            ),
                        )
                    }

                    if (spec.movable) {
                        // 声明为可交互实体，使其可被手势/射线选中并直接抓取移动
                        entity.components.set(InteractableComponent())
                        movables.add(entity)
                    } else {
                        room = entity
                    }
                }
                Log.i(TAG, "已加载模型: ${spec.assetUri}")
            } catch (e: Exception) {
                Log.e(TAG, "加载失败: ${spec.assetUri}", e)
                failures.add(spec.assetUri)
            }
        }

        val finalRoom = room
        Log.i(
            TAG,
            "场景就位: 前向偏移 z=$SCENE_Z_OFFSET，可交互 ${movables.size} 个，失败 ${failures.size} 个",
        )

        // room 理论上必然存在；兜底用一个空实体避免渲染层异常
        return Chamber(
            room = finalRoom ?: Entity(),
            movables = movables,
            failures = failures,
        )
    }
}
