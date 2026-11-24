package com.aiclassroom.classroom.exec

import com.aiclassroom.classroom.domain.*
import com.aiclassroom.classroom.repository.ClassroomStore

/** 单个单元的执行能力。抽成接口，编排器就能用假实现测各种路径。 */
interface UnitRunner {
    suspend fun execute(projectId: ProjectId, unitId: UnitId, runId: RunId): UnitOutcome
}

/** 任务控制：暂停与取消都由外部信号决定，编排器每轮检查一次。 */
interface RunControl {
    /** 返回 false 表示应当停下来（暂停或取消）。 */
    fun shouldContinue(): Boolean

    /**
     * 本次停下是用户**明确取消**，而不是暂停。
     *
     * 分开是必要的：暂停可以继续，取消不该被恢复成「还有待办」；
     * 两者的事件类型与界面文案也不一样。
     */
    fun isCancelled(): Boolean = false
}

sealed interface RunOutcome {
    /** 所有单元都提交完成。 */
    data class Completed(val committedUnits: Int) : RunOutcome
    /** 暂停原因：用户主动暂停，还是网络断了被动暂停。两者恢复方式不同。 */
    enum class PauseReason { USER, NETWORK }

    /** 被暂停：已完成的内容保留，任务可恢复。 */
    data class Paused(
        val committedUnits: Int,
        val reason: PauseReason = PauseReason.USER,
    ) : RunOutcome
    /** 被取消：保留已提交内容，不等于删除项目。 */
    data class Cancelled(val committedUnits: Int) : RunOutcome
    /** 需要人处理：某个单元反复失败，后面的依赖单元不该继续硬跑。 */
    data class NeedsAttention(val committedUnits: Int, val reason: String) : RunOutcome
    /** 无单元可跑但也没全部完成（通常是依赖链断了）。 */
    data class Stalled(val committedUnits: Int, val reason: String) : RunOutcome
}

/**
 * 多单元编排。
 *
 * 三条硬规则：
 * 1. 依赖没满足的单元绝不跳过 —— 跳过一章还说「整部完成」是最糟的谎。
 * 2. 单个单元需要人处理时整个任务停下来，而不是继续往下跑把错误放大。
 * 3. 暂停/取消只停止后续，已提交内容一律保留。
 */
