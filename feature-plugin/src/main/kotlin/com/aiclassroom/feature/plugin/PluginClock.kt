package com.aiclassroom.feature.plugin

/**
 * 宿主时间源：ctx.now() 用它，因此测试 / 演示可以伪造时间推进。
 */
fun interface PluginClock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM: PluginClock = PluginClock { System.currentTimeMillis() }
    }
}
