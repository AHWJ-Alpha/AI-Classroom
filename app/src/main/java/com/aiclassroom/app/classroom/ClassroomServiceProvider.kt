package com.aiclassroom.app.classroom

import android.content.Context
import com.aiclassroom.classroom.repository.ClassroomStore
import com.aiclassroom.classroom.store.FileClassroomStore
import java.io.File

/**
 * 进程内唯一的课堂仓库。
 *
 * 页面、后台任务、通知操作都必须通过它拿同一个实例：
 * 各自 new 一个 FileClassroomStore 会让同一份 JSON 出现多份内存快照，
 * 谁后写谁就把别人的提交抹掉。
 */
object ClassroomServiceProvider {

    @Volatile
    private var storeInstance: ClassroomStore? = null

    fun store(context: Context): ClassroomStore {
        storeInstance?.let { return it }
        return synchronized(this) {
            storeInstance ?: FileClassroomStore(
                File(context.applicationContext.filesDir, "classroom"),
            ).also { storeInstance = it }
        }
    }

    fun service(context: Context): ClassroomService = ClassroomService(context)

    /** 仅供测试或进程重置使用。 */
    fun reset() {
        storeInstance = null
    }
}