class RunEngine(
    private val store: ClassroomStore,
    private val runner: UnitRunner,
    private val control: RunControl,
    private val clock: () -> EpochMillis,
    private val maxUnitsPerRun: Int = 200,
    /** 执行者与令牌：写终态前校验，旧执行者不能覆盖新任务。 */
    private val owner: String? = null,
    private val fenceToken: Long? = null,
    /**
     * 只跑这一节：给「单节生成」按钮用。
     *
     * 为空时按依赖顺序跑完整项目；指定后就只处理这一个单元，
     * 跑完立刻收尾，不会顺带把后面的节也写掉。
     */
    private val onlyUnitId: UnitId? = null,
) {

    suspend fun run(projectId: ProjectId, runId: RunId): RunOutcome {
        var committed = 0
        var executed = 0
        // 跳过的单元与原因：一轮跑完要如实报告，不能装作全部成功。
        val skipped = mutableListOf<Pair<UnitId, String>>()

        while (true) {
            if (!control.shouldContinue()) {
                // 取消与暂停必须分开：状态、事件和界面文案都不一样。
                val cancelled = control.isCancelled()
                val status = if (cancelled) RunStatus.CANCELLED else RunStatus.PAUSED
                val stoppedRun = store.run(runId)?.copy(status = status, updatedAt = clock())
                if (stoppedRun != null) store.saveRun(stoppedRun)
                return if (cancelled) RunOutcome.Cancelled(committed)
                else RunOutcome.Paused(committed)
            }
            if (executed >= maxUnitsPerRun) {
                return RunOutcome.Stalled(committed, "超过单次任务的单元上限 " + maxUnitsPerRun)
            }

            val units = store.units(projectId)
            // 跳过已经失败过的单元：否则它会一直被选中，同一节反复重试到死循环。
            val skipIds = skipped.map { it.first }.toSet()
            val candidates = units.filterNot { it.id in skipIds }
            val next = if (onlyUnitId != null) {
                units.firstOrNull { it.id == onlyUnitId && it.status != UnitStatus.READY }
            } else {
                UnitExecutor.nextReadyUnit(candidates)
            }
            if (next == null) {
                // 只剩被跳过的单元也算「没有可跑的」：它们已在 skipped 里报告过。
                val remaining = candidates.filter { it.status != UnitStatus.READY }
                return when {
                    remaining.isEmpty() -> {
                        // 状态与结果事件必须同一次提交：分开写的话，
                        // 崩在中间会让 run 停在 COMPLETED 却永远没有事件。
                        store.project(projectId)?.let { project ->
                            RunFinalizer.finish(
                                store = store,
                                project = project,
                                run = store.run(runId),
                                // 有跳过的单元就不是「全部完成」：生命周期与文案都要如实。
                                runStatus = if (skipped.isEmpty()) RunStatus.COMPLETED else RunStatus.WAITING_INPUT,
                                projectLifecycle = if (skipped.isEmpty()) ProjectLifecycle.READY else ProjectLifecycle.NEEDS_ATTENTION,
                                type = if (skipped.isEmpty()) EventType.COMPLETED else EventType.NEEDS_ATTENTION,
                                summary = if (skipped.isEmpty()) "共 " + committed + " 节"
                                    else "共 " + committed + " 节，另有 " + skipped.size + " 节待处理：" +
                                        skipped.joinToString("；") { it.second }.take(120),
                                committedUnits = committed,
                                clock = clock,
                                owner = owner,
                                fenceToken = fenceToken,
                            )
                        }
                        RunOutcome.Completed(committed)
                    }
                    else -> RunOutcome.Stalled(
                        committed,
                        "还有 " + remaining.size + " 个单元无法开始（依赖未完成）",
                    )
                }
            }

            executed++
            when (val outcome = runner.execute(projectId, next.id, runId)) {
                is UnitOutcome.Committed -> {
                    committed++
                    store.run(runId)?.let {
                        store.saveRun(it.copy(completedUnits = committed, currentUnitId = next.id, updatedAt = clock()))
                    }
                }
                is UnitOutcome.AlreadyDone -> Unit
                is UnitOutcome.Failed -> {
                    // 一节失败不该拖垮整轮：记下原因，跳过它继续下一节。
                    // 以前这里直接 return，表现为「生成完一节就断」。
                    // 必须记进 skipped，否则 nextReadyUnit 会一直选中同一节，死循环。
                    // 不写进 run.error：那个字段是给「这一轮整体出了什么事」用的，
                    // 界面把它当红字显示。逐节写进去会让用户每写完一节就闪一条错误，
                    // 随后又被下一节盖掉 —— 看起来像莫名其妙一直在报错。
                    // 跳过原因在收尾时统一汇报（见 RunFinalizer 的 summary）。
                    skipped += next.id to outcome.reason
                }
                is UnitOutcome.NetworkInterrupted -> {
                    // 网络断了不是任务出错：把 run 标成暂停（可继续），
                    // 由 Worker 交给 WorkManager 退避重试，已提交的节一律保留。
                    store.run(runId)?.let {
                        store.saveRun(
                            it.copy(
                                status = RunStatus.PAUSED,
                                error = "网络中断，稍后自动继续：" + outcome.reason,
                                updatedAt = clock(),
                                completedUnits = committed,
                            ),
                        )
                    }
                    return RunOutcome.Paused(committed, RunOutcome.PauseReason.NETWORK)
                }
                is UnitOutcome.NeedsAttention -> {
                    // 同样跳过：这一节留在待处理，后面的节照常写。
                    // 同样不写 run.error：理由同上，逐节刷红字会被误读成持续报错。
                    skipped += next.id to outcome.reason
                }
            }
        }
    }
}
