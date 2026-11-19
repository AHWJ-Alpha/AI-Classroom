package com.aiclassroom.classroom.domain

import com.aiclassroom.classroom.repository.ClassroomStore

/**
 * 执行租约。
 *
 * 目的只有一个：同一个任务在同一时刻只有一个**有效**执行者。
 * 没有它的话，后台重启、页面重进、通知重放都可能起第二个执行者，
 * 两边同时提交，后写的把先写的覆盖掉。
 *
 * 用 fencing token 而不是只用 owner：租约过期后新执行者拿到更大的 token，
 * 旧执行者即使从长时间阻塞里恢复，它的提交也会因为 token 落后而被拒。
 */
object RunLease {

    data class Lease(val owner: String, val token: Long, val until: EpochMillis)

    sealed interface Acquired {
        data class Granted(val lease: Lease) : Acquired
        /** 别人还持有有效租约。 */
        data class Busy(val owner: String, val until: EpochMillis) : Acquired
        data class NotFound(val reason: String) : Acquired
    }

    /**
     * 抢占执行权。租约已过期或属于自己时可以直接接管；否则返回 Busy。
     */
    fun acquire(
        store: ClassroomStore,
        runId: RunId,
        owner: String,
        ttlMillis: Long,
        clock: () -> EpochMillis,
    ): Acquired {
        val now = clock()
        return store.transaction {
            val run = store.run(runId) ?: return@transaction Acquired.NotFound("任务不存在")
            val held = run.owner
            val valid = held != null && held != owner && run.leaseUntil > now
            if (valid) {
                return@transaction Acquired.Busy(held!!, run.leaseUntil)
            }
            // 换执行者（或续自己的租约）：token 只在真正换人时递增。
            val token = if (held == null || held == owner) run.fencingToken else run.fencingToken + 1
            val until = now + ttlMillis
            store.saveRun(
                run.copy(
                    owner = owner,
                    leaseUntil = until,
                    fencingToken = token,
                    status = if (run.status == RunStatus.QUEUED) RunStatus.RUNNING else run.status,
                    updatedAt = now,
                ),
            )
            Acquired.Granted(Lease(owner, token, until))
        }
    }

    /** 续租；不是自己的租约不能续。 */
    fun renew(
        store: ClassroomStore,
        runId: RunId,
        owner: String,
        ttlMillis: Long,
        clock: () -> EpochMillis,
    ): Boolean {
        val now = clock()
        return store.transaction {
            val run = store.run(runId) ?: return@transaction false
            if (run.owner != owner) return@transaction false
            store.saveRun(run.copy(leaseUntil = now + ttlMillis, updatedAt = now))
            true
        }
    }

    /**
     * 提交前的有效性检查：owner 与 token 都要对，且租约没过期。
     *
     * 租约过期也拒绝 —— 说明这段时间里可能有别人接手了。
     */
    fun validate(
        store: ClassroomStore,
        runId: RunId,
        owner: String,
    token: Long,
        clock: () -> EpochMillis,
    ): Validation {
        val now = clock()
        val run = store.run(runId) ?: return Validation.Invalid("任务不存在")
        if (run.owner != null && run.owner != owner) {
            return Validation.Invalid("执行者已变更（当前 " + run.owner + "）")
        }
        if (run.fencingToken != token) {
            return Validation.Invalid("执行令牌已过期（期望 " + run.fencingToken + "，收到 " + token + "）")
        }
        if (run.leaseUntil in 1 until now) {
            return Validation.Invalid("租约已过期")
        }
        return Validation.Valid
    }

    sealed interface Validation {
        data object Valid : Validation
        data class Invalid(val reason: String) : Validation
    }

    /** 主动释放：把 owner 清掉，token 保持不变（下一次换人时才递增）。 */
    fun release(store: ClassroomStore, runId: RunId, owner: String, clock: () -> EpochMillis) {
        store.transaction {
            val run = store.run(runId) ?: return@transaction
            if (run.owner != owner) return@transaction
            store.saveRun(run.copy(owner = null, leaseUntil = 0, updatedAt = clock()))
        }
    }
}
