package com.lunacattus.statemachine.sample

/**
 * [ISampleState] 的空实现基类。
 *
 * 所有方法默认空实现，[processEvent] 返回 false。
 * 继承此类只需重写需要的方法即可。
 */
open class SimpleState<E : Any> : ISampleState<E> {

    override suspend fun enter() = Unit

    override suspend fun exit() = Unit

    override suspend fun processEvent(event: E): Boolean = false

    override val name: String get() = this::class.simpleName ?: "SimpleState"
}
