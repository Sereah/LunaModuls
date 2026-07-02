package com.lunacattus.statemachine.sample

import com.lunacattus.statemachine.StateMachineLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 基于 Kotlin Coroutines [Channel] 的轻量级平级状态机。
 *
 * ## 设计思路
 * - **平级状态**：所有状态无父子层级，事件不会向上冒泡
 * - **线程安全**：所有事件在单协程中串行处理，无并发问题
 * - **结构化并发**：绑定 [CoroutineScope]，自动处理生命周期
 * - **可观察**：通过 [currentState] StateFlow 实时监听状态变化
 * - **类型安全**：事件通过泛型 [E] 约束，编译期类型检查
 *
 * ## 使用示例
 *
 * ```
 * // 1. 定义事件
 * sealed interface Event {
 *     data object Start : Event
 *     data object Stop : Event
 * }
 *
 * // 2. 定义状态
 * class IdleState(private val machine: SampleStateMachine<Event>) : ISampleState<Event> {
 *     override val name = "Idle"
 *     override suspend fun enter() = println("Idle.enter")
 *     override suspend fun exit() = println("Idle.exit")
 *     override suspend fun processEvent(event: Event): Boolean = when (event) {
 *         is Event.Start -> {
 *             machine.transitionTo(RunningState(machine))
 *             true
 *         }
 *         else -> false
 *     }
 * }
 *
 * class RunningState(private val machine: SampleStateMachine<Event>) : ISampleState<Event> {
 *     override val name = "Running"
 *     override suspend fun enter() = println("Running.enter")
 *     override suspend fun exit() = println("Running.exit")
 *     override suspend fun processEvent(event: Event): Boolean = when (event) {
 *         is Event.Stop -> {
 *             machine.transitionTo(IdleState(machine))
 *             true
 *         }
 *         else -> false
 *     }
 * }
 *
 * // 3. 构建与使用
 * val scope = CoroutineScope(Dispatchers.Default)
 * val machine = SampleStateMachine<Event>(scope, "Demo")
 * machine.addState(IdleState(machine))
 * machine.addState(RunningState(machine))
 * machine.setInitialState(IdleState(machine))
 * machine.start()
 *
 * machine.sendEvent(Event.Start) // Idle → Running
 * machine.sendEvent(Event.Stop)  // Running → Idle
 * machine.quit()
 * ```
 *
 * @param E 事件类型，推荐使用 sealed class / interface
 * @param scope 绑定的协程作用域，通常传 viewModelScope 或 lifecycleScope
 * @param name 状态机名称，用于日志标识
 */
open class SampleStateMachine<E : Any> @JvmOverloads constructor(
    private val scope: CoroutineScope,
    protected val name: String = "SampleStateMachine"
) {

    private val eventChannel = Channel<E>(Channel.UNLIMITED)

    private val deferredEvents = ArrayDeque<E>()

    private val states = mutableMapOf<String, ISampleState<E>>()

    private val _currentState = MutableStateFlow<ISampleState<E>?>(null)
    val currentState: StateFlow<ISampleState<E>?> = _currentState.asStateFlow()

    private var initialState: ISampleState<E>? = null

    private var currentStateRef: ISampleState<E>? = null

    private var targetState: ISampleState<E>? = null

    @Volatile
    private var started = false

    @Volatile
    private var quit = false

    private var eventLoopJob: Job? = null

    fun addState(state: ISampleState<E>) {
        states[state.name] = state
    }

    fun setInitialState(state: ISampleState<E>) {
        initialState = state
    }

    fun start() {
        check(initialState != null) {
            "$name: initialState must be set before start()"
        }
        if (started) return
        started = true

        eventLoopJob = scope.launch {
            val init = initialState ?: return@launch
            currentStateRef = init
            _currentState.value = init
            try {
                init.enter()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onStateEnterError(init, e)
            }

            for (event in eventChannel) {
                if (quit) break

                val current = currentStateRef ?: break
                var handled = false
                try {
                    handled = current.processEvent(event)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onEventError(event, current, e)
                }

                if (!handled) {
                    onUnhandledEvent(event, current)
                }

                performTransitionIfNeeded()
            }
        }
    }

    fun sendEvent(event: E) {
        if (quit) return
        if (!started) {
            StateMachineLog.d(name, "WARN: sendEvent() called before start(), event will be buffered")
        }
        eventChannel.trySend(event)
    }

    fun transitionTo(state: ISampleState<E>) {
        targetState = state
    }

    fun deferEvent(event: E) {
        deferredEvents.addLast(event)
    }

    fun quit() {
        if (quit) return
        quit = true
        eventChannel.close()
        deferredEvents.clear()
        targetState = null
        val current = currentStateRef
        if (current != null) {
            currentStateRef = null
            _currentState.value = null
            scope.launch {
                try {
                    current.exit()
                } catch (_: CancellationException) {
                } catch (_: Exception) {
                }
            }
        }
        onQuitting()
    }

    fun getCurrentState(): ISampleState<E>? = currentStateRef

    fun isStarted(): Boolean = started

    fun isQuit(): Boolean = quit

    private suspend fun performTransitionIfNeeded() {
        while (targetState != null) {
            val dest = targetState!!
            targetState = null

            val old = currentStateRef

            try {
                old?.exit()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onStateExitError(old ?: dest, e)
            }

            currentStateRef = dest
            _currentState.value = dest

            try {
                dest.enter()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onStateEnterError(dest, e)
            }

            flushDeferredEvents()
        }
    }

    private fun flushDeferredEvents() {
        while (deferredEvents.isNotEmpty()) {
            val event = deferredEvents.removeFirst()
            eventChannel.trySend(event)
        }
    }

    protected open fun onUnhandledEvent(event: E, state: ISampleState<E>) = Unit

    protected open fun onEventError(event: E, state: ISampleState<E>, e: Exception) {
        StateMachineLog.e(name, "Event error in ${state.name}: $event", e)
    }

    protected open fun onStateEnterError(state: ISampleState<E>, e: Exception) {
        StateMachineLog.e(name, "State enter error: ${state.name}", e)
    }

    protected open fun onStateExitError(state: ISampleState<E>, e: Exception) {
        StateMachineLog.e(name, "State exit error: ${state.name}", e)
    }

    protected open fun onQuitting() = Unit
}
