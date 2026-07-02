package com.lunacattus.statemachine.sample

/**
 * 状态接口。
 *
 * 所有状态平级，无父子层级关系。
 * 每个状态独立处理事件，事件不会冒泡。
 *
 * @param E 事件类型
 */
interface ISampleState<E : Any> {

    /** 进入状态时调用。适合在此做初始化工作（如启动定时器、发起网络请求）。 */
    suspend fun enter()

    /** 离开状态时调用。适合在此做清理工作（如释放资源、取消协程）。 */
    suspend fun exit()

    /**
     * 处理事件。
     *
     * @param event 待处理的事件
     * @return true 表示事件已被消费；false 表示未消费（状态机会调用 [SampleStateMachine.onUnhandledEvent]）
     */
    suspend fun processEvent(event: E): Boolean

    /** 状态名称，默认取类名，建议重写为有业务含义的名称 */
    val name: String
}
